package kz.dentify.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kz.dentify.platform.api.DentifyException;
import kz.dentify.platform.api.ErrorCodes;
import kz.dentify.platform.api.ForbiddenException;
import kz.dentify.platform.api.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Отказы Spring Security в фильтрах — тот же ProblemDetail, что у контроллеров: без редиректов на страницу входа и без
 * заголовка {@code WWW-Authenticate}. Исключение уходит в {@code HandlerExceptionResolver}, его обрабатывает общий
 * {@code @RestControllerAdvice} платформы — текст по коду на языке запроса и traceId.
 * <ul>
 * <li>нет входа — 401 {@code error.unauthorized}; cookie сессии есть, а сессии уже нет или истёк её срок от
 * входа — 401 {@code error.session-expired};</li>
 * <li>нет CSRF-токена или он чужой — 403 {@code error.csrf};</li>
 * <li>нет права — 403 {@code error.forbidden}.</li>
 * </ul>
 */
class ProblemSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	/**
	 * Атрибут запроса: фильтр погасил сессию этого запроса — отозвана или истёк срок от входа. Без него запрос не
	 * отличить от первого входа: {@code isRequestedSessionIdValid()} Spring Session после {@code invalidate()} остаётся
	 * true.
	 */
	static final String SESSION_ENDED_ATTRIBUTE = ProblemSecurityHandler.class.getName() + ".SESSION_ENDED";

	private static final Logger log = LoggerFactory.getLogger(ProblemSecurityHandler.class);

	private final HandlerExceptionResolver errors;

	ProblemSecurityHandler(HandlerExceptionResolver errors) {
		this.errors = errors;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) {
		respond(request, response, unauthorized(request));
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) {
		DentifyException problem = (accessDeniedException instanceof CsrfException)
				? new ForbiddenException(ErrorCodes.CSRF) : new ForbiddenException();
		respond(request, response, problem);
	}

	/**
	 * 401 с кодом по тому, была ли у запроса сессия: истёкшую интерфейс показывает иначе, чем первый вход. Истёкшая —
	 * cookie сессии есть, а сессии нет (бездействие, выход, завершение) или сессию погасили в этом же запросе: она
	 * отозвана ({@link SessionRevocationFilter}) или истёк её срок от входа ({@link SessionLifetimeFilter}).
	 */
	static UnauthorizedException unauthorized(HttpServletRequest request) {
		boolean expired = request.getAttribute(SESSION_ENDED_ATTRIBUTE) != null
				|| (request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid());
		return expired ? new UnauthorizedException(ErrorCodes.SESSION_EXPIRED) : new UnauthorizedException();
	}

	private void respond(HttpServletRequest request, HttpServletResponse response, DentifyException problem) {
		log.debug("Отказ Spring Security: {}", problem.getCode());
		this.errors.resolveException(request, response, null, problem);
	}

}
