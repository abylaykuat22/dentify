package kz.dentify.platform.security;

import java.util.Collection;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Только для тестов: права вошедшему аккаунту, пока их не выдаёт iam (T-IAM). Заменяет {@code Authentication} в
 * контексте на такую же — тот же аккаунт — с authorities и сохраняет контекст в сессию тем же хранилищем, что у цепочек
 * приложения.
 */
public final class TestAuthorities {

	private TestAuthorities() {
	}

	/** Выдать права аккаунту, который только что вошёл через {@code AccountSignIn} в этом запросе. */
	public static void grant(HttpServletRequest request, HttpServletResponse response, Collection<String> authorities) {
		Authentication current = SecurityContextHolder.getContext().getAuthentication();
		if (current == null) {
			throw new IllegalStateException("Права выдаются после входа");
		}
		Authentication granted = UsernamePasswordAuthenticationToken.authenticated(current.getName(), null,
				AuthorityUtils.createAuthorityList(authorities));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(granted);
		SecurityContextHolder.setContext(context);
		SecurityConfiguration.CONTEXT_REPOSITORY.saveContext(context, request, response);
	}

}
