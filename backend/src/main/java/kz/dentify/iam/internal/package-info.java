/**
 * Внутренности модуля iam: аккаунты ({@code Accounts}), членство в клиниках ({@code Memberships}), свои членства
 * аккаунта во всех клиниках ({@code AccountMemberships}), вход и выход ({@code SignIns}), журнал входов
 * ({@code AuthEvents}), вошедший аккаунт и клиника сессии ({@code AccountContexts}), одноразовые токены ссылок сброса
 * пароля и приглашения ({@code OneTimeTokens}), dev-сид владельца демо-клиники ({@code DemoOwnerSeed}). Другим модулям
 * не открыт.
 */
@NullMarked
package kz.dentify.iam.internal;

import org.jspecify.annotations.NullMarked;
