package kz.dentify.platform.persistence;

import java.util.UUID;

import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.tenant.TenantContextHolder;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Клиника для {@code @TenantId} Hibernate: Hibernate спрашивает её при открытии каждой сессии и подставляет в INSERT
 * сущностей {@code TenantEntity} и в условие {@code clinic_id = …} всех запросов к ним.
 * <p>
 * Без контекста клиники — {@link #NO_CLINIC}, нулевой UUID: ни одна строка ему не соответствует (id клиник —
 * UUIDv7), поэтому запрос к данным клиники ничего не находит, а вставку отклонит RLS. Null вернуть нельзя: Hibernate
 * тогда не откроет сессию вовсе, в том числе для глобальных сущностей (вход, справочники) и проверки запросов при
 * старте. Корневого арендатора, который видит все клиники, нет: {@link #isRoot} всегда false.
 */
final class ClinicTenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

	/** Значение без контекста клиники: не совпадает ни с одной клиникой. */
	static final UUID NO_CLINIC = new UUID(0L, 0L);

	@Override
	public UUID resolveCurrentTenantIdentifier() {
		Tenant tenant = TenantContextHolder.get();
		return (tenant != null) ? tenant.clinicId() : NO_CLINIC;
	}

	@Override
	public boolean validateExistingCurrentSessions() {
		return true;
	}

	@Override
	public boolean isRoot(UUID tenantId) {
		return false;
	}

}
