package kz.dentify.platform.tenancy;

import java.time.Duration;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Пробный настройщик кэша модуля только для {@code TenantCacheTests}: свой срок жизни кэша так, как его задаёт модуль,
 * — от конфигурации по умолчанию, поэтому префикс клиники сохраняется при любом порядке настройщиков.
 */
@Configuration(proxyBeanMethods = false)
class TenantCacheProbeCaches {

	static final String MODULE_CACHE = "test-module-ttl";

	static final Duration MODULE_TTL = Duration.ofMinutes(5);

	@Bean
	RedisCacheManagerBuilderCustomizer testModuleCaches() {
		return (builder) -> builder.withCacheConfiguration(MODULE_CACHE, builder.cacheDefaults().entryTtl(MODULE_TTL));
	}

}
