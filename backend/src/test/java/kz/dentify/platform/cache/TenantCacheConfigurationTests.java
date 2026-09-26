package kz.dentify.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Префикс клиники у всех кэшей при любом порядке настройщиков (PLT-01): автоконфигурация кэша Spring Boot с
 * платформенной конфигурацией и настройщиками модулей до и после неё. Без Valkey: менеджер кэшей при старте к нему не
 * обращается.
 * <p>
 * Настройщики без {@code @Order}, как у модулей, с одинаковым порядком идут в порядке регистрации бинов — здесь это
 * порядок классов в {@code withUserConfiguration}.
 */
class TenantCacheConfigurationTests {

	static final Duration MODULE_TTL = Duration.ofMinutes(5);

	final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class))
		.withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
		.withPropertyValues("spring.cache.type=redis", "spring.cache.redis.time-to-live=1h");

	@Test
	void cacheNamesFromPropertiesAndDefaultsGetClinicPrefix() {
		this.runner.withPropertyValues("spring.cache.cache-names=preset,global.preset-dictionary")
			.withUserConfiguration(TenantCacheConfiguration.class)
			.run((context) -> {
				RedisCacheManager cacheManager = context.getBean(RedisCacheManager.class);
				Map<String, RedisCacheConfiguration> caches = cacheManager.getCacheConfigurations();

				assertThat(caches).containsOnlyKeys("preset", "global.preset-dictionary");
				UUID clinic = UUID.randomUUID();
				TenantContext.run(Tenant.of(clinic), () -> {
					assertThat(caches.get("preset").getKeyPrefixFor("preset")).isEqualTo(clinic + "::preset::");
					assertThat(caches.get("global.preset-dictionary").getKeyPrefixFor("global.preset-dictionary"))
						.isEqualTo("global.preset-dictionary::");
				});
				assertThat(caches.get("preset").getTtlFunction().getTimeToLive("k", "v"))
					.as("время жизни из spring.cache.redis.*")
					.isEqualTo(Duration.ofHours(1));
			});
	}

	/** Настройщик модуля раньше платформенного заменил конфигурацию без префикса — платформа ставит префикс поверх. */
	@Test
	void moduleCustomizerBeforePlatformIsPrefixedByPlatform() {
		this.runner.withUserConfiguration(ReplacingModuleCaches.class, TenantCacheConfiguration.class)
			.run((context) -> {
				assertThat(context).hasNotFailed();
				RedisCacheConfiguration slots = context.getBean(RedisCacheManager.class)
					.getCacheConfigurations()
					.get("schedule-slots");
				assertThat(TenantCacheManagerCheck.clinicPrefixed(slots)).isTrue();
				assertThat(slots.getTtlFunction().getTimeToLive("k", "v")).isEqualTo(MODULE_TTL);
			});
	}

	/**
	 * Находка проверки T-PLT-03: настройщик модуля после платформенного с конфигурацией от
	 * {@code defaultCacheConfig()} дал бы ключ {@code schedule-slots::…}, общий для всех клиник. Теперь старт падает.
	 */
	@Test
	void moduleCustomizerAfterPlatformWithoutPrefixFailsStartup() {
		this.runner.withUserConfiguration(TenantCacheConfiguration.class, ReplacingModuleCaches.class)
			.run((context) -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("schedule-slots")
				.hasMessageContaining("builder.cacheDefaults()"));
	}

	/** Рекомендованный способ модуля — от {@code builder.cacheDefaults()}: префикс есть и после платформы. */
	@Test
	void moduleCustomizerAfterPlatformFromDefaultsKeepsPrefix() {
		this.runner.withUserConfiguration(TenantCacheConfiguration.class, DerivedModuleCaches.class)
			.run((context) -> {
				assertThat(context).hasNotFailed();
				RedisCacheConfiguration slots = context.getBean(RedisCacheManager.class)
					.getCacheConfigurations()
					.get("schedule-slots");
				assertThat(TenantCacheManagerCheck.clinicPrefixed(slots)).isTrue();
				assertThat(slots.getTtlFunction().getTimeToLive("k", "v")).isEqualTo(MODULE_TTL);
			});
	}

	/** Замена конфигурации по умолчанию после платформы: кэши, создаваемые при первом обращении, были бы без клиники. */
	@Test
	void replacedDefaultsWithoutPrefixFailStartup() {
		this.runner.withUserConfiguration(TenantCacheConfiguration.class, ReplacingModuleDefaults.class)
			.run((context) -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.hasMessageContaining(TenantCacheManagerCheck.DEFAULTS));
	}

	/** Выключенный префикс ключа — тоже без клиники. */
	@Test
	void disabledKeyPrefixFailsStartup() {
		this.runner.withUserConfiguration(TenantCacheConfiguration.class, UnprefixedModuleCaches.class)
			.run((context) -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.hasMessageContaining("schedule-slots"));
	}

	/** Свой менеджер кэшей модуля (в памяти) хранил бы ключи двух клиник в одной записи. */
	@Test
	void foreignCacheManagerFailsStartup() {
		this.runner.withUserConfiguration(TenantCacheConfiguration.class, InMemoryCacheManager.class)
			.run((context) -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.hasMessageContaining(ConcurrentMapCacheManager.class.getName()));
	}

	/** Модуль заменяет конфигурацию кэша своей, собранной с нуля: так делать нельзя. */
	static class ReplacingModuleCaches {

		@Bean
		RedisCacheManagerBuilderCustomizer scheduleCaches() {
			return (builder) -> builder.withCacheConfiguration("schedule-slots",
					RedisCacheConfiguration.defaultCacheConfig().entryTtl(MODULE_TTL));
		}

	}

	/** Модуль задаёт свой срок от конфигурации по умолчанию: так правильно. */
	static class DerivedModuleCaches {

		@Bean
		RedisCacheManagerBuilderCustomizer scheduleCaches() {
			return (builder) -> builder.withCacheConfiguration("schedule-slots",
					builder.cacheDefaults().entryTtl(MODULE_TTL));
		}

	}

	static class ReplacingModuleDefaults {

		@Bean
		RedisCacheManagerBuilderCustomizer moduleDefaults() {
			return (builder) -> builder.cacheDefaults(RedisCacheConfiguration.defaultCacheConfig());
		}

	}

	static class UnprefixedModuleCaches {

		@Bean
		RedisCacheManagerBuilderCustomizer scheduleCaches() {
			return (builder) -> builder.withCacheConfiguration("schedule-slots",
					builder.cacheDefaults().disableKeyPrefix());
		}

	}

	static class InMemoryCacheManager {

		@Bean
		CacheManager moduleCacheManager() {
			return new ConcurrentMapCacheManager();
		}

	}

}
