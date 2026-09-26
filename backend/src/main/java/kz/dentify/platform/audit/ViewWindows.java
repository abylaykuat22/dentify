package kz.dentify.platform.audit;

import java.time.Duration;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Окно просмотров в Valkey: не больше одного события просмотра на аккаунт и объект за окно (PLT-09, 15 минут). Метка —
 * ключ {@code dentify:audit:view:<клиника>:<аккаунт>:<тип>:<id>} с временем жизни окна, ставится атомарно
 * {@code SET NX PX}: из одновременных открытий одной карты метку ставит одно. Окно фиксированное — от записанного
 * события: просмотры внутри окна его не продлевают. Ключ из идентификаторов, ПД в нём нет.
 */
class ViewWindows {

	static final String PREFIX = "dentify:audit:view:";

	private static final Logger log = LoggerFactory.getLogger(ViewWindows.class);

	private final StringRedisTemplate redis;

	private final Duration window;

	ViewWindows(StringRedisTemplate redis, Duration window) {
		this.redis = redis;
		this.window = window;
	}

	/**
	 * Занять окно просмотра.
	 *
	 * @return ключ метки — окно занято этим вызовом, событие писать; null — окно уже занято, событие не писать. Valkey
	 * недоступен или не ответил за {@code spring.data.redis.timeout} — {@link #UNAVAILABLE}: событие писать без окна,
	 * полнота журнала важнее дублей
	 */
	@Nullable String claim(UUID clinicId, @Nullable UUID accountId, String entityType, UUID entityId) {
		String key = key(clinicId, accountId, entityType, entityId);
		try {
			Boolean claimed = this.redis.opsForValue().setIfAbsent(key, "1", this.window);
			return Boolean.TRUE.equals(claimed) ? key : null;
		}
		catch (DataAccessException ex) {
			log.warn("Окно просмотров в Valkey недоступно, просмотр пишется без проверки дублей", ex);
			return UNAVAILABLE;
		}
	}

	/** Снять метку: событие не сохранилось, следующий просмотр должен записаться. */
	void release(String key) {
		if (UNAVAILABLE.equals(key)) {
			return;
		}
		try {
			this.redis.delete(key);
		}
		catch (DataAccessException ex) {
			log.warn("Метка окна просмотров не снята, следующий просмотр запишется после окна", ex);
		}
	}

	static String key(UUID clinicId, @Nullable UUID accountId, String entityType, UUID entityId) {
		return PREFIX + clinicId + ":" + ((accountId != null) ? accountId : "system") + ":" + entityType + ":" + entityId;
	}

	/** Ключ-заглушка: Valkey недоступен, метки нет. */
	static final String UNAVAILABLE = "";

}
