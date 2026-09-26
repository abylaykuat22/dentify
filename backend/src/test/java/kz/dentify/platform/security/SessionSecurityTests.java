package kz.dentify.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import kz.dentify.AbstractIntegrationTest;
import kz.dentify.platform.identity.AccountSession;
import kz.dentify.platform.identity.AccountSessions;
import kz.dentify.platform.identity.TenantSession;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/**
 * Сессии и вход (PLT-04): цепочка приложения отвечает 401 и 403 ProblemDetail без редиректов, cookie сессии HttpOnly,
 * Secure, SameSite=Lax, сессия в Valkey с индексом по аккаунту, 12 часов бездействия, срок 30 дней от входа с
 * Запомнить и без него, новый id сессии и CSRF-токена при входе, выход уничтожает сессию. Время тесты сдвигают в самой
 * сессии в Valkey: вход или последний запрос раньше на нужный срок.
 */
class SessionSecurityTests extends AbstractIntegrationTest {

	static final String ME = SecuredProbeController.PATH + "/me";

	@Autowired
	FindByIndexNameSessionRepository<? extends Session> sessions;

	/** То же хранилище с его типом сессии: тесты сдвигают время входа и последнего запроса сессии в Valkey. */
	@Autowired
	RedisIndexedSessionRepository redisSessions;

	@Autowired
	AccountSessions accountSessions;

	@Test
	void sessionRepositoryIsIndexedValkey() {
		assertThat(this.sessions).isInstanceOf(RedisIndexedSessionRepository.class);
	}

	@Test
	void withoutSessionIsUnauthorizedProblemWithoutRedirect() {
		EntityExchangeResult<byte[]> result = this.http.get()
			.uri(ME)
			.exchange()
			.expectHeader()
			.doesNotExist(HttpHeaders.LOCATION)
			.expectHeader()
			.doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
			.expectBody()
			.returnResult();
		assertThat(result.getStatus().value()).isEqualTo(401);

		JsonNode problem = expectProblem(this.http.get().uri(ME).exchange(), 401, "error.unauthorized");
		assertThat(problem.path("detail").asString()).isEqualTo("Войдите в систему, чтобы продолжить.");
		assertThat(problem.path("instance").asString()).isEqualTo(ME);
	}

	/** Что путь API не существует, знает только вошедший: без входа — 401, а не 404. */
	@Test
	void unknownApiPathWithoutSessionIsUnauthorized() {
		expectProblem(this.http.get().uri("/api/v1/no-such-endpoint").exchange(), 401, "error.unauthorized");
	}

	/**
	 * Открытые ресурсы до входа — {@code /api/v1/public/**} (регистрация клиники, города, приглашение по ссылке): без
	 * входа запрос доходит до API, а не получает 401; изменяющий запрос — только с CSRF-токеном, как вход.
	 */
	@Test
	void publicApiPathsAreOpenWithoutSessionButKeepCsrf() {
		expectProblem(this.http.get().uri("/api/v1/public/no-such-resource").exchange(), 404, "error.not-found");
		expectProblem(this.http.post().uri("/api/v1/public/no-such-resource").exchange(), 403, "error.csrf");
		String xsrf = csrfToken();
		expectProblem(this.http.post()
			.uri("/api/v1/public/no-such-resource")
			.header(HttpHeaders.COOKIE, XSRF_COOKIE + "=" + xsrf)
			.header(XSRF_HEADER, xsrf)
			.exchange(), 404, "error.not-found");
	}

	@Test
	void signedInRequestKnowsAccount() {
		UUID account = UUID.randomUUID();
		TestSession session = signIn(account);

		this.http.get()
			.uri(ME)
			.header(HttpHeaders.COOKIE, session.cookies())
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.accountId")
			.isEqualTo(account.toString());
	}

	@Test
	void forbiddenByPreAuthorizeIsProblem() {
		TestSession session = signIn(UUID.randomUUID());

		expectProblem(this.http.get()
			.uri(SecuredProbeController.PATH + "/denied")
			.header(HttpHeaders.COOKIE, session.cookies())
			.exchange(), 403, "error.forbidden");
	}

	@Test
	void sessionCookieIsHttpOnlySecureLaxAndEndsWithBrowser() {
		List<String> cookies = signInCookies(UUID.randomUUID(), false, null);
		String cookie = setCookie(cookies, SESSION_COOKIE);

		assertThat(cookie).isNotNull()
			.contains("; HttpOnly", "; Secure", "; SameSite=Lax", "; Path=/")
			.doesNotContain("Max-Age", "Expires");
		assertThat(maxInactive(cookieValue(cookies, SESSION_COOKIE))).isEqualTo(Duration.ofHours(12));
	}

	/** С Запомнить — постоянная cookie на 30 дней от входа, в Valkey сессия при входе тоже на 30 дней. */
	@Test
	void rememberMeKeepsSessionThirtyDays() {
		List<String> cookies = signInCookies(UUID.randomUUID(), true, null);
		String cookie = setCookie(cookies, SESSION_COOKIE);

		assertThat(cookie).isNotNull().contains("; Max-Age=2592000", "; HttpOnly", "; Secure", "; SameSite=Lax");
		assertThat(maxInactive(cookieValue(cookies, SESSION_COOKIE))).isEqualTo(Duration.ofDays(30));
	}

	/** Приёмка PLT-04: без Запомнить после 12 часов бездействия запрос получает 401 error.session-expired. */
	@Test
	void sessionWithoutRememberMeExpiresAfterTwelveHoursIdle() {
		TestSession session = signIn(UUID.randomUUID());
		expectOk(session);

		change(session, (stored) -> stored.setLastAccessedTime(ago(Duration.ofHours(12).plusSeconds(1))));

		expectProblem(this.http.get().uri(ME).header(HttpHeaders.COOKIE, session.cookies()).exchange(), 401,
				"error.session-expired");
	}

	/** Бездействие меньше 12 часов сессию не гасит, и каждый запрос отсчитывает 12 часов заново. */
	@Test
	void sessionWithoutRememberMeSurvivesLessThanTwelveHoursIdle() {
		TestSession session = signIn(UUID.randomUUID());

		change(session, (stored) -> stored.setLastAccessedTime(ago(Duration.ofHours(11).plusMinutes(59))));
		expectOk(session);
		change(session, (stored) -> stored.setLastAccessedTime(ago(Duration.ofHours(11).plusMinutes(59))));

		expectOk(session);
		assertThat(maxInactive(session.session())).isEqualTo(Duration.ofHours(12));
	}

	/** С Запомнить 12 часов бездействия сессию не гасят: её срок — 30 дней от входа. */
	@Test
	void rememberMeSurvivesTwelveHoursIdle() {
		TestSession session = signIn(UUID.randomUUID(), true);

		change(session, (stored) -> stored.setLastAccessedTime(ago(Duration.ofHours(12).plusSeconds(1))));

		expectOk(session);
	}

	/**
	 * С Запомнить сессия гаснет через 30 дней от входа, как бы активно ею ни пользовались: последний запрос только что,
	 * а вход — 30 дней и секунду назад. Ответ — 401 error.session-expired, cookie стирается, сессии в Valkey больше нет.
	 */
	@Test
	void rememberMeEndsThirtyDaysAfterSignInDespiteActivity() {
		TestSession session = signIn(UUID.randomUUID(), true);
		expectOk(session);

		change(session, (stored) -> stored.setAttribute(SessionClients.SIGNED_IN_AT,
				ago(Duration.ofDays(30).plusSeconds(1))));

		List<String> cookies = expectExpired(session);
		assertThat(setCookie(cookies, SESSION_COOKIE)).isNotNull().contains("Max-Age=0");
		assertThat(this.sessions.findById(sessionId(session.session()))).isNull();
		expectProblem(this.http.get().uri(ME).header(HttpHeaders.COOKIE, session.cookies()).exchange(), 401,
				"error.session-expired");
	}

	/** Без Запомнить у сессии тот же предельный срок от входа, 30 дней, даже если запросы идут каждые 12 часов. */
	@Test
	void sessionWithoutRememberMeEndsThirtyDaysAfterSignIn() {
		TestSession session = signIn(UUID.randomUUID());

		change(session, (stored) -> stored.setAttribute(SessionClients.SIGNED_IN_AT,
				ago(Duration.ofDays(30).plusSeconds(1))));

		expectExpired(session);
		assertThat(this.sessions.findById(sessionId(session.session()))).isNull();
	}

	/** Истёкшая по сроку сессия не мешает войти снова: пути входа открыты, запрос идёт без входа, cookie стирается. */
	@Test
	void expiredSessionCookieDoesNotBlockAuthPaths() {
		TestSession session = signIn(UUID.randomUUID(), true);
		change(session, (stored) -> stored.setAttribute(SessionClients.SIGNED_IN_AT, ago(Duration.ofDays(31))));

		List<String> cookies = this.http.get()
			.uri(CSRF)
			.header(HttpHeaders.COOKIE, session.cookies())
			.exchange()
			.expectStatus()
			.isNoContent()
			.expectBody()
			.returnResult()
			.getResponseHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE);

		assertThat(setCookie(cookies, SESSION_COOKIE)).isNotNull().contains("Max-Age=0");
		assertThat(this.sessions.findById(sessionId(session.session()))).isNull();
	}

	/**
	 * Бездействие сессии в Valkey не заходит за срок от входа: за день до конца 30 дней ключ сессии истечёт через день
	 * (без минуты запаса на запись, {@code SessionLifetimeFilter.nextMaxInactive}), даже если с неё больше не придут, и
	 * она уйдёт из индекса аккаунта.
	 */
	@Test
	void rememberMeSessionExpiresInValkeyAtDeadline() {
		TestSession session = signIn(UUID.randomUUID(), true);
		change(session, (stored) -> stored.setAttribute(SessionClients.SIGNED_IN_AT, ago(Duration.ofDays(29))));

		expectOk(session);

		assertThat(maxInactive(session.session())).isBetween(Duration.ofDays(1).minusMinutes(2), Duration.ofDays(1));
	}

	/** Сессия с истёкшим сроком от входа не показывается в Активных сессиях и удаляется из Valkey. */
	@Test
	void sessionsPastDeadlineAreNotListed() {
		UUID account = UUID.randomUUID();
		TestSession current = signIn(account);
		TestSession stale = signIn(account, true);
		change(stale, (stored) -> stored.setAttribute(SessionClients.SIGNED_IN_AT,
				ago(Duration.ofDays(30).plusSeconds(1))));

		List<AccountSession> listed = this.accountSessions.list(account, sessionId(current.session()));

		assertThat(listed).singleElement().satisfies((session) -> assertThat(session.current()).isTrue());
		assertThat(this.sessions.findById(sessionId(stale.session()))).isNull();
		assertThat(this.sessions.findByPrincipalName(account.toString()).keySet())
			.containsExactly(sessionId(current.session()));
	}

	/**
	 * Защита от фиксации: id сессии, известный до входа, после входа недействителен. Клиника, выбранная в сессии до
	 * входа, снимается: новому аккаунту её выбирает вход после проверки членства.
	 */
	@Test
	void signInChangesSessionId() {
		UUID clinic = UUID.randomUUID();
		String before = anonymousSessionWithClinic(clinic);

		List<String> cookies = signInCookies(UUID.randomUUID(), false, before);
		String after = cookieValue(cookies, SESSION_COOKIE);

		assertThat(after).isNotNull().isNotEqualTo(before);
		assertThat(this.sessions.findById(sessionId(before))).as("старый id сессии").isNull();
		Session renamed = this.sessions.findById(sessionId(after));
		assertThat(renamed).isNotNull();
		assertThat((Object) renamed.getAttribute(TenantSession.CLINIC_ID)).as("клиника до входа").isNull();
		expectProblem(this.http.get().uri(ME).header(HttpHeaders.COOKIE, SESSION_COOKIE + "=" + before).exchange(),
				401, "error.session-expired");
	}

	/**
	 * Клиника прежнего входа снимается и с потока запроса входа: иначе до конца этого запроса событие аудита или
	 * обработчик входа писали бы в клинику, которую выбрал другой аккаунт в этом браузере.
	 */
	@Test
	void signInLeavesClinicOfPreviousSessionInCurrentRequest() {
		String before = anonymousSessionWithClinic(UUID.randomUUID());
		String xsrf = csrfToken();

		this.http.post()
			.uri(SIGN_IN_PROBE)
			.header(HttpHeaders.COOKIE, XSRF_COOKIE + "=" + xsrf + "; " + SESSION_COOKIE + "=" + before)
			.header(XSRF_HEADER, xsrf)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("accountId", UUID.randomUUID(), "rememberMe", false))
			.exchange()
			.expectStatus()
			.isNoContent()
			.expectHeader()
			.valueEquals(SignInProbeController.CLINIC_AFTER_SIGN_IN, SignInProbeController.NO_CLINIC);
	}

	/** Вход меняет и CSRF-токен: ответ несёт новую cookie XSRF-TOKEN. */
	@Test
	void signInRotatesCsrfToken() {
		String before = csrfToken();

		List<String> cookies = this.http.post()
			.uri(SIGN_IN_PROBE)
			.header(HttpHeaders.COOKIE, XSRF_COOKIE + "=" + before)
			.header(XSRF_HEADER, before)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("accountId", UUID.randomUUID(), "rememberMe", false))
			.exchange()
			.expectStatus()
			.isNoContent()
			.expectBody()
			.returnResult()
			.getResponseHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE);

		assertThat(cookieValue(cookies, XSRF_COOKIE)).isNotNull().isNotEqualTo(before);
	}

	@Test
	void signInIndexesSessionByAccount() {
		UUID account = UUID.randomUUID();
		TestSession first = signIn(account);
		TestSession second = signIn(account);

		assertThat(this.sessions.findByPrincipalName(account.toString()).keySet())
			.containsExactlyInAnyOrder(sessionId(first.session()), sessionId(second.session()));
	}

	/** Выход уничтожает сессию: cookie стирается, прежний id больше не пускает. */
	@Test
	void signOutDestroysSession() {
		TestSession session = signIn(UUID.randomUUID());

		List<String> cookies = this.http.post()
			.uri(SignInProbeController.SIGN_OUT)
			.header(HttpHeaders.COOKIE, session.cookies())
			.header(XSRF_HEADER, session.xsrf())
			.exchange()
			.expectStatus()
			.isNoContent()
			.expectBody()
			.returnResult()
			.getResponseHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE);

		assertThat(setCookie(cookies, SESSION_COOKIE)).isNotNull().contains("Max-Age=0");
		assertThat(this.sessions.findById(sessionId(session.session()))).isNull();
		expectProblem(this.http.get().uri(ME).header(HttpHeaders.COOKIE, session.cookies()).exchange(), 401,
				"error.session-expired");
	}

	/** Неизвестный id сессии в cookie — истёкшая сессия: интерфейс откроет вход и вернёт на прежний экран. */
	@Test
	void unknownSessionCookieIsSessionExpired() {
		String forged = Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));

		expectProblem(this.http.get().uri(ME).header(HttpHeaders.COOKIE, SESSION_COOKIE + "=" + forged).exchange(), 401,
				"error.session-expired");
	}

	/** Без входа сессия в Valkey не создаётся: ни запрос с 401, ни выдача CSRF-токена не пишут сессию. */
	@Test
	void anonymousRequestsCreateNoSession() {
		List<String> denied = this.http.get()
			.uri(ME)
			.exchange()
			.expectBody()
			.returnResult()
			.getResponseHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE);
		List<String> csrf = this.http.get()
			.uri(CSRF)
			.exchange()
			.expectBody()
			.returnResult()
			.getResponseHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE);

		assertThat(setCookie(denied, SESSION_COOKIE)).isNull();
		assertThat(setCookie(csrf, SESSION_COOKIE)).isNull();
	}

	/** Сессия без входа с выбранной клиникой: так её создаёт пробный выбор клиники тестов изоляции. */
	private String anonymousSessionWithClinic(UUID clinic) {
		List<String> cookies = this.http.post()
			.uri("/test/tenant-session")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("clinicId", clinic))
			.exchange()
			.expectStatus()
			.isNoContent()
			.expectBody()
			.returnResult()
			.getResponseHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE);
		String session = cookieValue(cookies, SESSION_COOKIE);
		assertThat(session).isNotNull();
		return session;
	}

	private List<String> signInCookies(UUID account, boolean rememberMe, @Nullable String existingSession) {
		String xsrf = csrfToken();
		String cookie = XSRF_COOKIE + "=" + xsrf
				+ ((existingSession != null) ? "; " + SESSION_COOKIE + "=" + existingSession : "");
		return this.http.post()
			.uri(SIGN_IN_PROBE)
			.header(HttpHeaders.COOKIE, cookie)
			.header(XSRF_HEADER, xsrf)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("accountId", account, "rememberMe", rememberMe))
			.exchange()
			.expectStatus()
			.isNoContent()
			.expectBody()
			.returnResult()
			.getResponseHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE);
	}

	private void expectOk(TestSession session) {
		this.http.get().uri(ME).header(HttpHeaders.COOKIE, session.cookies()).exchange().expectStatus().isOk();
	}

	/** Запрос вошедшей сессии получает 401 error.session-expired; ответ — заголовки Set-Cookie. */
	private List<String> expectExpired(TestSession session) {
		EntityExchangeResult<byte[]> result = this.http.get()
			.uri(ME)
			.header(HttpHeaders.COOKIE, session.cookies())
			.exchange()
			.expectBody()
			.returnResult();
		assertThat(result.getStatus().value()).isEqualTo(401);
		JsonNode problem = JsonMapper.builder()
			.build()
			.readTree(new String(result.getResponseBody(), StandardCharsets.UTF_8));
		assertThat(problem.path("code").asString()).isEqualTo("error.session-expired");
		assertThat(result.getResponseHeaders().getFirst("Content-Security-Policy")).as("заголовки безопасности на 401")
			.contains("frame-ancestors 'none'");
		return result.getResponseHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
	}

	/** Поменять сессию в Valkey в обход запросов: так тест сдвигает время, не дожидаясь его. */
	private void change(TestSession session, Consumer<RedisIndexedSessionRepository.RedisSession> change) {
		RedisIndexedSessionRepository.RedisSession stored = this.redisSessions.findById(sessionId(session.session()));
		assertThat(stored).as("сессия в Valkey").isNotNull();
		change.accept(stored);
		this.redisSessions.save(stored);
	}

	private static Instant ago(Duration duration) {
		return Instant.now().minus(duration);
	}

	private Duration maxInactive(String cookieValue) {
		Session session = this.sessions.findById(sessionId(cookieValue));
		assertThat(session).isNotNull();
		return session.getMaxInactiveInterval();
	}

	/** Значение cookie сессии — id в base64, так пишет его DefaultCookieSerializer Spring Session. */
	static String sessionId(String cookieValue) {
		return new String(Base64.getDecoder().decode(cookieValue), StandardCharsets.UTF_8);
	}

}
