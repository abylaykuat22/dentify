package kz.dentify.platform.web;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import kz.dentify.platform.api.AccountNameProvider;
import kz.dentify.platform.api.DataIntegrity;
import kz.dentify.platform.api.DentifyException;
import kz.dentify.platform.api.ErrorCodes;
import kz.dentify.platform.api.ForbiddenException;
import kz.dentify.platform.api.MissingTenantContextException;
import kz.dentify.platform.api.UnauthorizedException;
import kz.dentify.platform.persistence.LastModificationLookup.LastModification;
import kz.dentify.platform.persistence.LastModificationLookup;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Единый формат ошибок API (PLT-07): любое исключение из контроллера превращается в ProblemDetail по RFC 9457 с
 * {@code code}, {@code traceId} и {@code errors[]}, текст — из {@code MessageSource} (см. {@link ProblemFactory}).
 * <p>
 * Стандартные исключения Spring MVC (405, 406, 415, 413 и другие) разбирает базовый
 * {@link ResponseEntityExceptionHandler}, здесь меняется только тело ответа. Сопоставление:
 * <ul>
 * <li>400 — Bean Validation тела и параметров, неверный тип или отсутствие параметра, нечитаемый JSON,
 * {@code InvalidRequestException};</li>
 * <li>401, 403, 404, 409, 422, 429 — исключения платформы из {@code platform.api} по классу;</li>
 * <li>401 — обращение к данным клиники без клиники в сессии ({@code MissingTenantContextException});</li>
 * <li>401 и 403 — отказ Spring Security в методе, {@code @PreAuthorize}: без входа — 401, с входом — 403. Отказы
 * фильтров Spring Security приходят сюда исключениями платформы через {@code HandlerExceptionResolver};</li>
 * <li>404 — также {@code EntityNotFoundException} JPA: объект не виден, в том числе из-за RLS чужой клиники;</li>
 * <li>409 — конфликт версии {@code @Version} с автором и временем последней правки, нарушение уникальности,
 * внешнего ключа и EXCLUDE в БД, уникальности — со своим кодом;</li>
 * <li>500 — всё остальное, в том числе NOT NULL и CHECK в БД: в ответе только общий текст и traceId, без
 * стектрейса, сообщения исключения и SQL. В лог — ERROR с тем же traceId.</li>
 * </ul>
 * Ошибки вне контроллеров, например в фильтрах, отвечает {@link ProblemErrorController} в том же формате.
 */
@RestControllerAdvice
class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

	/** SQLSTATE PostgreSQL: нарушение уникальности. */
	private static final String UNIQUE_VIOLATION = "23505";

	/** SQLSTATE PostgreSQL: нарушение внешнего ключа — связанной записи нет или на запись ссылаются. */
	private static final String FOREIGN_KEY_VIOLATION = "23503";

	/** SQLSTATE PostgreSQL: нарушение EXCLUDE, например пересечение записей в расписании. */
	private static final String EXCLUSION_VIOLATION = "23P01";

	private static final Object[] NO_ARGS = {};

	private static final Logger log = LoggerFactory.getLogger(ProblemDetailsExceptionHandler.class);

	private final ProblemFactory problems;

	private final FieldErrors fieldErrors;

	private final LastModificationLookup lastModifications;

	private final ObjectProvider<AccountNameProvider> accountNames;

	private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

	ProblemDetailsExceptionHandler(ProblemFactory problems, LastModificationLookup lastModifications,
			ObjectProvider<AccountNameProvider> accountNames) {
		this.problems = problems;
		this.fieldErrors = new FieldErrors(problems);
		this.lastModifications = lastModifications;
		this.accountNames = accountNames;
	}

	@ExceptionHandler(DentifyException.class)
	ResponseEntity<Object> handleDentifyException(DentifyException ex) {
		HttpStatus status = ProblemFactory.statusOf(ex);
		log.debug("Ответ {} с кодом {}", status.value(), ex.getCode());
		return this.problems.response(ex);
	}

	/** Проверка методов сервисов ({@code @Validated}): входные данные неверны — 400 с ошибками полей. */
	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
		return validation(new HttpHeaders(), this.fieldErrors.of(ex.getConstraintViolations()));
	}

	@ExceptionHandler({ EntityNotFoundException.class, ObjectRetrievalFailureException.class })
	ResponseEntity<Object> handleEntityNotFound(Exception ex) {
		log.debug("Ответ 404: {}", ex.getClass().getSimpleName());
		return this.problems.response(HttpStatus.NOT_FOUND, new HttpHeaders());
	}

	/**
	 * 409 конфликт версии (PLT-08) с тем, кто и когда изменил запись последним: {@code modifiedAt}, {@code modifiedBy}
	 * и, если модуль сотрудников знает имя, {@code modifiedByName}. Интерфейс показывает по ним плашку и не теряет
	 * введённое. Не удалось узнать автора — обычный 409 без этих полей.
	 */
	@ExceptionHandler({ OptimisticLockingFailureException.class, OptimisticLockException.class })
	ResponseEntity<Object> handleOptimisticLock(Exception ex) {
		log.debug("Ответ 409: конфликт версии, {}", ex.getClass().getSimpleName());
		ProblemDetail problem = this.problems.create(HttpStatus.CONFLICT, ErrorCodes.VERSION_CONFLICT, NO_ARGS,
				List.of());
		lastModification(ex).ifPresent((modification) -> {
			problem.setProperty(ProblemFactory.MODIFIED_AT, modification.at());
			UUID author = modification.by();
			if (author != null) {
				problem.setProperty(ProblemFactory.MODIFIED_BY, author);
				authorName(author).ifPresent((name) -> problem.setProperty(ProblemFactory.MODIFIED_BY_NAME, name));
			}
		});
		return this.problems.response(problem, new HttpHeaders());
	}

	/**
	 * Отказ Spring Security в методе: {@code @PreAuthorize} не пропустил. Без входа — 401, чтобы интерфейс открыл вход,
	 * с входом — 403. Без этого обработчика отказ стал бы 500 в обработчике всех исключений.
	 */
	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
		boolean authenticated = this.trustResolver
			.isAuthenticated(SecurityContextHolder.getContext().getAuthentication());
		log.debug("Отказ в доступе к методу, вход: {}", authenticated);
		return this.problems.response(authenticated ? new ForbiddenException() : new UnauthorizedException());
	}

	/** Нет входа там, где метод его требует: 401. */
	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<Object> handleAuthentication(AuthenticationException ex) {
		log.debug("Ответ 401: {}", ex.getClass().getSimpleName());
		return this.problems.response(new UnauthorizedException());
	}

	/**
	 * Данные клиники без контекста клиники в HTTP-запросе: в сессии не выбрана клиника. 401, интерфейс ведёт на вход и
	 * выбор клиники.
	 */
	@ExceptionHandler(MissingTenantContextException.class)
	ResponseEntity<Object> handleMissingTenantContext(MissingTenantContextException ex) {
		log.debug("Ответ 401: нет клиники в сессии");
		return this.problems.response(HttpStatus.UNAUTHORIZED, new HttpHeaders(), ErrorCodes.CLINIC_NOT_SELECTED,
				NO_ARGS, List.of());
	}

	/**
	 * Нарушение ограничения БД. Конфликт с данными других запросов — 409: уникальность ({@code error.duplicate}),
	 * внешний ключ и EXCLUDE ({@code error.conflict}). Обычно сервис проверяет это сам и бросает
	 * {@code ConflictException} со своим кодом, а ограничение — страховка на гонку.
	 * <p>
	 * NOT NULL, CHECK, длина значения и прочее значат, что сервис пропустил проверку входных данных: это дефект, а
	 * не конфликт, поэтому 500 и ERROR. В лог — только SQLSTATE и имя ограничения или колонки: в сообщении драйвера
	 * есть значения строки, а это ПД.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		String sqlState = sqlState(ex);
		ConstraintKind kind = constraintKind(ex);
		String constraint = DataIntegrity.constraintName(ex);
		String described = (constraint != null) ? constraint : "не определено";
		if (ex instanceof DuplicateKeyException || UNIQUE_VIOLATION.equals(sqlState) || kind == ConstraintKind.UNIQUE) {
			log.warn("Нарушена уникальность в БД, ответ 409: SQLSTATE {}, ограничение {}", sqlState, described);
			return this.problems.response(HttpStatus.CONFLICT, new HttpHeaders(), ErrorCodes.DUPLICATE, NO_ARGS,
					List.of());
		}
		if (FOREIGN_KEY_VIOLATION.equals(sqlState) || EXCLUSION_VIOLATION.equals(sqlState)
				|| kind == ConstraintKind.FOREIGN_KEY) {
			log.warn("Нарушено ограничение БД, ответ 409: SQLSTATE {}, ограничение {}", sqlState, described);
			return this.problems.response(HttpStatus.CONFLICT, new HttpHeaders(), ErrorCodes.CONFLICT, NO_ARGS,
					List.of());
		}
		log.error("Данные не прошли ограничение БД, пропущена проверка входных данных, ответ 500: SQLSTATE {}, "
				+ "ограничение {}", sqlState, described);
		return this.problems.response(HttpStatus.INTERNAL_SERVER_ERROR, new HttpHeaders());
	}

	/** Всё, что не разобрано выше: 500 без подробностей. Исключение с traceId запроса — в лог. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex) {
		log.error("Необработанное исключение при обработке запроса", ex);
		return this.problems.response(HttpStatus.INTERNAL_SERVER_ERROR, new HttpHeaders());
	}

	/** 405 — ошибка клиента, а не сервера: без WARN, который пишет базовый класс на каждый такой запрос. */
	@Override
	protected @Nullable ResponseEntity<Object> handleHttpRequestMethodNotSupported(
			HttpRequestMethodNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return handleExceptionInternal(ex, null, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return validation(headers, this.fieldErrors.of(ex.getBindingResult()));
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return validation(headers, this.fieldErrors.of(ex));
	}

	@Override
	protected ResponseEntity<Object> handleMethodValidationException(MethodValidationException ex,
			HttpHeaders headers, HttpStatus status, WebRequest request) {
		return validation(headers, this.fieldErrors.of(ex));
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return validation(headers, List.of(this.fieldErrors.required(ex.getParameterName())));
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String field = (ex instanceof MethodArgumentTypeMismatchException argument) ? argument.getName()
				: ex.getPropertyName();
		if (field == null) {
			return handleExceptionInternal(ex, null, headers, status, request);
		}
		return validation(headers, List.of(this.fieldErrors.typeMismatch(field)));
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<ProblemFieldError> errors = this.fieldErrors.ofUnreadableBody(ex);
		if (!errors.isEmpty()) {
			return validation(headers, errors);
		}
		log.debug("Ответ 400: тело запроса не читается");
		return this.problems.response(HttpStatus.BAD_REQUEST, headers, ErrorCodes.MALFORMED_BODY, NO_ARGS, List.of());
	}

	/**
	 * Остальные исключения Spring MVC: статус и заголовки (Allow у 405, Accept у 415) — от базового класса, тело —
	 * ProblemDetail с кодом по статусу.
	 */
	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
			HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		if (request instanceof ServletWebRequest servletRequest) {
			HttpServletResponse response = servletRequest.getResponse();
			if (response != null && response.isCommitted()) {
				log.warn("Ответ уже отправлен, ошибка {} не передана клиенту", ex.getClass().getSimpleName());
				return null;
			}
		}
		if (statusCode.is5xxServerError()) {
			log.error("Ошибка обработки запроса, ответ {}", statusCode.value(), ex);
		}
		else {
			log.debug("Ответ {}: {}", statusCode.value(), ex.getClass().getSimpleName());
		}
		return this.problems.response(statusCode, headers);
	}

	private ResponseEntity<Object> validation(HttpHeaders headers, List<ProblemFieldError> errors) {
		log.debug("Ответ 400: ошибки полей {}", errors.stream().map(ProblemFieldError::code).toList());
		return this.problems.response(HttpStatus.BAD_REQUEST, headers, ErrorCodes.VALIDATION, NO_ARGS, errors);
	}

	private Optional<LastModification> lastModification(Exception conflict) {
		try {
			return this.lastModifications.find(conflict);
		}
		catch (RuntimeException ex) {
			log.warn("Не удалось прочитать последнюю правку записи для ответа 409: {}", ex.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	/** Имя автора для плашки конфликта; не прочиталось — ответ 409 без имени, а не 500. */
	private Optional<String> authorName(UUID accountId) {
		try {
			return Optional.ofNullable(
					AccountNameProvider.resolve(this.accountNames.orderedStream(), List.of(accountId)).get(accountId));
		}
		catch (RuntimeException ex) {
			log.warn("Не удалось прочитать имя автора последней правки для ответа 409: {}", ex.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	private static @Nullable String sqlState(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && sql.getSQLState() != null) {
				return sql.getSQLState();
			}
		}
		return null;
	}

	private static @Nullable ConstraintKind constraintKind(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
				return violation.getKind();
			}
		}
		return null;
	}

}
