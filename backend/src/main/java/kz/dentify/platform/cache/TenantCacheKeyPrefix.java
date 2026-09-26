package kz.dentify.platform.cache;

import kz.dentify.platform.api.MissingTenantContextException;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.tenant.TenantContextHolder;

import org.springframework.data.redis.cache.CacheKeyPrefix;

/**
 * Префикс ключей кэша в Valkey (PLT-01): ключ данных клиники начинается с её id —
 * {@code <clinicId>::<кэш>::<ключ>}, поэтому одинаковый ключ двух клиник — две разные записи, а очистка кэша
 * ({@code @CacheEvict(allEntries = true)}) удаляет записи только текущей клиники. Без контекста клиники обращение к
 * такому кэшу бросает {@link MissingTenantContextException}.
 * <p>
 * Глобальный кэш — общие для всех клиник данные, например справочник МКБ-10, — помечается явно именем с приставкой
 * {@value #GLOBAL}: {@code @Cacheable(global.icd10)}. Его ключ — {@code <кэш>::<ключ>}, контекст не нужен.
 * <p>
 * Spring Data Redis вычисляет префикс при каждом обращении к ключу ({@code RedisCache#createCacheKey}), поэтому
 * клиника берётся из контекста того потока, который обращается к кэшу. Асинхронное заполнение кэша
 * ({@code @Cacheable} у метода с {@code CompletableFuture}) может записать ключ в другом потоке — для данных клиники
 * его не используем.
 */
final class TenantCacheKeyPrefix implements CacheKeyPrefix {

	/** Приставка имени глобального кэша. */
	static final String GLOBAL = "global.";

	@Override
	public String compute(String cacheName) {
		if (cacheName.startsWith(GLOBAL)) {
			return cacheName + SEPARATOR;
		}
		Tenant tenant = TenantContextHolder.get();
		if (tenant == null) {
			throw new MissingTenantContextException();
		}
		return tenant.clinicId() + SEPARATOR + cacheName + SEPARATOR;
	}

}
