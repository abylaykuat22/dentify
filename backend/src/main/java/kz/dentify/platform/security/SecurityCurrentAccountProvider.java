package kz.dentify.platform.security;

import java.util.Optional;
import java.util.UUID;

import kz.dentify.platform.api.CurrentAccountProvider;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Вошедший аккаунт — из контекста Spring Security: имя {@code Authentication} — id аккаунта, его ставит вход
 * ({@link SessionAccountSignIn}). Без входа и в системной задаче — пусто. По нему аудит заполняет
 * {@code created_by} и {@code updated_by}.
 */
class SecurityCurrentAccountProvider implements CurrentAccountProvider {

	@Override
	public Optional<UUID> currentAccountId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(authentication.getName()));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

}
