package kz.dentify.platform.api;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Арендатор текущей работы: клиника и, если выбран, филиал. Клиника — граница изоляции данных (PLT-01): её
 * {@code clinicId} подставляет Hibernate в {@code @TenantId} и БД в политики RLS. Филиал — выбор внутри клиники
 * (фильтр по умолчанию в расписании и складе), на изоляцию не влияет.
 *
 * @param clinicId клиника
 * @param branchId выбранный филиал или null, если не выбран
 */
public record Tenant(UUID clinicId, @Nullable UUID branchId) {

	/** Клиника без выбранного филиала: фоновые задачи и обработчики событий. */
	public static Tenant of(UUID clinicId) {
		return new Tenant(clinicId, null);
	}

}
