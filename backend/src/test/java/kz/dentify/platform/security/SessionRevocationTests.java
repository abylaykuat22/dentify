package kz.dentify.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import kz.dentify.AbstractIntegrationTest;
import kz.dentify.platform.identity.AccountSession;
import kz.dentify.platform.identity.AccountSessions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/**
 * Завершение сессии необратимо (PLT-04): выход, завершение одной, всех и всех, кроме текущей, гасят сессию навсегда,
 * даже если в этот момент идёт её запрос. Spring Session в {@code deleteById} не удаляет сессию из Valkey, а помечает её
 * истёкшей, и конец запроса в полёте записывает свои изменения поверх: бездействие из фильтра срока от входа оживило бы
 * сессию, контекст безопасности вернул бы её в индекс аккаунта. Отметка отзыва в Valkey держит её мёртвой. Аккаунты —
 * вымышленные.
 */
class SessionRevocationTests extends AbstractIntegrationTest {

	static final String ME = SecuredProbeController.PATH + "/me";

	static final String SESSIONS = "/api/v1/account/sessions";

	/** Ключи сессий Spring Session в Valkey: {@code spring.session.data.redis.namespace} и {@code :sessions:}. */
	static final String SESSION_KEYS = "dentify:session:sessions:";

	static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	SessionHoldProbeController holds;

	@Autowired
	FindByIndexNameSessionRepository<? extends Session> sessions;

	@Autowired
	RedisIndexedSessionRepository redisSessions;

	@Autowired
	AccountSessions accountSessions;

	@Autowired
	StringRedisTemplate redis;

	/** Как завершается сессия: выход, со списка другим устройством, деактивация аккаунта, «все, кроме моей». */
	enum Ending {

		SIGN_OUT, TERMINATE_ONE, TERMINATE_ALL, TERMINATE_OTHERS

	}

	static Stream<Arguments> endings() {
		List<Arguments> endings = new ArrayList<>();
		for (Ending ending : Ending.values()) {
			endings.add(Arguments.of(ending, true));
			endings.add(Arguments.of(ending, false));
		}
		return endings.stream();
	}

	/**
	 * Запрос сессии держится, пока другой поток её завершает: следующий и все дальнейшие запросы с её cookie — 401
	 * error.session-expired, в индексе аккаунта её нет. Второе устройство аккаунта живёт, если его не завершали.
	 */
	@ParameterizedTest(name = "{0}, Запомнить: {1}")
	@MethodSource("endings")
	void requestInFlightDoesNotReviveEndedSession(Ending ending, boolean rememberMe) throws Exception {
		UUID account = UUID.randomUUID();
		TestSession target = signIn(account, rememberMe);
		TestSession other = signIn(account);

		endWhileRequestInFlight(ending, account, target, other, rememberMe);

		for (int i = 0; i < 2; i++) {
			expectProblem(me(target), 401, "error.session-expired");
		}
		assertThat(this.sessions.findByPrincipalName(account.toString())).doesNotContainKey(sessionId(target));
		if (ending == Ending.TERMINATE_ALL) {
			expectProblem(me(other), 401, "error.session-expired");
		}
		else {
			me(other).expectStatus().isOk();
		}
	}

	/**
	 * Запрос отозванной сессии, которую конец запроса в полёте записал обратно, уничтожает её снова: 401 с заголовками
	 * безопасности, ответ стирает cookie, сессии нет ни в Valkey, ни в индексе аккаунта.
	 */
	@Test
	void requestOfRevivedSessionEndsItAndClearsCookie() throws Exception {
		UUID account = UUID.randomUUID();
		TestSession target = signIn(account, true);
		TestSession other = signIn(account);
		endWhileRequestInFlight(Ending.TERMINATE_ONE, account, target, other, true);
		assertThat(this.redisSessions.findById(sessionId(target))).as("конец запроса в полёте записал сессию обратно")
			.isNotNull();

		EntityExchangeResult<byte[]> result = me(target).expectBody().returnResult();

		assertThat(result.getStatus().value()).isEqualTo(401);
		assertThat(new String(result.getResponseBody(), StandardCharsets.UTF_8)).contains("error.session-expired");
		assertThat(result.getResponseHeaders().getFirst("Content-Security-Policy")).contains("frame-ancestors 'none'");
		assertThat(setCookie(result.getResponseHeaders().getOrEmpty(HttpHeaders.SET_COOKIE), SESSION_COOKIE))
			.isNotNull()
			.contains("Max-Age=0");
		assertThat(this.redisSessions.findById(sessionId(target))).isNull();
		assertThat(this.sessions.findByPrincipalName(account.toString()).keySet())
			.containsExactly(sessionId(other));
	}

	/**
	 * Сессию, которую конец запроса в полёте вернул в индекс аккаунта, список активных сессий не показывает и убирает из
	 * индекса.
	 */
	@Test
	void listHidesRevivedSessionAndDropsItFromIndex() throws Exception {
		UUID account = UUID.randomUUID();
		TestSession target = signIn(account, false);
		TestSession other = signIn(account);
		endWhileRequestInFlight(Ending.TERMINATE_ONE, account, target, other, false);
		assertThat(this.sessions.findByPrincipalName(account.toString())).as("конец запроса вернул сессию в индекс")
			.containsKey(sessionId(target));

		List<AccountSession> listed = this.accountSessions.list(account, sessionId(other));

		assertThat(listed).singleElement().satisfies((session) -> assertThat(session.current()).isTrue());
		assertThat(this.sessions.findByPrincipalName(account.toString())).doesNotContainKey(sessionId(target));
		expectProblem(me(target), 401, "error.session-expired");
	}

	/**
	 * Запрос ожившей отозванной сессии на открытые пути идёт без входа: выдача CSRF-токена отвечает 204 и стирает cookie
	 * сессии, вход создаёт новую сессию. Старая остаётся недействительной.
	 */
	@Test
	void revivedRevokedSessionDoesNotBlockAuthPaths() throws Exception {
		UUID account = UUID.randomUUID();
		TestSession other = signIn(account);
		TestSession forCsrf = signIn(account, true);
		TestSession forSignIn = signIn(account, false);
		for (TestSession target : List.of(forCsrf, forSignIn)) {
			endWhileRequestInFlight(Ending.TERMINATE_ONE, account, target, other, target == forCsrf);
			assertThat(this.redisSessions.findById(sessionId(target))).as("конец запроса в полёте записал сессию обратно")
				.isNotNull();
		}

		List<String> csrfCookies = this.http.get()
			.uri(CSRF)
			.header(HttpHeaders.COOKIE, forCsrf.sessionCookie())
			.exchange()
			.expectStatus()
			.isNoContent()
			.expectBody()
			.returnResult()
			.getResponseHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE);
		assertThat(setCookie(csrfCookies, SESSION_COOKIE)).isNotNull().contains("Max-Age=0");
		assertThat(cookieValue(csrfCookies, XSRF_COOKIE)).as("CSRF-токен выдан").isNotBlank();

		List<String> signInCookies = this.http.post()
			.uri(SignInProbeController.SIGN_IN)
			.header(HttpHeaders.COOKIE, forSignIn.cookies())
			.header(XSRF_HEADER, forSignIn.xsrf())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("accountId", account, "rememberMe", false))
			.exchange()
			.expectStatus()
			.isNoContent()
			.expectBody()
			.returnResult()
			.getResponseHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE);
		String signedIn = cookieValue(signInCookies, SESSION_COOKIE);
		assertThat(signedIn).as("вход создал новую сессию").isNotNull().isNotEqualTo(forSignIn.session());
		String rotated = cookieValue(signInCookies, XSRF_COOKIE);
		TestSession fresh = new TestSession(signedIn, (rotated != null) ? rotated : forSignIn.xsrf());
		me(fresh).expectStatus().isOk();

		for (TestSession ended : List.of(forCsrf, forSignIn)) {
			expectProblem(me(ended), 401, "error.session-expired");
			assertThat(this.redisSessions.findById(sessionId(ended))).isNull();
		}
		assertThat(this.sessions.findByPrincipalName(account.toString()).keySet())
			.containsExactlyInAnyOrder(sessionId(other), sessionId(fresh));
	}

	/** Что первым встречает неполный hash завершённой сессии. */
	enum FirstContact {

		/** следующий запрос с её cookie */
		NEXT_REQUEST,
		/** список сессий со второго устройства */
		LIST,
		/** «все, кроме текущей» со второго устройства — смена пароля */
		TERMINATE_OTHERS,
		/** завершение всех — деактивация */
		TERMINATE_ALL

	}

	static Stream<Arguments> firstContacts() {
		List<Arguments> contacts = new ArrayList<>();
		for (FirstContact contact : FirstContact.values()) {
			contacts.add(Arguments.of(contact, true));
			contacts.add(Arguments.of(contact, false));
		}
		return contacts.stream();
	}

	/**
	 * Запрос в полёте закончился, когда ключа завершённой сессии в Valkey уже нет: {@code deleteById} оставляет его на 5
	 * минут, а запрос шёл дольше. Конец запроса пишет свои изменения на пустое место — hash без creationTime, а контекст
	 * безопасности возвращает id в индекс аккаунта. Такой hash — не сессия ({@link SafeRedisSessionMapper}): запрос с
	 * её cookie — 401, а не 500; список и завершение со второго устройства работают и её не видят; hash, теневой ключ
	 * срока и запись в индексе удаляются при первой встрече.
	 */
	@ParameterizedTest(name = "{0}, Запомнить: {1}")
	@MethodSource("firstContacts")
	void requestInFlightAfterSessionKeyExpiredLeavesNoBrokenSession(FirstContact contact, boolean rememberMe)
			throws Exception {
		UUID account = UUID.randomUUID();
		TestSession target = signIn(account, rememberMe);
		TestSession other = signIn(account);
		String id = sessionId(target);
		String hashKey = SESSION_KEYS + id;

		endWhileRequestInFlight(Ending.TERMINATE_ONE, account, target, other, rememberMe,
				() -> assertThat(sessionOperations().delete(hashKey)).as("ключ завершённой сессии истёк").isTrue());

		assertThat(sessionOperations().opsForHash().keys(hashKey)).as("конец запроса записал неполный hash")
			.isNotEmpty()
			.doesNotContain("creationTime");
		assertThat(indexMembers(account)).as("и вернул id в индекс аккаунта").contains(id);

		switch (contact) {
			case NEXT_REQUEST -> expectProblem(me(target), 401, "error.session-expired");
			case LIST -> {
				JsonNode listed = JSON.readTree(this.http.get()
					.uri(SESSIONS)
					.header(HttpHeaders.COOKIE, other.cookies())
					.exchange()
					.expectStatus()
					.isOk()
					.expectBody(String.class)
					.returnResult()
					.getResponseBody());
				assertThat(listed.size()).isEqualTo(1);
				assertThat(listed.get(0).path("current").asBoolean()).isTrue();
			}
			case TERMINATE_OTHERS -> this.http.delete()
				.uri(SESSIONS)
				.header(HttpHeaders.COOKIE, other.cookies())
				.header(XSRF_HEADER, other.xsrf())
				.exchange()
				.expectStatus()
				.isNoContent();
			case TERMINATE_ALL -> assertThat(this.accountSessions.terminateAll(account)).isEqualTo(1);
		}

		assertThat(sessionOperations().hasKey(hashKey)).as("неполный hash удалён").isFalse();
		assertThat(sessionOperations().hasKey(SESSION_KEYS + "expires:" + id)).as("теневой ключ срока удалён").isFalse();
		assertThat(indexMembers(account)).as("id убран из индекса аккаунта").doesNotContain(id);
		for (int i = 0; i < 2; i++) {
			expectProblem(me(target), 401, "error.session-expired");
		}
		if (contact == FirstContact.TERMINATE_ALL) {
			expectProblem(me(other), 401, "error.session-expired");
		}
		else {
			me(other).expectStatus().isOk();
			assertThat(this.accountSessions.list(account, sessionId(other))).singleElement()
				.satisfies((session) -> assertThat(session.current()).isTrue());
		}
	}

	/**
	 * Завершение всех гасит и сессию аккаунта, которой нет в индексе: так её оставлял запрос в полёте до этой защиты, так
	 * же выглядит вход, шедший одновременно с деактивацией, со временем входа не позже неё. Её гасит отметка аккаунта, а не
	 * удаление по индексу.
	 */
	@Test
	void terminateAllEndsSessionsMissingFromIndex() {
		UUID account = UUID.randomUUID();
		TestSession indexed = signIn(account);
		TestSession lost = signIn(account, true);
		dropFromIndex(account, lost);

		assertThat(this.accountSessions.terminateAll(account)).isEqualTo(1);

		for (TestSession ended : List.of(indexed, lost)) {
			expectProblem(me(ended), 401, "error.session-expired");
		}
	}

	/** «Все, кроме текущей» гасит сессию вне индекса, а текущую оставляет, хотя её вход тоже раньше отметки. */
	@Test
	void terminateOthersEndsSessionsMissingFromIndexButKeepsCurrent() {
		UUID account = UUID.randomUUID();
		TestSession current = signIn(account);
		TestSession lost = signIn(account);
		dropFromIndex(account, lost);

		assertThat(this.accountSessions.terminateOthers(account, sessionId(current))).isZero();

		expectProblem(me(lost), 401, "error.session-expired");
		me(current).expectStatus().isOk();
		assertThat(this.accountSessions.list(account, sessionId(current))).singleElement()
			.satisfies((session) -> assertThat(session.current()).isTrue());
	}

	/** Отметка аккаунта гасит только сессии со входом до завершения: вход после него работает. */
	@Test
	void signInAfterTerminateAllWorks() {
		UUID account = UUID.randomUUID();
		TestSession before = signIn(account, true);
		this.accountSessions.terminateAll(account);

		TestSession after = signIn(account, true);

		me(after).expectStatus().isOk();
		expectProblem(me(before), 401, "error.session-expired");
		assertThat(this.accountSessions.list(account, null)).singleElement()
			.satisfies((session) -> assertThat(session.id()).isEqualTo(RedisAccountSessions.publicId(sessionId(after))));
	}

	/**
	 * Время входа в сессию и T отметки аккаунта — по часам Valkey, а не экземпляра приложения: вход и завершение на разных
	 * экземплярах сравниваются по одним часам.
	 */
	@Test
	void signInTimeAndAccountMarkUseValkeyClock() {
		UUID account = UUID.randomUUID();
		Instant before = valkeyTime();
		TestSession session = signIn(account);
		RedisIndexedSessionRepository.RedisSession stored = this.redisSessions.findById(sessionId(session));
		assertThat(stored).isNotNull();
		Instant signedInAt = stored.getAttribute(SessionClients.SIGNED_IN_AT);

		this.accountSessions.terminateAll(account);
		Instant after = valkeyTime();

		String mark = this.redis.opsForValue().get(SessionRevocations.accountKey(account.toString()));
		assertThat(mark).isNotNull();
		Instant revokedAt = Instant.parse(mark);
		assertThat(signedInAt).isNotNull().isBetween(before, revokedAt);
		assertThat(revokedAt).isBetween(signedInAt, after);
	}

	/**
	 * Отметка сессии живёт до её срока от входа и ещё минуту, отметка аккаунта — самый долгий срок сессии и ещё минуту.
	 * Id сессии в Valkey — только хэшем: ни в ключе, ни в значении его нет.
	 */
	@Test
	void revocationMarksLiveUntilSessionDeadline() {
		UUID account = UUID.randomUUID();
		TestSession signedOut = signIn(account, true);
		TestSession late = signIn(account);
		TestSession kept = signIn(account);
		shiftSignIn(late, Duration.ofDays(29));

		signOut(signedOut);
		this.accountSessions.terminateOthers(account, sessionId(kept));

		assertThat(ttl(SessionRevocations.sessionKey(sessionId(signedOut))))
			.isBetween(Duration.ofDays(30).minusMinutes(1), Duration.ofDays(30).plusMinutes(1));
		assertThat(ttl(SessionRevocations.sessionKey(sessionId(late))))
			.isBetween(Duration.ofDays(1).minusMinutes(1), Duration.ofDays(1).plusMinutes(1));
		String accountKey = SessionRevocations.accountKey(account.toString());
		assertThat(ttl(accountKey)).isBetween(Duration.ofDays(30), Duration.ofDays(30).plusMinutes(1));
		assertThat(this.redis.opsForValue().get(accountKey)).isNotNull()
			.endsWith(" " + SessionRevocations.digest(sessionId(kept)))
			.doesNotContain(sessionId(kept));
		assertThat(this.redis.keys(SessionRevocations.SESSION_KEY_PREFIX + "*"))
			.noneMatch((key) -> key.contains(sessionId(signedOut)) || key.contains(sessionId(late)));
	}

	/**
	 * Завершить сессию {@code target}, пока её запрос держится в {@link SessionHoldProbeController}. Срок от входа сдвинут
	 * так, что фильтр срока в этом запросе перепишет бездействие сессии, а сам запрос заново кладёт контекст
	 * безопасности: конец запроса записывает сессию обратно в Valkey и в индекс аккаунта. Запрос, начатый до завершения,
	 * заканчивается как обычно.
	 */
	private void endWhileRequestInFlight(Ending ending, UUID account, TestSession target, TestSession other,
			boolean rememberMe) throws Exception {
		endWhileRequestInFlight(ending, account, target, other, rememberMe, () -> {
		});
	}

	/**
	 * То же, и перед тем как отпустить запрос — {@code beforeRelease}: например, ключ сессии истекает, пока запрос ещё
	 * идёт.
	 */
	private void endWhileRequestInFlight(Ending ending, UUID account, TestSession target, TestSession other,
			boolean rememberMe, Runnable beforeRelease) throws Exception {
		shiftSignIn(target, rememberMe ? Duration.ofDays(1) : Duration.ofDays(30).minusHours(11));
		SessionHoldProbeController.Hold hold = this.holds.prepare();
		CompletableFuture<Integer> inFlight = CompletableFuture.supplyAsync(() -> this.http.get()
			.uri(hold.path())
			.header(HttpHeaders.COOKIE, target.cookies())
			.exchange()
			.expectBody()
			.returnResult()
			.getStatus()
			.value());
		try {
			assertThat(hold.awaitEntered()).as("запрос в полёте загрузил сессию").isTrue();
			end(ending, account, target, other);
			beforeRelease.run();
		}
		finally {
			hold.release();
		}
		assertThat(inFlight.get(SessionHoldProbeController.MAX_HOLD.toSeconds(), TimeUnit.SECONDS))
			.as("запрос, начатый до завершения, заканчивается как обычно")
			.isEqualTo(204);
	}

	private void signOut(TestSession session) {
		this.http.post()
			.uri(SignInProbeController.SIGN_OUT)
			.header(HttpHeaders.COOKIE, session.cookies())
			.header(XSRF_HEADER, session.xsrf())
			.exchange()
			.expectStatus()
			.isNoContent();
	}

	private Duration ttl(String key) {
		Long seconds = this.redis.getExpire(key, TimeUnit.SECONDS);
		assertThat(seconds).as("срок ключа %s", key).isNotNull().isPositive();
		return Duration.ofSeconds(seconds);
	}

	private Instant valkeyTime() {
		Long micros = this.redis
			.execute((RedisCallback<Long>) (connection) -> connection.serverCommands().time(TimeUnit.MICROSECONDS));
		assertThat(micros).isNotNull();
		return Instant.EPOCH.plus(micros, ChronoUnit.MICROS);
	}

	private RedisOperations<String, Object> sessionOperations() {
		return this.redisSessions.getSessionRedisOperations();
	}

	/** Id в индексе аккаунта в Valkey как есть, без проверки, что за ними есть сессии. */
	private Set<Object> indexMembers(UUID account) {
		Set<Object> members = sessionOperations().boundSetOps(index(account)).members();
		return (members != null) ? members : Set.of();
	}

	private static String index(UUID account) {
		return "dentify:session:index:" + FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME + ":" + account;
	}

	/** Убрать сессию из индекса аккаунта в Valkey, оставив саму сессию: так её оставлял запрос в полёте. */
	private void dropFromIndex(UUID account, TestSession session) {
		sessionOperations().boundSetOps(index(account)).remove(sessionId(session));
		assertThat(this.sessions.findByPrincipalName(account.toString())).doesNotContainKey(sessionId(session));
		me(session).expectStatus().isOk();
	}

	private void end(Ending ending, UUID account, TestSession target, TestSession other) {
		switch (ending) {
			case SIGN_OUT -> signOut(target);
			case TERMINATE_ONE -> this.http.delete()
				.uri(SESSIONS + "/" + RedisAccountSessions.publicId(sessionId(target)))
				.header(HttpHeaders.COOKIE, other.cookies())
				.header(XSRF_HEADER, other.xsrf())
				.exchange()
				.expectStatus()
				.isNoContent();
			case TERMINATE_ALL -> assertThat(this.accountSessions.terminateAll(account)).isEqualTo(2);
			case TERMINATE_OTHERS -> this.http.delete()
				.uri(SESSIONS)
				.header(HttpHeaders.COOKIE, other.cookies())
				.header(XSRF_HEADER, other.xsrf())
				.exchange()
				.expectStatus()
				.isNoContent();
		}
	}

	private ResponseSpec me(TestSession session) {
		return this.http.get().uri(ME).header(HttpHeaders.COOKIE, session.cookies()).exchange();
	}

	/** Вход был раньше на {@code ago}: сдвиг времени в самой сессии в Valkey. */
	private void shiftSignIn(TestSession session, Duration ago) {
		RedisIndexedSessionRepository.RedisSession stored = this.redisSessions.findById(sessionId(session));
		assertThat(stored).as("сессия в Valkey").isNotNull();
		stored.setAttribute(SessionClients.SIGNED_IN_AT, Instant.now().minus(ago));
		this.redisSessions.save(stored);
	}

	private static String sessionId(TestSession session) {
		return new String(Base64.getDecoder().decode(session.session()), StandardCharsets.UTF_8);
	}

}
