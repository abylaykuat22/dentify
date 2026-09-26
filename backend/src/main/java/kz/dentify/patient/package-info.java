/**
 * «Карточка пациента»: персональные данные, представители, медблок, контакты.
 * <p>
 * От patient зависят schedule и medical — через {@code patient :: api}.
 */
@ApplicationModule(displayName = "Пациенты", allowedDependencies = "platform :: api")
@NullMarked
package kz.dentify.patient;

import org.jspecify.annotations.NullMarked;

import org.springframework.modulith.ApplicationModule;
