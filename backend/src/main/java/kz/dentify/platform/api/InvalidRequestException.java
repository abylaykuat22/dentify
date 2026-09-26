package kz.dentify.platform.api;

import java.util.List;

/**
 * 400: входные данные неверны, а проверить их аннотациями Bean Validation нельзя: контрольная сумма, формат, связь
 * полей. Нарушения полей идут в {@code errors[]}, общий код ответа — {@link ErrorCodes#VALIDATION}.
 */
public non-sealed class InvalidRequestException extends DentifyException {

	public InvalidRequestException(List<FieldViolation> violations) {
		super(ErrorCodes.VALIDATION, new Object[0], violations, null);
	}

	public InvalidRequestException(FieldViolation... violations) {
		this(List.of(violations));
	}

	/** Ошибка запроса целиком, без привязки к полю. */
	public InvalidRequestException(String code, Object... args) {
		super(code, args, List.of(), null);
	}

}
