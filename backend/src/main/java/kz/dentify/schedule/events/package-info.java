/**
 * События модуля schedule для подписчиков из других модулей: {@code AppointmentCompleted} → inventory
 * списывает материалы по нормам услуги.
 * <p>
 * Отдельный named interface, а не {@code api}: подписчик зависит только от типов событий. Слушатель
 * события — это зависимость по типу от модуля-издателя, поэтому у подписчика в
 * {@code allowedDependencies} стоит {@code schedule :: events}. Событие — неизменяемый record с
 * идентификаторами ({@code clinicId}, {@code appointmentId}), без ПД.
 */
@NamedInterface("events")
@NullMarked
package kz.dentify.schedule.events;

import org.jspecify.annotations.NullMarked;

import org.springframework.modulith.NamedInterface;
