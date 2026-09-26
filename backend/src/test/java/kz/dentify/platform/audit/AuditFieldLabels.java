package kz.dentify.platform.audit;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.springframework.core.io.ClassPathResource;

/**
 * Тексты снимка аудита для тестов модулей (dentify R003): у каждого поля снимка есть название
 * {@code audit.field.<тип объекта>.<путь>} или {@code audit.field.<путь>} (у пути с точкой — у каждой части), у каждого
 * значения поля-перечисления — {@code audit.value.<тип объекта>.<поле>.<ЗНАЧЕНИЕ>} или
 * {@code audit.value.<поле>.<ЗНАЧЕНИЕ>}. Без ключа журнал покажет сырой путь поля или код значения. Модуль, который пишет
 * события, проверяет свои снимки:
 *
 * <pre>
 * assertThat(AuditFieldLabels.missing("schedule.appointment", AppointmentSnapshot.class)).isEmpty();
 * </pre>
 *
 * Пути — как у разницы: служебные поля верхнего уровня пропускаются, вложенный record — по своим полям через точку.
 */
public final class AuditFieldLabels {

	private AuditFieldLabels() {
	}

	/** Поля и значения без текста в {@code messages.properties}: путь поля или {@code путь.ЗНАЧЕНИЕ}. */
	public static List<String> missing(String entityType, Class<? extends Record> snapshot) {
		return missing(entityType, snapshot, "messages.properties");
	}

	/** То же по своим словарям, например с тестовым {@code test-messages.properties}. */
	public static List<String> missing(String entityType, Class<? extends Record> snapshot, String... dictionaries) {
		Properties texts = new Properties();
		for (String dictionary : dictionaries) {
			texts.putAll(load(dictionary));
		}
		List<String> missing = new ArrayList<>();
		collect(entityType, snapshot, "", texts, missing);
		return missing;
	}

	private static void collect(String entityType, Class<?> type, String prefix, Properties texts, List<String> missing) {
		for (RecordComponent component : type.getRecordComponents()) {
			if (prefix.isEmpty() && AuditDiff.TECHNICAL.contains(component.getName())) {
				continue;
			}
			String path = prefix + component.getName();
			Class<?> declared = component.getType();
			if (declared.isRecord()) {
				collect(entityType, declared, path + ".", texts, missing);
				continue;
			}
			if (!hasLabel(entityType, path, texts)) {
				missing.add(path);
			}
			if (declared.isEnum()) {
				for (Object constant : declared.getEnumConstants()) {
					String value = path + "." + ((Enum<?>) constant).name();
					if (!has(texts, "audit.value." + entityType + "." + value, "audit.value." + value)) {
						missing.add(value);
					}
				}
			}
		}
	}

	/** Как {@code AuditTexts.fieldLabel}: ключ пути, иначе у пути с точкой — ключ каждой части. */
	private static boolean hasLabel(String entityType, String path, Properties texts) {
		if (has(texts, "audit.field." + entityType + "." + path, "audit.field." + path)) {
			return true;
		}
		if (!path.contains(".")) {
			return false;
		}
		for (String segment : path.split("\\.")) {
			if (!has(texts, "audit.field." + entityType + "." + segment, "audit.field." + segment)) {
				return false;
			}
		}
		return true;
	}

	private static boolean has(Properties texts, String... keys) {
		for (String key : keys) {
			String text = texts.getProperty(key);
			if (text != null && !text.isBlank()) {
				return true;
			}
		}
		return false;
	}

	private static Properties load(String name) {
		Properties properties = new Properties();
		try (Reader reader = new InputStreamReader(new ClassPathResource(name).getInputStream(), StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Не прочитан " + name, ex);
		}
		return properties;
	}

}
