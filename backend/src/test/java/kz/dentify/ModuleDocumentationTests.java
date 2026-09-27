package kz.dentify;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
 * {@code all-docs.adoc}. Каталог при каждом прогоне пересоздаётся — руками там не править. Связи на диаграммах
 * ({@code Rel(…)}) Documenter пишет в порядке, который меняется от прогона к прогону; тест их сортирует, чтобы файлы
 * менялись только вместе с модулями.
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
	void writesModuleDocumentation() throws IOException {
		// модули без связей тоже на общей диаграмме: пока бизнес-кода нет, связей нет ни у кого
		DiagramOptions diagrams = DiagramOptions.defaults()
			.withElementsWithoutRelationships(ElementsWithoutRelationships.VISIBLE);

		new Documenter(ApplicationModules.of(DentifyApplication.class),
				Options.defaults().withOutputFolder(OUTPUT.toString()))
			.writeDocumentation(diagrams, CanvasOptions.defaults());
		try (Stream<Path> files = Files.list(OUTPUT)) {
			for (Path diagram : files.filter((file) -> file.toString().endsWith(".puml")).toList()) {
				sortRelations(diagram);
			}
		}

		assertThat(OUTPUT.resolve("components.puml")).content()
			.contains("Платформа", "Моя клиника", "Доступы и аккаунты", "Сотрудники", "Пациенты", "Расписание",
					"Медкарта", "Склад");
		assertThat(OUTPUT.resolve("all-docs.adoc")).exists();
	}

	/** Строки {@code Rel(…)} диаграммы — по алфавиту, на тех же местах; остальное как есть. */
	static void sortRelations(Path diagram) throws IOException {
		String content = Files.readString(diagram, StandardCharsets.UTF_8);
		String[] lines = content.split("\n", -1);
		List<Integer> positions = new ArrayList<>();
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].startsWith("Rel(")) {
				positions.add(i);
			}
		}
		List<String> relations = positions.stream().map((i) -> lines[i]).sorted().toList();
		for (int i = 0; i < positions.size(); i++) {
			lines[positions.get(i)] = relations.get(i);
		}
		String sorted = String.join("\n", lines);
		if (!sorted.equals(content)) {
			Files.writeString(diagram, sorted, StandardCharsets.UTF_8);
		}
	}

}
