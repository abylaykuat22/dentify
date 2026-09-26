package kz.dentify.platform.web;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import kz.dentify.platform.api.ErrorCodes;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Превращает ошибки проверки Spring и Bean Validation в {@code errors[]} ProblemDetail.
 * <p>
 * Код ошибки поля:
 * <ul>
 * <li>своё сообщение ограничения в виде ключа, {@code @Pattern(message = {patient.iin.format})} или шаблон из
 * валидатора — код равен ключу, текст берётся по нему;</li>
 * <li>стандартное ограничение Jakarta — {@code validation.} и имя аннотации через дефис: {@code @NotBlank} —
 * {@code validation.not-blank}, {@code @Size} — {@code validation.size}. Текст — по ключу Jakarta
 * {@code jakarta.validation.constraints.NotBlank.message} в messages.properties, плейсхолдеры {@code {min}},
 * {@code {max}}, {@code {value}} подставляет Hibernate Validator;</li>
 * <li>значение не приводится к типу — {@link ErrorCodes#FIELD_TYPE_MISMATCH}.</li>
 * </ul>
 * Текст Bean Validation интерполирует Spring Boot через {@code MessageSource} на языке запроса ещё при проверке,
 * поэтому здесь он берётся готовым из нарушения.
 */
final class FieldErrors {

	/** Пакеты стандартных ограничений: их ключи по умолчанию не годятся в коды ответа. */
	private static final List<String> PROVIDER_MESSAGE_PREFIXES = List.of("jakarta.validation.constraints.",
			"org.hibernate.validator.constraints.");

	private static final String VALIDATION_PREFIX = "validation.";

	private final ProblemFactory problems;

	FieldErrors(ProblemFactory problems) {
		this.problems = problems;
	}

	/** Ошибки проверки тела запроса или объекта параметров, {@code @Valid}. */
	List<ProblemFieldError> of(Errors result) {
		List<ProblemFieldError> errors = new ArrayList<>();
		for (FieldError error : result.getFieldErrors()) {
			errors.add(of(error.getField(), error));
		}
		for (ObjectError error : result.getGlobalErrors()) {
			errors.add(of(null, error));
		}
		return errors;
	}

	/** Ошибки проверки параметров метода контроллера: {@code @RequestParam @Max(100) int size}. */
	List<ProblemFieldError> of(MethodValidationResult result) {
		List<ProblemFieldError> errors = new ArrayList<>();
		for (ParameterValidationResult parameter : result.getParameterValidationResults()) {
			if (parameter instanceof ParameterErrors bean) {
				errors.addAll(of((Errors) bean));
				continue;
			}
			String field = parameterName(parameter);
			for (MessageSourceResolvable error : parameter.getResolvableErrors()) {
				ConstraintViolation<?> violation = unwrap(parameter, error);
				errors.add((violation != null) ? of(field, violation) : of(field, error));
			}
		}
		for (MessageSourceResolvable error : result.getCrossParameterValidationResults()) {
			errors.add(of(null, error));
		}
		return errors;
	}

	/** Нарушения Bean Validation из проверки методов сервисов, {@code @Validated}. */
	List<ProblemFieldError> of(Collection<? extends ConstraintViolation<?>> violations) {
		return violations.stream().map((violation) -> of(fieldOf(violation.getPropertyPath()), violation)).toList();
	}

	/** Поле, которое не разобралось из JSON тела: неверный тип значения. Пусто, если JSON сломан целиком. */
	List<ProblemFieldError> ofUnreadableBody(Throwable ex) {
		if (NestedExceptionUtils.getMostSpecificCause(ex) instanceof MismatchedInputException mismatch) {
			String field = jsonPath(mismatch);
			if (!field.isEmpty()) {
				return List.of(typeMismatch(field));
			}
		}
		return List.of();
	}

	ProblemFieldError typeMismatch(String field) {
		return new ProblemFieldError(field, ErrorCodes.FIELD_TYPE_MISMATCH,
				this.problems.message(ErrorCodes.FIELD_TYPE_MISMATCH, new Object[0]));
	}

	ProblemFieldError required(String field) {
		return new ProblemFieldError(field, ErrorCodes.FIELD_REQUIRED,
				this.problems.message(ErrorCodes.FIELD_REQUIRED, new Object[0]));
	}

	private ProblemFieldError of(@Nullable String field, MessageSourceResolvable error) {
		if (error instanceof ObjectError objectError && objectError.contains(ConstraintViolation.class)) {
			return of(field, objectError.unwrap(ConstraintViolation.class));
		}
		if (error instanceof FieldError fieldError && fieldError.isBindingFailure()) {
			return typeMismatch(fieldError.getField());
		}
		String[] codes = error.getCodes();
		String code = (codes != null && codes.length > 0) ? codes[codes.length - 1] : ErrorCodes.FIELD_INVALID;
		Object[] args = error.getArguments();
		return new ProblemFieldError(field, code, this.problems.message(code, (args != null) ? args : new Object[0]));
	}

	private static ProblemFieldError of(@Nullable String field, ConstraintViolation<?> violation) {
		return new ProblemFieldError(field, codeOf(violation), violation.getMessage());
	}

	/** Код нарушения: свой ключ сообщения или {@code validation.<аннотация-через-дефис>}. */
	static String codeOf(ConstraintViolation<?> violation) {
		String template = violation.getMessageTemplate();
		if (template.length() > 2 && template.startsWith("{") && template.endsWith("}")
				&& template.indexOf('{', 1) < 0) {
			String key = template.substring(1, template.length() - 1);
			if (PROVIDER_MESSAGE_PREFIXES.stream().noneMatch(key::startsWith)) {
				return key;
			}
		}
		Class<? extends Annotation> constraint = violation.getConstraintDescriptor().getAnnotation().annotationType();
		return VALIDATION_PREFIX + kebab(constraint.getSimpleName());
	}

	/**
	 * Путь поля по пути нарушения: {@code items[0].name}. Узлы метода и параметра пропускаются; если свойств нет,
	 * это нарушение самого параметра метода, и полем становится имя параметра.
	 */
	static @Nullable String fieldOf(Path path) {
		StringBuilder field = new StringBuilder();
		String parameter = null;
		for (Path.Node node : path) {
			ElementKind kind = node.getKind();
			if (kind == ElementKind.PARAMETER) {
				parameter = node.getName();
			}
			if (kind != ElementKind.PROPERTY && kind != ElementKind.BEAN && kind != ElementKind.CONTAINER_ELEMENT) {
				continue;
			}
			if (node.isInIterable()) {
				Object position = (node.getIndex() != null) ? node.getIndex() : node.getKey();
				field.append('[').append((position != null) ? position : "").append(']');
			}
			if (kind == ElementKind.PROPERTY && node.getName() != null) {
				if (!field.isEmpty()) {
					field.append('.');
				}
				field.append(node.getName());
			}
		}
		return field.isEmpty() ? parameter : field.toString();
	}

	private static @Nullable ConstraintViolation<?> unwrap(ParameterValidationResult parameter,
			MessageSourceResolvable error) {
		try {
			return parameter.unwrap(error, ConstraintViolation.class);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static String parameterName(ParameterValidationResult result) {
		MethodParameter parameter = result.getMethodParameter();
		String name = annotatedName(parameter);
		if (name == null) {
			name = parameter.getParameterName();
		}
		if (name == null) {
			name = "arg" + parameter.getParameterIndex();
		}
		if (result.getContainerIndex() != null) {
			return name + "[" + result.getContainerIndex() + "]";
		}
		if (result.getContainerKey() != null) {
			return name + "[" + result.getContainerKey() + "]";
		}
		return name;
	}

	private static @Nullable String annotatedName(MethodParameter parameter) {
		RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
		if (requestParam != null && !requestParam.name().isEmpty()) {
			return requestParam.name();
		}
		PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
		if (pathVariable != null && !pathVariable.name().isEmpty()) {
			return pathVariable.name();
		}
		RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
		if (requestHeader != null && !requestHeader.name().isEmpty()) {
			return requestHeader.name();
		}
		return null;
	}

	private static String jsonPath(JacksonException ex) {
		StringBuilder path = new StringBuilder();
		for (JacksonException.Reference reference : ex.getPath()) {
			if (reference.getPropertyName() != null) {
				if (!path.isEmpty()) {
					path.append('.');
				}
				path.append(reference.getPropertyName());
			}
			else if (reference.getIndex() >= 0) {
				path.append('[').append(reference.getIndex()).append(']');
			}
		}
		return path.toString();
	}

	/** NotBlank — not-blank, PositiveOrZero — positive-or-zero. */
	static String kebab(String name) {
		StringBuilder result = new StringBuilder(name.length() + 4);
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (Character.isUpperCase(c)) {
				if (i > 0) {
					result.append('-');
				}
				result.append(Character.toLowerCase(c));
			}
			else {
				result.append(c);
			}
		}
		return result.toString().toLowerCase(Locale.ROOT);
	}

}
