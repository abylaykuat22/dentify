package kz.dentify.platform.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

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
 * Срок сессии от входа (PLT-04, {@link SessionLifetime}). Spring Session сам гасит сессию только по бездействию, а
 * срока от входа у него нет: с Запомнить сессия продлевалась бы каждым запросом, и украденная cookie жила бы
 * бессрочно. Поэтому на каждом запросе с вошедшей сессией:
 * <ul>
 * <li>срок от входа истёк — сессия уничтожается, контекст безопасности снимается, запрос идёт дальше без входа: закрытый
 * путь получит 401 {@code error.session-expired} ({@link ProblemSecurityHandler}), вход и выдача CSRF-токена
 * работают, ответ стирает cookie сессии;</li>
 * <li>иначе бездействие сессии в Valkey не заходит за срок от входа: ключ сессии истечёт не позже него, и сессия уйдёт
 * из индекса аккаунта, даже если с неё больше не придут. Бездействие пишется не на каждом запросе, а только когда
 * записанное ушло из окна ({@link #nextMaxInactive}): каждая запись — изменение сессии, которое конец запроса
 * отправляет в Valkey.</li>
 * </ul>
 * Стоит в цепочке API после записи заголовков безопасности, 401 уходит с ними, и после проверки отзыва
 * ({@link SessionRevocationFilter}): отозванной сессии бездействие не пишется.
 */
class SessionLifetimeFilter extends OncePerRequestFilter {

	/**
	 * Запас при записи бездействия, секунды: пишется на минуту короче нужного, поэтому следующая запись — не раньше чем
	 * через минуту.
	 */
	static final int REWRITE_SLACK_SECONDS = 60;

	private static final Logger log = LoggerFactory.getLogger(SessionLifetimeFilter.class);

	private final SessionLifetime lifetime;

	private final Clock clock;

	private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

	SessionLifetimeFilter(SessionLifetime lifetime, Clock clock) {
		this.lifetime = lifetime;
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session != null && session.getAttribute(SessionClients.SIGNED_IN_AT) instanceof Instant signedInAt) {
			boolean rememberMe = Boolean.TRUE.equals(session.getAttribute(SessionClients.REMEMBER_ME));
			Instant now = this.clock.instant();
			if (this.lifetime.expired(signedInAt, rememberMe, now)) {
				session.invalidate();
				this.holder.clearContext();
				request.setAttribute(ProblemSecurityHandler.SESSION_ENDED_ATTRIBUTE, Boolean.TRUE);
				log.debug("Сессия погашена по сроку от входа, Запомнить: {}", rememberMe);
			}
			else {
				int stored = session.getMaxInactiveInterval();
				int next = nextMaxInactive(stored, seconds(this.lifetime.maxInactive(signedInAt, rememberMe, now)));
				if (next != stored) {
					session.setMaxInactiveInterval(next);
				}
			}
		}
		chain.doFilter(request, response);
	}

	/**
	 * Бездействие сессии, которое записать: прежнее, если оно не длиннее нужного (ключ не переживёт срок от входа) и
	 * короче не больше чем на две минуты; иначе нужное без минуты запаса. Без Запомнить нужное — 12 часов, пока до срока
	 * от входа дальше, и запись идёт только в последние 12 часов; с Запомнить нужное — остаток до срока, он убывает
	 * каждую секунду, а запись — раз в минуту. В последнюю минуту — без запаса: сессия не гаснет раньше срока.
	 *
	 * @param stored записанное бездействие, секунды
	 * @param desired нужное бездействие, секунды
	 */
	static int nextMaxInactive(int stored, int desired) {
		if (stored <= desired && stored >= desired - 2 * REWRITE_SLACK_SECONDS) {
			return stored;
		}
		return (desired > REWRITE_SLACK_SECONDS) ? desired - REWRITE_SLACK_SECONDS : desired;
	}

	/** Секунды с округлением вверх: сессия не гаснет раньше срока. */
	static int seconds(Duration duration) {
		return Math.toIntExact((duration.toMillis() + 999) / 1000);
	}

}
