package kz.dentify.platform.web;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonFormat;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON API по соглашению {@code /api/v1} (PLT-23). Деньги и другие {@link BigDecimal} — десятичной строкой:
 * {@code 12500.50}, а не числом, чтобы JavaScript не терял точность и не отрезал нули. В запросе принимается и
 * строка, и число. Даты Jackson 3 и так пишет строками ISO 8601, с поясом у OffsetDateTime и в UTC у Instant.
 */
@Configuration(proxyBeanMethods = false)
class JsonConventions {

	@Bean
	JsonMapperBuilderCustomizer decimalsAsStrings() {
		return (builder) -> builder.withConfigOverride(BigDecimal.class,
				(override) -> override.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING)));
	}

}
