package kz.dentify.iam.internal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import kz.dentify.clinic.api.DemoClinic;
import kz.dentify.platform.api.EmailAddress;
import kz.dentify.platform.api.FieldViolation;
import kz.dentify.platform.api.InvalidRequestException;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dev-сид владельца (только профиль {@code dev}): для локального входа и демо этапа 0 — демо-клиника
 * ({@link DemoClinic}), вымышленный аккаунт {@code owner@demo.dentify.kz} и его активное членство в ней на все
 * филиалы.
 * <p>
 * Пароль — из {@code DENTIFY_DEV_OWNER_PASSWORD} в {@code deploy/.env}: его создаёт {@code deploy/dev.ps1 up}
 * (или {@code dev.sh}) случайным для машины разработчика, в репозитории пароля нет. Нет переменной — сид пропускается с
 * WARN. Сид идемпотентный: есть аккаунт — только дозаводит членство. Пароль уже созданного аккаунта не меняет; новый
 * пароль из {@code .env} — после {@code deploy/dev.ps1 reset-db -Force}.
 * <p>
 * Роль «Владелец» появится с каталогом прав (T-IAM-06), до этого у членства роли нет.
 */
@Component
@Profile("dev")
class DemoOwnerSeed implements ApplicationRunner {

	static final String OWNER_EMAIL = "owner@demo.dentify.kz";

	static final String OWNER_NAME = "Демо Владелец";

	private static final Logger log = LoggerFactory.getLogger(DemoOwnerSeed.class);

	private final DemoClinic demoClinic;

	private final Accounts accounts;

	private final Memberships memberships;

	private final String password;

	DemoOwnerSeed(DemoClinic demoClinic, Accounts accounts, Memberships memberships,
			@Value("${dentify.dev.owner-password:}") String password) {
		this.demoClinic = demoClinic;
		this.accounts = accounts;
		this.memberships = memberships;
		this.password = password;
	}

	@Override
	public void run(ApplicationArguments args) {
		seed();
	}

	/** Демо-клиника, владелец и его членство; уже есть — не трогает. */
	void seed() {
		if (this.password.isBlank()) {
			log.warn("Dev-сид пропущен: нет DENTIFY_DEV_OWNER_PASSWORD в deploy/.env, запустите deploy/dev.ps1 up");
			return;
		}
		Tenant demo = this.demoClinic.ensure();
		Optional<Account> owner = owner();
		if (owner.isEmpty()) {
			return;
		}
		UUID ownerId = Objects.requireNonNull(owner.get().getId(), "id");
		TenantContext.run(demo, () -> {
			if (this.memberships.ofAccount(ownerId).isEmpty()) {
				Membership membership = this.memberships.add(ownerId, MembershipStatus.ACTIVE, List.of());
				log.info("Dev-сид: владелец {} получил доступ к демо-клинике, членство {}", ownerId, membership.getId());
			}
		});
	}

	/** Аккаунт владельца: найденный или новый; пусто, если пароль из .env не проходит правила пароля. */
	private Optional<Account> owner() {
		Optional<Account> existing = this.accounts.findByEmail(new EmailAddress(OWNER_EMAIL));
		if (existing.isPresent()) {
			return existing;
		}
		try {
			return Optional.of(this.accounts.create(new NewAccount(null, OWNER_EMAIL, OWNER_NAME, this.password)));
		}
		catch (InvalidRequestException ex) {
			log.warn("Dev-сид пропущен: DENTIFY_DEV_OWNER_PASSWORD не проходит правила пароля {}",
					ex.getViolations().stream().map(FieldViolation::code).toList());
			return Optional.empty();
		}
	}

}
