/**
 * Медкарта: зубная формула, записи приёма и их версии, диагнозы.
 * <p>
 * Зависит от patient, schedule, clinic (решение 2026-09-25); от medical не зависит никто.
 */
@ApplicationModule(displayName = "Медкарта",
		allowedDependencies = { "platform :: api", "patient :: api", "schedule :: api", "clinic :: api" })
@NullMarked
package kz.dentify.medical;

import org.jspecify.annotations.NullMarked;

import org.springframework.modulith.ApplicationModule;
