package kz.dentify.iam.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import kz.dentify.AbstractIntegrationTest;
import kz.dentify.clinic.TestClinics;
import kz.dentify.clinic.api.DemoClinic;
import kz.dentify.platform.api.EmailAddress;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dev-сид владельца: бин только в профиле dev, тест собирает его сам. Клинику сида даёт тестовая замена
 * {@link DemoClinic} — новая тестовая клиника; сама демо-клиника проверяется в тесте модуля clinic. Аккаунт владельца —
 * один на тестовую базу (e-mail постоянный), поэтому всё — в одном тесте и по порядку.
 */
class DemoOwnerSeedTests extends AbstractIntegrationTest {

	static final String DEV_PASSWORD = "Demo-Пароль-Машины-7";

	@Autowired
	Accounts accounts;

	@Autowired
	Memberships memberships;

	@Autowired
	TestClinics clinics;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void seedsOwnerWithMembershipOnceAndSkipsWithoutUsablePassword() {
		Tenant demo = this.clinics.create();
		DemoClinic demoClinic = () -> demo;
		EmailAddress email = new EmailAddress(DemoOwnerSeed.OWNER_EMAIL);

		new DemoOwnerSeed(() -> {
			throw new AssertionError("без пароля сид не трогает клинику");
		}, this.accounts, this.memberships, " ").seed();
		new DemoOwnerSeed(demoClinic, this.accounts, this.memberships, "12345678").seed();
		assertThat(this.accounts.findByEmail(email)).as("пароль не прошёл правила — аккаунта нет").isEmpty();

		DemoOwnerSeed seed = new DemoOwnerSeed(demoClinic, this.accounts, this.memberships, DEV_PASSWORD);
		seed.seed();
		seed.seed();

		Account owner = this.accounts.findByEmail(email).orElseThrow();
		UUID ownerId = owner.getId();
		assertThat(owner.getFullName()).isEqualTo(DemoOwnerSeed.OWNER_NAME);
		assertThat(owner.getPhone()).isNull();
		assertThat(this.accounts.passwordMatches(owner, DEV_PASSWORD)).isTrue();
		assertThat(this.jdbc.queryForObject("select count(*) from account where lower(email) = ?", Long.class,
				DemoOwnerSeed.OWNER_EMAIL))
			.isOne();
		TenantContext.run(demo, () -> {
			Membership membership = this.memberships.ofAccount(ownerId).orElseThrow();
			assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
			assertThat(membership.getBranchIds()).as("все филиалы").isEmpty();
			assertThat(this.memberships.list()).hasSize(1);
		});

		new DemoOwnerSeed(demoClinic, this.accounts, this.memberships, "Другой-Пароль-99").seed();
		assertThat(this.accounts.passwordMatches(this.accounts.get(ownerId), DEV_PASSWORD))
			.as("пароль созданного владельца сид не меняет").isTrue();
	}

}
