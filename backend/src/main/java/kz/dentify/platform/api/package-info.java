/**
 * Публичный API платформы для всех модулей: исключения с кодами ошибок и их HTTP-статусы
 * ({@code DentifyException} и подклассы, {@code FieldViolation}, {@code ErrorCodes}), соглашения списков
 * ({@code PageQuery}, {@code SortBy}, {@code PageResponse}), двуязычный {@code LocalizedText}, нормализованные телефон
 * и e-mail ({@code PhoneNumber}, {@code EmailAddress}), языки и точка расширения языка аккаунта
 * ({@code AccountLocaleProvider}). Тенант-контекст: {@code TenantContext} и {@code Tenant}, события клиники
 * ({@code TenantEvent}), базовые сущности ({@code TenantEntity}, {@code GlobalEntity}) и точки расширения аудита
 * ({@code CurrentAccountProvider}, {@code AccountNameProvider}). Безопасность: правила пароля ({@code PasswordPolicy}),
 * права текущей работы ({@code CurrentPermissions}, {@code Permissions}). Журнал действий: запись
 * ({@code AuditService}, {@code Audited}, {@code AuditEvent}, {@code AuditSnapshots}, {@code AuditSecret},
 * {@code AuditActions}, точка расширения {@code CurrentStaffProvider}).
 * <p>
 * Клиника в сессии, вход и выход, активные сессии, лимиты входа и чтение журнала — не здесь, а в named interface
 * {@code platform :: identity}: их вызывает только iam.
 */
@NamedInterface("api")
@NullMarked
package kz.dentify.platform.api;

import org.jspecify.annotations.NullMarked;

import org.springframework.modulith.NamedInterface;
