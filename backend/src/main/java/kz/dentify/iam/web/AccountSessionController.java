package kz.dentify.iam.web;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import kz.dentify.iam.internal.AccountSessionCommands;
import kz.dentify.platform.api.CurrentAccountProvider;
import kz.dentify.platform.api.UnauthorizedException;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Активные сессии своего аккаунта (PLT-04, экран профиля): список с браузером, IP маской и городом, завершить одну,
 * все остальные или все. Правила — в {@link AccountSessionCommands}, сессии хранит платформа; завершённая сессия гаснет
 * сразу, следующий запрос с неё — 401 {@code error.session-expired}.
 */
@RestController
@RequestMapping(AccountSessionController.PATH)
@Tag(name = "account", description = "Свой аккаунт")
class AccountSessionController {

	static final String PATH = "/api/v1/account/sessions";

	private final AccountSessionCommands sessions;

	private final CurrentAccountProvider accounts;

	AccountSessionController(AccountSessionCommands sessions, CurrentAccountProvider accounts) {
		this.sessions = sessions;
		this.accounts = accounts;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	@Operation(operationId = "listAccountSessions", summary = "Активные сессии своего аккаунта",
			description = "Сначала последние по активности; текущая помечена current. Список короткий — по одной "
					+ "сессии на браузер, поэтому без страниц и поиска.")
	List<AccountSessionResponse> list(HttpServletRequest request) {
		return this.sessions.list(account(), currentSessionId(request))
			.stream()
			.map(AccountSessionResponse::from)
			.toList();
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("isAuthenticated()")
	@Operation(operationId = "terminateAccountSession", summary = "Завершить сессию",
			description = "Сессия гаснет сразу. Текущую тоже можно завершить — это выход. Чужая или уже завершённая "
					+ "сессия — 404.")
	@ApiResponse(responseCode = "204", description = "Сессия завершена")
	void terminate(@Parameter(description = "Открытый id сессии из списка") @PathVariable String id) {
		this.sessions.terminate(account(), id);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("isAuthenticated()")
	@Operation(operationId = "terminateAccountSessions", summary = "Завершить сессии аккаунта",
			description = "По умолчанию — все, кроме текущей. С includeCurrent=true — все, включая текущую: выход везде.")
	@ApiResponse(responseCode = "204", description = "Сессии завершены")
	void terminateAll(HttpServletRequest request, @Parameter(description = "Завершить и текущую сессию")
	@RequestParam(defaultValue = "false") boolean includeCurrent) {
		this.sessions.terminateAll(account(), currentSessionId(request), includeCurrent);
	}

	private UUID account() {
		return this.accounts.currentAccountId().orElseThrow(UnauthorizedException::new);
	}

	private static @Nullable String currentSessionId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		return (session != null) ? session.getId() : null;
	}

}
