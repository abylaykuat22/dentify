package kz.dentify.platform.audit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import kz.dentify.platform.logging.PiiMasker;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import org.springframework.context.MessageSource;
import org.springframework.context.support.DefaultMessageSourceResolvable;

/**
 * Тексты журнала на языке запроса из {@code MessageSource} (dentify R003): текст события, названия полей, значения
 * разницы. Ключи:
 * <ul>
 * <li>текст события — {@code audit.<тип объекта>.<действие>}, иначе общий {@code audit.action.<действие>}. Аргументы:
 * {@code {0}} — имя объекта, {@code {1}} — кто, дальше — аргументы события;</li>
 * <li>поле — {@code audit.field.<тип объекта>.<путь>}, иначе {@code audit.field.<путь>}; путь с точкой без своего
 * ключа — названия частей через точку в середине строки, без ключа — сам путь;</li>
 * <li>значение перечисления — {@code audit.value.<тип объекта>.<поле>.<ЗНАЧЕНИЕ>}, иначе
 * {@code audit.value.<поле>.<ЗНАЧЕНИЕ>}; да и нет — {@code audit.value.true}, {@code audit.value.false}.</li>
 * </ul>
 * Даты и время — по формату языка, момент времени — ISO 8601 (пояс клиники появится с модулем clinic). Маска
 * ({@link #mask}) — ИИН, телефоны и e-mail ({@link PiiMasker}); значение поля-контакта ({@link #CONTACT_WORDS}), в
 * котором маска ничего не нашла, скрывается целиком.
 */
final class AuditTexts {

	/** Слова имени поля с контактом пациента: его значение без права видно только маской. */
	static final Set<String> CONTACT_WORDS = Set.of("phone", "phones", "mobile", "tel", "iin", "email", "emails", "mail");

	private static final String SEPARATOR = " · ";

	private final MessageSource messages;

	AuditTexts(MessageSource messages) {
		this.messages = messages;
	}

	String eventText(String entityType, String action, @Nullable String label, String actor, List<?> args,
			Locale locale) {
		Object[] arguments = new Object[2 + args.size()];
		arguments[0] = (label != null) ? label : "—";
		arguments[1] = actor;
		for (int i = 0; i < args.size(); i++) {
			arguments[i + 2] = args.get(i);
		}
		String[] codes = { "audit." + entityType + "." + action, "audit.action." + action };
		return this.messages.getMessage(new DefaultMessageSourceResolvable(codes, arguments, action), locale);
	}

	String text(String code, Locale locale) {
		return this.messages.getMessage(code, null, code, locale);
	}

	String fieldLabel(String entityType, String path, Locale locale) {
		String label = message(locale, "audit.field." + entityType + "." + path, "audit.field." + path);
		if (label != null) {
			return label;
		}
		if (!path.contains(".")) {
			return path;
		}
		List<String> parts = new ArrayList<>();
		for (String segment : path.split("\\.")) {
			String part = message(locale, "audit.field." + entityType + "." + segment, "audit.field." + segment);
			parts.add((part != null) ? part : segment);
		}
		return String.join(SEPARATOR, parts);
	}

	/** Значение разницы для показа; masked — без права на контакты. */
	@Nullable String value(String entityType, String field, String type, @Nullable JsonNode node, Locale locale,
			boolean masked) {
		if (node == null || node.isNull()) {
			return null;
		}
		String text = switch (type) {
			case "boolean" -> text(node.asBoolean() ? "audit.value.true" : "audit.value.false", locale);
			case "date" -> temporal(node.asString(), (value) -> LocalDate.parse(value)
				.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)));
			case "time" -> temporal(node.asString(), (value) -> LocalTime.parse(value)
				.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)));
			case "datetime" -> temporal(node.asString(), (value) -> LocalDateTime.parse(value)
				.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)));
			case "enum" -> enumValue(entityType, field, node.asString(), locale);
			case "list" -> list(node);
			case "json" -> node.toString();
			default -> node.isValueNode() ? node.asString() : node.toString();
		};
		boolean plain = type.equals("boolean") || type.equals("date") || type.equals("time") || type.equals("datetime")
				|| type.equals("instant");
		return (masked && !plain) ? mask(text, isContactField(field)) : text;
	}

	/**
	 * Маска ИИН, телефонов и e-mail в тексте. Поле-контакт, в значении которого маска ничего не распознала, скрывается
	 * целиком.
	 */
	static @Nullable String mask(@Nullable String text, boolean contact) {
		if (text == null) {
			return null;
		}
		String masked = Objects.requireNonNull(PiiMasker.mask(text));
		if (contact && masked.equals(text) && !text.isBlank()) {
			return PiiMasker.MASK;
		}
		return masked;
	}

	/** Поле с контактом пациента: в пути есть слово из {@link #CONTACT_WORDS}, например phone, phone2, contacts.email. */
	static boolean isContactField(String path) {
		return AuditDiff.words(path).stream().anyMatch(CONTACT_WORDS::contains);
	}

	private String enumValue(String entityType, String field, String value, Locale locale) {
		String label = message(locale, "audit.value." + entityType + "." + field + "." + value,
				"audit.value." + field + "." + value);
		return (label != null) ? label : value;
	}

	private static String list(JsonNode node) {
		if (!node.isArray()) {
			return node.isValueNode() ? node.asString() : node.toString();
		}
		List<String> items = new ArrayList<>();
		node.forEach((item) -> items.add(item.isValueNode() ? item.asString() : item.toString()));
		return String.join(", ", items);
	}

	private static String temporal(String value, Function<String, String> format) {
		try {
			return format.apply(value);
		}
		catch (DateTimeParseException ex) {
			return value;
		}
	}

	private @Nullable String message(Locale locale, String... codes) {
		for (String code : codes) {
			String text = this.messages.getMessage(code, null, null, locale);
			if (text != null) {
				return text;
			}
		}
		return null;
	}

}
