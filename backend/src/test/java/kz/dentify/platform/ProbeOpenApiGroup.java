package kz.dentify.platform;

import org.springdoc.core.models.GroupedOpenApi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Группа springdoc {@code probes} только для тестов: спецификация пробных эндпоинтов {@link ProblemProbeController}
 * на {@code /v3/api-docs/probes}. На ней контрактный тест проверяет ответы ProblemDetail и параметры страницы, пока
 * настоящих эндпоинтов {@code /api/**} нет. Основная спецификация {@code /v3/api-docs} остаётся без пробных путей.
 */
@Configuration(proxyBeanMethods = false)
class ProbeOpenApiGroup {

	static final String GROUP = "probes";

	@Bean
	GroupedOpenApi probeOpenApi() {
		return GroupedOpenApi.builder().group(GROUP).pathsToMatch(ProblemProbeController.PATH + "/**").build();
	}

}
