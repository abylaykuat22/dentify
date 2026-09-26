package kz.dentify.platform.tenant;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.identity.TenantSession;
import org.jspecify.annotations.Nullable;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Контекст клиники HTTP-запроса — из сессии ({@link TenantSession}), и только из неё: заголовки, параметры и тело
 * запроса клинику не выбирают. Нет сессии или клиники в ней — запрос идёт без контекста, и данные клиники ему
 * недоступны.
 * <p>
 * Контекст ставится на весь запрос, включая обработку ошибок в {@code @RestControllerAdvice}, и снимается в конце,
 * даже если запрос упал: поток Tomcat уходит в пул чистым. Фильтр стоит после фильтров Spring Session и Spring
 * Security, чтобы вход (T-IAM-02) успел проверить сессию.
 */
class TenantContextFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		@Nullable Tenant tenant = (session != null) ? TenantSession.read(session).orElse(null) : null;
		@Nullable Tenant previous = TenantContextHolder.get();
		TenantContextHolder.set(tenant);
		try {
			chain.doFilter(request, response);
		}
		finally {
			TenantContextHolder.set(previous);
		}
	}

}
