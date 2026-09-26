package kz.dentify.platform;

import static org.assertj.core.api.Assertions.assertThat;

import kz.dentify.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * PLT-10 в собранном приложении: лог внутри запроса — одна JSON-строка (ECS) с traceId и spanId этого
 * запроса и без ПД. Лог пишет пробный {@link LogProbeController} из тестовых исходников.
 */
@ExtendWith(OutputCaptureExtension.class)
class TraceIdLoggingTests extends AbstractIntegrationTest {

	@Test
	void requestLogIsJsonWithTraceIdAndWithoutPersonalData(CapturedOutput output) {
		this.http.get().uri(LogProbeController.PATH).exchange().expectStatus().isOk();

		String line = output.getOut()
			.lines()
			.filter((l) -> l.contains(LogProbeController.MARKER))
			.findFirst()
			.orElseThrow();
		JsonNode json = JsonMapper.builder().build().readTree(line);

		assertThat(json.path("traceId").asString()).matches("[0-9a-f]{32}");
		assertThat(json.path("spanId").asString()).matches("[0-9a-f]{16}");
		assertThat(json.path("log").path("logger").asString()).isEqualTo(LogProbeController.class.getName());
		assertThat(json.path("message").asString()).contains("••••0123", "+7••••4567", "a•••@example.kz");
		assertThat(line).doesNotContain("900101300123", "7011234567", "aigerim");
	}

}
