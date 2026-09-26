package kz.dentify.platform.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpSession;
import kz.dentify.platform.api.Hashes;
import org.jspecify.annotations.Nullable;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;

/**
 * Необратимое завершение сессий (PLT-04). {@code deleteById} Spring Session сессию из Valkey не удаляет: он ставит ей
 * бездействие 0 и убирает её из индекса аккаунта. Запрос этой сессии, который шёл в тот момент, в конце записывает свои
 * изменения поверх: новое бездействие оживило бы сессию, контекст безопасности вернул бы её в индекс. Поэтому до
 * удаления в Valkey пишется отметка отзыва, и {@link SessionRevocationFilter} проверяет её на каждом запросе с сессией
 * (одна команда {@code MGET}):
 * <ul>
 * <li>отметка сессии {@code dentify:session-revoked:session:<SHA-256 id>} — выход, завершение одной сессии и каждой
 * сессии, найденной в индексе при завершении всех. Живёт до срока сессии от входа и ещё {@link #MARGIN}: после срока
 * сессию гасит {@link SessionLifetimeFilter}, время входа в сессии меняет только новый вход, а он меняет и id;</li>
 * <li>отметка аккаунта {@code dentify:session-revoked:account:<id аккаунта>} — завершение всех и всех, кроме текущей:
 * время T и хэш оставленной сессии. Недействительна любая сессия аккаунта со входом не позже T, кроме оставленной, в
 * том числе та, которой в индексе не было: её оживил запрос в полёте до этой защиты или её вход шёл одновременно с
 * завершением и получил время входа не позже T. Живёт самый долгий срок сессии от входа и ещё {@link #MARGIN}: к этому
 * времени все такие сессии истекли. Новое завершение заменяет отметку: его T позже, оставленная сессия — его.</li>
 * </ul>
 * T и время входа в сессию ({@link #now()}) берутся с одних часов — Valkey, а не экземпляра приложения: сравнение
 * входа с отметкой не зависит от расхождения часов экземпляров. Вход, у которого пароль проверен до завершения, а время
 * входа поставлено после T, отметка аккаунта не гасит: это закрывает вход T-IAM-02 повторной проверкой аккаунта после
 * входа (docs/architecture.md, Активные сессии).
 * <p>
 * Id сессии в ключах и значениях — только SHA-256 в base64url: id — пароль на время сессии.
 */
class SessionRevocations {

	static final String SESSION_KEY_PREFIX = "dentify:session-revoked:session:";

	static final String ACCOUNT_KEY_PREFIX = "dentify:session-revoked:account:";

	/** Запас к сроку отметки: часы экземпляров приложения расходятся, ключ сессии живёт чуть дольше её срока. */
	static final Duration MARGIN = Duration.ofMinutes(1);

	private static final String REVOKED = "1";

	private static final char KEPT_SEPARATOR = ' ';

	private final StringRedisTemplate redis;

	private final SessionLifetime lifetime;

	private final Clock clock;

	SessionRevocations(StringRedisTemplate redis, SessionLifetime lifetime, Clock clock) {
		this.redis = redis;
		this.lifetime = lifetime;
		this.clock = clock;
	}

	/** Отозвать сессию запроса: выход или завершение текущей. */
	void revoke(HttpSession session) {
		boolean rememberMe = Boolean.TRUE.equals(session.getAttribute(SessionClients.REMEMBER_ME));
		revoke(session.getId(), signedInAt(session), rememberMe);
	}

	/** Отозвать сессию из хранилища: завершение со списка или при завершении всех. */
	void revoke(Session session) {
		revoke(session.getId(), SessionClients.signedInAt(session), SessionClients.rememberMe(session));
	}

	private void revoke(String sessionId, Instant signedInAt, boolean rememberMe) {
		Duration untilDeadline = Duration.between(this.clock.instant(), this.lifetime.deadline(signedInAt, rememberMe));
		Duration ttl = untilDeadline.isNegative() ? MARGIN : untilDeadline.plus(MARGIN);
		this.redis.opsForValue().set(sessionKey(sessionId), REVOKED, ttl);
	}

	/**
	 * Сейчас по часам Valkey (команда {@code TIME}): одни часы для всех экземпляров приложения. По ним ставятся время входа
	 * в сессию и T отметки аккаунта.
	 */
	Instant now() {
		Long micros = this.redis
			.execute((RedisCallback<Long>) (connection) -> connection.serverCommands().time(TimeUnit.MICROSECONDS));
		if (micros == null) {
			throw new IllegalStateException("TIME Valkey не вернул время");
		}
		return Instant.EPOCH.plus(micros, ChronoUnit.MICROS);
	}

	/**
	 * Отозвать все сессии аккаунта со входом не позже этого момента ({@link #now()}), кроме {@code keptSessionId}:
	 * завершение всех (деактивация, выход везде) и всех, кроме текущей (смена пароля).
	 *
	 * @param keptSessionId настоящий id сессии, которая остаётся; null — не остаётся ни одной
	 */
	void revokeAccount(UUID accountId, @Nullable String keptSessionId) {
		String value = now().toString()
				+ ((keptSessionId != null) ? KEPT_SEPARATOR + digest(keptSessionId) : "");
		this.redis.opsForValue().set(accountKey(accountId.toString()), value, this.lifetime.longest().plus(MARGIN));
	}

	/** Сессия запроса отозвана: её отметкой или отметкой её аккаунта. */
	boolean isRevoked(HttpSession session) {
		return !revoked(account(session), Map.of(session.getId(), signedInAt(session))).isEmpty();
	}

	/** Id отозванных сессий аккаунта из найденных по индексу — одним запросом к Valkey. */
	Set<String> revoked(UUID accountId, Collection<? extends Session> sessions) {
		Map<String, Instant> signedInAt = new LinkedHashMap<>();
		for (Session session : sessions) {
			signedInAt.put(session.getId(), SessionClients.signedInAt(session));
		}
		return signedInAt.isEmpty() ? Set.of() : revoked(accountId.toString(), signedInAt);
	}

	private Set<String> revoked(@Nullable String account, Map<String, Instant> signedInAt) {
		List<String> ids = new ArrayList<>(signedInAt.keySet());
		List<String> keys = new ArrayList<>(ids.size() + 1);
		for (String id : ids) {
			keys.add(sessionKey(id));
		}
		if (account != null) {
			keys.add(accountKey(account));
		}
		List<@Nullable String> values = this.redis.opsForValue().multiGet(keys);
		if (values == null || values.size() != keys.size()) {
			throw new IllegalStateException("MGET отметок отзыва вернул не все значения");
		}
		AccountRevocation accountRevocation = (account != null) ? AccountRevocation.parse(values.get(ids.size()))
				: null;
		Set<String> revoked = new HashSet<>();
		for (int i = 0; i < ids.size(); i++) {
			String id = ids.get(i);
			if (values.get(i) != null
					|| (accountRevocation != null && accountRevocation.covers(id, signedInAt.get(id)))) {
				revoked.add(id);
			}
		}
		return revoked;
	}

	/** Аккаунт сессии — имя из контекста безопасности в ней, как у индекса Spring Session; без входа — null. */
	static @Nullable String account(HttpSession session) {
		Object context = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		Authentication authentication = (context instanceof SecurityContext security) ? security.getAuthentication()
				: null;
		return (authentication != null) ? authentication.getName() : null;
	}

	/** Время входа из сессии; без него — время создания сессии, как у {@link SessionClients#signedInAt(Session)}. */
	static Instant signedInAt(HttpSession session) {
		return (session.getAttribute(SessionClients.SIGNED_IN_AT) instanceof Instant signedInAt) ? signedInAt
				: Instant.ofEpochMilli(session.getCreationTime());
	}

	static String sessionKey(String sessionId) {
		return SESSION_KEY_PREFIX + digest(sessionId);
	}

	static String accountKey(String account) {
		return ACCOUNT_KEY_PREFIX + account;
	}

	/** SHA-256 id сессии в base64url: по нему нельзя восстановить id. */
	static String digest(String sessionId) {
		return Hashes.sha256Base64Url(sessionId);
	}

	/** Отметка аккаунта: сессии со входом не позже {@code signedInNotAfter}, кроме оставленной, отозваны. */
	private record AccountRevocation(Instant signedInNotAfter, @Nullable String keptDigest) {

		static @Nullable AccountRevocation parse(@Nullable String value) {
			if (value == null) {
				return null;
			}
			int separator = value.indexOf(KEPT_SEPARATOR);
			if (separator < 0) {
				return new AccountRevocation(Instant.parse(value), null);
			}
			return new AccountRevocation(Instant.parse(value.substring(0, separator)), value.substring(separator + 1));
		}

		boolean covers(String sessionId, @Nullable Instant signedInAt) {
			return signedInAt != null && !signedInAt.isAfter(this.signedInNotAfter)
					&& (this.keptDigest == null || !this.keptDigest.equals(digest(sessionId)));
		}

	}

}
