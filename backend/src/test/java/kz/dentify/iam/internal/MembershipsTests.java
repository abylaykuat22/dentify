package kz.dentify.iam.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import kz.dentify.AbstractIntegrationTest;
import kz.dentify.clinic.TestClinics;
import kz.dentify.iam.internal.AccountMemberships.ClinicMembership;
import kz.dentify.platform.api.ConflictException;
import kz.dentify.platform.api.MissingTenantContextException;
import kz.dentify.platform.api.NotFoundException;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Членство в клиниках (IAM-01): изолировано по клинике ({@code @TenantId} и RLS), одно на аккаунт в клинике, филиалы
 * доступа — только своей клиники. Свои членства аккаунта во всех клиниках — отдельное чтение
 * ({@link AccountMemberships}) по политике {@code own_memberships}, которая только читает.
 */
class MembershipsTests extends AbstractIntegrationTest {

	@Autowired
	Accounts accounts;

	@Autowired
	Memberships memberships;

	@Autowired
	AccountMemberships accountMemberships;

	@Autowired
	TestClinics clinics;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	MembershipRepository repository;

	@Test
	void membershipBelongsToClinicOfContext() {
		Tenant clinic = this.clinics.create();
		UUID account = newAccount();

		Membership membership = in(clinic,
				() -> this.memberships.add(account, MembershipStatus.ACTIVE, List.of(clinic.branchId())));

		assertThat(membership.getClinicId()).isEqualTo(clinic.clinicId());
		assertThat(membership.getAccountId()).isEqualTo(account);
		assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
		assertThat(membership.getBranchIds()).containsExactly(clinic.branchId());
		assertThat(membership.getRoleId()).isNull();
		assertThat(membership.getStaffId()).isNull();
		assertThat(membership.getVersion()).isZero();
		Membership loaded = in(clinic, () -> this.memberships.get(membership.getId()));
		assertThat(loaded.getBranchIds()).containsExactly(clinic.branchId());
		assertThat(in(clinic, () -> this.memberships.ofAccount(account))).map(Membership::getId)
			.contains(membership.getId());
	}

	@Test
	void oneMembershipPerAccountInClinic() {
		Tenant clinic = this.clinics.create();
		UUID account = newAccount();
		in(clinic, () -> this.memberships.add(account, MembershipStatus.ACTIVE, List.of()));

		assertThatThrownBy(() -> in(clinic, () -> this.memberships.add(account, MembershipStatus.INVITED, List.of())))
			.isInstanceOfSatisfying(ConflictException.class, (ex) -> assertThat(ex.getCode()).isEqualTo(Memberships.EXISTS));
	}

	/** Приёмка IAM-01: пользователь клиники A получает 404 на членства клиники B. */
	@Test
	void clinicDoesNotSeeMembershipsOfAnotherClinic() {
		Tenant a = this.clinics.create();
		Tenant b = this.clinics.create();
		UUID account = newAccount();
		Membership inA = in(a, () -> this.memberships.add(account, MembershipStatus.ACTIVE, List.of()));
		Membership inB = in(b, () -> this.memberships.add(account, MembershipStatus.ACTIVE, List.of(b.branchId())));

		assertThatThrownBy(() -> in(a, () -> this.memberships.get(inB.getId()))).isInstanceOf(NotFoundException.class);
		assertThat(in(a, () -> this.memberships.list())).extracting(Membership::getId)
			.contains(inA.getId())
			.doesNotContain(inB.getId());
		assertThat(in(a, () -> this.memberships.ofAccount(account))).map(Membership::getId).contains(inA.getId());
		assertThatThrownBy(() -> in(a, () -> this.memberships.limitToBranches(inB.getId(), List.of())))
			.isInstanceOf(NotFoundException.class);
		// второй рубеж — RLS: даже SQL без условия по клинике не видит строк клиники B
		assertThat(in(a, () -> this.jdbc.queryForObject(
				"select count(*) from membership where id = ?", Long.class, inB.getId()))).isZero();
		assertThat(in(a, () -> this.jdbc.queryForObject(
				"select count(*) from membership_branch where membership_id = ?", Long.class, inB.getId()))).isZero();
	}

	@Test
	void withoutClinicContextMembershipsAreRefused() {
		assertThatThrownBy(() -> this.memberships.list()).isInstanceOf(MissingTenantContextException.class);
		assertThatThrownBy(() -> this.memberships.add(newAccount(), MembershipStatus.ACTIVE, List.of()))
			.isInstanceOf(MissingTenantContextException.class);
	}

	/** Приёмка IAM-01: аккаунт с членством в двух клиниках — один аккаунт, один пароль, видны обе клиники. */
	@Test
	void accountWithMembershipsInTwoClinicsSeesBothWithOnePassword() {
		Tenant a = this.clinics.create();
		Tenant b = this.clinics.create();
		Tenant foreign = this.clinics.create();
		Account account = this.accounts.create(new NewAccount(AccountsTests.randomMobileDigits(), null, "Врач Двух Клиник",
				AccountsTests.PASSWORD));
		UUID accountId = account.getId();
		Membership inA = in(a, () -> this.memberships.add(accountId, MembershipStatus.ACTIVE, List.of()));
		Membership inB = in(b, () -> this.memberships.add(accountId, MembershipStatus.DISABLED, List.of()));
		UUID stranger = newAccount();
		in(a, () -> this.memberships.add(stranger, MembershipStatus.ACTIVE, List.of()));
		in(foreign, () -> this.memberships.add(stranger, MembershipStatus.ACTIVE, List.of()));

		List<ClinicMembership> own = this.accountMemberships.of(accountId);

		assertThat(own).containsExactly(new ClinicMembership(inA.getId(), a.clinicId(), MembershipStatus.ACTIVE),
				new ClinicMembership(inB.getId(), b.clinicId(), MembershipStatus.DISABLED));
		assertThat(this.jdbc.queryForObject("select count(*) from account where id = ?", Long.class, accountId)).isOne();
		assertThat(this.accounts.passwordMatches(this.accounts.get(accountId), AccountsTests.PASSWORD)).isTrue();
		assertThat(this.accountMemberships.of(UUID.randomUUID())).isEmpty();
	}

	/**
	 * Своё чтение не протекает дальше: внутри транзакции клиники A после него настройки app.account_id нет, и тот же
	 * SQL видит только клинику A.
	 */
	@Test
	void ownMembershipsReadLeavesNoAccountSettingInTransaction() {
		Tenant a = this.clinics.create();
		Tenant b = this.clinics.create();
		UUID account = newAccount();
		in(a, () -> this.memberships.add(account, MembershipStatus.ACTIVE, List.of()));
		in(b, () -> this.memberships.add(account, MembershipStatus.ACTIVE, List.of()));

		in(a, () -> this.transactions.execute((status) -> {
			assertThat(this.accountMemberships.of(account)).hasSize(2);
			assertThat(this.jdbc.queryForObject("select current_setting('app.account_id', true)", String.class))
				.isEmpty();
			assertThat(this.jdbc.queryForList("select clinic_id from membership where account_id = ?", UUID.class,
					account))
				.containsExactly(a.clinicId());
			return null;
		}));
	}

	/**
	 * Политика own_memberships только читает: с app.account_id и без клиники роль приложения видит членства аккаунта во
	 * всех клиниках, но не может их изменить, удалить или вставить; членства других аккаунтов не видит.
	 */
	@Test
	void ownMembershipsPolicyIsReadOnlyAndLimitedToOneAccount() {
		Tenant a = this.clinics.create();
		Tenant b = this.clinics.create();
		UUID account = newAccount();
		UUID other = newAccount();
		in(a, () -> this.memberships.add(account, MembershipStatus.ACTIVE, List.of(a.branchId())));
		in(b, () -> this.memberships.add(account, MembershipStatus.ACTIVE, List.of()));
		in(b, () -> this.memberships.add(other, MembershipStatus.ACTIVE, List.of()));

		this.transactions.executeWithoutResult((status) -> {
			this.jdbc.queryForObject("select set_config('app.account_id', ?, true)", String.class, account.toString());

			assertThat(this.jdbc.queryForList("select clinic_id from membership where account_id in (?, ?)", UUID.class,
					account, other))
				.containsExactlyInAnyOrder(a.clinicId(), b.clinicId());
			assertThat(this.jdbc.update("update membership set status = 'DISABLED' where account_id = ?", account)).isZero();
			assertThat(this.jdbc.queryForObject("select count(*) from membership_branch", Long.class))
				.as("филиалы членств политика не открывает").isZero();
			status.setRollbackOnly();
		});
		this.transactions.executeWithoutResult((status) -> {
			this.jdbc.queryForObject("select set_config('app.account_id', ?, true)", String.class, account.toString());
			assertThat(this.jdbc.update("delete from membership where account_id = ?", account)).isZero();
			status.setRollbackOnly();
		});
		UUID third = this.clinics.create().clinicId();
		assertThatThrownBy(() -> this.transactions.executeWithoutResult((status) -> {
			this.jdbc.queryForObject("select set_config('app.account_id', ?, true)", String.class, account.toString());
			this.jdbc.update("insert into membership (clinic_id, account_id, status) values (?, ?, 'ACTIVE')", third,
					account);
		})).isInstanceOf(DataAccessException.class).satisfies((ex) -> assertThat(sqlState(ex)).isEqualTo("42501"));

		assertThat(this.jdbc.queryForObject("select count(*) from membership where account_id = ?", Long.class, account))
			.as("после транзакции настройки нет").isZero();
		assertThat(in(a, () -> this.memberships.ofAccount(account))).map(Membership::getStatus)
			.contains(MembershipStatus.ACTIVE);
	}

	@Test
	void membershipIsLimitedToBranchesOfItsClinic() {
		Tenant clinic = this.clinics.create();
		UUID second = this.clinics.addBranch(clinic.clinicId());
		UUID third = this.clinics.addBranch(clinic.clinicId());
		UUID account = newAccount();
		Membership membership = in(clinic, () -> this.memberships.add(account, MembershipStatus.ACTIVE, List.of()));
		assertThat(membership.getBranchIds()).as("пусто — все филиалы").isEmpty();
		assertThat(membership.coversBranch(third)).isTrue();

		Membership limited = in(clinic,
				() -> this.memberships.limitToBranches(membership.getId(), List.of(clinic.branchId(), second)));
		assertThat(limited.getBranchIds()).containsExactlyInAnyOrder(clinic.branchId(), second);
		assertThat(limited.coversBranch(third)).isFalse();
		assertThat(limited.getVersion()).as("@Version: изменение филиалов меняет версию членства").isOne();
		assertThat(limited.getUpdatedAt()).as("и время изменения").isAfter(membership.getUpdatedAt());

		Membership narrowed = in(clinic, () -> this.memberships.limitToBranches(membership.getId(), List.of(second, third)));
		assertThat(narrowed.getBranchIds()).containsExactlyInAnyOrder(second, third);
		assertThat(in(clinic, () -> this.jdbc.queryForList(
				"select branch_id from membership_branch where membership_id = ?", UUID.class, membership.getId())))
			.containsExactlyInAnyOrder(second, third);

		assertThat(in(clinic, () -> this.memberships.limitToBranches(membership.getId(), Set.of())).getBranchIds())
			.isEmpty();
	}

	/** @Version: правка филиалов по устаревшей копии членства отклоняется, а не затирает чужую (409 у обработчика). */
	@Test
	void staleMembershipIsRejected() {
		Tenant clinic = this.clinics.create();
		UUID second = this.clinics.addBranch(clinic.clinicId());
		UUID account = newAccount();
		UUID id = in(clinic, () -> this.memberships.add(account, MembershipStatus.ACTIVE, List.of())).getId();
		Membership stale = in(clinic, () -> this.memberships.get(id));
		in(clinic, () -> this.memberships.limitToBranches(id, List.of(clinic.branchId())));

		stale.limitToBranches(List.of(second));
		assertThatThrownBy(() -> in(clinic, () -> this.repository.saveAndFlush(stale)))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
		assertThat(in(clinic, () -> this.memberships.get(id)).getBranchIds()).containsExactly(clinic.branchId());
	}

	/** Филиал другой клиники не связать: составной внешний ключ (clinic_id, branch_id). */
	@Test
	void branchOfAnotherClinicCannotBeLinked() {
		Tenant a = this.clinics.create();
		Tenant b = this.clinics.create();
		UUID account = newAccount();

		assertThatThrownBy(() -> in(a, () -> this.memberships.add(account, MembershipStatus.ACTIVE, List.of(b.branchId()))))
			.isInstanceOf(DataIntegrityViolationException.class)
			.satisfies((ex) -> assertThat(sqlState(ex)).isEqualTo("23503"));
		assertThat(in(a, () -> this.memberships.ofAccount(account))).isEmpty();
	}

	private UUID newAccount() {
		return this.accounts
			.create(new NewAccount(AccountsTests.randomMobileDigits(), null, "Сотрудник Демо", AccountsTests.PASSWORD))
			.getId();
	}

	private static <T> T in(Tenant tenant, Supplier<T> action) {
		return TenantContext.call(tenant, action);
	}

	private static String sqlState(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && sql.getSQLState() != null) {
				return sql.getSQLState();
			}
		}
		return "";
	}

}
