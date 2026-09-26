package kz.dentify.iam;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import kz.dentify.AbstractIntegrationTest;
import kz.dentify.clinic.TestClinics;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/**
 * Общие шаги тестов входа и выбора клиники: вход через {@code POST /api/v1/auth/login}, как у SPA (CSRF-токен, cookie
 * вручную), чтение {@code /api/v1/me} и журнала входов. Без своих аннотаций — контекст тот же, что у всех наследников
 * {@link AbstractIntegrationTest}.
 */
abstract class LoginIntegrationTest extends AbstractIntegrationTest {

	static final String LOGIN = "/api/v1/auth/login";

	static final String LOGOUT = "/api/v1/auth/logout";

	static final String ME = "/api/v1/me";

	static final String CONTEXT = "/api/v1/me/context";

	static final String FIREFOX_LINUX = "Mozilla/5.0 (X11; Linux x86_64; rv:141.0) Gecko/20100101 Firefox/141.0";

	static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	IamFixtures fixtures;

	@Autowired
	TestClinics clinics;

	@Autowired
	JdbcTemplate jdbc;

	/** Вход с нового устройства: случайный IP, без прежней сессии. */
	EntityExchangeResult<String> login(String login, String password, boolean rememberMe) {
		return login(login, password, rememberMe, testClientIp(), null, null);
	}

	/**
	 * Вход, как у SPA: CSRF-токен из cookie в заголовке.
	 *
	 * @param sessionCookie cookie сессии, которая была до входа, {@code DENTIFY_SESSION=…}, или null
	 */
	EntityExchangeResult<String> login(String login, String password, boolean rememberMe, String clientIp,
			@Nullable String userAgent, @Nullable String sessionCookie) {
		String xsrf = csrfToken(clientIp);
		String cookies = XSRF_COOKIE + "=" + xsrf + ((sessionCookie != null) ? "; " + sessionCookie : "");
		return this.http.post()
			.uri(LOGIN)
			.header(HttpHeaders.COOKIE, cookies)
			.header(XSRF_HEADER, xsrf)
			.header(FORWARDED_FOR, clientIp)
			.headers((headers) -> {
				if (userAgent != null) {
					headers.set(HttpHeaders.USER_AGENT, userAgent);
				}
			})
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("login", login, "password", password, "rememberMe", rememberMe))
			.exchange()
			.returnResult(String.class);
	}

	/** Успешный вход: 200, вошедшая сессия с новым CSRF-токеном. */
	TestSession signedIn(EntityExchangeResult<String> result) {
		assertThat(result.getStatus().value()).as("вход: %s", result.getResponseBody()).isEqualTo(200);
		List<String> cookies = setCookies(result);
		String session = cookieValue(cookies, SESSION_COOKIE);
		String xsrf = cookieValue(cookies, XSRF_COOKIE);
		assertThat(session).as("cookie сессии").isNotNull();
		assertThat(xsrf).as("новый CSRF-токен").isNotNull();
		return new TestSession(Objects.requireNonNull(session), Objects.requireNonNull(xsrf));
	}

	TestSession signIn(IamFixtures.Person person) {
		return signedIn(login(person.email(), IamFixtures.PASSWORD, false));
	}

	/** ProblemDetail ответа с ожидаемыми статусом и кодом. */
	static JsonNode problem(EntityExchangeResult<String> result, int status, String code) {
		assertThat(result.getStatus().value()).as("ответ: %s", result.getResponseBody()).isEqualTo(status);
		assertThat(result.getResponseHeaders().getContentType()).isNotNull()
			.satisfies((type) -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
		JsonNode problem = read(result);
		assertThat(problem.path("code").asString()).isEqualTo(code);
		assertThat(problem.path("detail").asString()).isNotBlank().doesNotContain("error.", "iam.", "{");
		return problem;
	}

	static JsonNode read(EntityExchangeResult<String> result) {
		return JSON.readTree(Objects.requireNonNullElse(result.getResponseBody(), ""));
	}

	static List<String> setCookies(EntityExchangeResult<String> result) {
		return result.getResponseHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
	}

	/** {@code GET /api/v1/me} вошедшей сессии. */
	EntityExchangeResult<String> me(TestSession session) {
		return me(session.cookies());
	}

	EntityExchangeResult<String> me(String cookies) {
		return this.http.get().uri(ME).header(HttpHeaders.COOKIE, cookies).exchange().returnResult(String.class);
	}

	/** Выбрать клинику и филиал сессии. */
	EntityExchangeResult<String> select(TestSession session, UUID clinicId, @Nullable UUID branchId) {
		Map<String, Object> body = (branchId != null) ? Map.of("clinicId", clinicId, "branchId", branchId)
				: Map.of("clinicId", clinicId);
		return this.http.post()
			.uri(CONTEXT)
			.header(HttpHeaders.COOKIE, session.cookies())
			.header(XSRF_HEADER, session.xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.exchange()
			.returnResult(String.class);
	}

	/** Выход. */
	EntityExchangeResult<String> logout(TestSession session) {
		return this.http.post()
			.uri(LOGOUT)
			.header(HttpHeaders.COOKIE, session.cookies())
			.header(XSRF_HEADER, session.xsrf())
			.exchange()
			.returnResult(String.class);
	}

	/**
	 * События журнала входов аккаунта по времени. Порядок событий разных запросов тесты не сверяют: время — часы
	 * PostgreSQL в контейнере, а они на Windows (WSL2) иногда сдвигаются назад на доли секунды.
	 */
	List<String> events(UUID accountId) {
		return this.jdbc.queryForList("select kind from auth_event where account_id = ? order by at, id", String.class,
				accountId);
	}

	/** Настоящий id сессии из значения cookie (base64). */
	static String sessionId(TestSession session) {
		return new String(java.util.Base64.getDecoder().decode(session.session()), StandardCharsets.UTF_8);
	}

}
