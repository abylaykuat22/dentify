package kz.dentify.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import kz.dentify.AbstractIntegrationTest;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/**
 * Общие шаги тестов изоляции клиник: вход в клинику через HTTP-сессию ({@link TenantSessionProbeController}),
 * пробные записи {@link TestNote}, разбор ответов. Без своих аннотаций — контекст тот же, что у всех наследников
 * {@link AbstractIntegrationTest}.
 */
abstract class TenantIntegrationTest extends AbstractIntegrationTest {

	static final JsonMapper JSON = JsonMapper.builder().build();

	static final String NOTES = TestNoteController.PATH;

	/** Новая вымышленная клиника: каждый тест работает со своими клиниками и не видит данных соседей. */
	static UUID newClinic() {
		return UUID.randomUUID();
	}

	/** Cookie сессии, в которой выбрана клиника; без входа — как системная работа, автора у правок нет. */
	String enter(UUID clinicId) {
		return enter(clinicId, null, null);
	}

	String enter(UUID clinicId, @Nullable UUID branchId) {
		return enter(clinicId, branchId, null);
	}

	/** Cookie сессии вымышленного аккаунта, в которой выбрана клиника: аудит пишет его автором. */
	String enterAs(UUID clinicId, UUID accountId) {
		return enter(clinicId, null, accountId);
	}

	String enter(UUID clinicId, @Nullable UUID branchId, @Nullable UUID accountId) {
		Map<String, Object> body = new HashMap<>();
		body.put("clinicId", clinicId);
		if (branchId != null) {
			body.put("branchId", branchId);
		}
		if (accountId != null) {
			body.put("accountId", accountId);
		}
		List<String> setCookies = this.http.post()
			.uri(TenantSessionProbeController.PATH)
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.exchange()
			.expectStatus()
			.isNoContent()
			.expectBody()
			.returnResult()
			.getResponseHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE);
		String session = cookieValue(setCookies, SESSION_COOKIE);
		assertThat(session).as("cookie сессии %s", SESSION_COOKIE).isNotNull();
		return SESSION_COOKIE + "=" + session;
	}

	/** Пробная запись в клинике сессии; ответ — её представление. */
	JsonNode createNote(String session, String title) {
		return ok(this.http.post()
			.uri(NOTES)
			.header(HttpHeaders.COOKIE, session)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("title", title))
			.exchange());
	}

	static JsonNode ok(ResponseSpec response) {
		return read(response.expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody());
	}

	/** ProblemDetail с ожидаемыми статусом и кодом. */
	static JsonNode problem(ResponseSpec response, int status, String code) {
		byte[] body = response.expectStatus()
			.isEqualTo(status)
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody(byte[].class)
			.returnResult()
			.getResponseBody();
		JsonNode problem = read(new String(body, StandardCharsets.UTF_8));
		assertThat(problem.path("code").asString()).isEqualTo(code);
		return problem;
	}

	static JsonNode read(String json) {
		return JSON.readTree(json);
	}

}
