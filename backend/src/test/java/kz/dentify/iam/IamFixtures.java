package kz.dentify.iam;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import kz.dentify.iam.internal.Accounts;
import kz.dentify.iam.internal.MembershipStatus;
import kz.dentify.iam.internal.Memberships;
import kz.dentify.iam.internal.NewAccount;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Вымышленные люди для тестов входа и выбора клиники: аккаунт с телефоном, e-mail и паролем и его членства в тестовых
 * клиниках ({@code clinic.TestClinics}). Отключение аккаунта и членства — запросами SQL, пока у iam нет API деактивации
 * (T-IAM-11). Только тестовые исходники.
 */
@Component
class IamFixtures {

	/** Вымышленный пароль всех тестовых людей: проходит правила пароля. */
	static final String PASSWORD = "Синий-чемодан-17";

	private final Accounts accounts;

	private final Memberships memberships;

	private final JdbcTemplate jdbc;

	IamFixtures(Accounts accounts, Memberships memberships, JdbcTemplate jdbc) {
		this.accounts = accounts;
		this.memberships = memberships;
		this.jdbc = jdbc;
	}

	/** Новый активный аккаунт со случайными телефоном +7 707… и e-mail. */
	Person person(String fullName) {
		String digits = "707" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		String email = "person." + UUID.randomUUID() + "@example.kz";
		UUID id = Objects.requireNonNull(
				this.accounts.create(new NewAccount("8" + digits, email, fullName, PASSWORD)).getId());
		return new Person(id, digits, email);
	}

	/**
	 * Членство в клинике.
	 *
	 * @param branchIds филиалы доступа; пусто — все филиалы
	 * @return id членства
	 */
	UUID join(Person person, Tenant clinic, MembershipStatus status, UUID... branchIds) {
		return TenantContext.call(Tenant.of(clinic.clinicId()), () -> Objects
			.requireNonNull(this.memberships.add(person.id(), status, List.of(branchIds)).getId()));
	}

	void disableAccount(UUID accountId) {
		this.jdbc.update("update account set status = 'DISABLED' where id = ?", accountId);
	}

	void disableMembership(Tenant clinic, UUID membershipId) {
		TenantContext.run(Tenant.of(clinic.clinicId()),
				() -> this.jdbc.update("update membership set status = 'DISABLED' where id = ?", membershipId));
	}

	/**
	 * Вымышленный человек.
	 *
	 * @param id аккаунт
	 * @param phoneDigits десять цифр мобильного без +7: 7071234567
	 * @param email e-mail в нижнем регистре
	 */
	record Person(UUID id, String phoneDigits, String email) {

		/** Телефон в E.164. */
		String phone() {
			return "+7" + this.phoneDigits;
		}

	}

}
