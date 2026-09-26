package kz.dentify.clinic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import kz.dentify.AbstractIntegrationTest;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Минимальные таблицы модуля clinic (T-IAM-01): клиника — корень арендатора, RLS по её id; филиал — таблица клиники по
 * шаблону. Роль приложения видит только клинику контекста и её филиалы, без контекста — ничего, чужую клинику не
 * записать.
 */
class ClinicTablesTests extends AbstractIntegrationTest {

	@Autowired
	TestClinics clinics;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void clinicSeesOnlyItselfAndItsBranches() {
		Tenant a = this.clinics.create();
		Tenant b = this.clinics.create();
		List<UUID> both = List.of(a.clinicId(), b.clinicId());

		assertThat(clinicsAmong(both)).as("без контекста клиники").isEmpty();
		assertThat(TenantContext.call(Tenant.of(a.clinicId()), () -> clinicsAmong(both))).containsExactly(a.clinicId());
		assertThat(TenantContext.call(Tenant.of(a.clinicId()), () -> this.jdbc.queryForList(
				"select id from branch where id = any (?::uuid[])", UUID.class,
				(Object) new String[] { a.branchId().toString(), b.branchId().toString() })))
			.containsExactly(a.branchId());
	}

	@Test
	void anotherClinicCannotBeWrittenOrChanged() {
		Tenant a = this.clinics.create();
		Tenant b = this.clinics.create();

		TenantContext.run(Tenant.of(a.clinicId()), () -> {
			assertThat(this.jdbc.update("update clinic set name = 'Чужая' where id = ?", b.clinicId())).isZero();
			assertThat(this.jdbc.update("delete from branch where id = ?", b.branchId())).isZero();
			assertThatThrownBy(() -> this.jdbc.update("insert into clinic (id, name, city) values (?, 'Чужая', 'Астана')",
					UUID.randomUUID()))
				.isInstanceOf(DataAccessException.class)
				.satisfies((ex) -> assertThat(sqlState(ex)).isEqualTo("42501"));
			assertThatThrownBy(() -> this.jdbc.update(
					"insert into branch (clinic_id, name, city) values (?, 'Чужой', 'Астана')", b.clinicId()))
				.isInstanceOf(DataAccessException.class)
				.satisfies((ex) -> assertThat(sqlState(ex)).isEqualTo("42501"));
		});
	}

	/** Филиал ссылается на существующую клинику: филиал выдуманной клиники БД не примет. */
	@Test
	void branchNeedsExistingClinic() {
		UUID ghost = UUID.randomUUID();
		assertThatThrownBy(() -> TenantContext.run(Tenant.of(ghost), () -> this.jdbc
			.update("insert into branch (clinic_id, name, city) values (?, 'Без клиники', 'Астана')", ghost)))
			.isInstanceOf(DataAccessException.class)
			.satisfies((ex) -> assertThat(sqlState(ex)).isEqualTo("23503"));
	}

	private List<UUID> clinicsAmong(List<UUID> ids) {
		return this.jdbc.queryForList("select id from clinic where id = any (?::uuid[]) order by id", UUID.class,
				(Object) ids.stream().map(UUID::toString).toArray(String[]::new));
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
