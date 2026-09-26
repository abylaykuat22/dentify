package kz.dentify.platform.security;

import java.io.IOException;
import java.time.Duration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kz.dentify.platform.api.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Общий лимит API (PLT-05): не больше 600 запросов в минуту на сессию, запросы без сессии — на IP. Стоит в цепочке
 * Spring Security до проверки CSRF и прав, поэтому считаются и отклонённые запросы. Превышение — 429 ProblemDetail с
 * {@code Retry-After} через общий обработчик ошибок.
 */
class ApiRateLimitFilter extends OncePerRequestFilter {

	static final String API_PREFIX = "/api/";

	private static final Logger log = LoggerFactory.getLogger(ApiRateLimitFilter.class);

	private final RedisRateLimiter limiter;

	private final HandlerExceptionResolver errors;

	ApiRateLimitFilter(RedisRateLimiter limiter, HandlerExceptionResolver errors) {
		this.limiter = limiter;
		this.errors = errors;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith(request.getContextPath() + API_PREFIX);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		String client = (session != null) ? "session:" + session.getId() : "ip:" + request.getRemoteAddr();
		Duration retryAfter = this.limiter.consumeApi(client);
		if (retryAfter == null) {
			chain.doFilter(request, response);
			return;
		}
		log.debug("Превышен общий лимит API, ответ 429, повтор через {} с", retryAfter.toSeconds());
		this.errors.resolveException(request, response, null, new TooManyRequestsException(retryAfter));
	}

}
