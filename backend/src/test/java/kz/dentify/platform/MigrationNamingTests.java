package kz.dentify.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Имена миграций (docs/architecture.md, «Файлы и версии»): {@code <модуль>/V<yyyy>_<MM>_<dd>_<HHmm>__<модуль>_<что>.sql}
 * или {@code <модуль>/R__<модуль>_<что>.sql}; исключение — {@code platform/V1__platform_baseline.sql}. Flyway с
 * {@code validate-migration-naming} ловит только нарушения своего формата, модуль и метку времени проверяет этот
 * тест.
 */
class MigrationNamingTests {

	static final String LOCATION = "db/migration/";

	static final String BASELINE = "platform/V1__platform_baseline.sql";

	static final Pattern VERSIONED = Pattern
		.compile("([a-z]+)/V(\\d{4})_(\\d{2})_(\\d{2})_(\\d{2})(\\d{2})__([a-z]+)_[a-z0-9_]+\\.sql");

	static final Pattern REPEATABLE = Pattern.compile("([a-z]+)/R__([a-z]+)_[a-z0-9_]+\\.sql");

	@Test
	void migrationsFollowNamingConvention() throws IOException {
		Resource[] migrations = new PathMatchingResourcePatternResolver()
			.getResources("classpath*:" + LOCATION + "**/*.sql");

		assertThat(migrations).isNotEmpty();
		for (Resource migration : migrations) {
			String url = migration.getURL().toString();
			String path = url.substring(url.lastIndexOf(LOCATION) + LOCATION.length());
			if (path.equals(BASELINE)) {
				continue;
			}
			Matcher versioned = VERSIONED.matcher(path);
			Matcher repeatable = REPEATABLE.matcher(path);
			if (versioned.matches()) {
				assertThat(versioned.group(7)).as("модуль в имени и в каталоге: %s", path).isEqualTo(versioned.group(1));
				assertThatNoException().as("метка времени — существующие дата и время: %s", path)
					.isThrownBy(() -> LocalDateTime.of(Integer.parseInt(versioned.group(2)),
							Integer.parseInt(versioned.group(3)), Integer.parseInt(versioned.group(4)),
							Integer.parseInt(versioned.group(5)), Integer.parseInt(versioned.group(6))));
				assertModule(versioned.group(1), path);
			}
			else {
				assertThat(repeatable.matches())
					.as("%s: ждали <модуль>/V<yyyy>_<MM>_<dd>_<HHmm>__<модуль>_<что>.sql или <модуль>/R__<модуль>_<что>.sql",
							path)
					.isTrue();
				assertThat(repeatable.group(2)).as("модуль в имени и в каталоге: %s", path).isEqualTo(repeatable.group(1));
				assertModule(repeatable.group(1), path);
			}
		}
	}

	@Test
	void conventionAcceptsTimestampWithUnderscoresOnly() {
		assertThat(VERSIONED.matcher("clinic/V2026_10_01_1200__clinic_create_branch_chair.sql").matches()).isTrue();
		assertThat(REPEATABLE.matcher("patient/R__patient_search_view.sql").matches()).isTrue();
		// прежний формат: 12 цифр подряд попадали в лог Flyway и маскировались как ИИН
		assertThat(VERSIONED.matcher("clinic/V202610011200__clinic_create_branch_chair.sql").matches()).isFalse();
		assertThat(VERSIONED.matcher("clinic/V2__clinic_create_branch_chair.sql").matches()).isFalse();
	}

	/**
	 * Flyway читает подчёркивания версии как точки: в логах {@code 2026.10.01.1200}, а не 12 цифр подряд, которые
	 * маска логов приняла бы за ИИН. Порядок — по дате и времени, базовая V1 — раньше всех.
	 */
	@Test
	void flywayReadsTimestampVersionWithDots() {
		MigrationVersion version = MigrationVersion.fromVersion("2026_10_01_1200");

		assertThat(version.getVersion()).isEqualTo("2026.10.01.1200").doesNotContainPattern("\\d{5}");
		assertThat(version).isGreaterThan(MigrationVersion.fromVersion("1"))
			.isLessThan(MigrationVersion.fromVersion("2026_10_01_1201"))
			.isLessThan(MigrationVersion.fromVersion("2026_10_02_0900"))
			.isGreaterThan(MigrationVersion.fromVersion("2026_09_30_2359"));
	}

	/** Каталог миграции — модуль Modulith: прямой подпакет kz.dentify со своим package-info. */
	private static void assertModule(String module, String path) {
		assertThat(new ClassPathResource("kz/dentify/" + module + "/package-info.class").exists())
			.as("%s: каталог %s — не модуль kz.dentify", path, module)
			.isTrue();
	}

}
