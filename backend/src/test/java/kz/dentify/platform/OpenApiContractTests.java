package kz.dentify.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import kz.dentify.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Контракт API (PLT-07, PLT-23):
 * <ul>
 * <li>спецификация — OpenAPI 3.1, выгружается в {@code apps/dentify/api/openapi.json} (коммитится, по ней orval
 * генерирует клиент фронта). Файл переписывается, только если спецификация изменилась;</li>
 * <li>у каждой операции все ответы 4xx и 5xx — {@code application/problem+json} со схемой ProblemDetail. Пока
 * настоящих эндпоинтов нет, то же проверяется на пробной группе {@code probes};</li>
 * <li>параметр PageQuery раскрыт в page, size, sort, q с пределами;</li>
 * <li>Swagger UI вне dev не отдаётся.</li>
 * </ul>
 */
class OpenApiContractTests extends AbstractIntegrationTest {

	/** Тесты Maven запускаются из каталога backend/. */
	static final Path SPEC_FILE = Path.of("..", "api", "openapi.json");

	static final String PROBLEM_REF = "#/components/schemas/ProblemDetail";

	@Test
	void exportsOpenApi31SpecToRepository() throws IOException {
		String spec = spec("/v3/api-docs/v1").replace("\r\n", "\n");
		String content = spec.endsWith("\n") ? spec : spec + "\n";
		JsonNode json = ProblemDetailsTests.read(content);

		assertThat(json.path("openapi").asString()).startsWith("3.1");
		assertThat(json.path("info").path("title").asString()).isEqualTo("Dentify API");
		assertThat(json.path("servers").get(0).path("url").asString()).isEqualTo("/");
		assertThat(json.path("paths").propertyNames()).allSatisfy((path) -> assertThat(path).startsWith("/api/v1/"));
		assertThat(json.path("components").path("schemas").path("ProblemDetail").path("properties").propertyNames())
			.contains("type", "title", "status", "detail", "instance", "code", "traceId", "errors");
		assertThat(json.path("components").path("schemas").path("ProblemFieldError").path("properties").propertyNames())
			.containsExactlyInAnyOrder("field", "code", "message");
		assertErrorResponsesAreProblemDetail(json);

		Files.createDirectories(SPEC_FILE.getParent());
		if (!Files.exists(SPEC_FILE) || !Files.readString(SPEC_FILE, StandardCharsets.UTF_8).equals(content)) {
			Files.writeString(SPEC_FILE, content, StandardCharsets.UTF_8);
		}
	}

	/**
	 * operationId — имя хука и функции в клиенте orval: у каждой операции контракта он задан явно в {@code @Operation} и
	 * уникален. Иначе springdoc берёт имя метода и при совпадении дописывает суффикс {@code _1}, {@code _2} по порядку
	 * контроллеров, и имена в клиенте менялись бы от нового контроллера.
	 */
	@Test
	void operationIdsAreExplicitAndUnique(
			@Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping) {
		List<String> ids = new ArrayList<>();
		ProblemDetailsTests.read(spec("/v3/api-docs/v1"))
			.path("paths")
			.forEachEntry((path, item) -> item.forEachEntry((method, operation) -> {
				if (operation.has("responses")) {
					ids.add(operation.path("operationId").asString(""));
				}
			}));
		assertThat(ids).isNotEmpty()
			.doesNotHaveDuplicates()
			.allSatisfy((id) -> assertThat(id).matches("[a-z][A-Za-z]*"));

		List<String> implicit = new ArrayList<>();
		mapping.getHandlerMethods().forEach((info, handler) -> {
			boolean contract = info.getPatternValues().stream().anyMatch((pattern) -> pattern.startsWith("/api/v1/"));
			boolean hidden = handler.hasMethodAnnotation(Hidden.class)
					|| handler.getBeanType().isAnnotationPresent(Hidden.class);
			Operation operation = handler.getMethodAnnotation(Operation.class);
			if (contract && !hidden && (operation == null || operation.operationId().isBlank())) {
				implicit.add(handler.toString());
			}
		});
		assertThat(implicit).as("эндпоинты /api/v1 без явного @Operation(operationId)").isEmpty();
	}

	/** 429 — с заголовком Retry-After: клиент ждёт столько секунд, а не повторяет запрос сразу. */
	@Test
	void tooManyRequestsDocumentsRetryAfter() {
		List<String> missing = new ArrayList<>();
		ProblemDetailsTests.read(spec("/v3/api-docs/v1"))
			.path("paths")
			.forEachEntry((path, item) -> item.forEachEntry((method, operation) -> {
				JsonNode header = operation.path("responses").path("429").path("headers").path(HttpHeaders.RETRY_AFTER);
				if (operation.has("responses") && !"integer".equals(types(header.path("schema")).getFirst())) {
					missing.add(method + " " + path);
				}
			}));
		assertThat(missing).as("429 без Retry-After").isEmpty();
	}

	/** Приёмка PLT-07: у каждого эндпоинта ответы 4xx описаны схемой ProblemDetail. */
	@Test
	void everyErrorResponseIsProblemDetail() {
		JsonNode probes = ProblemDetailsTests.read(spec("/v3/api-docs/" + ProbeOpenApiGroup.GROUP));

		assertThat(probes.path("paths").propertyNames()).isNotEmpty()
			.allSatisfy((path) -> assertThat(path).startsWith(ProblemProbeController.PATH));
		assertErrorResponsesAreProblemDetail(probes);
	}

	@Test
	void standardErrorResponsesByOperationKind() {
		JsonNode paths = ProblemDetailsTests.read(spec("/v3/api-docs/" + ProbeOpenApiGroup.GROUP)).path("paths");
		String probe = ProblemProbeController.PATH;

		assertThat(paths.path(probe + "/forbidden").path("get").path("responses").propertyNames())
			.containsExactlyInAnyOrder("200", "400", "401", "403", "429", "500");
		assertThat(paths.path(probe + "/not-found/{id}").path("get").path("responses").propertyNames()).contains("404")
			.doesNotContain("409", "422");
		assertThat(paths.path(probe + "/validate").path("post").path("responses").propertyNames()).contains("409",
				"422");
	}

	@Test
	void pageQueryIsDocumentedAsQueryParameters() {
		JsonNode operation = ProblemDetailsTests.read(spec("/v3/api-docs/" + ProbeOpenApiGroup.GROUP))
			.path("paths")
			.path(ProblemProbeController.PATH + "/page")
			.path("get");
		Map<String, JsonNode> parameters = new HashMap<>();
		operation.path("parameters").forEach((parameter) -> parameters.put(parameter.path("name").asString(), parameter));

		assertThat(parameters).containsOnlyKeys("page", "size", "sort", "q");
		assertThat(parameters.values()).allSatisfy((parameter) -> assertThat(parameter.path("in").asString())
			.isEqualTo("query"));
		JsonNode size = parameters.get("size").path("schema");
		assertThat(size.path("type").asString()).isEqualTo("integer");
		assertThat(size.path("maximum").asInt()).isEqualTo(100);
		assertThat(size.path("default").asInt()).isEqualTo(25);
		assertThat(parameters.get("sort").path("schema").path("items").path("enum").valueStream().map(JsonNode::asString))
			.containsExactly("name", "-name", "createdAt", "-createdAt");
		assertThat(parameters.get("q").path("schema").path("maxLength").asInt()).isEqualTo(100);
	}

	/** DTO — record: компонент без @Nullable обязателен в схеме, иначе orval сделает поле ответа необязательным. */
	@Test
	void recordComponentsWithoutNullableAreRequired() {
		JsonNode schemas = ProblemDetailsTests.read(spec("/v3/api-docs/" + ProbeOpenApiGroup.GROUP))
			.path("components")
			.path("schemas");

		assertThat(required(schemas, "PageResponseString")).containsExactlyInAnyOrder("items", "page", "size", "total");
		assertThat(required(schemas, "PageQueryEcho")).containsExactlyInAnyOrder("page", "size", "sort");
		assertThat(required(schemas, "LocalizedText")).containsExactly("ru");
		assertThat(required(schemas, "ProblemDetail")).containsExactlyInAnyOrder("title", "status", "detail", "code");
		assertThat(required(schemas, "ProblemFieldError")).containsExactlyInAnyOrder("code", "message");
	}

	/**
	 * Компонент с @Nullable Jackson пишет в JSON как null: {@code {"kk": null}}, {@code "field": null}. Схема должна
	 * это допускать (OpenAPI 3.1: null в type, у ссылки — oneOf с type null), иначе zod-схема orval отвергнет
	 * настоящий ответ. Компонент без @Nullable null не допускает.
	 */
	@Test
	void nullableRecordComponentsAllowNull() {
		JsonNode schemas = ProblemDetailsTests.read(spec("/v3/api-docs/" + ProbeOpenApiGroup.GROUP))
			.path("components")
			.path("schemas");

		assertThat(types(property(schemas, "LocalizedText", "kk"))).containsExactlyInAnyOrder("string", "null");
		assertThat(types(property(schemas, "LocalizedText", "ru"))).containsExactly("string");
		assertThat(types(property(schemas, "PageQueryEcho", "q"))).containsExactlyInAnyOrder("string", "null");
		assertThat(types(property(schemas, "PageQueryEcho", "page"))).containsExactly("integer");
		assertThat(types(property(schemas, "ProblemFieldError", "field"))).containsExactlyInAnyOrder("string", "null");
		assertThat(types(property(schemas, "ProblemFieldError", "code"))).containsExactly("string");
		assertThat(types(property(schemas, "ProblemDetail", "errors"))).containsExactlyInAnyOrder("array", "null");
		assertThat(types(property(schemas, "ProbeRequest", "age"))).containsExactlyInAnyOrder("integer", "null");
		assertThat(types(property(schemas, "ProbeRequest", "price"))).containsExactlyInAnyOrder("string", "null");
		assertThat(property(schemas, "ProbeRequest", "price").path("format").asString()).isEqualTo("decimal");

		JsonNode title = property(schemas, "ProbeRequest", "title");
		assertThat(title.has("$ref")).as("ссылка с null — через oneOf").isFalse();
		assertThat(title.path("oneOf").valueStream().map((option) -> option.has("$ref")
				? option.path("$ref").asString() : String.join(",", types(option))))
			.containsExactlyInAnyOrder("#/components/schemas/LocalizedText", "null");

		JsonNode apiV1 = ProblemDetailsTests.read(spec("/v3/api-docs/v1")).path("components").path("schemas");
		assertThat(types(property(apiV1, "ProblemFieldError", "field"))).containsExactlyInAnyOrder("string", "null");
	}

	/** Деньги — строка формата decimal, как их пишет JSON API. */
	@Test
	void decimalsAreDocumentedAsStrings() {
		JsonNode price = ProblemDetailsTests.read(spec("/v3/api-docs/" + ProbeOpenApiGroup.GROUP))
			.path("components")
			.path("schemas")
			.path("ProbeRequest")
			.path("properties")
			.path("price");

		assertThat(types(price)).contains("string");
		assertThat(price.path("format").asString()).isEqualTo("decimal");
	}

	/**
	 * Swagger UI вне dev не отдаётся ни через springdoc, ни как webjar: зависимость UI — только в Maven-профиле dev,
	 * а Boot не раздаёт /webjars/** ({@code spring.web.resources.add-mappings: false}) даже с UI на classpath.
	 */
	@Test
	void swaggerUiOnlyInDev() {
		String session = signIn(UUID.randomUUID()).cookies();
		for (String path : List.of("/swagger-ui.html", "/swagger-ui/index.html", "/swagger-ui/swagger-initializer.js",
				"/webjars/swagger-ui/index.html", "/webjars/swagger-ui/swagger-initializer.js")) {
			// без входа — 401: пути Swagger UI открыты без входа только там, где UI включён (dev)
			this.http.get().uri(path).exchange().expectStatus().isUnauthorized();
			this.http.get().uri(path).header(HttpHeaders.COOKIE, session).exchange().expectStatus().isNotFound();
		}
	}

	@Test
	void defaultStaticResourceHandlingIsDisabled(@Autowired Environment environment) {
		assertThat(environment.getProperty("spring.web.resources.add-mappings", Boolean.class)).isFalse();
	}

	private static void assertErrorResponsesAreProblemDetail(JsonNode spec) {
		List<String> problems = new ArrayList<>();
		spec.path("paths").forEachEntry((path, item) -> item.forEachEntry((method, operation) -> {
			if (!Set.of("get", "post", "put", "patch", "delete").contains(method)) {
				return;
			}
			operation.path("responses").forEachEntry((status, response) -> {
				if (!status.startsWith("4") && !status.startsWith("5")) {
					return;
				}
				JsonNode content = response.path("content");
				String ref = content.path("application/problem+json").path("schema").path("$ref").asString("");
				if (!PROBLEM_REF.equals(ref) || content.size() != 1) {
					problems.add(method + " " + path + " " + status);
				}
			});
		}));
		assertThat(problems).as("ответы 4xx/5xx без схемы ProblemDetail").isEmpty();
	}

	private static List<String> required(JsonNode schemas, String name) {
		return schemas.path(name).path("required").valueStream().map(JsonNode::asString).toList();
	}

	private static JsonNode property(JsonNode schemas, String schema, String property) {
		JsonNode node = schemas.path(schema).path("properties").path(property);
		assertThat(node.isMissingNode()).as("свойство %s.%s", schema, property).isFalse();
		return node;
	}

	/** Тип схемы OpenAPI 3.1: строка или массив типов. */
	private static List<String> types(JsonNode schema) {
		JsonNode type = schema.path("type");
		return type.isArray() ? type.valueStream().map(JsonNode::asString).toList() : List.of(type.asString());
	}

	private String spec(String path) {
		return this.http.get()
			.uri(path)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
	}

}
