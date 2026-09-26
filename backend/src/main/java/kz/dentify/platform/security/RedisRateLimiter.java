package kz.dentify.platform.security;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import kz.dentify.platform.api.ErrorCodes;
import kz.dentify.platform.api.Hashes;
import kz.dentify.platform.api.TooManyRequestsException;
import kz.dentify.platform.identity.RateLimits;
import kz.dentify.platform.security.SecurityProperties.Limit;
import kz.dentify.platform.security.SecurityProperties.RateLimit;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Лимиты частоты в Valkey, общие для всех экземпляров приложения. Два вида:
 * <ul>
 * <li>частота — попытки входа с IP и общий API: Bucket4j, официальная интеграция {@code bucket4j-lettuce}, корзины через
 * compare-and-swap, сглаженное пополнение {@code refillGreedy}: всплеск до лимита, потом ровный поток. Подключение —
 * отдельное соединение Lettuce того же клиента, что у Spring Data Redis: адрес, пароль и TLS из
 * {@code spring.data.redis.*};</li>
 * <li>не больше N событий за любой период — неверные попытки входа на логин и письма сброса пароля: скользящее окно,
 * журнал времён событий в sorted set, Lua-скрипт через {@code StringRedisTemplate}. У Bucket4j корзины, а не журнал:
 * {@code refillIntervally} считает окна от создания корзины, и на стыке двух окон проходило бы вдвое больше лимита.
 * Набралось 5 неверных попыток за 15 минут — вход на логин закрыт на 15 минут от пятой, ключ блокировки хранит её
 * конец.</li>
 * </ul>
 * <p>
 * Проверка входа занимает место в журнале логина атомарно, до проверки пароля: в журнале и неверные попытки, и те, что
 * ещё идут. Поэтому одновременные попытки на один логин, с одного IP или с разных, не проходят проверку разом: пока нет
 * успешного входа, пароль логина за любые 15 минут проверяется не больше 5 раз.
 * <p>
 * Ключ — {@code dentify:rate:<лимит>:<SHA-256 значения>}: логин, e-mail, IP и id сессии в Valkey в открытом виде не
 * лежат. Ключ живёт, пока нужен лимиту, дальше Valkey его удаляет. Время — часы приложения ({@code TimeMeter}): и у
 * корзин Bucket4j, и в журналах.
 */
class RedisRateLimiter implements RateLimits, DisposableBean {

	static final String KEY_PREFIX = "dentify:rate:";

	static final String LOGIN_IP = "login-ip";

	static final String LOGIN_FAILURES = "login-failures";

	static final String LOGIN_LOCK = "login-lock";

	static final String LOGIN_REJECTED = "login-rejected";

	static final String PASSWORD_RESETS = "password-resets";

	static final String API = "api";

	/**
	 * Скользящее окно: событие засчитывается, если за окно до него их меньше лимита. Ответ — 0, если засчитано, иначе
	 * сколько миллисекунд до выхода из окна самого старого события.
	 * <p>
	 * KEYS[1] — журнал; ARGV — сейчас, окно, лимит (мс, мс, штук), уникальная метка события.
	 */
	static final RedisScript<Long> ACQUIRE = RedisScript.of("""
			local now = tonumber(ARGV[1])
			local window = tonumber(ARGV[2])
			redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
			if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[3]) then
				local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
				return tonumber(oldest[2]) + window - now
			end
			redis.call('ZADD', KEYS[1], now, ARGV[4])
			redis.call('PEXPIRE', KEYS[1], window)
			return 0
			""", Long.class);

	/**
	 * Попытка входа занимает место в журнале логина до проверки пароля. Метка места — {@code p:<id>}: попытка идёт; она
	 * становится неверной ({@code f:<id>}) в {@link #RECORD_FAILURE} и уходит из журнала в {@link #RESET_LOGIN}. Ответ — 0,
	 * если место занято, иначе сколько миллисекунд ждать: до конца блокировки или, если журнал полон, до выхода из окна
	 * самой старой записи. Попытка, которая не закончилась ни неверным паролем, ни входом (исключение), держит место до
	 * выхода из окна.
	 * <p>
	 * KEYS[1] — журнал, KEYS[2] — блокировка; ARGV — сейчас, окно, лимит (мс, мс, штук), уникальная метка попытки.
	 */
	static final RedisScript<Long> RESERVE_LOGIN = RedisScript.of("""
			local now = tonumber(ARGV[1])
			local window = tonumber(ARGV[2])
			local lockedUntil = redis.call('GET', KEYS[2])
			if lockedUntil then
				local wait = tonumber(lockedUntil) - now
				if wait > 0 then
					return wait
				end
			end
			redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
			if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[3]) then
				local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
				return math.max(1, tonumber(oldest[2]) + window - now)
			end
			redis.call('ZADD', KEYS[1], now, 'p:' .. ARGV[4])
			redis.call('PEXPIRE', KEYS[1], window)
			return 0
			""", Long.class);

	/**
	 * Неверная попытка входа: место одной идущей попытки в журнале (если есть) становится неверной попыткой с текущим
	 * временем; неверных за окно набралось до лимита — журнал с нуля и блокировка, значение ключа — её конец. Идущие
	 * попытки в счёт блокировки не входят: она ставится на пятой неверной, а не на первой из пяти одновременных. Ответ —
	 * 1, если попытка закрыла вход.
	 * <p>
	 * KEYS[1] — журнал, KEYS[2] — блокировка; ARGV — сейчас, окно, лимит, блокировка (мс), конец блокировки (мс),
	 * уникальная метка попытки.
	 */
	static final RedisScript<Long> RECORD_FAILURE = RedisScript.of("""
			local now = tonumber(ARGV[1])
			local window = tonumber(ARGV[2])
			redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
			for _, member in ipairs(redis.call('ZRANGE', KEYS[1], 0, -1)) do
				if string.sub(member, 1, 2) == 'p:' then
					redis.call('ZREM', KEYS[1], member)
					break
				end
			end
			redis.call('ZADD', KEYS[1], now, 'f:' .. ARGV[6])
			local failures = 0
			for _, member in ipairs(redis.call('ZRANGE', KEYS[1], 0, -1)) do
				if string.sub(member, 1, 2) == 'f:' then
					failures = failures + 1
				end
			end
			if failures >= tonumber(ARGV[3]) then
				redis.call('DEL', KEYS[1])
				redis.call('SET', KEYS[2], ARGV[5], 'PX', ARGV[4])
				return 1
			end
			redis.call('PEXPIRE', KEYS[1], window)
			return 0
			""", Long.class);

	/**
	 * Успешный вход: неверные попытки логина обнуляются, место этой попытки освобождается. Места других идущих попыток
	 * остаются: и после успешного входа пароль логина одновременно проверяется не больше лимита раз.
	 * <p>
	 * KEYS[1] — журнал.
	 */
	static final RedisScript<Long> RESET_LOGIN = RedisScript.of("""
			local pendingReleased = false
			for _, member in ipairs(redis.call('ZRANGE', KEYS[1], 0, -1)) do
				if string.sub(member, 1, 2) == 'f:' then
					redis.call('ZREM', KEYS[1], member)
				elseif not pendingReleased then
					redis.call('ZREM', KEYS[1], member)
					pendingReleased = true
				end
			end
			return 0
			""", Long.class);

	/**
	 * Попытка входа, не проверившая пароль (429 предела хэшей): освобождается место одной идущей попытки, неверные
	 * попытки остаются. Ответ — 1, если место было.
	 * <p>
	 * KEYS[1] — журнал.
	 */
	static final RedisScript<Long> ABANDON_LOGIN = RedisScript.of("""
			for _, member in ipairs(redis.call('ZRANGE', KEYS[1], 0, -1)) do
				if string.sub(member, 1, 2) == 'p:' then
					redis.call('ZREM', KEYS[1], member)
					return 1
				end
			end
			return 0
			""", Long.class);

	/**
	 * Первый отказ во входе на логин за время отказа: ключ хранит конец этого времени (мс, часы приложения). Ответ — 1,
	 * если отказ первый; тогда время отказа начинается заново.
	 * <p>
	 * KEYS[1] — отметка; ARGV — сейчас, время отказа, его конец (мс).
	 */
	static final RedisScript<Long> FIRST_REJECTION = RedisScript.of("""
			local untilMillis = redis.call('GET', KEYS[1])
			if untilMillis and tonumber(untilMillis) > tonumber(ARGV[1]) then
				return 0
			end
			redis.call('SET', KEYS[1], ARGV[3], 'PX', ARGV[2])
			return 1
			""", Long.class);

	/** Отказы во входе на логин в журнал входов пишутся не чаще раза за это время. */
	static final Duration MIN_REJECTION_JOURNAL_WINDOW = Duration.ofMinutes(1);

	private final RateLimit limits;

	private final TimeMeter clock;

	private final LettuceConnectionFactory connectionFactory;

	private final StringRedisTemplate redis;

	/** Соединение Bucket4j открывается при первом обращении: к старту фабрика Spring Data Redis уже запущена. */
	private volatile @Nullable Connection connection;

	RedisRateLimiter(LettuceConnectionFactory connectionFactory, StringRedisTemplate redis, RateLimit limits,
			TimeMeter clock) {
		this.connectionFactory = connectionFactory;
		this.redis = redis;
		this.limits = limits;
		this.clock = clock;
	}

	@Override
	public void checkLogin(String login, String clientIp) {
		ConsumptionProbe ip = bucket(LOGIN_IP, clientIp, greedy(this.limits.getLoginIp()))
			.tryConsumeAndReturnRemaining(1);
		if (!ip.isConsumed()) {
			throw loginBlocked(ip.getNanosToWaitForRefill());
		}
		Limit failures = this.limits.getLoginFailures();
		Long waitMillis = this.redis.execute(RESERVE_LOGIN, loginKeys(normalize(login)), String.valueOf(nowMillis()),
				String.valueOf(failures.period().toMillis()), String.valueOf(failures.capacity()), eventId());
		if (waitMillis != null && waitMillis > 0) {
			throw loginBlocked(TimeUnit.MILLISECONDS.toNanos(waitMillis));
		}
	}

	@Override
	public boolean recordLoginFailure(String login) {
		Limit failures = this.limits.getLoginFailures();
		long lockMillis = this.limits.getLoginLock().toMillis();
		long now = nowMillis();
		Long locked = this.redis.execute(RECORD_FAILURE, loginKeys(normalize(login)), String.valueOf(now),
				String.valueOf(failures.period().toMillis()), String.valueOf(failures.capacity()),
				String.valueOf(lockMillis), String.valueOf(now + lockMillis), eventId());
		return locked != null && locked == 1L;
	}

	@Override
	public void resetLogin(String login) {
		this.redis.execute(RESET_LOGIN, List.of(key(LOGIN_FAILURES, normalize(login))));
	}

	@Override
	public void abandonLogin(String login) {
		this.redis.execute(ABANDON_LOGIN, List.of(key(LOGIN_FAILURES, normalize(login))));
	}

	@Override
	public boolean firstLoginRejection(String login, Duration retryAfter) {
		Duration window = (retryAfter.compareTo(MIN_REJECTION_JOURNAL_WINDOW) > 0) ? retryAfter
				: MIN_REJECTION_JOURNAL_WINDOW;
		long now = nowMillis();
		Long first = this.redis.execute(FIRST_REJECTION, List.of(key(LOGIN_REJECTED, normalize(login))),
				String.valueOf(now), String.valueOf(window.toMillis()), String.valueOf(now + window.toMillis()));
		return first != null && first == 1L;
	}

	@Override
	public void checkPasswordReset(String email) {
		Limit resets = this.limits.getPasswordReset();
		Long waitMillis = this.redis.execute(ACQUIRE, List.of(key(PASSWORD_RESETS, normalize(email))),
				String.valueOf(nowMillis()), String.valueOf(resets.period().toMillis()),
				String.valueOf(resets.capacity()), eventId());
		if (waitMillis != null && waitMillis > 0) {
			throw new TooManyRequestsException(retryAfter(TimeUnit.MILLISECONDS.toNanos(waitMillis)));
		}
	}

	/**
	 * Засчитать запрос API: по сессии, если она есть, иначе по IP.
	 *
	 * @return пусто, если лимит не превышен, иначе через сколько повторить
	 */
	@Nullable Duration consumeApi(String client) {
		ConsumptionProbe probe = bucket(API, client, greedy(this.limits.getApi())).tryConsumeAndReturnRemaining(1);
		return probe.isConsumed() ? null : retryAfter(probe.getNanosToWaitForRefill());
	}

	@Override
	public void destroy() {
		Connection open = this.connection;
		if (open != null) {
			this.connection = null;
			open.connection().close();
		}
	}

	private long nowMillis() {
		return TimeUnit.NANOSECONDS.toMillis(this.clock.currentTimeNanos());
	}

	private Connection connection() {
		Connection open = this.connection;
		if (open == null) {
			synchronized (this) {
				open = this.connection;
				if (open == null) {
					open = connect(this.connectionFactory, this.clock);
					this.connection = open;
				}
			}
		}
		return open;
	}

	private BucketProxy bucket(String limit, String value, Supplier<BucketConfiguration> configuration) {
		return connection().proxies().getProxy(key(limit, value), configuration);
	}

	private TooManyRequestsException loginBlocked(long nanosToWait) {
		Duration retryAfter = retryAfter(nanosToWait);
		long minutes = Math.max(1, (retryAfter.toSeconds() + 59) / 60);
		return new TooManyRequestsException(retryAfter, ErrorCodes.TOO_MANY_LOGIN_ATTEMPTS, minutes);
	}

	/** Через сколько повторить, с округлением вверх до секунды: заголовок Retry-After — в целых секундах. */
	static Duration retryAfter(long nanos) {
		long seconds = Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);
		return Duration.ofSeconds(seconds);
	}

	static String key(String limit, String value) {
		return KEY_PREFIX + limit + ":" + Hashes.sha256Base64Url(value);
	}

	/** Журнал попыток и блокировка логина. */
	private static List<String> loginKeys(String normalizedLogin) {
		return List.of(key(LOGIN_FAILURES, normalizedLogin), key(LOGIN_LOCK, normalizedLogin));
	}

	private static String normalize(String value) {
		return value.strip().toLowerCase(Locale.ROOT);
	}

	/** Метка события в журнале: у двух событий в одну миллисекунду разные метки, иначе второе затёрло бы первое. */
	private static String eventId() {
		return UUID.randomUUID().toString();
	}

	private static Supplier<BucketConfiguration> greedy(Limit limit) {
		return () -> BucketConfiguration.builder()
			.addLimit((bandwidth) -> bandwidth.capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.period()))
			.build();
	}

	private static Connection connect(LettuceConnectionFactory connectionFactory, TimeMeter clock) {
		AbstractRedisClient client = connectionFactory.getRequiredNativeClient();
		if (!(client instanceof RedisClient redis)) {
			throw new IllegalStateException("Лимиты рассчитаны на одиночный Valkey, а клиент — "
					+ client.getClass().getSimpleName());
		}
		StatefulRedisConnection<String, byte[]> connection = redis
			.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
		LettuceBasedProxyManager<String> proxies = Bucket4jLettuce.casBasedBuilder(connection)
			.clientClock(clock)
			.expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
			.build();
		return new Connection(connection, proxies);
	}

	private record Connection(StatefulRedisConnection<String, byte[]> connection,
			LettuceBasedProxyManager<String> proxies) {
	}

}
