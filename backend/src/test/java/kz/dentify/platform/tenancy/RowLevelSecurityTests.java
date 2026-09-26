package kz.dentify.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import kz.dentify.DentifyPostgresContainer;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Второй рубеж изоляции — Row Level Security в PostgreSQL (PLT-02):
 * <ul>
 * <li>тест каталога {@code pg_class}/{@code pg_policies}: у каждой таблицы с {@code clinic_id} включены RLS и FORCE, и
 * единственная разрешающая политика — стандартная {@code clinic_id = current_clinic_id()} на все команды. Проверка
 * идёт после всех миграций, в том числе миграций с данными, которые снимают FORCE на время;</li>
 * <li>таблица без {@code clinic_id} — только из явного реестра глобальных таблиц {@link #GLOBAL_TABLES} или корень
 * арендатора из {@link #TENANT_ROOT_TABLES} (клиника: RLS по {@code id = current_clinic_id()});</li>
 * <li>разрешающая политика сверх стандартной — только из реестра {@link #READ_POLICIES} и только на чтение;</li>
 * <li>роль приложения без {@code app.clinic_id} не видит ни одной строки таблиц арендатора, с клиникой — только её
 * строки; записать строку чужой клиники нельзя.</li>
 * </ul>
 */
class RowLevelSecurityTests extends TenantIntegrationTest {

	/** Глобальные таблицы без clinic_id и RLS: имя и причина. Новая глобальная таблица — сначала сюда. */
	static final Map<String, String> GLOBAL_TABLES = Map.of(
			"flyway_schema_history", "история миграций, приложению недоступна (V1)",
			"one_time_token", "iam: токены ссылок сброса пароля и приглашения, ссылку открывают до входа и клиники",
			"account", "iam: аккаунт один на человека для всех клиник, существует до выбора клиники (IAM-01)",
			"auth_event", "iam: журнал входов — попытка входа до выбора клиники, у неизвестного логина нет и аккаунта "
					+ "(T-IAM-02); журнал клиники — события аккаунтов с её членством",
			"test_localized_probe", "только в тестах: LocalizedText в jsonb",
			"test_constraint_probe", "только в тестах: ошибки ограничений БД");

	/**
	 * Корни арендатора: таблицы без clinic_id, у которых id строки и есть клиника. RLS, FORCE и единственная политика
	 * {@link #ROOT_POLICY}: клиника видит только саму себя.
	 */
	static final Map<String, String> TENANT_ROOT_TABLES = Map.of(
			"clinic", "clinic: сама клиника, её id — clinic_id всех её данных");

	static final String POLICY = "(clinic_id = current_clinic_id())";

	static final String ROOT_POLICY = "(id = current_clinic_id())";

	/**
	 * Разрешающие политики сверх стандартной — только на чтение (FOR SELECT) и только отсюда, с причиной: политики
	 * складываются через OR, и любая лишняя расширяет видимость. Изменения по-прежнему пропускает только стандартная
	 * политика: для UPDATE и DELETE строка должна пройти и политики SELECT, и политики изменения.
	 */
	static final Map<String, ReadPolicy> READ_POLICIES = Map.of("membership",
			new ReadPolicy("own_memberships", "(account_id = current_account_id())",
					"iam: свои членства аккаунта во всех клиниках — вход и выбор клиники до контекста (IAM-01)"));

	/** Таблицы с clinic_id, у которых нет RLS, FORCE или стандартной политики, или есть лишняя разрешающая политика. */
	static final String UNPROTECTED_TENANT_TABLES = """
			select c.relname::text as name, c.relrowsecurity as rls, c.relforcerowsecurity as forced,
			       (select count(*) from pg_policies p where p.schemaname = n.nspname and p.tablename = c.relname
			          and p.permissive = 'PERMISSIVE' and p.cmd = 'ALL' and p.roles = '{public}'::name[]
			          and p.qual = ? and p.with_check = ?) as standard_policies,
			       (select count(*) from pg_policies p where p.schemaname = n.nspname and p.tablename = c.relname
			          and p.permissive = 'PERMISSIVE') as permissive_policies
			from pg_class c join pg_namespace n on n.oid = c.relnamespace
			where n.nspname = 'dentify' and c.relkind in ('r', 'p')
			  and exists (select 1 from pg_attribute a
			              where a.attrelid = c.oid and a.attname = 'clinic_id' and not a.attisdropped)
			order by 1""";

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void everyTenantTableHasForcedRlsAndStandardPolicy() {
		List<Map<String, Object>> tables = this.jdbc.queryForList(UNPROTECTED_TENANT_TABLES, POLICY, POLICY);

		assertThat(tables).as("таблицы с clinic_id, в том числе журнал действий и его помесячные партиции")
			.extracting((table) -> (String) table.get("name"))
			.contains("test_note", "audit_log")
			.anyMatch((name) -> name.matches("audit_log_\\d{4}_\\d{2}"));
		assertThat(tables).allSatisfy((table) -> assertThat(table).as("таблица %s", table.get("name"))
			.containsEntry("rls", true)
			.containsEntry("forced", true)
			.containsEntry("standard_policies", 1L)
			.containsEntry("permissive_policies", READ_POLICIES.containsKey(table.get("name")) ? 2L : 1L));
	}

	@Test
	void tablesWithoutClinicIdAreRegisteredAsGlobalOrTenantRoot() {
		List<String> global = this.jdbc.queryForList("""
				select c.relname::text from pg_class c join pg_namespace n on n.oid = c.relnamespace
				where n.nspname = 'dentify' and c.relkind in ('r', 'p')
				  and not exists (select 1 from pg_attribute a
				                  where a.attrelid = c.oid and a.attname = 'clinic_id' and not a.attisdropped)""",
				String.class);

		assertThat(global).as("таблицы без clinic_id вне реестров GLOBAL_TABLES и TENANT_ROOT_TABLES")
			.isSubsetOf(Stream.concat(GLOBAL_TABLES.keySet().stream(), TENANT_ROOT_TABLES.keySet().stream()).toList())
			.containsAll(TENANT_ROOT_TABLES.keySet());
	}

	/** Корень арендатора (clinic): RLS, FORCE и единственная политика по id на все команды. */
	@Test
	void tenantRootTablesHaveForcedRlsAndPolicyById() {
		for (String table : TENANT_ROOT_TABLES.keySet()) {
			Map<String, Object> row = this.jdbc.queryForMap("""
					select c.relrowsecurity as rls, c.relforcerowsecurity as forced,
					       (select count(*) from pg_policies p where p.schemaname = n.nspname and p.tablename = c.relname
					          and p.permissive = 'PERMISSIVE' and p.cmd = 'ALL' and p.roles = '{public}'::name[]
					          and p.qual = ? and p.with_check = ?) as root_policies,
					       (select count(*) from pg_policies p where p.schemaname = n.nspname and p.tablename = c.relname
					          and p.permissive = 'PERMISSIVE') as permissive_policies
					from pg_class c join pg_namespace n on n.oid = c.relnamespace
					where n.nspname = 'dentify' and c.relname = ?""", ROOT_POLICY, ROOT_POLICY, table);

			assertThat(row).as("таблица %s", table)
				.containsEntry("rls", true)
				.containsEntry("forced", true)
				.containsEntry("root_policies", 1L)
				.containsEntry("permissive_policies", 1L);
		}
	}

	/**
	 * Разрешающие политики, кроме стандартной и политики корня, — ровно из {@link #READ_POLICIES}: только SELECT, для
	 * всех ролей, с тем же выражением и без WITH CHECK.
	 */
	@Test
	void extraPoliciesAreRegisteredAndReadOnly() {
		List<Map<String, Object>> extra = this.jdbc.queryForList("""
				select tablename::text as table_name, policyname::text as policy, cmd, qual, with_check,
				       roles = '{public}'::name[] as for_everyone
				from pg_policies
				where schemaname = 'dentify' and permissive = 'PERMISSIVE'
				  and not (cmd = 'ALL' and qual in (?, ?) and with_check = qual)
				order by 1, 2""", POLICY, ROOT_POLICY);

		assertThat(extra).extracting((policy) -> (String) policy.get("table_name"))
			.containsExactlyInAnyOrderElementsOf(READ_POLICIES.keySet());
		assertThat(extra).allSatisfy((policy) -> {
			ReadPolicy expected = READ_POLICIES.get((String) policy.get("table_name"));
			assertThat(policy).as("политика %s", policy.get("policy"))
				.containsEntry("policy", expected.name())
				.containsEntry("cmd", "SELECT")
				.containsEntry("qual", expected.expression())
				.containsEntry("for_everyone", true);
			assertThat(policy.get("with_check")).isNull();
		});
	}

	/**
	 * Разрешающая политика сверх стандартной на таблице клиники.
	 *
	 * @param name имя политики
	 * @param expression выражение USING, как его показывает pg_policies
	 * @param reason зачем она нужна
	 */
	record ReadPolicy(String name, String expression, String reason) {
	}

	/** Проверка каталога ловит таблицу с clinic_id без FORCE и политики: создаём такую в транзакции и откатываем. */
	@Test
	void catalogCheckCatchesUnprotectedTable() throws SQLException {
		try (Connection owner = connect(DentifyPostgresContainer.OWNER_ROLE, DentifyPostgresContainer.OWNER_PASSWORD)) {
			owner.setAutoCommit(false);
			try (Statement ddl = owner.createStatement()) {
				ddl.execute("create table dentify.test_forgotten (id uuid primary key, clinic_id uuid not null)");
				ddl.execute("alter table dentify.test_forgotten enable row level security");
			}
			List<String> problems = new ArrayList<>();
			try (PreparedStatement check = owner.prepareStatement(UNPROTECTED_TENANT_TABLES)) {
				check.setString(1, POLICY);
				check.setString(2, POLICY);
				try (ResultSet rows = check.executeQuery()) {
					while (rows.next()) {
						if (!rows.getBoolean("forced") || rows.getLong("standard_policies") != 1) {
							problems.add(rows.getString("name"));
						}
					}
				}
			}
			finally {
				owner.rollback();
			}

			assertThat(problems).containsExactly("test_forgotten");
		}
	}

	/** Приёмка PLT-02: запрос роли приложения без app.clinic_id — 0 строк; с клиникой — только её строки. */
	@Test
	void applicationRoleSeesOnlyRowsOfItsClinic() throws SQLException {
		UUID clinicA = newClinic();
		UUID clinicB = newClinic();
		String noteA = createNote(enter(clinicA), "Клиника A").path("id").asString();
		String noteB = createNote(enter(clinicB), "Клиника B").path("id").asString();

		try (Connection app = connect(POSTGRES.getUsername(), POSTGRES.getPassword())) {
			assertThat(visible(app, noteA, noteB)).as("без app.clinic_id").isEmpty();

			app.setAutoCommit(false);
			setClinic(app, clinicA);
			assertThat(visible(app, noteA, noteB)).as("в клинике A").containsExactly(noteA);
			try (PreparedStatement update = app.prepareStatement("update test_note set title = 'x' where id = ?::uuid")) {
				update.setString(1, noteB);
				assertThat(update.executeUpdate()).as("строку клиники B не изменить").isZero();
			}
			try (PreparedStatement insert = app
				.prepareStatement("insert into test_note (clinic_id, title) values (?::uuid, 'Чужая')")) {
				insert.setString(1, clinicB.toString());
				assertThatThrownBy(insert::executeUpdate).isInstanceOf(SQLException.class)
					.satisfies((ex) -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("42501"));
			}
			app.rollback();

			assertThat(visible(app, noteA, noteB)).as("после транзакции контекст не остался").isEmpty();
		}
	}

	/** Пул приложения без контекста клиники: ни одной строки, с контекстом — строки своей клиники. */
	@Test
	void applicationPoolWithoutClinicSeesNothing() {
		UUID clinic = newClinic();
		String note = createNote(enter(clinic), "Пул").path("id").asString();
		String sql = "select count(*) from test_note where id = ?::uuid";

		assertThat(this.jdbc.queryForObject(sql, Long.class, note)).isZero();
		assertThat(TenantContext.call(Tenant.of(clinic), () -> this.jdbc.queryForObject(sql, Long.class, note)))
			.isEqualTo(1L);
		assertThat(this.jdbc.queryForObject(sql, Long.class, note)).as("контекст не остался в соединении пула").isZero();
	}

	/**
	 * current_clinic_id() встраивается в запрос: в плане — выражение над app.clinic_id, а не вызов функции на каждую
	 * строку (PLT-02: RLS не должен заметно замедлять запросы).
	 */
	@Test
	void policyFunctionIsInlined() {
		List<String> plan = TenantContext.call(Tenant.of(newClinic()), () -> this.jdbc.queryForList(
				"explain (costs off) select * from test_note where clinic_id = current_clinic_id() and id = uuidv7()",
				String.class));

		assertThat(String.join("\n", plan)).contains("current_setting").doesNotContain("current_clinic_id");
	}

	private static List<String> visible(Connection connection, String... ids) throws SQLException {
		List<String> visible = new ArrayList<>();
		try (PreparedStatement select = connection
			.prepareStatement("select id::text from test_note where id = any (?::uuid[]) order by 1")) {
			select.setArray(1, connection.createArrayOf("text", ids));
			try (ResultSet rows = select.executeQuery()) {
				while (rows.next()) {
					visible.add(rows.getString(1));
				}
			}
		}
		return visible;
	}

	private static void setClinic(Connection connection, UUID clinic) throws SQLException {
		try (PreparedStatement set = connection.prepareStatement("select set_config('app.clinic_id', ?, true)")) {
			set.setString(1, clinic.toString());
			set.executeQuery().close();
		}
	}

	/** Прямое подключение к тестовой базе в обход пула приложения. */
	private static Connection connect(String user, String password) throws SQLException {
		return DriverManager.getConnection(POSTGRES.getJdbcUrl(), user, password);
	}

}
