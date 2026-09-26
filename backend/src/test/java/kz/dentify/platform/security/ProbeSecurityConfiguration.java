package kz.dentify.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Цепочка Spring Security только для тестов: пробные эндпоинты {@code /test/**} (формат ошибок, тенант-контекст, логи)
 * открыты без входа и CSRF — они проверяют платформу, а не безопасность. Контекст безопасности из сессии эта цепочка
 * загружает как обычно, поэтому вход через {@code TenantSessionProbeController} даёт аудиту автора. Безопасность
 * проверяется на цепочке приложения: {@code SecuredProbeController}, {@code /api/v1/**}.
 * <p>
 * В рабочем приложении путей {@code /test/**} нет, и они попали бы под общую цепочку: без входа — 401.
 */
@Configuration(proxyBeanMethods = false)
class ProbeSecurityConfiguration {

	/** Раньше цепочки API: у неё нет {@code securityMatcher}. */
	static final int ORDER = SecurityConfiguration.API_ORDER - 1;

	@Bean
	@Order(ORDER)
	SecurityFilterChain probeSecurity(HttpSecurity http) throws Exception {
		return http.securityMatcher("/test/**")
			.authorizeHttpRequests((requests) -> requests.anyRequest().permitAll())
			.csrf(AbstractHttpConfigurer::disable)
			.requestCache((cache) -> cache.requestCache(new NullRequestCache()))
			.securityContext((context) -> context.securityContextRepository(SecurityConfiguration.CONTEXT_REPOSITORY))
			.build();
	}

}
