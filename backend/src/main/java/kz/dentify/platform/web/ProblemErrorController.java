package kz.dentify.platform.web;

import java.net.URI;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import kz.dentify.platform.api.DentifyException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ошибки вне контроллеров — исключение или {@code sendError} в фильтре — Spring Boot пересылает на
 * {@code /error}. Этот контроллер заменяет стандартный BasicErrorController Boot, чтобы и такие ответы были
 * ProblemDetail с кодом и traceId, как у {@link ProblemDetailsExceptionHandler}, а не JSON Boot с полями timestamp и
 * path. Исключение платформы из фильтра, например {@code NotFoundException}, даёт свой статус и код.
 * <ul>
 * <li>{@code instance} — путь исходного запроса, а не {@code /error};</li>
 * <li>ERROR в лог — только при исключении: {@code sendError} с 5xx без исключения — WARN;</li>
 * <li>прямой запрос на {@code /error}, без пересылки ошибки, — 404 без записи в лог: такого ресурса у API нет, а
 * ERROR на каждый такой запрос позволил бы любому засыпать лог и поднять алерты.</li>
 * </ul>
 */
@Hidden
@RestController
@RequestMapping("${server.error.path:${error.path:/error}}")
class ProblemErrorController implements ErrorController {

	private static final Logger log = LoggerFactory.getLogger(ProblemErrorController.class);

	private final ProblemFactory problems;

	ProblemErrorController(ProblemFactory problems) {
		this.problems = problems;
	}

	@RequestMapping
	ResponseEntity<Object> error(HttpServletRequest request) {
		Throwable ex = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
		Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		if (ex == null && statusCode == null) {
			return this.problems.response(HttpStatus.NOT_FOUND, new HttpHeaders());
		}
		ResponseEntity<Object> response;
		if (ex instanceof DentifyException dentify) {
			response = this.problems.response(dentify);
		}
		else {
			HttpStatus status = status(statusCode);
			if (ex != null) {
				log.error("Исключение вне обработчиков MVC, ответ {}", status.value(), ex);
			}
			else if (status.is5xxServerError()) {
				log.warn("Ответ {} без исключения вне обработчиков MVC", status.value());
			}
			response = this.problems.response(status, new HttpHeaders());
		}
		if (response.getBody() instanceof ProblemDetail problem) {
			problem.setInstance(originalUri(request));
		}
		return response;
	}

	private static HttpStatus status(@Nullable Object code) {
		HttpStatus status = (code instanceof Integer value) ? HttpStatus.resolve(value) : null;
		return (status != null && status.isError()) ? status : HttpStatus.INTERNAL_SERVER_ERROR;
	}

	/** Путь запроса, на котором случилась ошибка; null — Spring MVC подставит путь текущего запроса. */
	private static @Nullable URI originalUri(HttpServletRequest request) {
		if (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String uri) {
			try {
				return URI.create(uri);
			}
			catch (IllegalArgumentException ex) {
				return null;
			}
		}
		return null;
	}

}
