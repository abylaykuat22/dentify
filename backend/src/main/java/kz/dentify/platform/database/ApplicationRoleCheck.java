package kz.dentify.platform.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Роняет запуск, если роль подключения приложения ({@code spring.datasource}) может обойти RLS или настроена не
 * так, как ждёт приложение. Изоляция клиник держится на RLS (T-PLT-03), а роли в managed PostgreSQL заводят
 * руками — ошибку в пользователе или грантах нужно поймать до первого запроса, а не после утечки. Правильная
 * роль — {@code dentify_app} (docs/architecture.md, «Роли и права»).
 * <p>
 * RLS обходит роль, которая:
 * <ul>
 * <li>суперпользователь или имеет {@code BYPASSRLS};</li>
 * <li>владеет схемой {@code dentify} или таблицами в ней: владелец снимает {@code FORCE ROW LEVEL SECURITY};</li>
 * <li>прямо или через другие роли состоит в роли из пунктов выше или в {@code dentify_owner}. Членство
 * проверяется через {@code pg_has_role(…, 'MEMBER')}: оно видит и транзитивные гранты, и гранты без
 * {@code INHERIT} — у {@code NOINHERIT}-роли права владельца не действуют сами, но {@code SET ROLE} к нему
 * доступен.</li>
 * </ul>
 * Кроме того, {@code search_path} роли должен начинаться со схемы {@code dentify} и содержать {@code public}:
 * таблицы приложения — в {@code dentify}, расширения ({@code pg_trgm}, {@code btree_gist}) — в {@code public}.
 */
@Component
class ApplicationRoleCheck implements InitializingBean {

	static final String SCHEMA = "dentify";

	/** Владелец схемы, которым ходит Flyway (deploy/postgres/init/01-roles.sh). */
	static final String OWNER_ROLE = "dentify_owner";

	/** Схема расширений: должна быть в search_path после {@link #SCHEMA}. */
	static final String EXTENSIONS_SCHEMA = "public";

	/**
	 * Роли, дающие обход RLS: суперпользователь, {@code BYPASSRLS}, {@code dentify_owner}, владелец схемы или
	 * таблиц в ней. Параметры: роль-владелец, схема, схема.
	 */
	private static final String RISKY_ROLE = """
			(m.rolsuper or m.rolbypassrls or m.rolname = ?
			 or exists (select 1 from pg_namespace n where n.nspname = ? and n.nspowner = m.oid)
			 or exists (select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
			            where n.nspname = ? and c.relowner = m.oid))""";

	private final JdbcTemplate jdbc;

	ApplicationRoleCheck(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void afterPropertiesSet() {
		verify(this.jdbc);
	}

	/**
	 * @throws IllegalStateException если роль текущего подключения может обойти RLS или её search_path не
	 * {@code dentify, public}
	 */
	static void verify(JdbcOperations jdbc) {
		Map<String, Object> role = jdbc.queryForMap("""
				select current_user::text as name, r.rolsuper, r.rolbypassrls,
				       exists (select 1 from pg_namespace n where n.nspname = ? and n.nspowner = r.oid) as schema_owner,
				       exists (select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
				               where n.nspname = ? and c.relowner = r.oid) as table_owner,
				       (select string_agg(m.rolname::text, ', ' order by m.rolname) from pg_roles m
				        where m.oid <> r.oid and pg_has_role(r.oid, m.oid, 'MEMBER') and %s) as risky_memberships,
				       array_to_string(current_schemas(false), ', ') as search_path,
				       coalesce((current_schemas(false))[1] = ? and ? = any (current_schemas(false)), false)
				           as search_path_ok
				from pg_roles r where r.rolname = current_user""".formatted(RISKY_ROLE), SCHEMA, SCHEMA, OWNER_ROLE,
				SCHEMA, SCHEMA, SCHEMA, EXTENSIONS_SCHEMA);
		String name = String.valueOf(role.get("name"));
		List<String> problems = new ArrayList<>();
		if (isTrue(role, "rolsuper")) {
			problems.add("суперпользователь");
		}
		if (isTrue(role, "rolbypassrls")) {
			problems.add("BYPASSRLS");
		}
		if (isTrue(role, "schema_owner")) {
			problems.add("владелец схемы " + SCHEMA);
		}
		if (isTrue(role, "table_owner")) {
			problems.add("владелец таблиц схемы " + SCHEMA);
		}
		if (role.get("risky_memberships") instanceof String memberships) {
			problems.add("член роли " + memberships + " (прямо или через другие роли)");
		}
		if (!problems.isEmpty()) {
			throw new IllegalStateException("Роль БД приложения '" + name + "' может обойти RLS: "
					+ String.join(", ", problems) + ". Приложение подключается ролью без этих прав и членств "
					+ "(dentify_app), см. spring.datasource.username и docs/architecture.md, «Роли и права».");
		}
		if (!isTrue(role, "search_path_ok")) {
			throw new IllegalStateException("У роли БД приложения '" + name + "' search_path — [" + role.get("search_path")
					+ "], а нужен " + SCHEMA + ", " + EXTENSIONS_SCHEMA + ": ALTER ROLE " + name + " IN DATABASE <база> SET "
					+ "search_path = " + SCHEMA + ", " + EXTENSIONS_SCHEMA + " (docs/architecture.md, «Роли и права»).");
		}
	}

	private static boolean isTrue(Map<String, Object> row, String column) {
		return Boolean.TRUE.equals(row.get(column));
	}

}
