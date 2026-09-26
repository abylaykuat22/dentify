/**
 * Примитивы входа и доступа, которые вызывает только модуль iam: клиника и филиал в сессии ({@code TenantSession}),
 * вход и выход в сессии ({@code AccountSignIn}), активные сессии аккаунта ({@code AccountSessions},
 * {@code AccountSession}), лимиты входа и сброса пароля ({@code RateLimits}) и чтение журнала действий клиники
 * ({@code AuditJournal}, {@code AuditQuery}, {@code AuditEntry}).
 * <p>
 * Отдельный named interface, а не {@code api}: здесь инварианты изоляции клиник. Кто пишет клинику в сессию мимо
 * проверки членства в iam, тот отдаёт сессии чужую клинику. Поэтому {@code platform :: identity} разрешён только iam
 * ({@code allowedDependencies} и {@code ModularityTests.ALLOWED}), остальные модули зависят только от
 * {@code platform :: api}, и обращение отсюда из другого модуля роняет {@code ApplicationModules.verify()}.
 */
@NamedInterface("identity")
@NullMarked
package kz.dentify.platform.identity;

import org.jspecify.annotations.NullMarked;

import org.springframework.modulith.NamedInterface;
