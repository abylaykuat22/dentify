package kz.dentify.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import kz.dentify.platform.api.MissingTenantContextException;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import org.assertj.core.api.AbstractCollectionAssert;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Кэш в Valkey (PLT-01): ключи кэша данных клиники начинаются с clinic_id, клиники не видят записей друг друга,
 * очистка кэша затрагивает только свою клинику; глобальный кэш — без клиники; без контекста кэш клиники недоступен.
 * <p>
 * Spring Data Redis 4 с Lettuce пишет в кэш асинхронно ({@code DefaultRedisCacheWriter.put} без ожидания отдаёт запись
 * в {@code store}): ключ появляется в Valkey не сразу после {@code put}, поэтому ключи проверяются с ожиданием
 * ({@link #keys(String, Consumer)}). Повторный вызов {@code @Cacheable}, который должен взять значение из кэша, идёт
 * только после того, как запись первого дошла ({@link #awaitKey(String)}), иначе метод вычислится второй раз. Префикс
 * при этом вычисляется синхронно, в потоке вызова.
 */
class TenantCacheTests extends TenantIntegrationTest {

	@Autowired
	TenantCacheProbe probe;

	@Autowired
	CacheManager cacheManager;

	@Autowired
	StringRedisTemplate redis;

	@Test
	void cacheIsRedisInValkey() {
		assertThat(this.cacheManager).isInstanceOf(RedisCacheManager.class);
	}

	/**
	 * Префикс клиники — у всех кэшей приложения: созданных заранее ({@code spring.cache.cache-names} профиля test),
	 * настроенных модулем со своим сроком жизни ({@link TenantCacheProbeCaches}) и создаваемых при первом обращении.
	 */
	@Test
	void everyCacheHasClinicPrefix() {
		UUID clinic = newClinic();
		String key = "key-" + UUID.randomUUID();
		Map<String, RedisCacheConfiguration> caches = ((RedisCacheManager) this.cacheManager).getCacheConfigurations();

		assertThat(caches).containsKeys("test-preset", "global.test-preset", TenantCacheProbeCaches.MODULE_CACHE);
		TenantContext.run(Tenant.of(clinic), () -> caches.forEach((name, configuration) -> {
			assertThat(configuration.usePrefix()).as(name).isTrue();
			assertThat(configuration.getKeyPrefixFor(name)).as(name)
				.isEqualTo(name.startsWith("global.") ? name + "::" : clinic + "::" + name + "::");
		}));
		assertThat(caches.get(TenantCacheProbeCaches.MODULE_CACHE).getTtlFunction().getTimeToLive(key, "v"))
			.as("срок жизни модуля сохранился")
			.isEqualTo(TenantCacheProbeCaches.MODULE_TTL);

		for (String name : new String[] { "test-preset", TenantCacheProbeCaches.MODULE_CACHE, "test-runtime-" + key }) {
			TenantContext.run(Tenant.of(clinic), () -> this.cacheManager.getCache(name).put(key, "v"));
			keys("*" + key, (keys) -> keys.as(name).contains(clinic + "::" + name + "::" + key));
		}
	}

	@Test
	void clinicCacheKeysStartWithClinicId() {
		UUID clinicA = newClinic();
		UUID clinicB = newClinic();
		String key = "key-" + UUID.randomUUID();
		int calls = this.probe.clinicCalls();

		String first = TenantContext.call(Tenant.of(clinicA), () -> this.probe.label(key));
		awaitKey(clinicA + "::" + TenantCacheProbe.CLINIC_CACHE + "::" + key);
		String cached = TenantContext.call(Tenant.of(clinicA), () -> this.probe.label(key));
		String other = TenantContext.call(Tenant.of(clinicB), () -> this.probe.label(key));

		assertThat(first).isEqualTo(cached).isEqualTo(clinicA + ":" + key);
		assertThat(other).as("другая клиника с тем же ключом — своя запись").isEqualTo(clinicB + ":" + key);
		assertThat(this.probe.clinicCalls() - calls).isEqualTo(2);
		keys("*" + key, (keys) -> keys.containsExactlyInAnyOrder(
				clinicA + "::" + TenantCacheProbe.CLINIC_CACHE + "::" + key,
				clinicB + "::" + TenantCacheProbe.CLINIC_CACHE + "::" + key));

		TenantContext.run(Tenant.of(clinicA), this.probe::evictAll);
		keys("*" + key, (keys) -> keys.as("очистка кэша — только своей клиники")
			.containsExactly(clinicB + "::" + TenantCacheProbe.CLINIC_CACHE + "::" + key));
	}

	@Test
	void clinicCacheWithoutClinicFails() {
		assertThatThrownBy(() -> this.probe.label("x")).isInstanceOf(MissingTenantContextException.class);
	}

	@Test
	void globalCacheIsSharedAndNeedsNoClinic() {
		String code = "K02." + UUID.randomUUID();
		int calls = this.probe.globalCalls();

		this.probe.term(code);
		awaitKey(TenantCacheProbe.GLOBAL_CACHE + "::" + code);
		TenantContext.call(Tenant.of(newClinic()), () -> this.probe.term(code));

		assertThat(this.probe.globalCalls() - calls).isEqualTo(1);
		keys("*" + code, (keys) -> keys.containsExactly(TenantCacheProbe.GLOBAL_CACHE + "::" + code));
	}

	/** Ждёт, пока асинхронная запись кэша дойдёт до Valkey: только после этого повторный вызов возьмёт значение из кэша. */
	private void awaitKey(String redisKey) {
		keys("*" + redisKey, (keys) -> keys.as("запись кэша дошла до Valkey").contains(redisKey));
	}

	/** Ключи Valkey по шаблону проходят проверку — сразу или после того, как дойдёт асинхронная запись. */
	private void keys(String pattern, Consumer<AbstractCollectionAssert<?, ?, String, ?>> check) {
		Awaitility.await()
			.atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> check.accept(assertThat(this.redis.keys(pattern))));
	}

}
