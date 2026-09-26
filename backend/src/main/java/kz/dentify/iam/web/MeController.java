package kz.dentify.iam.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kz.dentify.iam.internal.AccountContexts;
import kz.dentify.platform.api.CurrentAccountProvider;
import kz.dentify.platform.api.TenantContext;
import kz.dentify.platform.api.UnauthorizedException;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Вошедший аккаунт (IAM-08): аккаунт, членства и клиника сессии; выбор клиники и филиала. Правила — в
 * {@link AccountContexts}.
 */
@RestController
@RequestMapping(MeController.PATH)
@Tag(name = "account", description = "Свой аккаунт")
class MeController {

	static final String PATH = "/api/v1/me";

	private final AccountContexts contexts;

	private final CurrentAccountProvider accounts;

	MeController(AccountContexts contexts, CurrentAccountProvider accounts) {
		this.contexts = contexts;
		this.accounts = accounts;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	@Operation(operationId = "getMe", summary = "Вошедший аккаунт",
			description = "Аккаунт, членства во всех клиниках (с филиалами, в которые можно войти) и клиника с филиалом "
					+ "сессии. context пустой — интерфейс показывает выбор клиники. Интерфейс перезапрашивает его при "
					+ "фокусе вкладки и после ответа 403.")
	MeResponse me() {
		return MeResponse.from(this.contexts.overview(account(), TenantContext.current().orElse(null)));
	}

	@PostMapping("/context")
	@PreAuthorize("isAuthenticated()")
	@Operation(operationId = "selectContext", summary = "Выбрать клинику и филиал",
			description = "Клиника — из своих активных членств, филиал — из доступных этому членству. Без branchId "
					+ "выбирается единственный доступный филиал, если он один. Дальше все запросы сессии идут в этой "
					+ "клинике. Нужен CSRF-токен.")
	@ApiResponse(responseCode = "200", description = "Клиника выбрана; ответ — как GET /api/v1/me")
	@ApiResponse(responseCode = "403",
			description = "iam.membership.disabled — доступ к клинике отключён; error.csrf — нет CSRF-токена")
	@ApiResponse(responseCode = "404",
			description = "error.not-found — нет членства в клинике или филиал ему недоступен")
	MeResponse selectContext(@Valid @RequestBody SelectContextRequest body, HttpServletRequest request) {
		return MeResponse
			.from(this.contexts.select(account(), body.clinicId(), body.branchId(), request.getSession()));
	}

	private UUID account() {
		return this.accounts.currentAccountId().orElseThrow(UnauthorizedException::new);
	}

}
