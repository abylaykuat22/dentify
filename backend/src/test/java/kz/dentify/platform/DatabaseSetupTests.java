package kz.dentify.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import kz.dentify.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Flyway V1 и роли БД: миграции — владельцем схемы, приложение — ролью только с DML.
 */
class DatabaseSetupTests extends AbstractIntegrationTest {

	@Autowired
	Flyway flyway;

	/** Подключения приложения (spring.datasource → dentify_app). */
	@Autowired
	JdbcTemplate jdbc;

	@Test
	void baselineMigrationIsAppliedBySchemaOwner() {
		MigrationInfo[] applied = this.flyway.info().applied();

		assertThat(applied).isNotEmpty();
		assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
		assertThat(applied).allSatisfy((migration) -> {
			assertThat(migration.getState()).isEqualTo(MigrationState.SUCCESS);
			assertThat(migration.getInstalledBy()).isEqualTo("dentify_owner");
		});
		assertThat(this.flyway.info().pending()).isEmpty();
		assertThat(this.flyway.getConfiguration().isValidateMigrationNaming())
			.as("файл с неверным именем роняет старт, а не пропускается (spring.flyway.validate-migration-naming)")
			.isTrue();
	}

	/**
	 * Атрибуты и права роли приложения — те же, что создаёт deploy/postgres/init/01-roles.sh; в managed PostgreSQL
	 * роль заводят руками по тому же списку (T-PLT-25).
	 */
	@Test
	void applicationUsesRestrictedRole() {
		Map<String, Object> role = this.jdbc.queryForMap("""
				select current_user::text as name, current_schema() as schema,
				       r.rolsuper, r.rolbypassrls, r.rolcreatedb, r.rolcreaterole, r.rolreplication,
				       has_database_privilege(current_user, current_database(), 'TEMP') as can_temp,
				       has_database_privilege(current_user, current_database(), 'CREATE') as can_create_schema,
				       has_schema_privilege(current_user, 'dentify', 'CREATE') as can_create_in_dentify,
				       has_schema_privilege(current_user, 'public', 'CREATE') as can_create_in_public,
				       (select nspowner::regrole::text from pg_namespace where nspname = 'dentify') as schema_owner
				from pg_roles r where r.rolname = current_user""");

		assertThat(role).containsEntry("name", "dentify_app")
			.containsEntry("schema", "dentify")
			.containsEntry("rolsuper", false)
			.containsEntry("rolbypassrls", false)
			.containsEntry("rolcreatedb", false)
			.containsEntry("rolcreaterole", false)
			.containsEntry("rolreplication", false)
			.containsEntry("can_temp", false)
			.containsEntry("can_create_schema", false)
			.containsEntry("can_create_in_dentify", false)
			.containsEntry("can_create_in_public", false)
			.containsEntry("schema_owner", "dentify_owner");
	}

	/**
	 * Одна история миграций на всю схему (docs/architecture.md, «Миграции»): модульные миграции Spring Modulith
	 * ({@code spring.modulith.runtime.flyway-enabled}) не включены — они завели бы по таблице
	 * {@code flyway_schema_history_<модуль>} со своей нумерацией версий.
	 */
	@Test
	void singleMigrationHistory() {
		assertThat(this.jdbc.queryForList(
				"select tablename::text from pg_tables where schemaname = 'dentify' and tablename like 'flyway%'",
				String.class))
			.containsExactly("flyway_schema_history");
	}

	@Test
	void extensionsAndUuidV7AreAvailable() {
		assertThat(this.jdbc.queryForList("select extname from pg_extension", String.class)).contains("btree_gist",
				"pg_trgm");
		assertThat(this.jdbc.queryForObject("select uuid_extract_version(uuidv7())", Integer.class)).isEqualTo(7);
		assertThat(this.jdbc.queryForObject("show server_version_num", Integer.class)).isGreaterThanOrEqualTo(180000);
	}

	@Test
	void applicationCannotChangeSchema() {
		assertThatThrownBy(() -> this.jdbc.execute("create table dentify.app_must_not_create (id int)"))
			.rootCause()
			.hasMessageContaining("permission denied");
	}

	@Test
	void applicationCannotReadMigrationHistory() {
		assertThatThrownBy(
				() -> this.jdbc.queryForObject("select count(*) from dentify.flyway_schema_history", Long.class))
			.rootCause()
			.hasMessageContaining("permission denied");
	}

}
