package kz.dentify.platform.tenancy;

import java.util.concurrent.atomic.AtomicInteger;

import kz.dentify.platform.api.TenantContext;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/** Пробные кэшируемые методы только для тестов кэша клиник ({@code TenantCacheTests}). */
@Component
class TenantCacheProbe {

	static final String CLINIC_CACHE = "test-clinic-labels";

	static final String GLOBAL_CACHE = "global.test-dictionary";

	private final AtomicInteger clinicCalls = new AtomicInteger();

	private final AtomicInteger globalCalls = new AtomicInteger();

	/** Сколько раз значение клиники вычислялось, а не бралось из кэша. Через метод: поля прокси кэша пусты. */
	public int clinicCalls() {
		return this.clinicCalls.get();
	}

	public int globalCalls() {
		return this.globalCalls.get();
	}

	/** Значение зависит от клиники: из кэша другой клиники его брать нельзя. */
	@Cacheable(CLINIC_CACHE)
	public String label(String key) {
		this.clinicCalls.incrementAndGet();
		return TenantContext.clinicId() + ":" + key;
	}

	/**
	 * Очистка всего кэша клиники. {@code beforeInvocation} — чтобы Spring вызвал немедленный {@code invalidate()}:
	 * обычный {@code clear()} Spring Data Redis с Lettuce удаляет ключи асинхронно, и тест видел бы их ещё мгновение.
	 */
	@CacheEvict(cacheNames = CLINIC_CACHE, allEntries = true, beforeInvocation = true)
	public void evictAll() {
	}

	/** Общие для всех клиник данные, как справочник МКБ-10. */
	@Cacheable(GLOBAL_CACHE)
	public String term(String code) {
		this.globalCalls.incrementAndGet();
		return "term:" + code;
	}

}
