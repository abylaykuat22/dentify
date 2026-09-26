package kz.dentify.platform.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * Юнит-тест логгера (PLT-10): строка с ИИН, телефоном +7XXXXXXXXXX и e-mail уходит в JSON-лог
 * замаскированной — в сообщении, исключении, MDC и key-value любого типа; поля с ФИО, телефоном, ИИН, e-mail,
 * адресом и датой рождения — по имени ключа, в том числе составному; собственные поля форматов — как есть.
 * Настоящий энкодер Spring Boot (формат ECS, как в application.yml) в отдельном LoggerContext — без
 * Spring-контекста и без влияния на логирование других тестов.
 */
class StructuredLogMaskingTests {

	static final String IIN = "900101300123";

	static final String PHONE = "+77011234567";

	static final String EMAIL = "aigerim@example.kz";

	final LoggerContext context = new LoggerContext();

	final ByteArrayOutputStream output = new ByteArrayOutputStream();

	Logger logger;

	@BeforeEach
	void setUp() {
		// тот же MDC-адаптер, что у SLF4J: значения MDC.put из теста попадают в событие
		this.context.setMDCAdapter(MDC.getMDCAdapter());
		this.context.putObject(Environment.class.getName(),
				new MockEnvironment().withProperty("spring.application.name", "dentify-backend"));
		this.logger = logger("ecs", "kz.dentify.test");
	}

	/** Логгер с энкодером Boot в заданном формате, пишет в {@link #output}. */
	private Logger logger(String format, String name) {
		StructuredLogEncoder encoder = new StructuredLogEncoder();
		encoder.setContext(this.context);
		encoder.setFormat(format);
		encoder.start();
		OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
		appender.setContext(this.context);
		appender.setEncoder(encoder);
		appender.setOutputStream(this.output);
		appender.start();
		Logger result = this.context.getLogger(name);
		result.addAppender(appender);
		return result;
	}

	@AfterEach
	void tearDown() {
		MDC.clear();
		this.context.stop();
	}

	@Test
	void masksIinPhoneAndEmailInMessage() {
		this.logger.info("Пациент ИИН {} телефон {} почта {}", IIN, PHONE, EMAIL);

		JsonNode line = singleLine();
		assertThat(line.path("message").asString())
			.isEqualTo("Пациент ИИН ••••0123 телефон +7••••4567 почта a•••@example.kz");
		assertThat(line.toString()).doesNotContain(IIN, "7011234567", "aigerim");
	}

	@Test
	void masksExceptionMessageStackTraceAndMdc() {
		MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
		MDC.put("contact", PHONE);
		this.logger.error("Сбой сохранения",
				new IllegalStateException("Key (iin)=(" + IIN + ") already exists, email " + EMAIL));

		JsonNode line = singleLine();
		assertThat(line.path("error").path("message").asString())
			.isEqualTo("Key (iin)=(••••0123) already exists, email a•••@example.kz");
		assertThat(line.path("error").path("stack_trace").asString()).contains("••••0123").doesNotContain(IIN);
		assertThat(line.path("contact").asString()).isEqualTo("+7••••4567");
		assertThat(line.path("traceId").asString()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
		assertThat(line.toString()).doesNotContain(IIN, "7011234567", "aigerim");
	}

	/** Маска по значению: ключи без признака ПД в имени, значения разных типов. */
	@Test
	void masksKeyValuesOfAnyType() {
		this.logger.atInfo()
			.addKeyValue("codeLong", 900101300123L)
			.addKeyValue("numberLong", 77011234567L)
			.addKeyValue("visits", 3)
			.addKeyValue("patient", new PatientRef(IIN, PHONE))
			.addKeyValue("contacts", List.of(PHONE, EMAIL))
			.log("Карточка открыта");

		JsonNode line = singleLine();
		assertThat(line.path("codeLong").asString()).isEqualTo("••••0123");
		assertThat(line.path("numberLong").asString()).isEqualTo("+7••••4567");
		assertThat(line.path("visits").isNumber()).as("число без ПД остаётся числом").isTrue();
		assertThat(line.path("patient").asString()).isEqualTo("PatientRef[iin=••••0123, phone=+7••••4567]");
		assertThat(line.path("contacts").toString()).isEqualTo("[\"+7••••4567\",\"a•••@example.kz\"]");
		assertThat(line.toString()).doesNotContain(IIN, "7011234567", "aigerim");
	}

	@Test
	void masksNameFieldsByKey() {
		MDC.put("patient.lastName", "Нурланова");
		this.logger.atInfo()
			.addKeyValue("fullName", "Айгерим Нурланова")
			.addKeyValue("birthDate", "1990-01-01")
			.addKeyValue("clinicId", "01a0d780-19fc-78b8-9c3e-123456789012")
			.log("Карточка открыта");

		JsonNode line = singleLine();
		assertThat(line.path("fullName").asString()).isEqualTo(PiiMasker.MASK);
		assertThat(line.path("birthDate").asString()).isEqualTo(PiiMasker.MASK);
		assertThat(line.path("patient").path("lastName").asString()).isEqualTo(PiiMasker.MASK);
		assertThat(line.path("clinicId").asString()).isEqualTo("01a0d780-19fc-78b8-9c3e-123456789012");
		assertThat(line.toString()).doesNotContain("Айгерим", "Нурланова", "1990-01-01");
	}

	/**
	 * Составные ключи: признак ПД в любом месте имени, без учёта регистра, camelCase и snake_case, в MDC и
	 * key-value. Технические ключи без этих слов остаются.
	 */
	@Test
	void masksCompositePersonalKeys() {
		MDC.put("patientFullName", "Айгерим Нурланова");
		MDC.put("doctor.lastName", "Серикбаев");
		this.logger.atInfo()
			.addKeyValue("doctorLastName", "Серикбаев")
			.addKeyValue("patientBirthDate", "1990-01-01")
			.addKeyValue("PATIENT_SURNAME", "Нурланова")
			.addKeyValue("contact_phone", "дом. два-два-три")
			.addKeyValue("patientIIN", "без номера")
			.addKeyValue("notifyEmail", "почта не указана")
			.addKeyValue("homeAddress", "ул. Абая, 1")
			.addKeyValue("patientId", "01a0d780-19fc-78b8-9c3e-123456789012")
			.addKeyValue("status", "SCHEDULED")
			.addKeyValue("visits", 3)
			.log("Карточка открыта");

		JsonNode line = singleLine();
		for (String key : new String[] { "patientFullName", "doctorLastName", "patientBirthDate", "PATIENT_SURNAME",
				"contact_phone", "patientIIN", "notifyEmail", "homeAddress" }) {
			assertThat(line.path(key).asString()).as(key).isEqualTo(PiiMasker.MASK);
		}
		assertThat(line.path("doctor").path("lastName").asString()).isEqualTo(PiiMasker.MASK);
		assertThat(line.path("patientId").asString()).isEqualTo("01a0d780-19fc-78b8-9c3e-123456789012");
		assertThat(line.path("status").asString()).isEqualTo("SCHEDULED");
		assertThat(line.path("visits").isNumber()).isTrue();
		assertThat(line.toString()).doesNotContain("Айгерим", "Нурланова", "Серикбаев", "1990-01-01", "Абая",
				"два-два-три", "без номера", "не указана");
	}

	/**
	 * Собственные поля форматов с name в имени (имя сервиса, потока, логгера, уровня) не маскируются — в ECS,
	 * Logstash и GELF; ключ с ПД из MDC в них же маскируется.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "ecs", "logstash", "gelf" })
	void keepsFormatOwnFieldsIntact(String format) {
		Logger formatLogger = logger(format, "kz.dentify.test." + format);
		formatLogger.setAdditive(false);
		MDC.put("patientLastName", "Нурланова");
		formatLogger.info("Готово");

		JsonNode line = singleLine();
		String thread = Thread.currentThread().getName();
		switch (format) {
			case "ecs" -> {
				assertThat(line.path("service").path("name").asString()).isEqualTo("dentify-backend");
				assertThat(line.path("process").path("thread").path("name").asString()).isEqualTo(thread);
				assertThat(line.path("log").path("logger").asString()).isEqualTo("kz.dentify.test.ecs");
			}
			case "logstash" -> {
				assertThat(line.path("logger_name").asString()).isEqualTo("kz.dentify.test.logstash");
				assertThat(line.path("thread_name").asString()).isEqualTo(thread);
			}
			default -> {
				assertThat(line.path("host").asString()).isEqualTo("dentify-backend");
				assertThat(line.path("_process_thread_name").asString()).isEqualTo(thread);
				assertThat(line.path("_level_name").asString()).isEqualTo("INFO");
			}
		}
		assertThat(line.toString()).contains(PiiMasker.MASK).doesNotContain("Нурланова");
	}

	private JsonNode singleLine() {
		String text = this.output.toString(StandardCharsets.UTF_8).strip();
		assertThat(text.lines()).hasSize(1);
		return JsonMapper.builder().build().readTree(text);
	}

	/** Объект в key-value: энкодер пишет его через {@code toString()}. */
	record PatientRef(String iin, String phone) {
	}

}
