package kz.dentify.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.Properties;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import kz.dentify.platform.api.AuditActions;
import kz.dentify.platform.api.EmailAddress;
import kz.dentify.platform.api.ErrorCodes;
import kz.dentify.platform.api.PhoneNumber;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;

/**
 * Словарь бэкенда (dentify R003, PLT-11): у каждого кода платформы и каждого стандартного ограничения Bean Validation
 * есть русский текст в базовом {@code messages.properties}, переводы не содержат лишних ключей.
 */
class MessagesTests {

	/** Все ограничения пакета jakarta.validation.constraints (Jakarta Validation 3.1). */
	static final List<Class<?>> CONSTRAINTS = List.of(AssertFalse.class, AssertTrue.class, DecimalMax.class,
			DecimalMin.class, Digits.class, Email.class, Future.class, FutureOrPresent.class, Max.class, Min.class,
			Negative.class, NegativeOrZero.class, NotBlank.class, NotEmpty.class, NotNull.class, Null.class, Past.class,
			PastOrPresent.class, Pattern.class, Positive.class, PositiveOrZero.class, Size.class);

	/** Статусы, которые отдаёт платформа: у каждого — заголовок ProblemDetail. */
	static final List<Integer> STATUSES = List.of(400, 401, 403, 404, 405, 406, 409, 413, 415, 422, 429, 500, 503);

	static final Properties RUSSIAN = load("messages.properties");

	@Test
	void everyPlatformErrorCodeHasRussianText() throws IllegalAccessException {
		for (Field field : ErrorCodes.class.getFields()) {
			if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
				String code = (String) field.get(null);
				assertThat(RUSSIAN.getProperty(code)).as("текст для %s (%s)", field.getName(), code).isNotBlank();
			}
		}
		assertThat(List.of(PhoneNumber.INVALID, EmailAddress.INVALID, EmailAddress.TOO_LONG))
			.as("коды полей телефона и e-mail")
			.allSatisfy((code) -> assertThat(RUSSIAN.getProperty(code)).as(code).isNotBlank());
	}

	/**
	 * Журнал действий: у каждого стандартного действия есть общий текст события, иначе пользователь увидел бы код
	 * действия; есть и служебные тексты журнала — кто, да и нет, ошибки чтения и выгрузки.
	 */
	@Test
	void everyAuditActionAndServiceTextHasRussianText() throws IllegalAccessException {
		for (Field field : AuditActions.class.getFields()) {
			if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
				String key = "audit.action." + field.get(null);
				assertThat(RUSSIAN.getProperty(key)).as("текст для %s (%s)", field.getName(), key).isNotBlank();
			}
		}
		assertThat(List.of("audit.actor.system", "audit.actor.unknown", "audit.value.true", "audit.value.false",
				"audit.platform.audit-log.exported", "audit.export.too-many-rows", "audit.period.invalid",
				"audit.period.too-long"))
			.allSatisfy((key) -> assertThat(RUSSIAN.getProperty(key)).as(key).isNotBlank());
	}

	@Test
	void everyStatusHasTitle() {
		assertThat(STATUSES).allSatisfy(
				(status) -> assertThat(RUSSIAN.getProperty("problem.title." + status)).as("status %s", status).isNotBlank());
	}

	@Test
	void everyStandardConstraintHasRussianText() {
		assertThat(CONSTRAINTS).allSatisfy((constraint) -> assertThat(RUSSIAN.getProperty(constraint.getName() + ".message"))
			.as(constraint.getSimpleName())
			.isNotBlank());
	}

	/** Тексты с аргументами идут через MessageFormat: апострофы и скобки не должны ломать шаблон. */
	@Test
	void messageFormatPatternsAreValid() {
		RUSSIAN.stringPropertyNames()
			.stream()
			.filter((key) -> !key.startsWith("jakarta."))
			.forEach((key) -> assertThatNoException().as(key)
				.isThrownBy(() -> new MessageFormat(RUSSIAN.getProperty(key)).format(new Object[] { 1, 2 })));
	}

	/** Перевод — только ключи базового словаря: опечатка в ключе kk молча показала бы русский текст. */
	@Test
	void translationsHaveOnlyKnownKeys() {
		Properties kazakh = load("messages_kk.properties");

		assertThat(kazakh.stringPropertyNames()).isNotEmpty().isSubsetOf(RUSSIAN.stringPropertyNames());
		assertThat(load("test-messages_kk.properties").stringPropertyNames())
			.isSubsetOf(load("test-messages.properties").stringPropertyNames());
	}

	@Test
	void russianIsTheBaseFileNotALocaleVariant() {
		assertThat(new ClassPathResource("messages.properties").exists()).isTrue();
		assertThat(new ClassPathResource("messages_ru.properties").exists())
			.as("русский — базовый messages.properties (иначе Boot не создаст MessageSource)")
			.isFalse();
	}

	static Properties load(String name) {
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
