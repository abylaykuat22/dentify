package kz.dentify.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import kz.dentify.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/**
 * Заголовки безопасности (PLT-06): CSP без unsafe-inline для скриптов, запрет встраивания во фреймы, nosniff,
 * Referrer-Policy, Permissions-Policy, HSTS на год за HTTPS — на каждом ответе API, в том числе на ошибках. CORS
 * выключен: SPA и API на одном origin.
 */
class SecurityHeadersTests extends AbstractIntegrationTest {

	/** Успешный ответ, 401 и 404 после входа: заголовки не зависят от исхода. */
	@ParameterizedTest
	@ValueSource(strings = { "/api/v1/auth/csrf", "/api/v1/account/sessions", "/api/v1/no-such-endpoint" })
	void everyResponseHasSecurityHeaders(String path) {
		HttpHeaders headers = get(path, null).getResponseHeaders();

		assertSecurityHeaders(headers);
	}

	@Test
	void signedInResponsesHaveSecurityHeaders() {
		TestSession session = signIn(UUID.randomUUID());

		assertSecurityHeaders(get("/api/v1/account/sessions", session.cookies()).getResponseHeaders());
		assertSecurityHeaders(get("/api/v1/no-such-endpoint", session.cookies()).getResponseHeaders());
	}

	@Test
	void contentSecurityPolicyForbidsInlineScriptsAndFraming() {
		String csp = get(CSRF, null).getResponseHeaders().getFirst("Content-Security-Policy");

		assertThat(csp).isNotNull().contains("default-src 'self'", "script-src 'self'", "object-src 'none'",
				"frame-ancestors 'none'", "base-uri 'self'");
		String scriptSrc = directive(csp, "script-src");
		assertThat(scriptSrc).doesNotContain("unsafe-inline", "unsafe-eval", "*");
	}

	@Test
	void frameOptionsDeny() {
		assertThat(get(CSRF, null).getResponseHeaders().getFirst("X-Frame-Options")).isEqualTo("DENY");
	}

	@Test
	void contentTypeOptionsNosniff() {
		assertThat(get(CSRF, null).getResponseHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
	}

	@Test
	void referrerPolicyStrictOriginWhenCrossOrigin() {
		assertThat(get(CSRF, null).getResponseHeaders().getFirst("Referrer-Policy"))
			.isEqualTo("strict-origin-when-cross-origin");
	}

	@Test
	void permissionsPolicyDisablesUnusedFeatures() {
		assertThat(get(CSRF, null).getResponseHeaders().getFirst("Permissions-Policy")).contains("camera=()",
				"microphone=()", "geolocation=()", "payment=()", "usb=()");
	}

	/** HSTS на год — только на ответах по HTTPS: за Caddy это X-Forwarded-Proto: https от внутреннего адреса. */
	@Test
	void hstsOneYearOverHttps() {
		String hsts = this.http.get()
			.uri(CSRF)
			.header("X-Forwarded-Proto", "https")
			.exchange()
			.expectBody()
			.returnResult()
			.getResponseHeaders()
			.getFirst("Strict-Transport-Security");

		assertThat(hsts).isNotNull().contains("max-age=31536000").contains("includeSubDomains");
	}

	/** По HTTP браузер HSTS не принимает (RFC 6797), и Spring Security его не пишет. */
	@Test
	void noHstsOverPlainHttp() {
		assertThat(get(CSRF, null).getResponseHeaders().getFirst("Strict-Transport-Security")).isNull();
	}

	/** Ответы API не кэшируются: в них данные пациентов. */
	@Test
	void apiResponsesAreNotCached() {
		assertThat(get(CSRF, null).getResponseHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
	}

	/** CORS выключен: чужой origin не получает разрешающих заголовков, ни на запрос, ни на preflight. */
	@Test
	void corsIsDisabled() {
		HttpHeaders simple = this.http.get()
			.uri(CSRF)
			.header(HttpHeaders.ORIGIN, "https://evil.example")
			.exchange()
			.expectBody()
			.returnResult()
			.getResponseHeaders();
		HttpHeaders preflight = this.http.options()
			.uri("/api/v1/account/sessions")
			.header(HttpHeaders.ORIGIN, "https://evil.example")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE")
			.exchange()
			.expectBody()
			.returnResult()
			.getResponseHeaders();

		assertThat(simple.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
		assertThat(preflight.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
		assertThat(preflight.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).isNull();
	}

	private static void assertSecurityHeaders(HttpHeaders headers) {
		assertThat(headers.getFirst("Content-Security-Policy")).contains("script-src 'self'", "frame-ancestors 'none'");
		assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
		assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
		assertThat(headers.getFirst("Permissions-Policy")).contains("camera=()");
	}

	private EntityExchangeResult<byte[]> get(String path, String cookies) {
		return this.http.get().uri(path).headers((headers) -> {
			if (cookies != null) {
				headers.add(HttpHeaders.COOKIE, cookies);
			}
		}).exchange().expectBody().returnResult();
	}

	private static String directive(String policy, String name) {
		for (String directive : policy.split(";")) {
			if (directive.strip().startsWith(name + " ")) {
				return directive.strip();
			}
		}
		throw new AssertionError("нет директивы " + name + " в " + policy);
	}

}
