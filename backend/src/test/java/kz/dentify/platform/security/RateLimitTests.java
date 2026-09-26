package kz.dentify.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.github.bucket4j.TimeMeter;
import kz.dentify.AbstractIntegrationTest;
import kz.dentify.platform.api.ErrorCodes;
import kz.dentify.platform.api.TooManyRequestsException;
import kz.dentify.platform.identity.RateLimits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/**
 * Лимиты частоты (PLT-05) в Valkey. Правила входа и сброса пароля проверяются на лимитере с управляемыми часами (15
 * минут и час проходят мгновенно), в том числе на стыке окон: 5 неверных попыток за любые 15 минут закрывают вход на 15
 * минут от пятой, писем сброса — не больше 3 за любой час. Ответ 429 входа и общий лимит API — настоящими запросами:
 * 601-й запрос сессии за минуту — 429 ProblemDetail с {@code Retry-After}.
 */
class RateLimitTests extends AbstractIntegrationTest {

	@Autowired
	LettuceConnectionFactory connectionFactory;

	@Autowired
	RateLimits rateLimits;

	@Autowired
	StringRedisTemplate redis;

	/** Часы лимитера в тесте: время идёт только по команде. */
	final AtomicLong nanos = new AtomicLong(Duration.ofDays(20_000).toNanos());

	/** Лимитеры теста: у каждого своё соединение Bucket4j, после теста оно закрывается. */
	final List<RedisRateLimiter> limiters = new ArrayList<>();

	@AfterEach
	void closeLimiters() {
		this.limiters.forEach(RedisRateLimiter::destroy);
	}

	@Test
	void rateLimitsBeanIsValkeyBucket4j() {
		assertThat(this.rateLimits).isInstanceOf(RedisRateLimiter.class);
	}

	/**
	 * Приёмка PLT-05: после пяти неверных попыток шестая — 429 с Retry-After, даже с верным паролем (проверка до
	 * пароля); блок — 15 минут от пятой попытки, через 15 минут войти снова можно. Что вход закрыт, сообщает пятая
	 * неверная попытка, и только она: по ней журнал входов пишет «Блокировка после попыток».
	 */
	@Test
	void fiveFailuresLockLoginForFifteenMinutes() {
		RedisRateLimiter limiter = limiter();
		String login = "dinara." + UUID.randomUUID() + "@example.kz";

		List<Boolean> locked = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			limiter.checkLogin(login, ip());
			locked.add(limiter.recordLoginFailure(login));
			advance(Duration.ofMinutes(1));
		}
		assertThat(locked).containsExactly(false, false, false, false, true);
		TooManyRequestsException blocked = loginBlocked(limiter, login);

		assertThat(blocked.getCode()).isEqualTo(ErrorCodes.TOO_MANY_LOGIN_ATTEMPTS);
		assertThat(blocked.getRetryAfter()).isEqualTo(Duration.ofMinutes(14));
		assertThat(blocked.getArgs()).containsExactly(14L);

		advance(Duration.ofMinutes(14).minusSeconds(1));
		assertThat(loginBlocked(limiter, login).getRetryAfter()).isEqualTo(Duration.ofSeconds(1));
		advance(Duration.ofSeconds(1));
		assertThatNoException().isThrownBy(() -> limiter.checkLogin(login, ip()));
	}

	/**
	 * Блок считается от пятой неверной попытки, а не от первой: одна попытка в начале 15 минут и четыре в их конце
	 * закрывают вход ещё на 15 минут, и на стыке окон новые попытки не открываются.
	 */
	@Test
	void lockStartsAtFifthFailureNotAtFirst() {
		RedisRateLimiter limiter = limiter();
		String login = "arman." + UUID.randomUUID() + "@example.kz";

		limiter.recordLoginFailure(login);
		advance(Duration.ofMinutes(15).minusSeconds(1));
		for (int i = 0; i < 4; i++) {
			limiter.checkLogin(login, ip());
			limiter.recordLoginFailure(login);
		}
		advance(Duration.ofSeconds(1));
		TooManyRequestsException blocked = loginBlocked(limiter, login);

		assertThat(blocked.getRetryAfter()).isEqualTo(Duration.ofMinutes(15).minusSeconds(1));
		assertThat(blocked.getArgs()).containsExactly(15L);
	}

	/**
	 * Окно скользящее: считаются неверные попытки за последние 15 минут. Попытка, которой больше 15 минут, выпадает, а
	 * пятая за любые 15 минут закрывает вход, даже если окно от первой попытки уже прошло.
	 */
	@Test
	void failuresCountInSlidingFifteenMinutes() {
		RedisRateLimiter limiter = limiter();
		String login = "saule." + UUID.randomUUID() + "@example.kz";

		limiter.recordLoginFailure(login);
		advance(Duration.ofMinutes(10));
		for (int i = 0; i < 3; i++) {
			limiter.recordLoginFailure(login);
		}
		advance(Duration.ofMinutes(5));
		limiter.recordLoginFailure(login);
		assertThatNoException().as("первая попытка выпала из окна: за 15 минут их четыре")
			.isThrownBy(() -> limiter.checkLogin(login, ip()));

		advance(Duration.ofSeconds(1));
		limiter.recordLoginFailure(login);

		assertThat(loginBlocked(limiter, login).getRetryAfter()).isEqualTo(Duration.ofMinutes(15));
	}

	/**
	 * Приёмка PLT-05 при одновременных попытках: проверка входа занимает место попытки атомарно, до проверки пароля.
	 * Из 30 одновременных попыток на один логин с 30 разных IP проверку проходят ровно 5, остальные — 429, не дожидаясь
	 * пароля первых пяти. Когда пять оказались неверными, вход закрыт на 15 минут от пятой.
	 */
	@Test
	void concurrentAttemptsOnOneLoginPassCheckOnlyFiveTimes() throws Exception {
		RedisRateLimiter limiter = limiter();
		String login = "yerlan." + UUID.randomUUID() + "@example.kz";

		List<TooManyRequestsException> outcomes = concurrently(30, () -> {
			try {
				limiter.checkLogin(login, ip());
				return null;
			}
			catch (TooManyRequestsException ex) {
				return ex;
			}
		});

		assertThat(outcomes).filteredOn(Objects::isNull).as("прошли проверку").hasSize(5);
		assertThat(outcomes).filteredOn(Objects::nonNull)
			.hasSize(25)
			.allSatisfy((rejected) -> assertThat(rejected.getCode()).isEqualTo(ErrorCodes.TOO_MANY_LOGIN_ATTEMPTS));

		concurrently(5, () -> {
			limiter.recordLoginFailure(login);
			return null;
		});
		advance(Duration.ofMinutes(1));
		assertThat(loginBlocked(limiter, login).getRetryAfter()).isEqualTo(Duration.ofMinutes(14));
	}

	/**
	 * Идущие попытки держат место: пять начатых попыток без исхода закрывают шестую, успешный вход освобождает только
	 * своё место, а попытка, которая так и не закончилась (исключение до исхода), считается до выхода из окна.
	 */
	@Test
	void pendingAttemptsHoldTheirPlaces() {
		RedisRateLimiter limiter = limiter();
		String login = "madina." + UUID.randomUUID() + "@example.kz";
		for (int i = 0; i < 5; i++) {
			limiter.checkLogin(login, ip());
		}
		assertThat(loginBlocked(limiter, login).getRetryAfter()).as("до выхода из окна первой")
			.isEqualTo(Duration.ofMinutes(15));

		limiter.resetLogin(login);
		assertThatNoException().isThrownBy(() -> limiter.checkLogin(login, ip()));
		loginBlocked(limiter, login);

		advance(Duration.ofMinutes(15));
		assertThatNoException().as("незаконченные попытки вышли из окна")
			.isThrownBy(() -> limiter.checkLogin(login, ip()));
	}

	/**
	 * Блокировка ставится на пятой неверной попытке, а не на первой неверной из пяти одновременных: одновременные попытки
	 * не закрывают вход раньше, чем наберётся пять неверных, и успешная среди них вход не закрывает.
	 */
	@Test
	void lockNeedsFiveFailuresNotFivePending() {
		RedisRateLimiter limiter = limiter();
		String login = "nurlan." + UUID.randomUUID() + "@example.kz";
		for (int i = 0; i < 5; i++) {
			limiter.checkLogin(login, ip());
		}

		for (int i = 0; i < 4; i++) {
			limiter.recordLoginFailure(login);
		}
		limiter.resetLogin(login);

		assertThatNoException().as("четыре неверных обнулил успешный вход")
			.isThrownBy(() -> limiter.checkLogin(login, ip()));
		assertThat(this.redis.hasKey(RedisRateLimiter.key(RedisRateLimiter.LOGIN_LOCK, login))).isFalse();
	}

	/**
	 * Попытка, которой предел одновременных хэшей не дал проверить пароль, освобождает своё место, а неверные попытки
	 * логина остаются: без этого пять таких повторов закрыли бы вход без единого неверного пароля. Освобождать нечего —
	 * ничего не меняется.
	 */
	@Test
	void abandonedAttemptFreesItsPlaceButKeepsFailures() {
		RedisRateLimiter limiter = limiter();
		String login = "aliya." + UUID.randomUUID() + "@example.kz";
		limiter.abandonLogin(login);
		for (int i = 0; i < 4; i++) {
			limiter.checkLogin(login, ip());
			limiter.recordLoginFailure(login);
		}
		limiter.abandonLogin(login);
		limiter.checkLogin(login, ip());
		loginBlocked(limiter, login);

		limiter.abandonLogin(login);

		assertThatNoException().as("место пятой попытки свободно").isThrownBy(() -> limiter.checkLogin(login, ip()));
		assertThat(limiter.recordLoginFailure(login)).as("четыре неверных остались: эта — пятая").isTrue();
		loginBlocked(limiter, login);
	}

	/**
	 * Отказ во входе пишется в журнал входов один раз за время отказа, но не чаще раза в минуту: первый отказ на логин —
	 * да, следующие до конца блокировки — нет, другой логин — свой отсчёт; из одновременных отказов — ровно один.
	 */
	@Test
	void loginRejectionIsJournaledOncePerRejectionTime() throws Exception {
		RedisRateLimiter limiter = limiter();
		String login = "bolat." + UUID.randomUUID() + "@example.kz";

		assertThat(limiter.firstLoginRejection(login, Duration.ofMinutes(15))).isTrue();
		assertThat(limiter.firstLoginRejection(" " + login.toUpperCase() + " ", Duration.ofMinutes(14))).isFalse();
		assertThat(limiter.firstLoginRejection("other." + login, Duration.ofMinutes(15))).isTrue();
		advance(Duration.ofMinutes(15).minusSeconds(1));
		assertThat(limiter.firstLoginRejection(login, Duration.ofSeconds(1))).as("блокировка ещё идёт").isFalse();
		advance(Duration.ofSeconds(1));
		assertThat(limiter.firstLoginRejection(login, Duration.ofSeconds(2))).as("новый отказ").isTrue();
		advance(Duration.ofSeconds(59));
		assertThat(limiter.firstLoginRejection(login, Duration.ofSeconds(2))).as("не чаще раза в минуту").isFalse();
		advance(Duration.ofSeconds(1));
		assertThat(limiter.firstLoginRejection(login, Duration.ofSeconds(2))).isTrue();

		String concurrent = "gulnara." + UUID.randomUUID() + "@example.kz";
		List<Boolean> first = concurrently(20, () -> limiter.firstLoginRejection(concurrent, Duration.ofMinutes(15)));
		assertThat(first).filteredOn(Boolean::booleanValue).hasSize(1);
	}

	/** Логин без учёта регистра и крайних пробелов: DINARA@… и dinara@… — один счётчик. */
	@Test
	void loginIsNormalized() {
		RedisRateLimiter limiter = limiter();
		String login = "Aigerim." + UUID.randomUUID() + "@Example.kz";

		for (int i = 0; i < 5; i++) {
			limiter.recordLoginFailure((i % 2 == 0) ? login.toUpperCase() : "  " + login.toLowerCase() + " ");
		}

		loginBlocked(limiter, login);
	}

	/** Успешный вход обнуляет неверные попытки. */
	@Test
	void successfulLoginResetsFailures() {
		RedisRateLimiter limiter = limiter();
		String login = "askar." + UUID.randomUUID() + "@example.kz";
		for (int i = 0; i < 4; i++) {
			limiter.recordLoginFailure(login);
		}

		limiter.resetLogin(login);
		for (int i = 0; i < 4; i++) {
			limiter.recordLoginFailure(login);
		}

		assertThatNoException().isThrownBy(() -> limiter.checkLogin(login, ip()));
	}

	/** Не больше 30 попыток входа с одного IP в минуту, на любые логины. */
	@Test
	void loginAttemptsPerIpAreLimited() {
		RedisRateLimiter limiter = limiter();
		String ip = ip();

		for (int i = 0; i < 30; i++) {
			limiter.checkLogin("user" + i + "." + UUID.randomUUID() + "@example.kz", ip);
		}
		TooManyRequestsException blocked = catchThrowableOfType(TooManyRequestsException.class,
				() -> limiter.checkLogin("other." + UUID.randomUUID() + "@example.kz", ip));

		assertThat(blocked).isNotNull();
		assertThat(blocked.getCode()).isEqualTo(ErrorCodes.TOO_MANY_LOGIN_ATTEMPTS);
		assertThat(blocked.getRetryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1));
		assertThatNoException().isThrownBy(() -> limiter.checkLogin("other@example.kz", ip()));
	}

	/** Сброс пароля — не больше 3 писем в час на адрес; через час снова можно. */
	@Test
	void passwordResetIsThreePerHour() {
		RedisRateLimiter limiter = limiter();
		String email = "reset." + UUID.randomUUID() + "@example.kz";

		for (int i = 0; i < 3; i++) {
			limiter.checkPasswordReset(email);
		}
		TooManyRequestsException blocked = resetBlocked(limiter, email);

		assertThat(blocked.getCode()).isEqualTo(ErrorCodes.TOO_MANY_REQUESTS);
		assertThat(blocked.getRetryAfter()).isEqualTo(Duration.ofHours(1));
		assertThatNoException().isThrownBy(() -> limiter.checkPasswordReset("other." + email));

		advance(Duration.ofHours(1));
		assertThatNoException().isThrownBy(() -> limiter.checkPasswordReset(email));
	}

	/**
	 * Окно сброса скользящее: за любой час не больше 3 писем, и на стыке часов их не 6 подряд. Письмо в начале часа и
	 * два в конце — четвёртое через секунду после конца часа проходит только потому, что первое выпало из окна, пятое
	 * ждёт выхода второго.
	 */
	@Test
	void passwordResetWindowSlides() {
		RedisRateLimiter limiter = limiter();
		String email = "slide." + UUID.randomUUID() + "@example.kz";

		limiter.checkPasswordReset(email);
		advance(Duration.ofHours(1).minusSeconds(1));
		limiter.checkPasswordReset(email);
		limiter.checkPasswordReset(email);
		assertThat(resetBlocked(limiter, email).getRetryAfter()).isEqualTo(Duration.ofSeconds(1));

		advance(Duration.ofSeconds(1));
		limiter.checkPasswordReset(email);

		assertThat(resetBlocked(limiter, email).getRetryAfter()).isEqualTo(Duration.ofHours(1).minusSeconds(1));
	}

	/** Логин, e-mail и IP в Valkey только хэшем: ключи не содержат ни адреса, ни IP. */
	@Test
	void keysAreHashed() {
		RedisRateLimiter limiter = limiter();
		String email = "hash." + UUID.randomUUID() + "@example.kz";
		String locked = "lock." + UUID.randomUUID() + "@example.kz";
		String ip = ip();

		limiter.checkLogin(email, ip);
		limiter.recordLoginFailure(email);
		limiter.firstLoginRejection(email, Duration.ofMinutes(1));
		limiter.checkPasswordReset(email);
		for (int i = 0; i < 5; i++) {
			limiter.recordLoginFailure(locked);
		}

		Set<String> keys = this.redis.keys(RedisRateLimiter.KEY_PREFIX + "*");
		assertThat(keys).isNotEmpty()
			.contains(RedisRateLimiter.key(RedisRateLimiter.LOGIN_FAILURES, email),
					RedisRateLimiter.key(RedisRateLimiter.PASSWORD_RESETS, email),
					RedisRateLimiter.key(RedisRateLimiter.LOGIN_REJECTED, email),
					RedisRateLimiter.key(RedisRateLimiter.LOGIN_IP, ip),
					RedisRateLimiter.key(RedisRateLimiter.LOGIN_LOCK, locked))
			.doesNotContain(RedisRateLimiter.key(RedisRateLimiter.LOGIN_FAILURES, locked))
			.noneMatch((key) -> key.contains("@") || key.contains(email) || key.contains(ip));
		assertThat(this.redis.getExpire(RedisRateLimiter.key(RedisRateLimiter.LOGIN_LOCK, locked))).as("TTL блокировки")
			.isPositive();
	}

	/**
	 * Приёмка PLT-05 по HTTP: после пяти неверных паролей шестая попытка, даже с верным паролем, — 429 ProblemDetail
	 * {@code error.too-many-login-attempts} с Retry-After на 15 минут и числом минут в тексте.
	 */
	@Test
	void sixthLoginAttemptIsTooManyRequestsProblem() {
		String login = "timur." + UUID.randomUUID() + "@example.kz";
		String client = testClientIp();
		String xsrf = csrfToken(client);

		for (int i = 0; i < 5; i++) {
			expectProblem(loginAttempt(login, false, client, xsrf), 401, ErrorCodes.UNAUTHORIZED);
		}
		JsonNode problem = expectProblem(loginAttempt(login, true, client, xsrf), 429,
				ErrorCodes.TOO_MANY_LOGIN_ATTEMPTS);

		assertThat(problem.path("detail").asString()).isEqualTo("Слишком много попыток, попробуйте через 15 мин.");
		EntityExchangeResult<byte[]> again = loginAttempt(login, true, client, xsrf).expectBody().returnResult();
		assertThat(again.getStatus().value()).isEqualTo(429);
		assertThat(Long.parseLong(again.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER))).as("Retry-After, с")
			.isBetween(840L, 900L);
	}

	/**
	 * Приёмка PLT-05 по HTTP при одновременных попытках: 20 неверных паролей на один логин разом с 20 разных IP — ровно
	 * пять доходят до проверки пароля (401), остальные — 429 {@code error.too-many-login-attempts}.
	 */
	@Test
	void concurrentLoginAttemptsFromManyIpsCheckPasswordFiveTimes() throws Exception {
		String login = "zhanna." + UUID.randomUUID() + "@example.kz";
		String xsrf = csrfToken();
		AtomicInteger clients = new AtomicInteger();

		List<Integer> statuses = parallel(20,
				() -> loginAttempt(login, false, "192.0.2." + clients.incrementAndGet(), xsrf).expectBody()
					.returnResult()
					.getStatus()
					.value());

		assertThat(statuses).filteredOn((status) -> status == 401).hasSize(5);
		assertThat(statuses).filteredOn((status) -> status == 429).hasSize(15);
		expectProblem(loginAttempt(login, true, testClientIp(), xsrf), 429, ErrorCodes.TOO_MANY_LOGIN_ATTEMPTS);
	}

	/**
	 * Общий лимит API — 600 запросов в минуту на сессию: первые 600 проходят, дальше — 429 ProblemDetail с
	 * {@code Retry-After}.
	 */
	@Test
	void apiRequestsPerSessionAreLimited() throws Exception {
		TestSession session = signIn(UUID.randomUUID());
		TestSession neighbour = signIn(UUID.randomUUID());
		long started = System.nanoTime();

		List<Integer> statuses = parallel(600, () -> csrf(session.cookies(), null).getStatus().value());
		assertThat(statuses).containsOnly(204);
		EntityExchangeResult<byte[]> exceeded = firstRejected(session.cookies(), null, started);

		assertThat(exceeded.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(Long.parseLong(exceeded.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isBetween(1L, 60L);
		JsonNode problem = JsonMapper.builder()
			.build()
			.readTree(new String(exceeded.getResponseBody(), StandardCharsets.UTF_8));
		assertThat(problem.path("code").asString()).isEqualTo(ErrorCodes.TOO_MANY_REQUESTS);
		assertThat(problem.path("detail").asString()).isEqualTo("Слишком много запросов. Подождите немного и повторите.");
		assertThat(csrf(neighbour.cookies(), null).getStatus().value()).as("соседняя сессия").isEqualTo(204);
	}

	/** Без сессии общий лимит API — на IP: за Caddy это адрес клиента из X-Forwarded-For. */
	@Test
	void apiRequestsWithoutSessionAreLimitedPerIp() throws Exception {
		String client = "203.0.113.10";
		long started = System.nanoTime();

		List<Integer> statuses = parallel(600, () -> csrf(null, client).getStatus().value());
		assertThat(statuses).containsOnly(204);
		EntityExchangeResult<byte[]> exceeded = firstRejected(null, client, started);

		assertThat(exceeded.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
		assertThat(csrf(null, "203.0.113.11").getStatus().value()).as("другой IP").isEqualTo(204);
	}

	/**
	 * Запросы после исчерпания корзины до первого 429. Корзина сглаженная, 10 запросов в секунду возвращаются, поэтому
	 * до отказа возможны ещё несколько успешных — не больше, чем вернулось за время теста.
	 */
	private EntityExchangeResult<byte[]> firstRejected(String cookies, String clientIp, long started) {
		int extra = 0;
		while (true) {
			EntityExchangeResult<byte[]> result = csrf(cookies, clientIp);
			if (result.getStatus().value() == 429) {
				return result;
			}
			assertThat(result.getStatus().value()).isEqualTo(204);
			extra++;
			assertThat(extra).as("успешных сверх 600").isLessThanOrEqualTo(allowedRefill(started));
		}
	}

	private EntityExchangeResult<byte[]> csrf(String cookies, String clientIp) {
		return this.http.get().uri(CSRF).headers((headers) -> {
			if (cookies != null) {
				headers.add(HttpHeaders.COOKIE, cookies);
			}
			if (clientIp != null) {
				headers.add(FORWARDED_FOR, clientIp);
			}
		}).exchange().expectBody().returnResult();
	}

	/** Попытка входа через пробный эндпоинт: лимиты входа до проверки пароля, как у входа T-IAM-02. */
	private ResponseSpec loginAttempt(String login, boolean passwordMatches, String clientIp, String xsrf) {
		return this.http.post()
			.uri(SignInProbeController.LOGIN_ATTEMPT)
			.header(HttpHeaders.COOKIE, XSRF_COOKIE + "=" + xsrf)
			.header(XSRF_HEADER, xsrf)
			.header(FORWARDED_FOR, clientIp)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("login", login, "passwordMatches", passwordMatches))
			.exchange();
	}

	/** Сколько запросов корзина вернула с начала теста: 600 в минуту — 10 в секунду, с запасом на секунду. */
	private static int allowedRefill(long startedNanos) {
		long elapsedSeconds = Duration.ofNanos(System.nanoTime() - startedNanos).toSeconds() + 1;
		return (int) (elapsedSeconds * 10 + 10);
	}

	private static TooManyRequestsException loginBlocked(RedisRateLimiter limiter, String login) {
		TooManyRequestsException blocked = catchThrowableOfType(TooManyRequestsException.class,
				() -> limiter.checkLogin(login, ip()));
		assertThat(blocked).as("вход на логин закрыт").isNotNull();
		assertThat(blocked.getCode()).isEqualTo(ErrorCodes.TOO_MANY_LOGIN_ATTEMPTS);
		return blocked;
	}

	private static TooManyRequestsException resetBlocked(RedisRateLimiter limiter, String email) {
		TooManyRequestsException blocked = catchThrowableOfType(TooManyRequestsException.class,
				() -> limiter.checkPasswordReset(email));
		assertThat(blocked).as("сброс пароля отклонён").isNotNull();
		return blocked;
	}

	/** Лимитер с часами теста: у каждого теста свои логины и IP, счётчики не пересекаются. */
	private RedisRateLimiter limiter() {
		TimeMeter clock = new TimeMeter() {

			@Override
			public long currentTimeNanos() {
				return RateLimitTests.this.nanos.get();
			}

			@Override
			public boolean isWallClockBased() {
				return true;
			}

		};
		RedisRateLimiter limiter = new RedisRateLimiter(this.connectionFactory, this.redis,
				new SecurityProperties().getRateLimit(), clock);
		this.limiters.add(limiter);
		return limiter;
	}

	private void advance(Duration duration) {
		this.nanos.addAndGet(duration.toNanos());
	}

	/** Ключ клиента для лимитера: у каждого вызова свой, счётчики тестов не пересекаются. */
	private static String ip() {
		return "client-" + UUID.randomUUID();
	}

	/** Вызовы разом: все потоки ждут общего старта, чтобы попытки шли одновременно. */
	private static <T> List<T> concurrently(int count, Callable<T> call) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		List<Future<T>> futures = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(count)) {
			for (int i = 0; i < count; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					return call.call();
				}));
			}
			start.countDown();
		}
		List<T> results = new ArrayList<>();
		for (Future<T> future : futures) {
			results.add(future.get());
		}
		return results;
	}

	private static List<Integer> parallel(int count, java.util.concurrent.Callable<Integer> call) throws Exception {
		List<Future<Integer>> futures = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
			for (int i = 0; i < count; i++) {
				futures.add(pool.submit(call));
			}
		}
		List<Integer> statuses = new ArrayList<>();
		for (Future<Integer> future : futures) {
			statuses.add(future.get());
		}
		return statuses;
	}

}
