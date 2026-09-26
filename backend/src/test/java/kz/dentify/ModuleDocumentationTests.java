package kz.dentify;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import org.springframework.modulith.docs.Documenter.CanvasOptions;
import org.springframework.modulith.docs.Documenter.DiagramOptions;
import org.springframework.modulith.docs.Documenter.DiagramOptions.ElementsWithoutRelationships;
import org.springframework.modulith.docs.Documenter.Options;

/**
 * Генерирует документацию модулей в apps/dentify/docs/modules/: общую C4-диаграмму {@code components.puml},
 * диаграмму и канву каждого модуля ({@code module-*.puml}, {@code module-*.adoc}) и сводный
 * {@code all-docs.adoc}. Каталог при каждом прогоне пересоздаётся — руками там не править.
 * <p>
 * В канвы попадает Javadoc модулей и классов: его при компиляции собирает spring-modulith-apt
 * ({@code target/generated-spring-modulith/javadoc.json}). Documenter Modulith 2.1.1 разбирает этот файл
 * простым {@code BasicJsonParser} Boot, который не учитывает строки: двойные кавычки и непарные скобки
 * ({@code [}, {@code (}, {@code {}) в Javadoc рабочих классов ломают разбор, и тест падает с «No signature found
 * in source map». В Javadoc — без кавычек и с парными скобками.
 */
class ModuleDocumentationTests {

	static final Path OUTPUT = Path.of("..", "docs", "modules");

	@Test
	void writesModuleDocumentation() {
		// модули без связей тоже на общей диаграмме: пока бизнес-кода нет, связей нет ни у кого
		DiagramOptions diagrams = DiagramOptions.defaults()
			.withElementsWithoutRelationships(ElementsWithoutRelationships.VISIBLE);

		new Documenter(ApplicationModules.of(DentifyApplication.class),
				Options.defaults().withOutputFolder(OUTPUT.toString()))
			.writeDocumentation(diagrams, CanvasOptions.defaults());

		assertThat(OUTPUT.resolve("components.puml")).content()
			.contains("Платформа", "Моя клиника", "Доступы и аккаунты", "Сотрудники", "Пациенты", "Расписание",
					"Медкарта", "Склад");
		assertThat(OUTPUT.resolve("all-docs.adoc")).exists();
	}

}
