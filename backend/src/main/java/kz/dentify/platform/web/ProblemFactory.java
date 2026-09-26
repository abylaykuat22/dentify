package kz.dentify.platform.web;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import kz.dentify.platform.api.BusinessRuleException;
import kz.dentify.platform.api.ConflictException;
import kz.dentify.platform.api.DentifyException;
import kz.dentify.platform.api.ErrorCodes;
import kz.dentify.platform.api.FieldViolation;
import kz.dentify.platform.api.ForbiddenException;
import kz.dentify.platform.api.InvalidRequestException;
import kz.dentify.platform.api.NotFoundException;
import kz.dentify.platform.api.TooManyRequestsException;
import kz.dentify.platform.api.UnauthorizedException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Собирает ответ об ошибке в формате ProblemDetail по RFC 9457 с расширениями Dentify:
 * <ul>
 * <li>{@code code} — машинный код, он же ключ сообщения;</li>
 * <li>{@code detail} — текст по коду из {@code MessageSource} на языке запроса;</li>
 * <li>{@code title} — заголовок по статусу, ключ {@code problem.title.<статус>};</li>
 * <li>{@code traceId} — идентификатор трассировки запроса, тот же, что в логах;</li>
 * <li>{@code errors[]} — ошибки полей, если есть.</li>
 * </ul>
 * Текстов в коде нет: всё из {@code messages.properties}, язык выбирает {@code LocaleResolver} платформы. Нет
 * текста по коду — в {@code detail} идёт общий текст статуса, а код остаётся как был, в лог пишется WARN.
 */
@Component
public class ProblemFactory {

	/** Расширение ProblemDetail: машинный код. */
	public static final String CODE = "code";

	/** Расширение ProblemDetail: идентификатор трассировки запроса. */
	public static final String TRACE_ID = "traceId";

	/** Расширение ProblemDetail: ошибки полей. */
	public static final String ERRORS = "errors";

	/** Расширение ProblemDetail конфликта версии: время последней правки записи. */
	public static final String MODIFIED_AT = "modifiedAt";

	/** Расширение ProblemDetail конфликта версии: id аккаунта автора последней правки. */
	public static final String MODIFIED_BY = "modifiedBy";

	/** Расширение ProblemDetail конфликта версии: имя автора последней правки в клинике. */
	public static final String MODIFIED_BY_NAME = "modifiedByName";

	static final String TITLE_PREFIX = "problem.title.";

	private static final Object[] NO_ARGS = {};

	/** Код ответа по статусу, если исключение не принесло своего. */
	private static final Map<Integer, String> STATUS_CODES = Map.ofEntries(Map.entry(400, ErrorCodes.BAD_REQUEST),
			Map.entry(401, ErrorCodes.UNAUTHORIZED), Map.entry(403, ErrorCodes.FORBIDDEN),
			Map.entry(404, ErrorCodes.NOT_FOUND), Map.entry(405, ErrorCodes.METHOD_NOT_ALLOWED),
			Map.entry(406, ErrorCodes.NOT_ACCEPTABLE), Map.entry(409, ErrorCodes.CONFLICT),
			Map.entry(413, ErrorCodes.PAYLOAD_TOO_LARGE), Map.entry(415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE),
			Map.entry(422, ErrorCodes.BUSINESS_RULE), Map.entry(429, ErrorCodes.TOO_MANY_REQUESTS),
			Map.entry(500, ErrorCodes.INTERNAL), Map.entry(503, ErrorCodes.UNAVAILABLE));

	private static final Logger log = LoggerFactory.getLogger(ProblemFactory.class);

	private final MessageSource messages;

	private final ObjectProvider<Tracer> tracer;

	ProblemFactory(MessageSource messages, ObjectProvider<Tracer> tracer) {
		this.messages = messages;
		this.tracer = tracer;
	}

	/** Код по статусу: 404 — {@code error.not-found}; неизвестный 4xx — 400-й, неизвестный 5xx — 500-й. */
	public static String codeFor(HttpStatusCode status) {
		String code = STATUS_CODES.get(status.value());
		if (code != null) {
			return code;
		}
		return status.is5xxServerError() ? ErrorCodes.INTERNAL : ErrorCodes.BAD_REQUEST;
	}

	/** HTTP-статус исключения платформы: его задаёт класс исключения. */
	public static HttpStatus statusOf(DentifyException ex) {
		return switch (ex) {
			case InvalidRequestException _ -> HttpStatus.BAD_REQUEST;
			case UnauthorizedException _ -> HttpStatus.UNAUTHORIZED;
			case ForbiddenException _ -> HttpStatus.FORBIDDEN;
			case NotFoundException _ -> HttpStatus.NOT_FOUND;
			case ConflictException _ -> HttpStatus.CONFLICT;
			case BusinessRuleException _ -> HttpStatus.UNPROCESSABLE_CONTENT;
			case TooManyRequestsException _ -> HttpStatus.TOO_MANY_REQUESTS;
		};
	}

	/** Ответ по исключению платформы: статус по классу, код, аргументы и ошибки полей из исключения. */
	public ResponseEntity<Object> response(DentifyException ex) {
		HttpHeaders headers = new HttpHeaders();
		if (ex instanceof TooManyRequestsException tooMany) {
			headers.set(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, tooMany.getRetryAfter().toSeconds())));
		}
		return response(statusOf(ex), headers, ex.getCode(), ex.getArgs(), fieldErrors(ex.getViolations()));
	}

	/** Ответ с кодом по статусу. */
	public ResponseEntity<Object> response(HttpStatusCode status, HttpHeaders headers) {
		return response(status, headers, codeFor(status), NO_ARGS, List.of());
	}

	public ResponseEntity<Object> response(HttpStatusCode status, HttpHeaders headers, String code, Object[] args,
			List<ProblemFieldError> errors) {
		return response(create(status, code, args, errors), headers);
	}

	/** Ответ с готовым телом, например дополненным расширениями, которые знает только обработчик. */
	public ResponseEntity<Object> response(ProblemDetail problem, HttpHeaders headers) {
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.addAll(headers);
		responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		return ResponseEntity.status(problem.getStatus()).headers(responseHeaders).body(problem);
	}

	public ProblemDetail create(HttpStatusCode status, String code, Object[] args, List<ProblemFieldError> errors) {
		Locale locale = LocaleContextHolder.getLocale();
		ProblemDetail problem = ProblemDetail.forStatus(status);
		problem.setTitle(title(status, locale));
		problem.setDetail(detail(status, code, args, locale));
		problem.setProperty(CODE, code);
		String traceId = traceId();
		if (traceId != null) {
			problem.setProperty(TRACE_ID, traceId);
		}
		if (!errors.isEmpty()) {
			problem.setProperty(ERRORS, List.copyOf(errors));
		}
		return problem;
	}

	/** Ошибки полей из нарушений, найденных сервисом: текст по коду каждого нарушения. */
	public List<ProblemFieldError> fieldErrors(List<FieldViolation> violations) {
		return violations.stream()
			.map((violation) -> new ProblemFieldError(violation.field(), violation.code(),
					message(violation.code(), violation.args().toArray())))
			.toList();
	}

	/** Текст по коду на языке запроса; нет текста — код не подменяется, а текст берётся общий для ошибки поля. */
	public String message(String code, Object[] args) {
		Locale locale = LocaleContextHolder.getLocale();
		String message = this.messages.getMessage(code, args, null, locale);
		if (message != null) {
			return message;
		}
		log.warn("Нет текста для кода ошибки {} в messages.properties", code);
		return this.messages.getMessage(ErrorCodes.FIELD_INVALID, NO_ARGS, code, locale);
	}

	/** Идентификатор трассировки текущего запроса: тот же, что в поле traceId JSON-логов. */
	public @Nullable String traceId() {
		Tracer current = this.tracer.getIfAvailable();
		Span span = (current != null) ? current.currentSpan() : null;
		return (span != null) ? span.context().traceId() : null;
	}

	private String title(HttpStatusCode status, Locale locale) {
		String title = this.messages.getMessage(TITLE_PREFIX + status.value(), NO_ARGS, null, locale);
		if (title != null) {
			return title;
		}
		String fallback = TITLE_PREFIX + (status.is5xxServerError() ? 500 : 400);
		return this.messages.getMessage(fallback, NO_ARGS, fallback, locale);
	}

	private String detail(HttpStatusCode status, String code, Object[] args, Locale locale) {
		String detail = this.messages.getMessage(code, args, null, locale);
		if (detail != null) {
			return detail;
		}
		log.warn("Нет текста для кода ошибки {} в messages.properties", code);
		String fallback = codeFor(status);
		return this.messages.getMessage(fallback, NO_ARGS, fallback, locale);
	}

}
