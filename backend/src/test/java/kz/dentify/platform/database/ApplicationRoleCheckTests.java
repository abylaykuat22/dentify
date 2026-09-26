package kz.dentify.platform.database;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import kz.dentify.AbstractIntegrationTest;
import kz.dentify.DentifyPostgresContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Проверка роли при старте: dentify_app проходит (приложение поднялось); суперпользователь, владелец схемы, член
 * dentify_owner или роли с BYPASSRLS (прямо и транзитивно) и роль с чужим search_path — нет.
 * <p>
 * Пробные роли создаёт суперпользователь контейнера и удаляет после каждого теста: контейнер общий на прогон.
 */
class ApplicationRoleCheckTests extends AbstractIntegrationTest {

	/** Пробная роль с атрибутами как у dentify_app: LOGIN, NOSUPERUSER, NOBYPASSRLS, NOINHERIT. */
	static final String PROBE = "zz_role_check_probe";

	static final String PROBE_PASSWORD = "test-probe-password";

	/** Промежуточная групповая роль для транзитивного членства. */
	static final String GROUP = "zz_role_check_group";

	/** Групповая роль с BYPASSRLS. */
	static final String BYPASS = "zz_role_check_bypass";

	@Autowired
	JdbcTemplate jdbc;

	/** DROP OWNED BY снимает и гранты на базу и схему, без него DROP ROLE откажет; членства DROP ROLE снимает сам. */
	@AfterEach
	void dropProbeRoles() {
		asSuperuser("""
				DO $$
				DECLARE r text;
				BEGIN
				    FOREACH r IN ARRAY ARRAY['%s', '%s', '%s'] LOOP
				        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
				            EXECUTE format('DROP OWNED BY %%I', r);
				            EXECUTE format('DROP ROLE %%I', r);
				        END IF;
				    END LOOP;
				END
				$$""".formatted(PROBE, GROUP, BYPASS));
	}

	@Test
	void applicationRolePasses() {
		assertThatNoException().isThrownBy(() -> ApplicationRoleCheck.verify(this.jdbc));
	}

	@Test
	void superuserIsRejected() {
		assertThatIllegalStateException()
			.isThrownBy(() -> verifyAs(DentifyPostgresContainer.SUPERUSER, DentifyPostgresContainer.SUPERUSER_PASSWORD))
			.withMessageContaining("суперпользователь")
			.withMessageContaining("BYPASSRLS");
	}

	@Test
	void schemaOwnerIsRejected() {
		assertThatIllegalStateException()
			.isThrownBy(() -> verifyAs(DentifyPostgresContainer.OWNER_ROLE, DentifyPostgresContainer.OWNER_PASSWORD))
			.withMessageContaining("владелец схемы dentify");
	}

	@Test
	void probeRoleWithoutMembershipsPasses() {
		createProbe();

		assertThatNoException().isThrownBy(() -> verifyAs(PROBE, PROBE_PASSWORD));
	}

	/** GRANT dentify_owner TO роль: даже при NOINHERIT роль может SET ROLE dentify_owner и снять FORCE RLS. */
	@Test
	void memberOfSchemaOwnerIsRejected() {
		createProbe();
		asSuperuser("GRANT " + DentifyPostgresContainer.OWNER_ROLE + " TO " + PROBE);

		assertThatIllegalStateException().isThrownBy(() -> verifyAs(PROBE, PROBE_PASSWORD))
			.withMessageContaining("член роли dentify_owner")
			.withMessageNotContaining("суперпользователь");
	}

	@Test
	void transitiveMemberOfSchemaOwnerIsRejected() {
		createProbe();
		asSuperuser("CREATE ROLE " + GROUP + " NOLOGIN");
		asSuperuser("GRANT " + DentifyPostgresContainer.OWNER_ROLE + " TO " + GROUP);
		asSuperuser("GRANT " + GROUP + " TO " + PROBE);

		assertThatIllegalStateException().isThrownBy(() -> verifyAs(PROBE, PROBE_PASSWORD))
			.withMessageContaining("член роли dentify_owner")
			.withMessageNotContaining(GROUP);
	}

	@Test
	void transitiveMemberOfBypassRlsRoleIsRejected() {
		createProbe();
		asSuperuser("CREATE ROLE " + BYPASS + " NOLOGIN BYPASSRLS");
		asSuperuser("CREATE ROLE " + GROUP + " NOLOGIN");
		asSuperuser("GRANT " + BYPASS + " TO " + GROUP);
		asSuperuser("GRANT " + GROUP + " TO " + PROBE);

		assertThatIllegalStateException().isThrownBy(() -> verifyAs(PROBE, PROBE_PASSWORD))
			.withMessageContaining("член роли " + BYPASS);
	}

	/** Прод: search_path роли приложения — dentify, public (docs/architecture.md, «Роли и права»). */
	@Test
	void searchPathWithoutApplicationSchemaIsRejected() {
		String url = POSTGRES.getJdbcUrl() + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=public";

		assertThatIllegalStateException()
			.isThrownBy(() -> verifyAs(url, POSTGRES.getUsername(), POSTGRES.getPassword()))
			.withMessageContaining("search_path — [public]")
			.withMessageContaining("dentify, public");
	}

	/** Пробная роль: вход в базу dentify и USAGE на схему, как у dentify_app, но без грантов на таблицы. */
	private static void createProbe() {
		asSuperuser("CREATE ROLE " + PROBE + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS "
				+ "NOINHERIT PASSWORD '" + PROBE_PASSWORD + "'");
		asSuperuser("GRANT CONNECT ON DATABASE dentify TO " + PROBE);
		asSuperuser("GRANT USAGE ON SCHEMA dentify TO " + PROBE);
	}

	private static void asSuperuser(String sql) {
		SingleConnectionDataSource dataSource = new SingleConnectionDataSource(POSTGRES.getJdbcUrl(),
				DentifyPostgresContainer.SUPERUSER, DentifyPostgresContainer.SUPERUSER_PASSWORD, true);
		try {
			new JdbcTemplate(dataSource).execute(sql);
		}
		finally {
			dataSource.destroy();
		}
	}

	private static void verifyAs(String user, String password) {
		verifyAs(POSTGRES.getJdbcUrl(), user, password);
	}

	private static void verifyAs(String url, String user, String password) {
		SingleConnectionDataSource dataSource = new SingleConnectionDataSource(url, user, password, true);
		try {
			ApplicationRoleCheck.verify(new JdbcTemplate(dataSource));
		}
		finally {
			dataSource.destroy();
		}
	}

}
