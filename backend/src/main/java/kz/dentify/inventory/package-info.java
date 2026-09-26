/**
 * «Склад»: номенклатура, склады, поставщики, документы, движения и остатки.
 * <p>
 * Зависит от clinic ({@code clinic :: api}) и подписан на события schedule ({@code schedule :: events}:
 * {@code AppointmentCompleted} → списание по нормам услуги).
 */
@ApplicationModule(displayName = "Склад",
		allowedDependencies = { "platform :: api", "clinic :: api", "schedule :: events" })
@NullMarked
package kz.dentify.inventory;

import org.jspecify.annotations.NullMarked;

import org.springframework.modulith.ApplicationModule;
