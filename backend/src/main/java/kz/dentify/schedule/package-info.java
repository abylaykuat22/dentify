/**
 * «Приёмы»: записи, статусы, конфликты по креслу и врачу.
 * <p>
 * Зависит от clinic, staff, patient. От schedule зависят medical — через {@code schedule :: api} и
 * inventory — через {@code schedule :: events} (подписка на события).
 */
@ApplicationModule(displayName = "Расписание",
		allowedDependencies = { "platform :: api", "clinic :: api", "staff :: api", "patient :: api" })
@NullMarked
package kz.dentify.schedule;

import org.jspecify.annotations.NullMarked;

import org.springframework.modulith.ApplicationModule;
