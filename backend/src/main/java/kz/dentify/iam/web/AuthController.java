package kz.dentify.iam.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kz.dentify.iam.internal.SignIns;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Вход и выход (IAM-02, IAM-03): JSON, серверная сессия в cookie, CSRF-токен в заголовке и для входа. Правила — в
 * {@link SignIns}. Выдача CSRF-токена — {@code GET /api/v1/auth/csrf} платформы.
 */
@RestController
@RequestMapping(AuthController.PATH)
@Tag(name = "auth", description = "Вход, выход и CSRF-токен")
class AuthController {

	static final String PATH = "/api/v1/auth";

	private final SignIns signIns;

	AuthController(SignIns signIns) {
		this.signIns = signIns;
	}

	@PostMapping("/login")
	@PreAuthorize("permitAll()")
	@Operation(operationId = "login", summary = "Войти по телефону или e-mail и паролю",
			description = "Нужен CSRF-токен: cookie XSRF-TOKEN из GET /api/v1/auth/csrf и её значение в заголовке "
					+ "X-XSRF-TOKEN. При успехе — новая cookie сессии DENTIFY_SESSION (HttpOnly, Secure, SameSite=Lax; "
					+ "с rememberMe — на 30 дней, иначе до закрытия браузера) и новый CSRF-токен. Ответ — как GET "
					+ "/api/v1/me: при одном активном членстве с одним филиалом клиника и филиал уже выбраны (context), "
					+ "иначе context пустой и интерфейс показывает выбор клиники. Неверный пароль и неизвестный логин — "
					+ "один ответ 401 iam.login.invalid. Лимиты: 5 неверных попыток на логин за 15 минут, затем 429 на "
					+ "15 минут даже с верным паролем; 30 попыток с IP в минуту.")
	@ApiResponse(responseCode = "200", description = "Вход выполнен")
	@ApiResponse(responseCode = "401", description = "iam.login.invalid — неверный телефон, e-mail или пароль")
	@ApiResponse(responseCode = "403",
			description = "iam.login.access-disabled — доступ отключён; error.csrf — нет CSRF-токена")
	@ApiResponse(responseCode = "429",
			description = "error.too-many-login-attempts — слишком много попыток; error.too-many-requests — сервер занят "
					+ "проверкой других паролей, пароль не проверен и попыткой не считается. Retry-After в секундах")
	MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		return MeResponse.from(this.signIns.login(body.login(), body.password(), body.rememberMe(), request, response));
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("permitAll()")
	@Operation(operationId = "logout", summary = "Выйти",
			description = "Сессия завершается на сервере сразу и необратимо, cookie сессии и CSRF-токена стираются. "
					+ "Без сессии — тоже 204. Нужен CSRF-токен.")
	@ApiResponse(responseCode = "204", description = "Выход выполнен")
	void logout(HttpServletRequest request, HttpServletResponse response) {
		this.signIns.logout(request, response);
	}

}
