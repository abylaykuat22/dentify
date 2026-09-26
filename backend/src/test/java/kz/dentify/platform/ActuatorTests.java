package kz.dentify.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import kz.dentify.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * PLT-10: liveness и readiness, метрики Prometheus, info и modulith — только на внутреннем порту управления;
 * на порту приложения (его проксирует Caddy) actuator не отвечает.
 */
class ActuatorTests extends AbstractIntegrationTest {

	@LocalServerPort
	int serverPort;

	@LocalManagementPort
	int managementPort;

	/** Клиент к порту управления (8081 в dev и prod, случайный в тестах). */
	RestTestClient management;

	@BeforeEach
	void managementClient() {
		this.management = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + this.managementPort).build();
	}

	@Test
	void managementRunsOnSeparatePort() {
		assertThat(this.managementPort).isPositive().isNotEqualTo(this.serverPort);
	}

	@Test
	void livenessIsUp() {
		assertThat(get(this.management, "/actuator/health/liveness")).contains("\"status\":\"UP\"");
	}

	@Test
	void readinessIsUpAndChecksDatabaseAndValkey() {
		assertThat(get(this.management, "/actuator/health/readiness")).contains("\"status\":\"UP\"", "\"db\"",
				"\"redis\"", "\"readinessState\"");
	}

	@Test
	void prometheusMetricsAreOnManagementPort() {
		assertThat(get(this.management, "/actuator/prometheus")).contains("jvm_memory_used_bytes",
				"hikaricp_connections", "application=\"dentify-backend\"");
	}

	/** Алерт «p95 больше 1 с» (PLT-10) считается в Prometheus по бакетам гистограммы http.server.requests. */
	@Test
	void httpServerRequestsHaveHistogramBuckets() {
		this.http.get().uri("/api/no-such-endpoint").exchange();

		assertThat(get(this.management, "/actuator/prometheus")).contains("http_server_requests_seconds_bucket");
	}

	/** Версия и время сборки (spring-boot-maven-plugin, цель build-info) — для трекера ошибок и алертов. */
	@Test
	void infoShowsBuild() {
		assertThat(get(this.management, "/actuator/info")).contains("\"build\"", "\"artifact\":\"dentify-backend\"");
	}

	@Test
	void modulithStructureIsOnManagementPort() {
		assertThat(get(this.management, "/actuator/modulith")).contains("\"platform\"", "\"medical\"");
	}

	/**
	 * На порту приложения actuator нет: без входа — 401, как у любого пути вне открытых, с входом — 404. Открыт без
	 * входа actuator только на порту управления.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "/actuator", "/actuator/health", "/actuator/health/readiness", "/actuator/prometheus",
			"/actuator/modulith" })
	void actuatorIsNotExposedOnApplicationPort(String path) {
		this.http.get().uri(path).exchange().expectStatus().isUnauthorized();
		this.http.get()
			.uri(path)
			.header(HttpHeaders.COOKIE, signIn(UUID.randomUUID()).cookies())
			.exchange()
			.expectStatus()
			.isNotFound();
	}

	private static String get(RestTestClient client, String path) {
		return client.get()
			.uri(path)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
	}

}
