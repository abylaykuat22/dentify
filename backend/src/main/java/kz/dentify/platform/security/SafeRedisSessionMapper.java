package kz.dentify.platform.security;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.PrincipalNameIndexResolver;
import org.springframework.session.data.redis.RedisSessionMapper;

/**
 * Разбор hash сессии из Valkey, который не падает на неполном hash: официальный рецепт Spring Session (reference,
 * Configuring Redis Session Mapper, вариант для {@code RedisIndexedSessionRepository}).
 * <p>
 * Неполный hash — без {@code creationTime} — пишет конец запроса завершённой сессии, если он пришёл позже, чем истёк её
 * ключ: {@code deleteById} оставляет ключ жить 5 минут, а изменения запроса ложатся командой {@code HSET}, которая
 * создаёт ключ заново. Стандартный {@link RedisSessionMapper} на таком hash бросает {@link IllegalStateException}, и
 * запрос с cookie этой сессии, список сессий аккаунта, завершение всех отвечали бы 500, пока hash не истечёт (с
 * Запомнить — до 30 дней). Здесь такой hash — не сессия: он удаляется, возвращается null, и хранилище считает сессию
 * отсутствующей — запрос с её cookie получает 401 {@code error.session-expired}, поиск по индексу её пропускает.
 * <p>
 * Вместе с hash удаляются теневой ключ срока ({@code sessions:expires:<id>}) и id в индексе аккаунта, если контекст
 * безопасности в hash его туда вернул: иначе id лежал бы в индексе без срока. Удаление — прямыми командами, а не
 * {@code deleteById}: тот снова читает сессию через этот же разбор. Живую сессию это не задевает: полный hash пишется
 * одной командой при создании сессии, неполный бывает только у ключа, который уже истёк или удалён.
 */
final class SafeRedisSessionMapper implements BiFunction<String, Map<String, Object>, MapSession> {

	private static final Logger log = LoggerFactory.getLogger(SafeRedisSessionMapper.class);

	/** Приставка атрибутов сессии в hash, как у {@link RedisSessionMapper}. */
	private static final String ATTRIBUTE_PREFIX = "sessionAttr:";

	private final RedisSessionMapper delegate = new RedisSessionMapper();

	private final PrincipalNameIndexResolver<MapSession> principals = new PrincipalNameIndexResolver<>();

	private final RedisOperations<String, Object> redis;

	private final String namespace;

	/**
	 * @param redis операции Valkey хранилища сессий ({@code getSessionRedisOperations()})
	 * @param namespace приставка ключей сессий без двоеточия в конце ({@code spring.session.data.redis.namespace})
	 */
	SafeRedisSessionMapper(RedisOperations<String, Object> redis, String namespace) {
		this.redis = redis;
		this.namespace = namespace.trim() + ":";
	}

	@Override
	public @Nullable MapSession apply(String sessionId, Map<String, Object> map) {
		try {
			return this.delegate.apply(sessionId, map);
		}
		catch (IllegalStateException ex) {
			String principal = principal(sessionId, map);
			this.redis.delete(List.of(sessionKey(sessionId), sessionKey("expires:" + sessionId)));
			if (principal != null) {
				this.redis.boundSetOps(principalKey(principal)).remove(sessionId);
			}
			log.info("Неполная сессия аккаунта {} в Valkey удалена: её записал запрос, закончившийся после её завершения",
					principal);
			return null;
		}
	}

	/** Аккаунт из атрибутов неполного hash — так же, как индекс Spring Session; нет контекста — null. */
	private @Nullable String principal(String sessionId, Map<String, Object> map) {
		MapSession attributes = new MapSession(sessionId);
		map.forEach((name, value) -> {
			if (name.startsWith(ATTRIBUTE_PREFIX)) {
				attributes.setAttribute(name.substring(ATTRIBUTE_PREFIX.length()), value);
			}
		});
		return this.principals.resolveIndexValueFor(attributes);
	}

	private String sessionKey(String sessionId) {
		return this.namespace + "sessions:" + sessionId;
	}

	private String principalKey(String principal) {
		return this.namespace + "index:" + FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME + ":" + principal;
	}

}
