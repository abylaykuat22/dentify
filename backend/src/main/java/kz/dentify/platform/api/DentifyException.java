package kz.dentify.platform.api;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Ошибка, которую код модулей бросает вместо текста: машинный {@link #getCode() код} — ключ сообщения в
 * {@code messages.properties}, аргументы для него и, если ошибка про поля формы, {@link #getViolations() нарушения
 * полей}. Текст на языке пользователя собирает обработчик ошибок платформы из {@code MessageSource} — в коде
 * модулей готовых фраз нет (dentify R003).
 * <p>
 * Класс HTTP-ответа задаёт подкласс, статус ставит обработчик:
 * <ul>
 * <li>{@link InvalidRequestException} — 400, неверные входные данные;</li>
 * <li>{@link UnauthorizedException} — 401, нет сессии;</li>
 * <li>{@link ForbiddenException} — 403, нет права;</li>
 * <li>{@link NotFoundException} — 404, не найдено или объект чужой клиники;</li>
 * <li>{@link ConflictException} — 409, конфликт версии или уникальности;</li>
 * <li>{@link BusinessRuleException} — 422, нарушено бизнес-правило;</li>
 * <li>{@link TooManyRequestsException} — 429, превышен лимит.</li>
 * </ul>
 * Подклассы открыты для наследования в модулях, например {@code PatientNotFoundException extends NotFoundException}.
 * Сообщение исключения — только код: аргументы и значения полей в лог не попадают, в них могут быть ПД.
 */
public abstract sealed class DentifyException extends RuntimeException permits InvalidRequestException,
		UnauthorizedException, ForbiddenException, NotFoundException, ConflictException, BusinessRuleException,
		TooManyRequestsException {

	private static final Object[] NO_ARGS = {};

	private final String code;

	private final Object[] args;

	private final List<FieldViolation> violations;

	protected DentifyException(String code, Object[] args, List<FieldViolation> violations, @Nullable Throwable cause) {
		super(code, cause);
		this.code = code;
		this.args = (args.length > 0) ? args.clone() : NO_ARGS;
		this.violations = List.copyOf(violations);
	}

	/** Машинный код ошибки, он же ключ сообщения, например {@code error.not-found}. */
	public String getCode() {
		return this.code;
	}

	/** Аргументы сообщения по коду в порядке плейсхолдеров {@code {0}, {1}}. */
	public Object[] getArgs() {
		return this.args.clone();
	}

	/** Нарушения по полям: попадают в {@code errors[]} ответа. */
	public List<FieldViolation> getViolations() {
		return this.violations;
	}

}
