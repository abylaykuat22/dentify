/**
 * «Моя клиника»: организация, филиалы, кресла, настройки, прайс, справочники.
 * <p>
 * От clinic зависят iam, staff, schedule, inventory, medical — через {@code clinic :: api}.
 */
@ApplicationModule(displayName = "Моя клиника", allowedDependencies = "platform :: api")
@NullMarked
package kz.dentify.clinic;

import org.jspecify.annotations.NullMarked;

import org.springframework.modulith.ApplicationModule;
