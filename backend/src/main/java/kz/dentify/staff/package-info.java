/**
 * «Карточка врача»: сотрудники, специализации, смены и шаблоны смен.
 * <p>
 * От staff зависят schedule — через {@code staff :: api} и iam — через {@code staff :: events}
 * (подписка на события).
 */
@ApplicationModule(displayName = "Сотрудники", allowedDependencies = { "platform :: api", "clinic :: api" })
@NullMarked
package kz.dentify.staff;

import org.jspecify.annotations.NullMarked;

import org.springframework.modulith.ApplicationModule;
