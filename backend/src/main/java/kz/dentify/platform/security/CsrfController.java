package kz.dentify.platform.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CSRF-токен для SPA: при запуске интерфейс вызывает этот эндпоинт и получает cookie {@code XSRF-TOKEN}, а затем
 * каждый POST, PUT, PATCH и DELETE шлёт её значение в заголовке {@code X-XSRF-TOKEN}. Токен кладёт в cookie сама
 * цепочка Spring Security в режиме {@code csrf.spa()} на любом ответе, у которого cookie ещё нет, так что эндпоинт
 * только даёт для этого явный запрос до входа. После входа и выхода токен меняется, новая cookie приходит в том же
 * ответе.
 */
@RestController
@Tag(name = "auth", description = "Вход, выход и CSRF-токен")
class CsrfController {

	static final String PATH = "/api/v1/auth/csrf";

	@GetMapping(PATH)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("permitAll()")
	@Operation(operationId = "getCsrfToken", summary = "Выдать CSRF-токен",
			description = "Ставит cookie XSRF-TOKEN, если её нет. Её значение отправляется в заголовке X-XSRF-TOKEN "
					+ "с каждым изменяющим запросом, иначе ответ — 403 error.csrf.")
	@ApiResponse(responseCode = "204", description = "Cookie XSRF-TOKEN установлена или уже была")
	void csrf() {
		// токен в cookie кладёт цепочка Spring Security: csrf.spa() загружает его на каждом запросе
	}

}
