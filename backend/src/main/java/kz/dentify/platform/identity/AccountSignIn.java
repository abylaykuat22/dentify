package kz.dentify.platform.identity;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Вход и выход в серверной сессии (PLT-04). Пароль, блокировки и журнал входов проверяет вызывающий — вход модуля iam
 * (T-IAM-02); здесь только то, что делает из проверенного аккаунта сессию:
 * <ul>
 * <li>новый id сессии — защита от фиксации: id, известный до входа, после входа недействителен;</li>
 * <li>новый CSRF-токен в cookie {@code XSRF-TOKEN}, если старый был;</li>
 * <li>аккаунт в контексте Spring Security и в сессии, по нему сессия попадает в индекс аккаунта, см.
 * {@link AccountSessions};</li>
 * <li>браузер, IP и время входа — для списка активных сессий;</li>
 * <li>Запомнить: сессия живёт 30 дней от входа, как бы активно ею ни пользовались, cookie постоянная на тот же срок;
 * продлевает срок только новый вход. Без галочки cookie сессионная, до закрытия браузера, а сессия обрывается после
 * 12 часов бездействия и в любом случае через 30 дней от входа. После срока запрос с cookie — 401
 * {@code error.session-expired}.</li>
 * </ul>
 * Клиника, выбранная в этой сессии до входа, снимается; клинику нового входа кладёт {@link TenantSession} после
 * проверки членства.
 * <p>
 * Вход, шедший одновременно с деактивацией или сменой пароля, {@link AccountSessions#terminateAll} гасит, только если
 * его время входа не позже вызова. Поэтому вызывающий после {@link #signIn} перечитывает аккаунт новым запросом к БД —
 * статус и версию учётных данных — и, если они изменились с проверенных, выходит ({@link #signOut}) и отвечает 401
 * (docs/architecture.md, Активные сессии).
 */
public interface AccountSignIn {

	/** Войти аккаунтом, пароль которого уже проверен. */
	void signIn(HttpServletRequest request, HttpServletResponse response, UUID accountId, boolean rememberMe);

	/** Выйти: сессия уничтожается, cookie сессии и CSRF-токен стираются. */
	void signOut(HttpServletRequest request, HttpServletResponse response);

}
