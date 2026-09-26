package kz.dentify.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import kz.dentify.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/**
 * CSRF для SPA (PLT-04, приёмка): изменяющий запрос без заголовка {@code X-XSRF-TOKEN} — 403 {@code error.csrf}, с
 * заголовком, равным cookie {@code XSRF-TOKEN}, — проходит. Режим Spring Security 7 {@code csrf.spa()}: cookie
 * читается скриптом, значение возвращается заголовком.
 */
class CsrfTests extends AbstractIntegrationTest {

	static final String ECHO = SecuredProbeController.PATH + "/echo";

	/** Cookie токена: не HttpOnly — её читает SPA; Secure и SameSite=Lax, как у cookie сессии. */
	@Test
	void csrfEndpointSetsReadableSecureLaxCookie() {
		List<String> cookies = this.http.get()
			.uri(CSRF)
			.exchange()
			.expectStatus()
			.isNoContent()
			.expectBody()
			.returnResult()
			.getResponseHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE);

		String cookie = setCookie(cookies, XSRF_COOKIE);
		assertThat(cookie).isNotNull().contains("; Secure", "; SameSite=Lax", "; Path=/").doesNotContain("HttpOnly");
		assertThat(cookieValue(cookies, XSRF_COOKIE)).isNotBlank();
	}

	@Test
	void postWithoutCsrfHeaderIsForbidden() {
		TestSession session = signIn(UUID.randomUUID());

		expectProblem(echo(session.cookies(), null), 403, "error.csrf");
	}

	@Test
	void postWithCsrfHeaderPasses() {
		TestSession session = signIn(UUID.randomUUID());

		echo(session.cookies(), session.xsrf()).expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.note")
			.isEqualTo("Осмотр");
	}

	/** Заголовок не совпадает с cookie — чужой токен, 403. */
	@Test
	void postWithForeignCsrfTokenIsForbidden() {
		TestSession session = signIn(UUID.randomUUID());

		expectProblem(echo(session.cookies(), UUID.randomUUID().toString()), 403, "error.csrf");
	}

	/** Токен в заголовке без cookie не принимается: страница другого сайта cookie не прочитает и не подставит. */
	@Test
	void postWithHeaderButWithoutCsrfCookieIsForbidden() {
		TestSession session = signIn(UUID.randomUUID());

		expectProblem(echo(session.sessionCookie(), session.xsrf()), 403, "error.csrf");
	}

	/** CSRF проверяется раньше входа: и без сессии POST без токена — 403. */
	@Test
	void anonymousPostWithoutCsrfIsForbidden() {
		expectProblem(echo(null, null), 403, "error.csrf");
	}

	@Test
	void everyChangingMethodNeedsCsrf() {
		TestSession session = signIn(UUID.randomUUID());
		String path = "/api/v1/account/sessions/" + UUID.randomUUID();

		expectProblem(this.http.delete().uri(path).header(HttpHeaders.COOKIE, session.cookies()).exchange(), 403,
				"error.csrf");
		expectProblem(this.http.put()
			.uri(ECHO)
			.header(HttpHeaders.COOKIE, session.cookies())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of())
			.exchange(), 403, "error.csrf");
		expectProblem(this.http.patch()
			.uri(ECHO)
			.header(HttpHeaders.COOKIE, session.cookies())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of())
			.exchange(), 403, "error.csrf");
	}

	private ResponseSpec echo(String cookies, String xsrf) {
		var request = this.http.post().uri(ECHO).contentType(MediaType.APPLICATION_JSON);
		if (cookies != null) {
			request = request.header(HttpHeaders.COOKIE, cookies);
		}
		if (xsrf != null) {
			request = request.header(XSRF_HEADER, xsrf);
		}
		return request.body(Map.of("note", "Осмотр")).exchange();
	}

}
