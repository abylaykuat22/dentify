package kz.dentify.platform.security;

import java.nio.file.Path;
import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Настройки безопасности, {@code dentify.security}. Значения по умолчанию — требования PLT-04, PLT-05 и PLT-06; сессия
 * и её cookie настраиваются стандартными свойствами {@code server.servlet.session.*} и {@code spring.session.*}.
 */
@ConfigurationProperties("dentify.security")
public class SecurityProperties {

	/**
	 * Срок сессии и её cookie при входе с галочкой Запомнить — от входа, активность его не продлевает. Сервер гасит
	 * сессию по этому сроку сам ({@link SessionLifetimeFilter}), cookie получает тот же {@code Max-Age}.
	 */
	private Duration rememberMe = Duration.ofDays(30);

	/**
	 * Предельный срок сессии без Запомнить от входа, даже при непрерывной активности: кроме 12 часов бездействия
	 * ({@code server.servlet.session.timeout}). Не дольше, чем с Запомнить: украденная cookie не живёт бессрочно.
	 */
	private Duration sessionMaxAge = Duration.ofDays(30);

	/**
	 * Content-Security-Policy ответов бэкенда. Для страниц SPA тот же заголовок ставит Caddy. Скрипты — только со своего
	 * origin, без unsafe-inline; встраивание во фреймы запрещено.
	 */
	private String contentSecurityPolicy = "default-src 'self'; script-src 'self'; style-src 'self'; "
			+ "img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; "
			+ "form-action 'self'; frame-ancestors 'none'";

	/** Permissions-Policy: возможности браузера, которые приложению не нужны, выключены. */
	private String permissionsPolicy = "accelerometer=(), autoplay=(), camera=(), display-capture=(), "
			+ "geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), usb=()";

	/** Strict-Transport-Security: срок, в течение которого браузер ходит только по HTTPS. */
	private Duration hstsMaxAge = Duration.ofDays(365);

	private final GeoIp geoip = new GeoIp();

	private final RateLimit rateLimit = new RateLimit();

	private final PasswordHashing passwordHashing = new PasswordHashing();

	public Duration getRememberMe() {
		return this.rememberMe;
	}

	public void setRememberMe(Duration rememberMe) {
		this.rememberMe = rememberMe;
	}

	public Duration getSessionMaxAge() {
		return this.sessionMaxAge;
	}

	public void setSessionMaxAge(Duration sessionMaxAge) {
		this.sessionMaxAge = sessionMaxAge;
	}

	public String getContentSecurityPolicy() {
		return this.contentSecurityPolicy;
	}

	public void setContentSecurityPolicy(String contentSecurityPolicy) {
		this.contentSecurityPolicy = contentSecurityPolicy;
	}

	public String getPermissionsPolicy() {
		return this.permissionsPolicy;
	}

	public void setPermissionsPolicy(String permissionsPolicy) {
		this.permissionsPolicy = permissionsPolicy;
	}

	public Duration getHstsMaxAge() {
		return this.hstsMaxAge;
	}

	public void setHstsMaxAge(Duration hstsMaxAge) {
		this.hstsMaxAge = hstsMaxAge;
	}

	public GeoIp getGeoip() {
		return this.geoip;
	}

	public RateLimit getRateLimit() {
		return this.rateLimit;
	}

	public PasswordHashing getPasswordHashing() {
		return this.passwordHashing;
	}

	/** Локальная GeoIP-база города для списка сессий. */
	public static class GeoIp {

		/**
		 * Путь к файлу базы в формате mmdb, например DB-IP IP to City Lite; относительный — от рабочего каталога. Не
		 * задан или файла нет — город сессий не определяется, приложение работает. База не коммитится, как её
		 * получить — backend/README.md. Строка, а не Path: конвертер Spring понял бы путь как ресурс и не принял бы
		 * ../ в начале.
		 */
		private @Nullable String database;

		public @Nullable String getDatabase() {
			return this.database;
		}

		public void setDatabase(@Nullable String database) {
			this.database = database;
		}

		/** Файл базы или null, если путь не задан. */
		public @Nullable Path databasePath() {
			return StringUtils.hasText(this.database) ? Path.of(this.database.strip()) : null;
		}

	}

	/**
	 * Предел одновременных вычислений хэша пароля ({@link BoundedPasswordEncoder}): Argon2id берёт 19 МиБ памяти на
	 * каждое, а виртуальные потоки не ограничивают число одновременных запросов.
	 */
	public static class PasswordHashing {

		/**
		 * Сколько хэшей пароля считается одновременно, остальные ждут. Не задано — по числу ядер процессора: хэш
		 * занимает ядро целиком, больше одновременных вычислений быстрее не станет. Память — 19 МиБ на каждое.
		 */
		private @Nullable Integer concurrency;

		/** Сколько запрос ждёт места для хэша; не дождался — 429 с Retry-After. */
		private Duration maxWait = Duration.ofSeconds(3);

		public @Nullable Integer getConcurrency() {
			return this.concurrency;
		}

		public void setConcurrency(@Nullable Integer concurrency) {
			this.concurrency = concurrency;
		}

		public Duration getMaxWait() {
			return this.maxWait;
		}

		public void setMaxWait(Duration maxWait) {
			this.maxWait = maxWait;
		}

		/** Предел с учётом значения по умолчанию: заданный или число ядер процессора. */
		public int effectiveConcurrency() {
			return (this.concurrency != null) ? this.concurrency : Runtime.getRuntime().availableProcessors();
		}

	}

	/** Лимиты частоты, PLT-05. Лимит — ёмкость и период. */
	public static class RateLimit {

		/**
		 * Неверные попытки входа на логин за скользящее окно: набралось столько за любой такой период — вход закрыт на
		 * {@link #loginLock}.
		 */
		private Limit loginFailures = new Limit(5, Duration.ofMinutes(15));

		/** Блокировка входа на логин после лимита неверных попыток, от последней из них. */
		private Duration loginLock = Duration.ofMinutes(15);

		/** Попытки входа с одного IP. */
		private Limit loginIp = new Limit(30, Duration.ofMinutes(1));

		/** Запросы сброса пароля на один адрес за скользящее окно. */
		private Limit passwordReset = new Limit(3, Duration.ofHours(1));

		/** Общий API на сессию, без сессии — на IP. */
		private Limit api = new Limit(600, Duration.ofMinutes(1));

		public Limit getLoginFailures() {
			return this.loginFailures;
		}

		public void setLoginFailures(Limit loginFailures) {
			this.loginFailures = loginFailures;
		}

		public Duration getLoginLock() {
			return this.loginLock;
		}

		public void setLoginLock(Duration loginLock) {
			this.loginLock = loginLock;
		}

		public Limit getLoginIp() {
			return this.loginIp;
		}

		public void setLoginIp(Limit loginIp) {
			this.loginIp = loginIp;
		}

		public Limit getPasswordReset() {
			return this.passwordReset;
		}

		public void setPasswordReset(Limit passwordReset) {
			this.passwordReset = passwordReset;
		}

		public Limit getApi() {
			return this.api;
		}

		public void setApi(Limit api) {
			this.api = api;
		}

	}

	/**
	 * Лимит: не больше {@code capacity} событий за {@code period}.
	 *
	 * @param capacity сколько событий разрешено
	 * @param period за какой период
	 */
	public record Limit(int capacity, Duration period) {

	}

}
