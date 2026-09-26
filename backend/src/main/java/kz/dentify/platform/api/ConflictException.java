package kz.dentify.platform.api;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 409: действие конфликтует с текущим состоянием данных, например запись с таким ключом уже есть. Конфликт версии
 * при {@code @Version} и нарушение уникального индекса обработчик превращает в 409 сам, без этого исключения.
 */
public non-sealed class ConflictException extends DentifyException {

	public ConflictException() {
		this(ErrorCodes.CONFLICT);
	}

	public ConflictException(String code, Object... args) {
		super(code, args, List.of(), null);
	}

	/**
	 * 409 с ошибками полей в {@code errors[]}: какое поле формы конфликтует, например телефон уже занят другим
	 * аккаунтом. Причина — исключение БД, если конфликт нашло ограничение, а не проверка сервиса.
	 */
	protected ConflictException(String code, List<FieldViolation> violations, @Nullable Throwable cause) {
		super(code, new Object[0], violations, cause);
	}

	/** 409 с кодом и ошибками полей, см. {@link #ConflictException(String, List, Throwable)}. */
	public static ConflictException ofFields(String code, List<FieldViolation> violations, @Nullable Throwable cause) {
		return new ConflictException(code, violations, cause);
	}

}
