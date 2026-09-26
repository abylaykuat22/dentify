package kz.dentify.platform.logging;

import java.nio.file.Path;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.json.JsonWriter.MemberPath;
import org.springframework.boot.json.JsonWriter.Members;
import org.springframework.boot.json.JsonWriter.ValueProcessor;
import org.springframework.boot.json.WritableJson;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.util.ObjectUtils;

/**
 * Прогоняет через {@link PiiMasker} каждое значение структурированного лога: {@code message},
 * {@code error.message}, {@code error.stack_trace}, значения MDC и key-value пар — любого типа, а не только
 * строки. Энкодер Boot пишет {@link Number} как есть, а прочие объекты — через {@code toString()}, поэтому
 * {@code addKeyValue(iin, 900101300123L)} или record пациента в key-value маскируются здесь же.
 * <p>
 * Поле, в имени которого есть признак ПД ({@link #PERSONAL_KEY_PARTS}: name, phone, iin, email, address, birth и
 * др.), заменяется маской целиком: регулярным выражением имя не найти. Имя сравнивается без учёта регистра и
 * целиком, поэтому составные ключи в camelCase и snake_case тоже закрыты: {@code patientFullName},
 * {@code doctorLastName}, {@code patientBirthDate}, {@code contact_phone}. Собственные поля форматов с name в имени
 * ({@link #FORMAT_FIELDS}: имя сервиса, потока) не маскируются. Соглашение — ПД в лог не пишем вовсе, в логах
 * только id; технические значения — под ключами без этих слов ({@code module}, {@code file}, а не
 * {@code moduleName}).
 * <p>
 * Зарегистрирован в {@code META-INF/spring.factories}, а не свойством
 * {@code logging.structured.json.customizer}: так маскирование действует в любом профиле и формате
 * (ecs, logstash, gelf) и его нельзя выключить настройкой.
 */
public class PiiMaskingJsonMembersCustomizer implements StructuredLoggingJsonMembersCustomizer<Object> {

	/**
	 * Части имени ключа MDC и key-value (без учёта регистра), при которых значение — ПД целиком. Для вложенных
	 * ключей ECS ({@code patient.fullName}) проверяется последний сегмент, для плоских форматов
	 * ({@code patient_fullName}) — всё имя.
	 */
	static final List<String> PERSONAL_KEY_PARTS = List.of("name", "phone", "iin", "email", "address", "birth",
			"patronymic", "fio");

	/**
	 * Собственные поля форматов Spring Boot 4.1 с признаком ПД в имени, полный путь: ECS, Logstash, GELF. В них
	 * имя сервиса, узла, потока и уровня, а не данные пациента.
	 */
	static final Set<String> FORMAT_FIELDS = Set.of("service.name", "service.node.name", "process.thread.name",
			"logger_name", "thread_name", "_process_thread_name", "_level_name");

	@Override
	public void customize(Members<Object> members) {
		ValueProcessor<Object> masking = PiiMaskingJsonMembersCustomizer::maskValue;
		members.applyingValueProcessor(masking);
	}

	/**
	 * Маскирует одно значение перед записью в JSON; {@code path} — путь поля (у элемента массива последний
	 * сегмент без имени).
	 * <ul>
	 * <li>значение поля с признаком ПД в имени ({@link #isPersonalKey}) — маска целиком;</li>
	 * <li>{@code null}, {@link Boolean}, время, {@link UUID}, {@link Enum} — как есть: ПД в них нет;</li>
	 * <li>{@link Map}, {@link Iterable}, массив, {@link WritableJson} — как есть: энкодер запишет их
	 * элементы по одному, и каждый снова пройдёт через этот метод;</li>
	 * <li>{@link Number} — остаётся числом, если маскировать нечего, иначе строка с маской;</li>
	 * <li>строки и любые другие объекты — {@code toString()} через {@link PiiMasker}.</li>
	 * </ul>
	 */
	static @Nullable Object maskValue(MemberPath path, @Nullable Object value) {
		if (value == null) {
			return null;
		}
		if (isPersonalKey(path)) {
			return PiiMasker.MASK;
		}
		if (value instanceof CharSequence text) {
			return PiiMasker.mask(text.toString());
		}
		if (isWrittenAsIs(value)) {
			return value;
		}
		String text = value.toString();
		String masked = PiiMasker.mask(text);
		return (value instanceof Number && text.equals(masked)) ? value : masked;
	}

	/**
	 * Имя поля (последний сегмент пути) содержит часть из {@link #PERSONAL_KEY_PARTS} без учёта регистра и поле
	 * не из {@link #FORMAT_FIELDS}.
	 */
	static boolean isPersonalKey(MemberPath path) {
		String name = path.name();
		if (name == null || FORMAT_FIELDS.contains(path.toUnescapedString())) {
			return false;
		}
		String lowerCase = name.toLowerCase(Locale.ROOT);
		for (String part : PERSONAL_KEY_PARTS) {
			if (lowerCase.contains(part)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isWrittenAsIs(Object value) {
		return value instanceof Boolean || value instanceof TemporalAccessor || value instanceof UUID
				|| value instanceof Enum<?> || value instanceof Map<?, ?> || value instanceof WritableJson
				|| (value instanceof Iterable<?> && !(value instanceof Path)) || ObjectUtils.isArray(value);
	}

}
