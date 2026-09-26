package kz.dentify.platform.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kz.dentify.platform.identity.AccountSignIn;
import kz.dentify.platform.identity.TenantSession;
import kz.dentify.platform.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;

/**
 * Вход и выход в сессии Spring Session по рецепту Spring Security для своего эндпоинта входа: контекст с
 * {@code Authentication} сохраняется в {@code SecurityContextRepository}, до этого — стратегия сессии, как у
 * стандартных фильтров входа: смена id сессии и CSRF-токена.
 * <p>
 * В контексте — {@code UsernamePasswordAuthenticationToken} с именем, равным id аккаунта, без пароля и без прав. Права
 * по клинике в сессию не кладутся: сессия живёт до 30 дней, и права в ней пережили бы смену роли и смену клиники.
 * Эффективные права считаются на запрос по клинике контекста и членству (docs/architecture.md, Права). По имени Spring
 * Session ведёт индекс сессий аккаунта. В сессии только классы Spring
 * и JDK, сессия не зависит от классов приложения. Клиент — IP и User-Agent — записывается при входе
 * ({@link SessionClients}). Клиника прежнего входа снимается и с сессии, и с потока запроса: её выбирает вход после
 * проверки членства.
 * <p>
 * Срок сессии — {@link SessionLifetime}: без Запомнить — 12 часов бездействия, с Запомнить — 30 дней от входа, и
 * cookie с тем же сроком. Срок от входа проверяет {@link SessionLifetimeFilter}. Выход необратим: сессия отзывается
 * ({@link SessionRevocations}) до уничтожения.
 */
class SessionAccountSignIn implements AccountSignIn {

	private static final Logger log = LoggerFactory.getLogger(SessionAccountSignIn.class);

	private final SessionAuthenticationStrategy sessionStrategy;

	private final SecurityContextRepository contexts;

	private final CsrfLogoutHandler csrfLogout;

	private final SessionLifetime lifetime;

	private final SessionRevocations revocations;

	private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

	SessionAccountSignIn(SessionAuthenticationStrategy sessionStrategy, SecurityContextRepository contexts,
			CsrfTokenRepository csrfTokens, SessionLifetime lifetime, SessionRevocations revocations) {
		this.sessionStrategy = sessionStrategy;
		this.contexts = contexts;
		this.csrfLogout = new CsrfLogoutHandler(csrfTokens);
		this.lifetime = lifetime;
		this.revocations = revocations;
	}

	@Override
	public void signIn(HttpServletRequest request, HttpServletResponse response, UUID accountId, boolean rememberMe) {
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(accountId.toString(), null,
				List.of());
		this.sessionStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = this.holder.createEmptyContext();
		context.setAuthentication(authentication);
		this.holder.setContext(context);
		this.contexts.saveContext(context, request, response);

		HttpSession session = request.getSession();
		// смена id сохраняет атрибуты сессии: клиника прежнего входа в этом браузере не должна достаться новому
		// аккаунту без проверки членства — её выбирает вход после signIn
		TenantSession.clear(session);
		// и контекст потока: его поставил TenantContextFilter из сессии прежнего аккаунта, и до конца запроса входа
		// событие аудита или обработчик входа писали бы в ту клинику. Фильтр вернёт прежнее значение в finally
		leavePreviousClinic();
		// время входа — по часам Valkey, как T отметки аккаунта (SessionRevocations): их сравнение не зависит от часов
		// экземпляров
		Instant now = this.revocations.now();
		SessionClients.record(session, request, now, rememberMe);
		// срок от входа отсчитывается заново: новый вход продлевает Запомнить на полный срок
		session.setMaxInactiveInterval(SessionLifetimeFilter.seconds(this.lifetime.maxInactive(now, rememberMe, now)));
		if (rememberMe) {
			// cookie с Max-Age на срок Запомнить (SessionCookieSerializer)
			request.setAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR, Boolean.TRUE);
		}
		log.debug("Вход аккаунта {} в сессию, Запомнить: {}", accountId, rememberMe);
	}

	/**
	 * Снять клинику прежнего входа с потока запроса. Внутри транзакции нельзя: её соединение и сессия Hibernate уже
	 * открыты в той клинике (docs/architecture.md, Контекст клиники) — вход идёт вне транзакции.
	 */
	private static void leavePreviousClinic() {
		if (TenantContextHolder.get() == null) {
			return;
		}
		if (TenantContextHolder.pinnedByTransaction()) {
			throw new IllegalStateException("Вход внутри транзакции в контексте клиники: клинику прежнего входа не снять");
		}
		TenantContextHolder.set(null);
	}

	/**
	 * Выход необратим: отметка отзыва до уничтожения сессии ({@link SessionRevocations}), иначе запрос этой сессии,
	 * который шёл одновременно с выходом, в конце записал бы её обратно.
	 */
	@Override
	public void signOut(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			this.revocations.revoke(session);
		}
		Authentication authentication = this.holder.getContext().getAuthentication();
		SecurityContextLogoutHandler logout = new SecurityContextLogoutHandler();
		logout.setSecurityContextRepository(this.contexts);
		logout.logout(request, response, authentication);
		this.csrfLogout.logout(request, response, authentication);
		log.debug("Выход из сессии");
	}

}
