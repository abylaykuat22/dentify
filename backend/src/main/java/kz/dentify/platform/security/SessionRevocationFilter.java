package kz.dentify.platform.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Отозванная сессия не пускает (PLT-04, {@link SessionRevocations}). Завершённую сессию может записать обратно в
 * Valkey запрос, который шёл в момент завершения; поэтому на каждом запросе с сессией проверяется отметка отзыва —
 * сессии и её аккаунта. Отозвана — сессия уничтожается снова ({@code invalidate()}: изменения этого запроса в Valkey не
 * пишутся, сессия уходит из индекса аккаунта), контекст безопасности снимается, запрос идёт дальше без входа: закрытый
 * путь получит 401 {@code error.session-expired}, вход работает, ответ стирает cookie сессии.
 * <p>
 * Стоит в цепочке API после записи заголовков безопасности и раньше {@link SessionLifetimeFilter}: тот пишет
 * бездействие сессии, а отозванной писать нечего.
 */
class SessionRevocationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(SessionRevocationFilter.class);

	private final SessionRevocations revocations;

	private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

	SessionRevocationFilter(SessionRevocations revocations) {
		this.revocations = revocations;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session != null && this.revocations.isRevoked(session)) {
			String account = SessionRevocations.account(session);
			session.invalidate();
			this.holder.clearContext();
			request.setAttribute(ProblemSecurityHandler.SESSION_ENDED_ATTRIBUTE, Boolean.TRUE);
			log.info("Запрос отозванной сессии аккаунта {}: сессия уничтожена снова", account);
		}
		chain.doFilter(request, response);
	}

}
