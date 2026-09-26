package kz.dentify.iam;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import kz.dentify.AbstractIntegrationTest;
import kz.dentify.platform.identity.AccountSessions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/**
 * Активные сессии своего аккаунта (PLT-04): список с браузером, ОС, IP маской и городом, завершить одну, все кроме
 * текущей или все. После завершения следующий запрос с любого устройства аккаунта — 401, быстрее чем за 5 секунд.
 * Аккаунты, адреса и браузеры — вымышленные.
 */
class AccountSessionsTests extends AbstractIntegrationTest {

	static final String SESSIONS = "/api/v1/account/sessions";

	static final String CHROME_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
			+ "Chrome/140.0.0.0 Safari/537.36";

	static final String SAFARI_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 "
			+ "(KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1";

	/** Лондон в тестовой GeoIP-базе MaxMind. */
	static final String LONDON_IP = "81.2.69.142";

	static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	AccountSessions accountSessions;

	@Test
	void listsOwnSessionsWithBrowserMaskedIpAndCity() {
		UUID account = UUID.randomUUID();
		TestSession desktop = signIn(account, false, LONDON_IP, CHROME_WINDOWS);
		TestSession phone = signIn(account, true, "198.51.100.77", SAFARI_IPHONE);
		TestSession stranger = signIn(UUID.randomUUID());

		JsonNode sessions = list(desktop);

		assertThat(sessions.size()).isEqualTo(2);
		JsonNode current = byCurrent(sessions, true);
		assertThat(current.path("browser").asString()).isEqualTo("Chrome 140");
		assertThat(current.path("os").asString()).isEqualTo("Windows 10");
		assertThat(current.path("ip").asString()).isEqualTo("81.2.•.•");
		assertThat(current.path("city").asString()).isEqualTo("Лондон");
		assertThat(current.path("rememberMe").asBoolean()).isFalse();
		assertThat(current.path("signedInAt").asString()).isNotBlank();
		assertThat(current.path("lastActiveAt").asString()).isNotBlank();
		JsonNode other = byCurrent(sessions, false);
		assertThat(other.path("os").asString()).isEqualTo("iOS 18");
		assertThat(other.path("ip").asString()).isEqualTo("198.51.•.•");
		assertThat(other.path("city").isNull()).as("адреса нет в базе").isTrue();
		assertThat(other.path("rememberMe").asBoolean()).isTrue();

		String body = sessions.toString();
		assertThat(body).as("настоящий id сессии и полный IP наружу не уходят")
			.doesNotContain(sessionId(desktop), sessionId(phone), sessionId(stranger), desktop.session(), LONDON_IP,
					"198.51.100.77");
	}

	@Test
	void terminateOneSessionSignsItOutEverywhere() {
		UUID account = UUID.randomUUID();
		TestSession desktop = signIn(account);
		TestSession phone = signIn(account);
		String phoneId = byCurrent(list(phone), true).path("id").asString();

		this.http.delete()
			.uri(SESSIONS + "/" + phoneId)
			.header(HttpHeaders.COOKIE, desktop.cookies())
			.header(XSRF_HEADER, desktop.xsrf())
			.exchange()
			.expectStatus()
			.isNoContent();

		expectProblem(this.http.get().uri(SESSIONS).header(HttpHeaders.COOKIE, phone.cookies()).exchange(), 401,
				"error.session-expired");
		assertThat(list(desktop).size()).isEqualTo(1);
	}

	/** Чужую или выдуманную сессию завершить нельзя: 404, как будто её нет. */
	@Test
	void foreignOrUnknownSessionIsNotFound() {
		TestSession mine = signIn(UUID.randomUUID());
		TestSession stranger = signIn(UUID.randomUUID());
		String strangerId = byCurrent(list(stranger), true).path("id").asString();

		expectProblem(this.http.delete()
			.uri(SESSIONS + "/" + strangerId)
			.header(HttpHeaders.COOKIE, mine.cookies())
			.header(XSRF_HEADER, mine.xsrf())
			.exchange(), 404, "error.not-found");
		expectProblem(this.http.delete()
			.uri(SESSIONS + "/not-a-session")
			.header(HttpHeaders.COOKIE, mine.cookies())
			.header(XSRF_HEADER, mine.xsrf())
			.exchange(), 404, "error.not-found");
		assertThat(list(stranger).size()).isEqualTo(1);
	}

	/** «Завершить все, кроме моей»: остальные устройства аккаунта получают 401, текущая сессия живёт. */
	@Test
	void terminateOthersKeepsCurrentSession() {
		UUID account = UUID.randomUUID();
		TestSession current = signIn(account);
		TestSession laptop = signIn(account);
		TestSession phone = signIn(account);

		this.http.delete()
			.uri(SESSIONS)
			.header(HttpHeaders.COOKIE, current.cookies())
			.header(XSRF_HEADER, current.xsrf())
			.exchange()
			.expectStatus()
			.isNoContent();

		for (TestSession ended : List.of(laptop, phone)) {
			expectProblem(this.http.get().uri(SESSIONS).header(HttpHeaders.COOKIE, ended.cookies()).exchange(), 401,
					"error.session-expired");
		}
		assertThat(list(current).size()).isEqualTo(1);
	}

	/** Выход везде: с includeCurrent завершается и текущая, её cookie стирается в том же ответе. */
	@Test
	void terminateAllIncludingCurrent() {
		UUID account = UUID.randomUUID();
		TestSession current = signIn(account);
		TestSession other = signIn(account);

		List<String> cookies = this.http.delete()
			.uri(SESSIONS + "?includeCurrent=true")
			.header(HttpHeaders.COOKIE, current.cookies())
			.header(XSRF_HEADER, current.xsrf())
			.exchange()
			.expectStatus()
			.isNoContent()
			.expectBody()
			.returnResult()
			.getResponseHeaders()
			.getOrEmpty(HttpHeaders.SET_COOKIE);

		assertThat(setCookie(cookies, SESSION_COOKIE)).isNotNull().contains("Max-Age=0");
		for (TestSession ended : List.of(current, other)) {
			expectProblem(this.http.get().uri(SESSIONS).header(HttpHeaders.COOKIE, ended.cookies()).exchange(), 401,
					"error.session-expired");
		}
		assertThat(this.accountSessions.list(account, null)).isEmpty();
	}

	/**
	 * Приёмка PLT-04: деактивация или смена пароля (iam) гасит все сессии аккаунта — следующий запрос с любого устройства
	 * получает 401, и быстрее, чем за 5 секунд.
	 */
	@Test
	void serviceTerminatesAllSessionsOfAccountWithinFiveSeconds() {
		UUID account = UUID.randomUUID();
		List<TestSession> devices = List.of(signIn(account), signIn(account, true), signIn(account));
		TestSession colleague = signIn(UUID.randomUUID());
		long started = System.nanoTime();

		int terminated = this.accountSessions.terminateAll(account);

		for (TestSession device : devices) {
			expectProblem(this.http.get().uri(SESSIONS).header(HttpHeaders.COOKIE, device.cookies()).exchange(), 401,
					"error.session-expired");
		}
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
		assertThat(terminated).isEqualTo(3);
		assertThat(list(colleague).size()).as("сессии другого аккаунта не тронуты").isEqualTo(1);
	}

	@Test
	void serviceTerminatesOthersOnPasswordChange() {
		UUID account = UUID.randomUUID();
		TestSession changing = signIn(account);
		TestSession other = signIn(account);

		int terminated = this.accountSessions.terminateOthers(account, sessionId(changing));

		assertThat(terminated).isEqualTo(1);
		assertThat(list(changing).size()).isEqualTo(1);
		expectProblem(this.http.get().uri(SESSIONS).header(HttpHeaders.COOKIE, other.cookies()).exchange(), 401,
				"error.session-expired");
	}

	/**
	 * Отключение членства в клинике (T-IAM-11): гаснут только сессии аккаунта, в которых выбрана эта клиника. Работа того
	 * же человека в другой клинике и сессии коллег той же клиники живы.
	 */
	@Test
	void serviceTerminatesOnlySessionsOfOneClinic() {
		UUID account = UUID.randomUUID();
		UUID clinic = UUID.randomUUID();
		String inClinic = enterClinic(clinic, account);
		String inClinicToo = enterClinic(clinic, account);
		String inOtherClinic = enterClinic(UUID.randomUUID(), account);
		String colleague = enterClinic(clinic, UUID.randomUUID());

		int terminated = this.accountSessions.terminateInClinic(account, clinic);

		assertThat(terminated).isEqualTo(2);
		for (String ended : List.of(inClinic, inClinicToo)) {
			expectProblem(this.http.get().uri(SESSIONS).header(HttpHeaders.COOKIE, ended).exchange(), 401,
					"error.session-expired");
		}
		for (String alive : List.of(inOtherClinic, colleague)) {
			this.http.get().uri(SESSIONS).header(HttpHeaders.COOKIE, alive).exchange().expectStatus().isOk();
		}
		assertThat(this.accountSessions.list(account, null)).hasSize(1);
	}

	@Test
	void withoutSessionIsUnauthorized() {
		expectProblem(this.http.get().uri(SESSIONS).exchange(), 401, "error.unauthorized");
	}

	private JsonNode list(TestSession session) {
		String body = this.http.get()
			.uri(SESSIONS)
			.header(HttpHeaders.COOKIE, session.cookies())
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		return JSON.readTree(body);
	}

	private static JsonNode byCurrent(JsonNode sessions, boolean current) {
		for (JsonNode session : sessions) {
			if (session.path("current").asBoolean() == current) {
				return session;
			}
		}
		throw new AssertionError("нет сессии с current=" + current + " в " + sessions);
	}

	private static String sessionId(TestSession session) {
		return new String(Base64.getDecoder().decode(session.session()), StandardCharsets.UTF_8);
	}

}
