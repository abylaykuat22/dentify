package kz.dentify.platform.cache;

import java.util.List;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Кэш Spring в Valkey ({@code spring.cache.type: redis}, настройки — {@code spring.cache.redis.*}) с префиксом ключей
 * по клинике: {@link TenantCacheKeyPrefix}.
 * <ul>
 * <li>{@link #tenantCacheKeys()} ставит префикс кэшам по умолчанию, то есть создаваемым при первом обращении, и всем
 * кэшам, настроенным к моменту его запуска: {@code spring.cache.cache-names} и настройщики модулей. Время жизни и
 * сериализация остаются из свойств и настройщиков;</li>
 * <li>{@link TenantCacheManagerCheck} после сборки менеджера кэшей роняет старт, если у какого-то кэша префикса клиники
 * нет. Так ловится настройщик модуля, который выполнился позже платформенного и заменил конфигурацию кэша своей,
 * собранной не из {@code builder.cacheDefaults()}.</li>
 * </ul>
 * Свой срок жизни кэша модуль задаёт от конфигурации по умолчанию — тогда префикс есть при любом порядке настройщиков:
 * {@code builder.withCacheConfiguration(name, builder.cacheDefaults().entryTtl(ttl))}.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
class TenantCacheConfiguration {

	/**
	 * Последним среди упорядоченных настройщиков. Настройщик без {@code @Order} тоже последний, и между ними порядок —
	 * порядок регистрации бинов, поэтому гарантию даёт не порядок, а {@link TenantCacheManagerCheck}.
	 */
	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE)
	RedisCacheManagerBuilderCustomizer tenantCacheKeys() {
		TenantCacheKeyPrefix prefix = new TenantCacheKeyPrefix();
		return (builder) -> {
			RedisCacheConfiguration defaults = builder.cacheDefaults();
			builder.cacheDefaults(defaults.computePrefixWith(prefix));
			for (String name : List.copyOf(builder.getConfiguredCaches())) {
				builder.getCacheConfigurationFor(name)
					.ifPresent((configuration) -> builder.withCacheConfiguration(name,
							configuration.computePrefixWith(prefix)));
			}
		};
	}

	@Bean
	static BeanPostProcessor tenantCacheManagerCheck() {
		return new TenantCacheManagerCheck();
	}

}
