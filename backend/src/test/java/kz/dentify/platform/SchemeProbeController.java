package kz.dentify.platform;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Пробный эндпоинт только для тестов: как приложение видит схему запроса и адрес клиента за прокси. Top-level класс в
 * тестовых исходниках — контекст остаётся один (см. {@code AbstractIntegrationTest}).
 */
@RestController
class SchemeProbeController {

	static final String PATH = "/test/scheme";

	/** Адрес клиента, как его видят лимиты по IP и журналы. */
	static final String CLIENT = PATH + "/client";

	@GetMapping(PATH)
	String scheme(HttpServletRequest request) {
		return request.getScheme() + " secure=" + request.isSecure();
	}

	@GetMapping(CLIENT)
	String client(HttpServletRequest request) {
		return request.getRemoteAddr();
	}

}
