package kz.dentify.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import kz.dentify.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/**
 * PLT-07: любая ошибка API — ProblemDetail по RFC 9457 с {@code code}, {@code traceId}, текстом из
 * {@code messages.properties} и для ошибок полей — {@code errors[]}. Каждый код ответа — через пробный
 * {@link ProblemProbeController} и {@link ProblemProbeFilter}. Язык — по умолчанию русский: запросы без
 * Accept-Language.
 */
@ExtendWith(OutputCaptureExtension.class)
class ProblemDetailsTests extends AbstractIntegrationTest {

	static final JsonMapper JSON = JsonMapper.builder().build();

	static final String PROBE = ProblemProbeController.PATH;

	@Test
	void badRequestFromBeanValidationListsFieldErrors() {
		JsonNode problem = problem(this.http.post()
			.uri(PROBE + "/validate")
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"name": " ", "code": "a", "age": 200, "iin": "123", "title": {"ru": ""}}""")
			.exchange(), 400, "error.validation");

		assertThat(problem.path("detail").asString()).isEqualTo("Проверьте правильность заполнения полей.");
		assertThat(fieldErrors(problem)).containsExactlyInAnyOrder(
				Map.of("field", "name", "code", "validation.not-blank", "message", "Заполните поле."),
				Map.of("field", "code", "code", "validation.size", "message", "Допустимая длина — от 2 до 5."),
				Map.of("field", "age", "code", "validation.max", "message", "Значение не должно быть больше 150."),
				Map.of("field", "iin", "code", "probe.iin.format", "message", "ИИН — 12 цифр."),
				Map.of("field", "title.ru", "code", "validation.not-blank", "message", "Заполните поле."));
	}

	@Test
	void badRequestFromServiceViolationUsesItsCode() {
		JsonNode problem = problem(this.http.post().uri(PROBE + "/invalid").exchange(), 400, "error.validation");

		assertThat(fieldErrors(problem))
			.containsExactly(Map.of("field", "iin", "code", "probe.iin.format", "message", "ИИН — 12 цифр."));
	}

	@Test
	void badRequestForWrongJsonTypeNamesTheField() {
		JsonNode problem = problem(this.http.post()
			.uri(PROBE + "/validate")
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"name": "Айгерим", "age": "сорок", "birthDate": "2020-01-01"}""")
			.exchange(), 400, "error.validation");

		assertThat(fieldErrors(problem)).containsExactly(
				Map.of("field", "age", "code", "validation.type-mismatch", "message", "Неверный формат значения."));
	}

	@Test
	void badRequestForMalformedJson() {
		JsonNode problem = problem(this.http.post()
			.uri(PROBE + "/validate")
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\": ")
			.exchange(), 400, "error.malformed-body");

		assertThat(problem.path("detail").asString()).isEqualTo("Не удалось прочитать данные запроса.");
		assertThat(problem.has("errors")).isFalse();
	}

	@Test
	void badRequestForRequestParameters() {
		assertThat(fieldErrors(problem(this.http.get().uri(PROBE + "/params").exchange(), 400, "error.validation")))
			.containsExactly(Map.of("field", "count", "code", "validation.required", "message", "Обязательный параметр."));
		assertThat(fieldErrors(
				problem(this.http.get().uri(PROBE + "/params?count=many").exchange(), 400, "error.validation")))
			.containsExactly(Map.of("field", "count", "code", "validation.type-mismatch", "message",
					"Неверный формат значения."));
		assertThat(fieldErrors(
				problem(this.http.get().uri(PROBE + "/params?count=500").exchange(), 400, "error.validation")))
			.containsExactly(Map.of("field", "count", "code", "validation.max", "message",
					"Значение не должно быть больше 100."));
		assertThat(fieldErrors(
				problem(this.http.get().uri(PROBE + "/not-found/not-a-uuid").exchange(), 400, "error.validation")))
			.containsExactly(Map.of("field", "id", "code", "validation.type-mismatch", "message",
					"Неверный формат значения."));
	}

	/** Ограничение класса — ошибка всей формы: в errors[] она без поля, field — null. */
	@Test
	void formLevelErrorHasNullField() {
		JsonNode problem = problem(this.http.post()
			.uri(PROBE + "/period")
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"from": "2026-10-10", "to": "2026-10-01"}""")
			.exchange(), 400, "error.validation");

		assertThat(problem.path("errors")).hasSize(1);
		JsonNode error = problem.path("errors").get(0);
		assertThat(error.has("field")).isTrue();
		assertThat(error.path("field").isNull()).isTrue();
		assertThat(error.path("code").asString()).isEqualTo("probe.period.order");
		assertThat(error.path("message").asString()).isEqualTo("Дата окончания раньше даты начала.");
	}

	/**
	 * Проверка в сервисе ({@code @Validated}), а не в контроллере: ConstraintViolationException — тоже 400, поле —
	 * путь внутри объекта или имя параметра, без имени метода.
	 */
	@Test
	void badRequestFromServiceMethodValidationNamesTheField() {
		JsonNode command = problem(this.http.post()
			.uri(PROBE + "/service-validate")
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"name": " ", "tags": ["брекеты", " "]}""")
			.exchange(), 400, "error.validation");

		assertThat(fieldErrors(command)).containsExactlyInAnyOrder(
				Map.of("field", "name", "code", "validation.not-blank", "message", "Заполните поле."),
				Map.of("field", "tags[1]", "code", "validation.not-blank", "message", "Заполните поле."));
		assertThat(fieldErrors(
				problem(this.http.get().uri(PROBE + "/service-rename?name={name}", " ").exchange(), 400,
						"error.validation")))
			.containsExactly(Map.of("field", "name", "code", "validation.not-blank", "message", "Заполните поле."));
	}

	@Test
	void unauthorized() {
		JsonNode problem = problem(this.http.get().uri(PROBE + "/unauthorized").exchange(), 401, "error.unauthorized");

		assertThat(problem.path("title").asString()).isEqualTo("Требуется вход");
		assertThat(problem.path("detail").asString()).isEqualTo("Войдите в систему, чтобы продолжить.");
	}

	@Test
	void forbidden() {
		problem(this.http.get().uri(PROBE + "/forbidden").exchange(), 403, "error.forbidden");
	}

	@Test
	void notFound() {
		UUID id = UUID.randomUUID();
		JsonNode problem = problem(this.http.get().uri(PROBE + "/not-found/" + id).exchange(), 404, "error.not-found");

		assertThat(problem.path("title").asString()).isEqualTo("Не найдено");
		assertThat(problem.path("detail").asString()).isEqualTo("Запись не найдена или удалена.");
		assertThat(problem.path("instance").asString()).isEqualTo(PROBE + "/not-found/" + id);
	}

	/**
	 * Неизвестный путь API с входом — 404; без входа — 401 (SecurityTests): что пути нет, знает только вошедший. В лог —
	 * ни строки: WARN Spring MVC «No mapping for …» на каждый такой запрос дал бы вошедшему клиенту засыпать лог до
	 * предела общего лимита API.
	 */
	@Test
	void notFoundForUnknownPath(CapturedOutput output) {
		String path = "/api/v1/no-such-endpoint-" + UUID.randomUUID();
		problem(this.http.get()
			.uri(path)
			.header(HttpHeaders.COOKIE, signIn(UUID.randomUUID()).cookies())
			.exchange(), 404, "error.not-found");

		assertThat(output.getOut().lines().filter((line) -> line.contains(path)))
			.noneMatch((line) -> line.contains("\"WARN\"") || line.contains("\"ERROR\""));
	}

	@Test
	void methodNotAllowedKeepsAllowHeader() {
		ResponseSpec response = this.http.delete().uri(PROBE + "/forbidden").exchange();

		response.expectHeader().value(HttpHeaders.ALLOW, (allow) -> assertThat(allow).contains("GET"));
		problem(response, 405, "error.method-not-allowed");
	}

	@Test
	void unsupportedMediaType() {
		problem(this.http.post()
			.uri(PROBE + "/validate")
			.contentType(MediaType.TEXT_PLAIN)
			.body("name=x")
			.exchange(), 415, "error.unsupported-media-type");
	}

	/** 406: клиент не принимает JSON. Тело — всё равно ProblemDetail. */
	@Test
	void notAcceptable() {
		problem(this.http.get().uri(PROBE + "/page").accept(MediaType.APPLICATION_XML).exchange(), 406,
				"error.not-acceptable");
	}

	@Test
	void conflict() {
		problem(this.http.post().uri(PROBE + "/conflict").exchange(), 409, "error.conflict");
	}

	@Test
	void versionConflict() {
		problem(this.http.post().uri(PROBE + "/version-conflict").exchange(), 409, "error.version-conflict");
	}

	/** Нарушение уникального индекса — 409 со своим кодом, но без SQL, имени ограничения и значения поля. */
	@Test
	void duplicateHidesSqlAndValues() {
		String body = body(this.http.post().uri(PROBE + "/duplicate").exchange(), 409);

		assertThat(read(body).path("code").asString()).isEqualTo("error.duplicate");
		assertThat(body).doesNotContain("insert", "ux_patient_iin", "900101300123", "SQL", "Exception");
	}

	@Test
	void businessRuleWithArguments() {
		JsonNode problem = problem(this.http.post().uri(PROBE + "/business-rule").exchange(), 422,
				"probe.rule.backdated");

		assertThat(problem.path("detail").asString()).isEqualTo("Нельзя менять запись старше 30 дней.");
		assertThat(problem.path("title").asString()).isEqualTo("Действие недопустимо");
	}

	@Test
	void tooManyRequestsSetsRetryAfter() {
		ResponseSpec response = this.http.get().uri(PROBE + "/too-many-requests").exchange();

		response.expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "30");
		problem(response, 429, "error.too-many-requests");
	}

	/**
	 * Необработанное исключение: 500 с общим текстом и traceId, без стектрейса, сообщения и SQL; тот же traceId — у
	 * записи ERROR в логе.
	 */
	@Test
	void internalErrorHidesDetailsAndLogsSameTraceId(CapturedOutput output) {
		String body = body(this.http.get().uri(PROBE + "/failure").exchange(), 500);
		JsonNode problem = read(body);

		assertThat(problem.path("code").asString()).isEqualTo("error.internal");
		assertThat(problem.path("detail").asString())
			.isEqualTo("Что-то пошло не так. Если ошибка повторится, сообщите в поддержку код ошибки.");
		assertThat(body).doesNotContain("probe-failure", "select", "900101300123", "IllegalStateException",
				"kz.dentify", "trace\"");
		String traceId = problem.path("traceId").asString();
		JsonNode logLine = output.getOut()
			.lines()
			.filter((line) -> line.contains("probe-failure"))
			.map(ProblemDetailsTests::read)
			.findFirst()
			.orElseThrow();
		assertThat(logLine.path("log").path("level").asString()).isEqualTo("ERROR");
		assertThat(logLine.path("traceId").asString()).isEqualTo(traceId);
		assertThat(logLine.toString()).doesNotContain("900101300123");
	}

	/** Исключение в фильтре, до контроллеров: Boot пересылает на /error, ответ — тот же ProblemDetail. */
	@Test
	void filterFailureIsProblemDetail() {
		String body = body(this.http.get().uri(ProblemProbeFilter.FAILURE).exchange(), 500);

		assertThat(read(body).path("code").asString()).isEqualTo("error.internal");
		assertThat(read(body).path("instance").asString()).as("путь запроса, а не /error")
			.isEqualTo(ProblemProbeFilter.FAILURE);
		assertThat(body).doesNotContain("probe-filter-failure", "IllegalStateException", "timestamp");
	}

	@Test
	void sendErrorInFilterIsProblemDetail() {
		JsonNode problem = problem(this.http.get().uri(ProblemProbeFilter.SEND_ERROR).exchange(), 403,
				"error.forbidden");

		assertThat(problem.path("instance").asString()).isEqualTo(ProblemProbeFilter.SEND_ERROR);
	}

	@Test
	void platformExceptionInFilterKeepsItsStatus() {
		JsonNode problem = problem(this.http.get().uri(ProblemProbeFilter.NOT_FOUND).exchange(), 404,
				"error.not-found");

		assertThat(problem.path("instance").asString()).isEqualTo(ProblemProbeFilter.NOT_FOUND);
	}

	/**
	 * Прямой запрос на /error, без пересылки ошибки: 404 и без ERROR в логе, иначе любой клиент засыпал бы лог и поднял
	 * алерты.
	 */
	@Test
	void directErrorPathIsNotFoundWithoutErrorLog(CapturedOutput output) {
		problem(this.http.get().uri("/error").exchange(), 404, "error.not-found");
		problem(this.http.post().uri("/error").exchange(), 404, "error.not-found");

		assertThat(output.getOut().lines().filter((line) -> line.contains("ProblemErrorController")))
			.noneMatch((line) -> line.contains("\"ERROR\"") || line.contains("\"WARN\""));
	}

	/**
	 * Проверяет общий формат и возвращает тело: статус, тип application/problem+json, поля RFC 9457 и расширения
	 * code и traceId. Поле type Spring не пишет, если оно about:blank, — по RFC 9457 это то же самое.
	 */
	static JsonNode problem(ResponseSpec response, int status, String code) {
		JsonNode problem = read(body(response, status));
		assertThat(problem.path("type").asString("about:blank")).as("type: нет — значит about:blank, RFC 9457")
			.isEqualTo("about:blank");
		assertThat(problem.path("status").asInt()).isEqualTo(status);
		assertThat(problem.path("title").asString()).isNotBlank();
		assertThat(problem.path("detail").asString()).isNotBlank().doesNotContain("error.", "{");
		assertThat(problem.path("code").asString()).isEqualTo(code);
		assertThat(problem.path("traceId").asString()).matches("[0-9a-f]{32}");
		return problem;
	}

	/** Тело как UTF-8: у application/problem+json нет charset, а JSON всегда в UTF-8 (RFC 8259). */
	static String body(ResponseSpec response, int status) {
		byte[] body = response.expectStatus()
			.isEqualTo(status)
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody(byte[].class)
			.returnResult()
			.getResponseBody();
		return new String(body, StandardCharsets.UTF_8);
	}

	static JsonNode read(String json) {
		return JSON.readTree(json);
	}

	static List<Map<String, String>> fieldErrors(JsonNode problem) {
		List<Map<String, String>> errors = new ArrayList<>();
		problem.path("errors")
			.forEach((error) -> errors.add(Map.of("field", error.path("field").asString(), "code",
					error.path("code").asString(), "message", error.path("message").asString())));
		return errors;
	}

}
