package kz.dentify.platform.cache;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.util.ReflectionUtils;

/**
 * Проверка при старте (PLT-01): у каждого кэша приложения ключ начинается с клиники. Роняет старт, если
 * <ul>
 * <li>менеджер кэшей — не {@link RedisCacheManager}: свой {@code CacheManager} модуля, например в памяти, хранил бы
 * одинаковые ключи двух клиник в одной записи;</li>
 * <li>у конфигурации по умолчанию, по которой создаются кэши при первом обращении, или у любого настроенного кэша,
 * включая {@code spring.cache.cache-names}, префикс ключа выключен или вычисляется не {@link TenantCacheKeyPrefix}.
 * Глобальные кэши с приставкой {@code global.} тоже идут через него: он сам не добавляет им клинику.</li>
 * </ul>
 * Конфигурацию по умолчанию {@code RedisCacheManager} отдаёт только защищённым методом, поэтому она читается через
 * {@link ReflectionUtils}; если метод пропадёт в новой версии Spring Data Redis, проверка упадёт явно, а не пропустит.
 */
final class TenantCacheManagerCheck implements BeanPostProcessor {

	static final String DEFAULTS = "кэши по умолчанию";

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof CacheManager cacheManager) {
			verify(beanName, cacheManager);
		}
		return bean;
	}

	static void verify(String beanName, CacheManager cacheManager) {
		if (!(cacheManager instanceof RedisCacheManager redis)) {
			throw new IllegalStateException("Менеджер кэшей " + beanName + " (" + cacheManager.getClass().getName()
					+ ") не RedisCacheManager платформы: ключи кэша без клиники (docs/architecture.md, раздел Кэш)");
		}
		Map<String, @Nullable RedisCacheConfiguration> configurations = new TreeMap<>(redis.getCacheConfigurations());
		configurations.put(DEFAULTS, defaults(redis));
		List<String> withoutClinic = new ArrayList<>();
		configurations.forEach((name, configuration) -> {
			if (!clinicPrefixed(configuration)) {
				withoutClinic.add(name);
			}
		});
		if (!withoutClinic.isEmpty()) {
			throw new IllegalStateException("Кэши без префикса клиники " + withoutClinic + ": конфигурацию кэша "
					+ "собирают от builder.cacheDefaults(), а не от RedisCacheConfiguration.defaultCacheConfig(), "
					+ "и не выключают префикс (docs/architecture.md, раздел Кэш)");
		}
	}

	static boolean clinicPrefixed(@Nullable RedisCacheConfiguration configuration) {
		return configuration != null && configuration.usePrefix()
				&& configuration.getKeyPrefix() instanceof TenantCacheKeyPrefix;
	}

	private static @Nullable RedisCacheConfiguration defaults(RedisCacheManager cacheManager) {
		Method getter = ReflectionUtils.findMethod(RedisCacheManager.class, "getDefaultCacheConfiguration");
		if (getter == null) {
			throw new IllegalStateException("В RedisCacheManager нет getDefaultCacheConfiguration(): проверку префикса "
					+ "клиники у кэшей по умолчанию нужно переписать под новую версию Spring Data Redis");
		}
		ReflectionUtils.makeAccessible(getter);
		return (RedisCacheConfiguration) ReflectionUtils.invokeMethod(getter, cacheManager);
	}

}
