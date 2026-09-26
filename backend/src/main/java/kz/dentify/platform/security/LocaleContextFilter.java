package kz.dentify.platform.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Язык запроса для ответов из фильтров Spring Security (401, 403, 429): тот же {@code LocaleResolver} платформы, что
 * у DispatcherServlet, — язык аккаунта, cookie, {@code Accept-Language}, с учётом флага {@code dentify.i18n.kk}. Без
 * него до DispatcherServlet действует язык {@code RequestContextFilter} Boot — голый {@code Accept-Language}.
 * Стоит после загрузки контекста безопасности: язык аккаунта известен только после неё.
 */
class LocaleContextFilter extends OncePerRequestFilter {

	private final LocaleResolver localeResolver;

	LocaleContextFilter(LocaleResolver localeResolver) {
		this.localeResolver = localeResolver;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		@Nullable LocaleContext previous = LocaleContextHolder.getLocaleContext();
		LocaleContextHolder.setLocaleContext(new SimpleLocaleContext(this.localeResolver.resolveLocale(request)));
		try {
			chain.doFilter(request, response);
		}
		finally {
			LocaleContextHolder.setLocaleContext(previous);
		}
	}

}
