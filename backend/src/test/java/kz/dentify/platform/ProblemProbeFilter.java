package kz.dentify.platform;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kz.dentify.platform.api.NotFoundException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Пробный фильтр только для тестов: ошибки до DispatcherServlet, которые Spring Boot пересылает на {@code /error}.
 * Реагирует только на свои пути, остальные запросы пропускает. Top-level класс — контекст остаётся один.
 */
@Component
class ProblemProbeFilter extends OncePerRequestFilter {

	static final String FAILURE = "/test/filter-problems/failure";

	static final String SEND_ERROR = "/test/filter-problems/send-error";

	static final String NOT_FOUND = "/test/filter-problems/not-found";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		switch (request.getRequestURI()) {
			case FAILURE -> throw new IllegalStateException("probe-filter-failure");
			case SEND_ERROR -> response.sendError(HttpServletResponse.SC_FORBIDDEN);
			case NOT_FOUND -> throw new NotFoundException();
			default -> chain.doFilter(request, response);
		}
	}

}
