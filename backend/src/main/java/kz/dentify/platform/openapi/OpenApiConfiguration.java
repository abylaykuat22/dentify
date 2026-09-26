package kz.dentify.platform.openapi;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import kz.dentify.platform.api.PageQuery;
import kz.dentify.platform.api.SortBy;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.utils.SpringDocUtils;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;

/**
 * Общие соглашения API в спецификации OpenAPI (PLT-07, PLT-23):
 * <ul>
 * <li>у каждой операции ответы 4xx и 5xx описаны схемой {@value #PROBLEM_DETAIL} с типом
 * {@code application/problem+json}. Всем операциям добавляются 400, 401, 403, 429 и 500, операциям с параметром
 * пути — 404, изменяющим (POST, PUT, PATCH, DELETE) — 409 и 422. Ответ, который контроллер описал сам через
 * {@code @ApiResponse}, остаётся со своим описанием, но тело у него тоже ProblemDetail. У 429 — заголовок Retry-After
 * в секундах;</li>
 * <li>параметр {@link PageQuery} раскрывается в query-параметры page, size, sort и q с пределами из
 * {@link PageQuery} и полями сортировки из {@link SortBy};</li>
 * <li>BigDecimal, то есть деньги, — строка формата decimal, как его пишет {@code JsonConventions};</li>
 * <li>сервер в спецификации — относительный {@code /}: выгруженный файл не зависит от порта, на котором его
 * сняли.</li>
 * </ul>
 * Спецификация — группа {@value #GROUP}, {@code /v3/api-docs/v1}, включена настройками springdoc в dev и test. Swagger
 * UI — только локально: его зависимость подключает Maven-профиль dev, в jar для стенда и прода её нет.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

	/** Группа springdoc с контрактом для фронта: спецификация на {@code /v3/api-docs/v1}. */
	static final String GROUP = "v1";

	static final String PROBLEM_DETAIL = "ProblemDetail";

	static final String PROBLEM_JSON = org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

	/** Описания ответов об ошибках, которые добавляются операциям. */
	static final Map<String, String> ERROR_RESPONSES = Map.of(
			"400", "Неверные данные запроса; ошибки полей — в errors",
			"401", "Нет сессии",
			"403", "Нет права на действие",
			"404", "Не найдено или принадлежит другой клинике",
			"409", "Конфликт версии записи или уникальности",
			"422", "Нарушено бизнес-правило",
			"429", "Превышен лимит запросов; через сколько повторить — в заголовке Retry-After",
			"500", "Внутренняя ошибка: только общий текст и traceId");

	static final List<String> ALWAYS = List.of("400", "401", "403", "429", "500");

	/** Ответ 429 несёт заголовок Retry-After в секундах: клиент ждёт его, а не повторяет сразу. */
	static final String TOO_MANY_REQUESTS = "429";

	static final Set<PathItem.HttpMethod> MUTATING = Set.of(PathItem.HttpMethod.POST, PathItem.HttpMethod.PUT,
			PathItem.HttpMethod.PATCH, PathItem.HttpMethod.DELETE);

	static {
		// PageQuery собирает PageQueryArgumentResolver, в спецификации вместо него — четыре query-параметра
		SpringDocUtils.getConfig().addRequestWrapperToIgnore(PageQuery.class);
		// деньги в JSON — десятичной строкой (JsonConventions), в схеме тоже строка
		SpringDocUtils.getConfig()
			.replaceWithSchema(BigDecimal.class, types(new StringSchema().format("decimal").example("12500.50")));
	}

	@Bean
	OpenAPI dentifyOpenApi() {
		return new OpenAPI()
			.info(new Info().title("Dentify API")
				.version("v1")
				.description("REST API Dentify. Ошибки — ProblemDetail по RFC 9457 с полями code, traceId и errors; "
						+ "списки — PageResponse с параметрами page, size, sort, q."))
			.servers(List.of(new Server().url("/")));
	}

	/** Контракт для фронта — только {@code /api/v1/**}: служебные пути вроде /error в него не попадают. */
	@Bean
	GroupedOpenApi apiV1() {
		return GroupedOpenApi.builder().group(GROUP).displayName("Dentify API v1").pathsToMatch("/api/v1/**").build();
	}

	@Bean
	GlobalOperationCustomizer pageQueryParameters() {
		return (operation, handlerMethod) -> addPageParameters(operation, handlerMethod);
	}

	@Bean
	GlobalOpenApiCustomizer problemDetailResponses() {
		return OpenApiConfiguration::addProblemResponses;
	}

	static Operation addPageParameters(Operation operation, HandlerMethod handlerMethod) {
		for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
			if (PageQuery.class.equals(parameter.getParameterType())) {
				pageParameters(parameter.getParameterAnnotation(SortBy.class)).forEach(operation::addParametersItem);
			}
		}
		return operation;
	}

	static List<Parameter> pageParameters(@Nullable SortBy sortBy) {
		List<Parameter> parameters = new ArrayList<>();
		parameters.add(query("page", "Номер страницы с 0",
				types(new IntegerSchema().minimum(BigDecimal.ZERO)._default(0))));
		parameters.add(query("size", "Размер страницы; больше " + PageQuery.MAX_SIZE + " — вернётся " + PageQuery.MAX_SIZE,
				types(new IntegerSchema().minimum(BigDecimal.ONE)
					.maximum(BigDecimal.valueOf(PageQuery.MAX_SIZE))
					._default(PageQuery.DEFAULT_SIZE))));
		if (sortBy != null) {
			List<String> values = new ArrayList<>();
			for (String field : sortBy.value()) {
				values.add(field);
				values.add("-" + field);
			}
			parameters.add(query("sort", "Сортировка: поля через запятую или повтором параметра, минус — по убыванию",
					types(new ArraySchema().items(types(new StringSchema()._enum(values))))));
		}
		parameters.add(query("q", "Строка поиска",
				types(new StringSchema().maxLength(PageQuery.MAX_QUERY_LENGTH))));
		return parameters;
	}

	@SuppressWarnings("rawtypes")
	static void addProblemResponses(OpenAPI openApi) {
		Components components = (openApi.getComponents() != null) ? openApi.getComponents() : new Components();
		openApi.setComponents(components);
		Map<String, Schema> schemas = ModelConverters.getInstance(true)
			.readAll(new AnnotatedType(ProblemDetailSchema.class));
		schemas.forEach(components::addSchemas);
		if (openApi.getPaths() == null) {
			return;
		}
		openApi.getPaths()
			.forEach((path, item) -> item.readOperationsMap()
				.forEach((method, operation) -> addProblemResponses(path, method, operation)));
	}

	private static void addProblemResponses(String path, PathItem.HttpMethod method, Operation operation) {
		ApiResponses responses = (operation.getResponses() != null) ? operation.getResponses() : new ApiResponses();
		operation.setResponses(responses);
		List<String> statuses = new ArrayList<>(ALWAYS);
		if (path.contains("{")) {
			statuses.add("404");
		}
		if (MUTATING.contains(method)) {
			statuses.add("409");
			statuses.add("422");
		}
		for (String status : statuses) {
			if (!responses.containsKey(status)) {
				responses.addApiResponse(status, new ApiResponse().description(ERROR_RESPONSES.get(status)));
			}
		}
		responses.forEach((status, response) -> {
			if (status.startsWith("4") || status.startsWith("5")) {
				response.setContent(new Content().addMediaType(PROBLEM_JSON,
						new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL))));
			}
			if (TOO_MANY_REQUESTS.equals(status)) {
				response.addHeaderObject(HttpHeaders.RETRY_AFTER, new Header()
					.description("Через сколько секунд повторить запрос")
					.required(true)
					.schema(types(new IntegerSchema().minimum(BigDecimal.ONE))));
			}
		});
	}

	private static Parameter query(String name, String description, Schema<?> schema) {
		return new Parameter().in("query").name(name).description(description).required(false).schema(schema);
	}

	/** В OpenAPI 3.1 тип схемы пишется из набора types: springdoc переносит в него type. */
	private static <T extends Schema<?>> T types(T schema) {
		SpringDocUtils.handleSchemaTypes(schema);
		return schema;
	}

}
