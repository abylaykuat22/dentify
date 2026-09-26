package kz.dentify.iam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import kz.dentify.iam.IamFixtures.Person;
import kz.dentify.iam.internal.AccountContexts;
import kz.dentify.iam.internal.MembershipStatus;
import kz.dentify.iam.internal.SignIns;
import kz.dentify.platform.api.ErrorCodes;
import kz.dentify.platform.api.PhoneNumber;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import kz.dentify.platform.identity.AccountSessions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/**
 * Вход и выход (IAM-02, IAM-03, PLT-05) через {@code POST /api/v1/auth/login} и {@code /logout} на PostgreSQL и Valkey:
 * cookie сессии и смена её id, Запомнить, CSRF, одинаковый ответ и время для неизвестного логина, отключённый доступ,
 * блокировка после попыток, журнал входов, вход одновременно с деактивацией и сменой пароля. Люди, клиники, адреса и
 * браузеры — вымышленные.
 */
class SignInTests extends LoginIntegrationTest {

	@Autowired
	AccountSessions accountSessions;

	@Autowired
	FindByIndexNameSessionRepository<? extends Session> sessions;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	MessageSource messages;

	/**
	 * Приёмка IAM-02: верные данные — cookie HttpOnly, Secure, SameSite=Lax без срока (до закрытия браузера), новый
	 * CSRF-токен; одна клиника с одним филиалом — клиника и филиал уже в сессии; вход в журнале с устройством и IP.
	 */
	@Test
	void loginByEmailCreatesSecureSessionAndSelectsOnlyClinic() {
		Tenant clinic = this.clinics.create();
		Person person = this.fixtures.person("Алиева Динара");
		UUID membership = this.fixtures.join(person, clinic, MembershipStatus.ACTIVE);
		String ip = testClientIp();

		EntityExchangeResult<String> result = login("  " + person.email().toUpperCase(Locale.ROOT) + " ",
				IamFixtures.PASSWORD, false, ip, FIREFOX_LINUX, null);
		TestSession session = signedIn(result);

		String cookie = setCookie(setCookies(result), SESSION_COOKIE);
		assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Lax").doesNotContain("Max-Age", "Expires");
		JsonNode me = read(result);
		assertThat(me.path("account").path("id").asString()).isEqualTo(person.id().toString());
		assertThat(me.path("account").path("fullName").asString()).isEqualTo("Алиева Динара");
		assertThat(me.path("context").path("clinic").path("id").asString()).isEqualTo(clinic.clinicId().toString());
		assertThat(me.path("context").path("branch").path("id").asString()).isEqualTo(clinic.branchId().toString());
		assertThat(read(me(session)).path("context")).isEqualTo(me.path("context"));
		assertThat(result.getResponseBody()).doesNotContain(IamFixtures.PASSWORD, "password");

		assertThat(events(person.id())).containsExactly("SIGN_IN");
		Map<String, Object> event = this.jdbc.queryForMap(
				"select host(ip) as ip, user_agent from auth_event where account_id = ? and kind = 'SIGN_IN'", person.id());
		assertThat(event).containsEntry("ip", ip).containsEntry("user_agent", FIREFOX_LINUX);
		Map<String, Object> joined = TenantContext.call(clinic, () -> this.jdbc
			.queryForMap("select last_sign_in_at, version from membership where id = ?", membership));
		assertThat(joined.get("last_sign_in_at")).as("последний вход в клинику").isNotNull();
		assertThat(joined.get("version")).as("вход — не правка членства").isEqualTo(0L);
	}

	/** Телефон — в любой записи: «8 707…», «+7 (707)…», без кода страны. */
	@Test
	void loginByPhoneInAnyNotation() {
		Person person = this.fixtures.person("Нурланов Аскар");
		String digits = person.phoneDigits();

		for (String phone : List.of("8 " + digits.substring(0, 3) + " " + digits.substring(3, 6) + " "
				+ digits.substring(6, 8) + " " + digits.substring(8), "+7 (" + digits.substring(0, 3) + ") "
						+ digits.substring(3), digits)) {
			TestSession session = signedIn(login(phone, IamFixtures.PASSWORD, false));
			assertThat(read(me(session)).path("account").path("phone").asString()).as(phone).isEqualTo(person.phone());
		}
	}

	/**
	 * Приёмка IAM-02: id сессии при входе меняется — сессия, известная до входа, после него недействительна, и
	 * клиника, выбранная в ней до входа, новому входу не достаётся.
	 */
	@Test
	void loginChangesSessionIdAndDropsClinicChosenBefore() {
		Tenant own = this.clinics.create();
		Tenant foreign = this.clinics.create();
		Person person = this.fixtures.person("Сапарова Жанна");
		this.fixtures.join(person, own, MembershipStatus.ACTIVE);
		String before = enterClinic(foreign.clinicId(), null);

		TestSession session = signedIn(login(person.email(), IamFixtures.PASSWORD, false, testClientIp(), null, before));

		assertThat(SESSION_COOKIE + "=" + session.session()).isNotEqualTo(before);
		problem(me(before), 401, ErrorCodes.SESSION_EXPIRED);
		assertThat(read(me(session)).path("context").path("clinic").path("id").asString())
			.isEqualTo(own.clinicId().toString());
	}

	/**
	 * Приёмка IAM-02: неверный пароль и несуществующий логин — один ответ: тот же статус, код, заголовок и текст, без
	 * сессии. В журнале — неверный пароль аккаунта и попытка без аккаунта; введённый логин не хранится.
	 */
	@Test
	void wrongPasswordAndUnknownLoginGetSameAnswer() {
		Person person = this.fixtures.person("Ким Виктор");
		String ip = testClientIp();
		// свой User-Agent: по нему находятся события этого теста без аккаунта, адрес из 254 могут делить и другие тесты
		String agent = "Probe/" + UUID.randomUUID();

		EntityExchangeResult<String> wrong = login(person.email(), "Неверный-пароль-1", false, ip, agent, null);
		EntityExchangeResult<String> unknown = login("nobody." + UUID.randomUUID() + "@example.kz", "Неверный-пароль-1",
				false, ip, agent, null);
		EntityExchangeResult<String> unknownPhone = login("8 707 000 00 01", "Неверный-пароль-1", false, ip, agent, null);
		EntityExchangeResult<String> nonsense = login("не телефон и не почта", "Неверный-пароль-1", false, ip, agent,
				null);

		JsonNode expected = problem(wrong, 401, SignIns.INVALID);
		assertThat(expected.path("detail").asString()).isEqualTo("Неверный телефон, e-mail или пароль.");
		for (EntityExchangeResult<String> other : List.of(unknown, unknownPhone, nonsense)) {
			JsonNode answer = problem(other, 401, SignIns.INVALID);
			for (String field : List.of("status", "title", "detail", "code", "instance")) {
				assertThat(answer.path(field)).as(field).isEqualTo(expected.path(field));
			}
			assertThat(answer.propertyNames()).containsExactlyInAnyOrderElementsOf(expected.propertyNames());
			assertThat(setCookie(setCookies(other), SESSION_COOKIE)).as("без сессии").isNull();
		}
		assertThat(setCookie(setCookies(wrong), SESSION_COOKIE)).isNull();

		assertThat(events(person.id())).containsExactly("WRONG_PASSWORD");
		assertThat(this.jdbc.queryForObject("select count(*) from auth_event where account_id is null and host(ip) = ? "
				+ "and user_agent = ? and kind = 'WRONG_PASSWORD'", Long.class, ip, agent)).isEqualTo(3L);
	}

	/**
	 * Приёмка IAM-02 и PLT-05: неизвестный логин отвечает за то же время, что и неверный пароль (± 100 мс): пароль
	 * неизвестного логина тоже проверяется Argon2id с теми же параметрами. Медианы по нескольким попыткам, каждая — со
	 * своего логина и IP, чтобы не упереться в лимиты. Что проверка хэша-пустышки вообще есть, этот замер не докажет:
	 * хэш короче допуска. Это ловит {@link #unknownLoginChecksPasswordOnceLikeWrongPassword}.
	 */
	@Test
	void unknownLoginTakesAsLongAsWrongPassword() {
		int attempts = 7;
		List<Person> people = new ArrayList<>();
		for (int i = 0; i < attempts + 1; i++) {
			people.add(this.fixtures.person("Сотрудник Замер"));
		}
		// прогрев: первый запрос каждого пути
		login(people.getLast().email(), "Неверный-пароль-1", false);
		login("warmup." + UUID.randomUUID() + "@example.kz", "Неверный-пароль-1", false);

		List<Long> wrong = new ArrayList<>();
		List<Long> unknown = new ArrayList<>();
		for (int i = 0; i < attempts; i++) {
			wrong.add(timed(people.get(i).email()));
			unknown.add(timed("unknown." + UUID.randomUUID() + "@example.kz"));
		}

		long wrongMedian = median(wrong);
		long unknownMedian = median(unknown);
		assertThat(Math.abs(wrongMedian - unknownMedian)).as("неверный пароль %d мс, неизвестный логин %d мс",
				wrongMedian, unknownMedian)
			.isLessThan(100);
	}

	/**
	 * Приёмка IAM-02 и PLT-05 без замера времени: неизвестный e-mail, неизвестный телефон и бессмысленный ввод проверяют
	 * пароль бином {@code PasswordEncoder} ровно один раз — как неверный пароль известного аккаунта. Замер выше этого не
	 * ловит: хэш — около 35 мс, а допуск — 100 мс, и вход без проверки хэша-пустышки прошёл бы его. Этот тест без неё
	 * падает. Проверки считает тестовая обёртка бина {@link PasswordChecks} по паролю, у каждой попытки — свой.
	 */
	@Test
	void unknownLoginChecksPasswordOnceLikeWrongPassword() {
		Person person = this.fixtures.person("Сейтова Карина");
		// 747 — не код тестовых людей (707): такого аккаунта нет
		String unknownPhone = "+7 747 " + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		assertThat(PhoneNumber.parse(unknownPhone)).as("неизвестный логин — телефон").isPresent();
		Map<String, String> logins = new LinkedHashMap<>();
		logins.put("неверный пароль", person.email());
		logins.put("неизвестный e-mail", "nobody." + UUID.randomUUID() + "@example.kz");
		logins.put("неизвестный телефон", unknownPhone);
		logins.put("не телефон и не e-mail", "не телефон " + UUID.randomUUID());

		logins.forEach((what, login) -> {
			String password = "Проверка-" + UUID.randomUUID();
			PasswordChecks.count(password);

			problem(login(login, password, false), 401, SignIns.INVALID);

			assertThat(PasswordChecks.checks(password)).as("проверок пароля: %s", what).isEqualTo(1);
		});
	}

	/**
	 * Попытка, которую оборвал предел одновременных хэшей (429 {@code error.too-many-requests}, пароль не проверен), не
	 * занимает место в лимите входа: шесть таких подряд не закрывают вход, следующая с верным паролем входит. В журнал
	 * входов такие попытки не пишутся — пароль не проверен.
	 */
	@Test
	void attemptCutByHashingLimitDoesNotCountAgainstLogin() {
		Person person = this.fixtures.person("Бектурова Динара");
		String busy = "Занято-" + UUID.randomUUID();
		PasswordChecks.busy(busy);

		for (int i = 0; i < 6; i++) {
			problem(login(person.email(), busy, false), 429, ErrorCodes.TOO_MANY_REQUESTS);
		}

		signedIn(login(person.email(), IamFixtures.PASSWORD, false));
		assertThat(events(person.id())).containsExactly("SIGN_IN");
	}

	/**
	 * Приёмка IAM-03: с Запомнить cookie постоянная — Max-Age 30 дней, переживает перезапуск браузера; без — сессионная,
	 * до закрытия браузера.
	 */
	@Test
	void rememberMeMakesCookiePersistentForThirtyDays() {
		Person person = this.fixtures.person("Омарова Гульнар");

		EntityExchangeResult<String> remembered = login(person.email(), IamFixtures.PASSWORD, true);
		EntityExchangeResult<String> browserOnly = login(person.email(), IamFixtures.PASSWORD, false);

		assertThat(setCookie(setCookies(remembered), SESSION_COOKIE)).contains("Max-Age=2592000", "HttpOnly", "Secure",
				"SameSite=Lax");
		assertThat(setCookie(setCookies(browserOnly), SESSION_COOKIE)).doesNotContain("Max-Age", "Expires");
		assertThat(this.accountSessions.list(person.id(), null)).extracting((session) -> session.rememberMe())
			.containsExactlyInAnyOrder(true, false);
	}

	/** Приёмка IAM-03: вход и выход без CSRF-токена — 403 {@code error.csrf}; без токена вход не создаёт сессию. */
	@Test
	void loginAndLogoutNeedCsrfToken() {
		Person person = this.fixtures.person("Бекова Асель");

		EntityExchangeResult<String> withoutToken = this.http.post()
			.uri(LOGIN)
			.header(FORWARDED_FOR, testClientIp())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("login", person.email(), "password", IamFixtures.PASSWORD, "rememberMe", false))
			.exchange()
			.returnResult(String.class);
		problem(withoutToken, 403, ErrorCodes.CSRF);
		assertThat(setCookie(setCookies(withoutToken), SESSION_COOKIE)).isNull();
		assertThat(events(person.id())).isEmpty();

		TestSession session = signIn(person);
		EntityExchangeResult<String> logoutWithoutToken = this.http.post()
			.uri(LOGOUT)
			.header(HttpHeaders.COOKIE, session.cookies())
			.exchange()
			.returnResult(String.class);
		problem(logoutWithoutToken, 403, ErrorCodes.CSRF);
		assertThat(me(session).getStatus().value()).as("сессия жива").isEqualTo(200);
	}

	/**
	 * Приёмка IAM-03: выход удаляет сессию на сервере и cookie, пишет событие «Выход»; запросы с прежней cookie — 401,
	 * в Активных сессиях и в Spring Session её нет, другое устройство аккаунта работает.
	 */
	@Test
	void logoutEndsSessionAndIsJournaled() {
		Person person = this.fixtures.person("Жумабаев Ерлан");
		TestSession session = signIn(person);
		TestSession phone = signIn(person);

		EntityExchangeResult<String> result = logout(session);

		assertThat(result.getStatus().value()).isEqualTo(204);
		assertThat(setCookie(setCookies(result), SESSION_COOKIE)).contains("Max-Age=0");
		assertThat(setCookie(setCookies(result), XSRF_COOKIE)).as("CSRF-токен стёрт").startsWith(XSRF_COOKIE + "=;")
			.contains("Expires=Thu, 01 Jan 1970");
		problem(me(session), 401, ErrorCodes.SESSION_EXPIRED);
		assertThat(this.sessions.findById(sessionId(session))).as("сессии нет в хранилище").isNull();
		assertThat(this.accountSessions.list(person.id(), null)).hasSize(1);
		assertThat(me(phone).getStatus().value()).isEqualTo(200);
		assertThat(events(person.id())).containsExactlyInAnyOrder("SIGN_IN", "SIGN_IN", "SIGN_OUT");
	}

	/** Выход без сессии — не ошибка: 204, повторный выход тоже. */
	@Test
	void logoutWithoutSessionIsNoContent() {
		String xsrf = csrfToken();

		this.http.post()
			.uri(LOGOUT)
			.header(HttpHeaders.COOKIE, XSRF_COOKIE + "=" + xsrf)
			.header(XSRF_HEADER, xsrf)
			.exchange()
			.expectStatus()
			.isNoContent();
	}

	/**
	 * Приёмка IAM-02: отключённый доступ — отдельный текст «Доступ отключён», но только после верного пароля; с неверным
	 * паролем — тот же ответ, что всегда.
	 */
	@Test
	void disabledAccountGetsAccessDisabledOnlyWithCorrectPassword() {
		Person person = this.fixtures.person("Ахметова Сауле");
		this.fixtures.disableAccount(person.id());

		JsonNode disabled = problem(login(person.email(), IamFixtures.PASSWORD, false), 403, SignIns.ACCESS_DISABLED);
		problem(login(person.email(), "Неверный-пароль-1", false), 401, SignIns.INVALID);

		assertThat(disabled.path("detail").asString()).isEqualTo("Доступ отключён. Обратитесь к владельцу клиники.");
		assertThat(events(person.id())).containsExactlyInAnyOrder("ACCESS_DISABLED", "WRONG_PASSWORD");
		assertThat(this.accountSessions.list(person.id(), null)).isEmpty();
	}

	/**
	 * Все членства отключены — «Доступ отключён». Отключено одно, а другое — приглашение: вход открыт (принять
	 * приглашение), клиника не выбрана.
	 */
	@Test
	void allMembershipsDisabledMeansAccessDisabled() {
		Tenant a = this.clinics.create();
		Tenant b = this.clinics.create();
		Person person = this.fixtures.person("Ли Марина");
		this.fixtures.join(person, a, MembershipStatus.DISABLED);
		this.fixtures.join(person, b, MembershipStatus.DISABLED);
		Person invited = this.fixtures.person("Касымов Данияр");
		this.fixtures.join(invited, a, MembershipStatus.DISABLED);
		this.fixtures.join(invited, b, MembershipStatus.INVITED);

		problem(login(person.email(), IamFixtures.PASSWORD, false), 403, SignIns.ACCESS_DISABLED);
		JsonNode me = read(login(invited.email(), IamFixtures.PASSWORD, false));

		assertThat(me.path("context").isNull()).isTrue();
		assertThat(me.path("memberships").valueStream().map((membership) -> membership.path("status").asString()))
			.containsExactly("DISABLED", "INVITED");
	}

	/**
	 * Приёмка PLT-05 и IAM-04: пятая неверная попытка за 15 минут закрывает вход — в журнале «Неверный пароль» и
	 * «Блокировка после попыток»; шестая, даже с верным паролем, — 429 с оставшимися минутами и снова «Блокировка».
	 * Дальнейшие отказы за время блокировки журнал не пишет: отказ ничего не стоит нападающему, и сотни строк в минуту
	 * засыпали бы журнал входов аккаунта. Телефон в разных записях — один логин и один счётчик.
	 */
	@Test
	void fifthWrongPasswordLocksLoginAndIsJournaled() {
		Person person = this.fixtures.person("Иванова Елена");
		String digits = person.phoneDigits();
		List<String> notations = List.of("8" + digits, "+7" + digits, digits, "8 (" + digits.substring(0, 3) + ") "
				+ digits.substring(3), "+7 " + digits);

		for (String phone : notations) {
			problem(login(phone, "Неверный-пароль-1", false), 401, SignIns.INVALID);
		}
		EntityExchangeResult<String> locked = login("8" + digits, IamFixtures.PASSWORD, false);

		JsonNode problem = problem(locked, 429, ErrorCodes.TOO_MANY_LOGIN_ATTEMPTS);
		assertThat(problem.path("detail").asString()).isEqualTo("Слишком много попыток, попробуйте через 15 мин.");
		assertThat(locked.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
		for (String phone : notations) {
			problem(login(phone, IamFixtures.PASSWORD, false), 429, ErrorCodes.TOO_MANY_LOGIN_ATTEMPTS);
		}
		assertThat(events(person.id())).containsExactlyInAnyOrder("WRONG_PASSWORD", "WRONG_PASSWORD", "WRONG_PASSWORD",
				"WRONG_PASSWORD", "WRONG_PASSWORD", "LOCKED_OUT", "LOCKED_OUT");
		assertThat(this.accountSessions.list(person.id(), null)).isEmpty();
	}

	/**
	 * Одна клиника, несколько филиалов: без ограничения филиалов клиника не выбирается сама (интерфейс спросит филиал),
	 * с доступом к одному филиалу — выбирается он.
	 */
	@Test
	void clinicIsSelectedOnlyWithOneAvailableBranch() {
		Tenant clinic = this.clinics.create();
		UUID second = this.clinics.addBranch(clinic.clinicId());
		Person everywhere = this.fixtures.person("Мусин Тимур");
		this.fixtures.join(everywhere, clinic, MembershipStatus.ACTIVE);
		Person oneBranch = this.fixtures.person("Абенова Камила");
		this.fixtures.join(oneBranch, clinic, MembershipStatus.ACTIVE, second);

		JsonNode all = read(login(everywhere.email(), IamFixtures.PASSWORD, false));
		JsonNode restricted = read(login(oneBranch.email(), IamFixtures.PASSWORD, false));

		assertThat(all.path("context").isNull()).isTrue();
		assertThat(all.path("memberships").get(0).path("branches").size()).isEqualTo(2);
		assertThat(restricted.path("context").path("branch").path("id").asString()).isEqualTo(second.toString());
		assertThat(restricted.path("memberships").get(0).path("branches").size()).isEqualTo(1);
	}

	/**
	 * Вход одновременно с деактивацией (docs/architecture.md, Активные сессии): аккаунт прочитан и пароль проверен до
	 * коммита деактивации, а сессия создаётся после. Вход перечитывает аккаунт после создания сессии и выходит: 403, в
	 * Valkey сессии аккаунта нет, в журнале — отказ, а не вход.
	 */
	@Test
	void deactivationCommittedDuringLoginSignsOut() {
		Person person = this.fixtures.person("Серикова Айжан");
		SignInInterlude.beforeSignIn(person.id(), () -> this.fixtures.disableAccount(person.id()));

		EntityExchangeResult<String> result = login(person.email(), IamFixtures.PASSWORD, false);

		problem(result, 403, SignIns.ACCESS_DISABLED);
		assertThat(cookieValue(setCookies(result), SESSION_COOKIE)).as("живой cookie сессии нет").isNull();
		assertThat(this.accountSessions.list(person.id(), null)).isEmpty();
		assertThat(events(person.id())).containsExactly("ACCESS_DISABLED");
	}

	/** То же со сменой пароля: вход с прежним паролем не переживает смену — 401, как у неверного пароля. */
	@Test
	void passwordChangeCommittedDuringLoginSignsOut() {
		Person person = this.fixtures.person("Токаев Нурлан");
		String changed = this.passwordEncoder.encode("Новый-пароль-2026");
		SignInInterlude.beforeSignIn(person.id(), () -> this.jdbc
			.update("update account set password_hash = ?, version = version + 1 where id = ?", changed, person.id()));

		EntityExchangeResult<String> result = login(person.email(), IamFixtures.PASSWORD, false);

		problem(result, 401, SignIns.INVALID);
		assertThat(cookieValue(setCookies(result), SESSION_COOKIE)).isNull();
		assertThat(this.accountSessions.list(person.id(), null)).isEmpty();
		assertThat(events(person.id())).containsExactly("WRONG_PASSWORD");
	}

	/** То же с отключением последнего членства: 403 «Доступ отключён». */
	@Test
	void membershipDisabledDuringLoginSignsOut() {
		Tenant clinic = this.clinics.create();
		Person person = this.fixtures.person("Жанибекова Алия");
		UUID membership = this.fixtures.join(person, clinic, MembershipStatus.ACTIVE);
		SignInInterlude.beforeSignIn(person.id(), () -> this.fixtures.disableMembership(clinic, membership));

		problem(login(person.email(), IamFixtures.PASSWORD, false), 403, SignIns.ACCESS_DISABLED);

		assertThat(this.accountSessions.list(person.id(), null)).isEmpty();
		assertThat(events(person.id())).containsExactly("ACCESS_DISABLED");
	}

	/** Хэш старого алгоритма вход перехэширует в Argon2id с текущими параметрами; версия аккаунта растёт. */
	@Test
	void oldPasswordHashIsUpgradedOnLogin() {
		Person person = this.fixtures.person("Попов Андрей");
		this.jdbc.update("update account set password_hash = ? where id = ?",
				"{bcrypt}" + new BCryptPasswordEncoder().encode(IamFixtures.PASSWORD), person.id());
		long version = this.jdbc.queryForObject("select version from account where id = ?", Long.class, person.id());

		signIn(person);

		Map<String, Object> account = this.jdbc.queryForMap("select password_hash, version from account where id = ?",
				person.id());
		assertThat((String) account.get("password_hash")).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$");
		assertThat(account.get("version")).isEqualTo(version + 1);
		signIn(person);
	}

	/** Пустые поля — 400 с ошибками полей, без обращения к лимитам и журналу. */
	@Test
	void blankLoginOrPasswordIsValidationError() {
		String xsrf = csrfToken();

		EntityExchangeResult<String> result = this.http.post()
			.uri(LOGIN)
			.header(HttpHeaders.COOKIE, XSRF_COOKIE + "=" + xsrf)
			.header(XSRF_HEADER, xsrf)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("login", " ", "password", "", "rememberMe", true))
			.exchange()
			.returnResult(String.class);

		JsonNode problem = problem(result, 400, ErrorCodes.VALIDATION);
		assertThat(problem.path("errors").valueStream().map((error) -> error.path("field").asString()))
			.containsExactlyInAnyOrder("login", "password");
	}

	/**
	 * Без поля rememberMe — 400 с ошибкой поля «Заполните поле» ({@code validation.not-null}), как у обязательного поля
	 * спецификации, а не ошибка формата значения; до лимитов и журнала запрос не доходит.
	 */
	@Test
	void missingRememberMeIsRequiredFieldError() {
		Person person = this.fixtures.person("Оспанов Бауыржан");
		String xsrf = csrfToken();

		EntityExchangeResult<String> result = this.http.post()
			.uri(LOGIN)
			.header(HttpHeaders.COOKIE, XSRF_COOKIE + "=" + xsrf)
			.header(XSRF_HEADER, xsrf)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("login", person.email(), "password", IamFixtures.PASSWORD))
			.exchange()
			.returnResult(String.class);

		JsonNode problem = problem(result, 400, ErrorCodes.VALIDATION);
		assertThat(problem.path("errors").size()).isEqualTo(1);
		assertThat(problem.path("errors").get(0).path("field").asString()).isEqualTo("rememberMe");
		assertThat(problem.path("errors").get(0).path("code").asString()).isEqualTo("validation.not-null");
		assertThat(events(person.id())).isEmpty();
	}

	/** Журнал входов только дописывается: у роли приложения нет UPDATE, DELETE и TRUNCATE. */
	@Test
	void authEventIsAppendOnlyForApplication() {
		for (String privilege : List.of("UPDATE", "DELETE", "TRUNCATE")) {
			assertThat(this.jdbc.queryForObject("select has_table_privilege(current_user, 'auth_event', ?)",
					Boolean.class, privilege)).as(privilege).isFalse();
		}
		for (String privilege : List.of("SELECT", "INSERT")) {
			assertThat(this.jdbc.queryForObject("select has_table_privilege(current_user, 'auth_event', ?)",
					Boolean.class, privilege)).as(privilege).isTrue();
		}
	}

	/** Коды входа и выбора клиники — с русским текстом (dentify R003). */
	@Test
	void loginCodesHaveRussianText() {
		for (String code : List.of(SignIns.INVALID, SignIns.ACCESS_DISABLED, AccountContexts.MEMBERSHIP_DISABLED)) {
			assertThat(this.messages.getMessage(code, null, null, Locale.of("ru"))).as(code).isNotBlank();
		}
	}

	private long timed(String login) {
		String ip = testClientIp();
		String xsrf = csrfToken(ip);
		long started = System.nanoTime();
		this.http.post()
			.uri(LOGIN)
			.header(HttpHeaders.COOKIE, XSRF_COOKIE + "=" + xsrf)
			.header(XSRF_HEADER, xsrf)
			.header(FORWARDED_FOR, ip)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("login", login, "password", "Неверный-пароль-1", "rememberMe", false))
			.exchange()
			.expectStatus()
			.isUnauthorized();
		return Duration.ofNanos(System.nanoTime() - started).toMillis();
	}

	private static long median(List<Long> values) {
		List<Long> sorted = values.stream().sorted().toList();
		return sorted.get(sorted.size() / 2);
	}

}
