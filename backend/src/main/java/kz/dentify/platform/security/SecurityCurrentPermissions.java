package kz.dentify.platform.security;

import kz.dentify.platform.api.CurrentPermissions;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Права текущей работы — authorities контекста Spring Security: то же, что проверяет {@code hasAuthority} в
 * {@code @PreAuthorize}. Без входа и в фоновой задаче прав нет.
 */
class SecurityCurrentPermissions implements CurrentPermissions {

	@Override
	public boolean has(String permission) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return false;
		}
		for (GrantedAuthority authority : authentication.getAuthorities()) {
			if (permission.equals(authority.getAuthority())) {
				return true;
			}
		}
		return false;
	}

}
