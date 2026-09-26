/**
 * События модуля staff для подписчиков из других модулей: {@code StaffDeactivated} → iam гасит сессии
 * сотрудника (PLT-24).
 * <p>
 * Отдельный named interface, а не {@code api}: подписчик зависит только от типов событий. Слушатель
 * ({@code @ApplicationModuleListener void on(StaffDeactivated event)}) — это зависимость по типу от
 * модуля-издателя, поэтому у подписчика в {@code allowedDependencies} стоит {@code staff :: events}.
 * Событие — неизменяемый record с идентификаторами ({@code clinicId}, {@code staffId}), без ПД.
 */
@NamedInterface("events")
@NullMarked
package kz.dentify.staff.events;

import org.jspecify.annotations.NullMarked;

import org.springframework.modulith.NamedInterface;
