/**
 * Платформа — общее ядро: тенант-контекст, безопасность, аудит, ошибки, i18n, логирование.
 * <p>
 * Ни от одного модуля не зависит, от неё зависят все. Другим модулям открыты только её named interfaces:
 * {@code api} — всем, {@code identity} (клиника в сессии, вход, сессии, лимиты входа, чтение журнала) — только iam.
 * Остальные пакеты — внутренние.
 */
@ApplicationModule(displayName = "Платформа", allowedDependencies = {})
@NullMarked
package kz.dentify.platform;

import org.jspecify.annotations.NullMarked;

import org.springframework.modulith.ApplicationModule;
