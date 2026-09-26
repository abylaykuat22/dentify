package kz.dentify;

import java.nio.file.Files;
import java.nio.file.Path;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL 18 для интеграционных тестов — та же база, что в dev: образ из deploy/compose.dev.yml и тот же
 * init-скрипт deploy/postgres/init/01-roles.sh (база и схема dentify, роли dentify_owner / dentify_app,
 * расширения, гранты по умолчанию).
 * <p>
 * Суперпользователь образа — {@code postgres}, но клиенты контейнера ({@code @ServiceConnection} →
 * {@code spring.datasource}) видят роль приложения {@code dentify_app}: тесты ходят в БД так же, как
 * приложение, и RLS на них действует. Flyway ходит владельцем {@code dentify_owner}.
 */
public final class DentifyPostgresContainer extends PostgreSQLContainer {

	/** Тот же тег, что в deploy/compose.dev.yml (там же закреплён digest). */
	static final String IMAGE = "postgres:18.6";

	/** Суперпользователь образа: только init-скрипт и тесты, которые проверяют отказ от него. */
	public static final String SUPERUSER = "postgres";

	public static final String APP_ROLE = "dentify_app";

	public static final String OWNER_ROLE = "dentify_owner";

	/** Пароли одноразового контейнера: живут только на время прогона тестов. */
	static final String APP_PASSWORD = "test-app-password";

	public static final String OWNER_PASSWORD = "test-owner-password";

	public static final String SUPERUSER_PASSWORD = "test-postgres-password";

	/** Тесты Maven запускаются из каталога backend/. */
	static final Path ROLES_SCRIPT = Path.of("..", "deploy", "postgres", "init", "01-roles.sh");

	DentifyPostgresContainer() {
		super(DockerImageName.parse(IMAGE));
		if (!Files.isRegularFile(ROLES_SCRIPT)) {
			throw new IllegalStateException("Не найден init-скрипт ролей: " + ROLES_SCRIPT.toAbsolutePath());
		}
		withDatabaseName("dentify");
		withUsername(SUPERUSER);
		withPassword(SUPERUSER_PASSWORD);
		withEnv("DENTIFY_OWNER_PASSWORD", OWNER_PASSWORD);
		withEnv("DENTIFY_APP_PASSWORD", APP_PASSWORD);
		withCopyFileToContainer(MountableFile.forHostPath(ROLES_SCRIPT, 0755), "/docker-entrypoint-initdb.d/01-roles.sh");
	}

	/** Логин для клиентов контейнера — роль приложения, а не суперпользователь образа. */
	@Override
	public String getUsername() {
		return APP_ROLE;
	}

	@Override
	public String getPassword() {
		return APP_PASSWORD;
	}

}
