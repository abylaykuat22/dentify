package kz.dentify;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

/**
 * Базовый класс интеграционных тестов: всё приложение на случайных портах против PostgreSQL 18 и Valkey в
 * Testcontainers, профиль {@code test}.
 * <p>
 * Контейнеры — синглтоны на весь прогон (JVM): стартуют один раз в статическом блоке, общие для всех
 * наследников, останавливаются Ryuk-ом после прогона. Подключения отдаёт {@code @ServiceConnection}:
 * <ul>
 * <li>PostgreSQL — только {@link JdbcConnectionDetails} (роль {@code dentify_app}); Flyway берёт тот же URL, но
 * пользователя {@code dentify_owner} из {@code spring.flyway.*} — как в dev и prod;</li>
 * <li>Valkey — как Redis ({@code name = "redis"}): протокол тот же.</li>
 * </ul>
 * Метрики и трейсинг включены ({@code @AutoConfigureMetrics}, {@code @AutoConfigureTracing}): в тестах всё
 * как в рабочем приложении, включая /actuator/prometheus и traceId в логах. Эндпоинты проверяются через
 * {@link #http} ({@code RestTestClient} Spring 7, настроен на запущенный сервер).
 * <p>
 * Одинаковая конфигурация у всех наследников — один закэшированный Spring-контекст на прогон. Поэтому
 * наследники не добавляют {@code @Import}, {@code @MockitoBean}, {@code @TestPropertySource} и свои профили:
 * любое из них меняет ключ кэша и поднимает второй контекст (Tomcat, пул, Flyway). Пробные бины для тестов
 * — отдельными top-level классами в тестовых исходниках пакета {@code kz.dentify}: их подхватывает
 * компонент-скан во всех тестах (пример — {@code platform.LogProbeController}).
 * <p>
 * Безопасность — настоящая: пробные эндпоинты {@code /test/**} обслуживает тестовая цепочка без входа и CSRF
 * ({@code platform.security.ProbeSecurityConfiguration}), всё остальное — цепочка приложения. Вошедшая сессия с
 * CSRF-токеном — {@link #signIn(UUID)}: вход через пробный эндпоинт {@value #SIGN_IN_PROBE}, который вызывает
 * настоящий {@code AccountSignIn}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMetrics
@AutoConfigureTracing
@AutoConfigureRestTestClient
public abstract class AbstractIntegrationTest {

	@ServiceConnection(type = JdbcConnectionDetails.class)
	protected static final DentifyPostgresContainer POSTGRES = new DentifyPostgresContainer();

	/** Тот же тег, что в deploy/compose.dev.yml (там же закреплён digest); в тестах без пароля. */
	@ServiceConnection(name = "redis")
	protected static final GenericContainer<?> VALKEY = new GenericContainer<>(DockerImageName.parse("valkey/valkey:8.1.10"))
		.withExposedPorts(6379);

	static {
		Startables.deepStart(POSTGRES, VALKEY).join();
	}

	/** Пробный вход только для тестов: {@code platform.security.SignInProbeController}. */
	public static final String SIGN_IN_PROBE = "/api/v1/auth/test-sign-in";

	/** Выдача CSRF-токена SPA. */
	public static final String CSRF = "/api/v1/auth/csrf";

	/**
	 * Пробный вход в клинику только для тестов ({@code platform.tenancy.TenantSessionProbeController}): клиника в
	 * сессии, по желанию — вход вымышленным аккаунтом и его права.
	 */
	public static final String TENANT_SESSION_PROBE = "/test/tenant-session";

	public static final String SESSION_COOKIE = "DENTIFY_SESSION";

	public static final String XSRF_COOKIE = "XSRF-TOKEN";

	public static final String XSRF_HEADER = "X-XSRF-TOKEN";

	/** Адрес клиента за прокси: Tomcat верит ему от внутренних адресов, в тестах запросы идут с 127.0.0.1. */
	public static final String FORWARDED_FOR = "X-Forwarded-For";

	/** HTTP-клиент к порту приложения (тот, что проксирует Caddy). */
	@Autowired
	protected RestTestClient http;

	/** Вошедшая сессия вымышленного аккаунта, как у SPA: CSRF-токен, вход, новые cookie сессии и токена. */
	protected TestSession signIn(UUID accountId) {
		return signIn(accountId, false);
	}

	protected TestSession signIn(UUID accountId, boolean rememberMe) {
		return signIn(accountId, rememberMe, testClientIp(), null);
	}

	/**
	 * Вход с адреса и браузера клиента: за прокси адрес приходит в X-Forwarded-For. По умолчанию адрес — случайный из
	 * диапазона для документации: запросы без сессии считаются по IP в общем лимите API, и тесты не делят один счётчик.
	 */
	protected TestSession signIn(UUID accountId, boolean rememberMe, String clientIp, @Nullable String userAgent) {
		String xsrf = csrfToken(clientIp);
		List<String> cookies = this.http.post()
			.uri(SIGN_IN_PROBE)
			.header(HttpHeaders.COOKIE, XSRF_COOKIE + "=" + xsrf)
			.header(XSRF_HEADER, xsrf)
			.header(FORWARDED_FOR, clientIp)
			.headers((headers) -> {
				if (userAgent != null) {
					headers.set(HttpHeaders.USER_AGENT, userAgent);
				}
			})
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("accountId", accountId, "rememberMe", rememberMe))
			.exchange()
			.expectStatus()
			.isNoContent()
			.expectBody()
			.returnResult()
			.getResponseHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE);
		String session = cookieValue(cookies, SESSION_COOKIE);
		assertThat(session).as("cookie сессии после входа").isNotNull();
		String rotated = cookieValue(cookies, XSRF_COOKIE);
		return new TestSession(session, (rotated != null) ? rotated : xsrf);
	}

	/**
	 * Cookie сессии, в которой выбрана клиника; с аккаунтом — вход им, с правами — и права (authorities), пока их не
	 * выдаёт iam. Изменяющие запросы по такой сессии идут только на пробные пути {@code /test/**}: CSRF-токена у неё нет.
	 */
	protected String enterClinic(UUID clinicId, @Nullable UUID accountId, String... authorities) {
		Map<String, Object> body = new HashMap<>();
		body.put("clinicId", clinicId);
		if (accountId != null) {
			body.put("accountId", accountId);
		}
		if (authorities.length > 0) {
			body.put("authorities", List.of(authorities));
		}
		List<String> setCookies = this.http.post()
			.uri(TENANT_SESSION_PROBE)
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

	/** Новый CSRF-токен из cookie, которую ставит {@value #CSRF}. */
	protected String csrfToken() {
		return csrfToken(testClientIp());
	}

	protected String csrfToken(String clientIp) {
		List<String> cookies = this.http.get()
			.uri(CSRF)
			.header(FORWARDED_FOR, clientIp)
			.exchange()
			.expectStatus()
			.isNoContent()
			.expectBody()
			.returnResult()
			.getResponseHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE);
		String token = cookieValue(cookies, XSRF_COOKIE);
		assertThat(token).as("cookie XSRF-TOKEN").isNotBlank();
		return token;
	}

	/** Случайный вымышленный IPv4 из TEST-NET-2, 198.51.100.0/24 (RFC 5737). */
	protected static String testClientIp() {
		return "198.51.100." + ThreadLocalRandom.current().nextInt(1, 255);
	}

	/** ProblemDetail с ожидаемыми статусом и кодом: тип application/problem+json, текст и traceId на месте. */
	protected static JsonNode expectProblem(ResponseSpec response, int status, String code) {
		byte[] body = response.expectStatus()
			.isEqualTo(status)
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
			.expectBody(byte[].class)
			.returnResult()
			.getResponseBody();
		JsonNode problem = JsonMapper.builder().build().readTree(new String(body, StandardCharsets.UTF_8));
		assertThat(problem.path("status").asInt()).isEqualTo(status);
		assertThat(problem.path("code").asString()).isEqualTo(code);
		assertThat(problem.path("detail").asString()).isNotBlank().doesNotContain("error.", "{");
		assertThat(problem.path("traceId").asString()).matches("[0-9a-f]{32}");
		return problem;
	}

	/** Значение cookie из заголовков Set-Cookie; последняя непустая, пусто — null. */
	protected static @Nullable String cookieValue(List<String> setCookies, String name) {
		String value = null;
		for (String header : setCookies) {
			if (header.startsWith(name + "=")) {
				int end = header.indexOf(';');
				String candidate = header.substring(name.length() + 1, (end >= 0) ? end : header.length());
				value = candidate.isEmpty() ? null : candidate;
			}
		}
		return value;
	}

	/** Заголовок Set-Cookie с этим именем, последний. */
	protected static @Nullable String setCookie(List<String> setCookies, String name) {
		String found = null;
		for (String header : setCookies) {
			if (header.startsWith(name + "=")) {
				found = header;
			}
		}
		return found;
	}

	/**
	 * Вошедшая сессия в тесте.
	 *
	 * @param session значение cookie {@code DENTIFY_SESSION}
	 * @param xsrf CSRF-токен: значение cookie {@code XSRF-TOKEN} и заголовка {@code X-XSRF-TOKEN}
	 */
	public record TestSession(String session, String xsrf) {

		/** Заголовок Cookie: сессия и CSRF-токен. */
		public String cookies() {
			return SESSION_COOKIE + "=" + this.session + "; " + XSRF_COOKIE + "=" + this.xsrf;
		}

		/** Только cookie сессии, как в {@code enter} тестов изоляции. */
		public String sessionCookie() {
			return SESSION_COOKIE + "=" + this.session;
		}

	}

	@DynamicPropertySource
	static void flywayOwner(DynamicPropertyRegistry registry) {
		registry.add("spring.flyway.user", () -> DentifyPostgresContainer.OWNER_ROLE);
		registry.add("spring.flyway.password", () -> DentifyPostgresContainer.OWNER_PASSWORD);
	}

}
