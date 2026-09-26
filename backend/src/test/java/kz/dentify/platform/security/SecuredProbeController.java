package kz.dentify.platform.security;

import java.util.Map;

import kz.dentify.platform.api.CurrentAccountProvider;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Пробные эндпоинты только для тестов под цепочкой приложения, вне {@code /test/**} и {@code /api/**}: без входа — 401,
 * изменяющий запрос без CSRF — 403, {@code @PreAuthorize} без права — 403. Путь вне {@code /api/v1}, в спецификацию
 * OpenAPI не попадают.
 */
@RestController
@RequestMapping(SecuredProbeController.PATH)
class SecuredProbeController {

	static final String PATH = "/secured-probe";

	private final CurrentAccountProvider accounts;

	SecuredProbeController(CurrentAccountProvider accounts) {
		this.accounts = accounts;
	}

	/** Кто вошёл: id аккаунта из контекста Spring Security. */
	@GetMapping("/me")
	@PreAuthorize("isAuthenticated()")
	Map<String, String> me() {
		return Map.of("accountId", this.accounts.currentAccountId().map(Object::toString).orElse(""));
	}

	@PostMapping("/echo")
	@PreAuthorize("isAuthenticated()")
	Map<String, Object> echo(@RequestBody Map<String, Object> body) {
		return body;
	}

	@GetMapping("/denied")
	@PreAuthorize("hasAuthority('probe:never-granted')")
	void denied() {
	}

}
