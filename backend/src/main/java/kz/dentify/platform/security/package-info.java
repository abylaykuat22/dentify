/**
 * Безопасность платформы (PLT-04, PLT-05, PLT-06): цепочки Spring Security, сессии Spring Session в Valkey, вход и
 * выход в сессии, активные сессии аккаунта, CSRF для SPA, хэш и правила пароля, лимиты частоты Bucket4j, заголовки
 * безопасности. Внутренний пакет модуля platform: модули работают через {@code PasswordPolicy} и
 * {@code CurrentAccountProvider} из {@code platform.api} и бин Spring Security {@code PasswordEncoder}, а iam — ещё и
 * через {@code AccountSignIn}, {@code AccountSessions} и {@code RateLimits} из {@code platform.identity}.
 */
@NullMarked
package kz.dentify.platform.security;

import org.jspecify.annotations.NullMarked;
