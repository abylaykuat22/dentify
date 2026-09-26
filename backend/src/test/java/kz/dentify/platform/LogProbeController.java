package kz.dentify.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Пробный эндпоинт только для тестов ({@code src/test}): пишет в лог строку с вымышленными ИИН, телефоном и
 * e-mail внутри HTTP-запроса. Top-level класс в пакете приложения — его подхватывает компонент-скан во всех
 * интеграционных тестах, поэтому {@code @Import} не нужен и контекст остаётся один (см.
 * {@code AbstractIntegrationTest}).
 */
@RestController
class LogProbeController {

	static final String PATH = "/test/log-probe";

	static final String MARKER = "log-probe";

	private static final Logger log = LoggerFactory.getLogger(LogProbeController.class);

	@GetMapping(PATH)
	String probe() {
		// вымышленные данные
		log.info("{}: ИИН {} телефон {} почта {}", MARKER, "900101300123", "+77011234567", "aigerim@example.kz");
		return "ok";
	}

}
