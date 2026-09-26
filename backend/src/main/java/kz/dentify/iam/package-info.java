/**
 * «Доступы и аккаунты»: аккаунты, членство в клиниках, роли и права, приглашения, журналы, сессии.
 * <p>
 * Зависит от clinic ({@code clinic :: api}) и платформы: {@code platform :: api}, как все модули, и
 * {@code platform :: identity} — только iam (клиника в сессии, вход, сессии, лимиты входа, чтение журнала). Подписан
 * на события staff ({@code staff :: events}: {@code StaffDeactivated} → гасит сессии). Ни один модуль от iam не
 * зависит.
 */
@ApplicationModule(displayName = "Доступы и аккаунты",
		allowedDependencies = { "platform :: api", "platform :: identity", "clinic :: api", "staff :: events" })
@NullMarked
package kz.dentify.iam;

import org.jspecify.annotations.NullMarked;

import org.springframework.modulith.ApplicationModule;
