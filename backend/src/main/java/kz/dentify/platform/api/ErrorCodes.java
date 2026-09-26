package kz.dentify.platform.api;

/**
 * Общие коды ошибок платформы. Код — это и значение поля {@code code} в ProblemDetail, и ключ текста в
 * {@code messages.properties}. Коды модулей называются от модуля: {@code patient.iin.checksum},
 * {@code schedule.backdated-edit}. Тест {@code MessagesTests} проверяет, что у каждого кода здесь есть русский
 * текст.
 */
public final class ErrorCodes {

	/** 400 без подробностей. */
	public static final String BAD_REQUEST = "error.bad-request";

	/** 400 с нарушениями полей в {@code errors[]}. */
	public static final String VALIDATION = "error.validation";

	/** 400: тело запроса не читается, JSON сломан. */
	public static final String MALFORMED_BODY = "error.malformed-body";

	/** 401: запрос без входа. */
	public static final String UNAUTHORIZED = "error.unauthorized";

	/**
	 * 401: запрос с cookie сессии, которой больше нет: истекла по бездействию, завершена со списка сессий,
	 * деактивацией или сменой пароля. Интерфейс открывает вход и после него возвращает на прежний экран.
	 */
	public static final String SESSION_EXPIRED = "error.session-expired";

	/** 401: в сессии не выбрана клиника, а запрос обращается к данным клиники. */
	public static final String CLINIC_NOT_SELECTED = "error.clinic-not-selected";

	public static final String FORBIDDEN = "error.forbidden";

	/**
	 * 403: изменяющий запрос без CSRF-токена или с чужим: нет заголовка {@code X-XSRF-TOKEN} или он не совпадает с
	 * cookie {@code XSRF-TOKEN}. Интерфейс перечитывает cookie и повторяет запрос.
	 */
	public static final String CSRF = "error.csrf";

	public static final String NOT_FOUND = "error.not-found";

	public static final String METHOD_NOT_ALLOWED = "error.method-not-allowed";

	public static final String NOT_ACCEPTABLE = "error.not-acceptable";

	/** 409 без подробностей, в том числе нарушение внешнего ключа или EXCLUDE в БД. */
	public static final String CONFLICT = "error.conflict";

	/** 409: запись изменили после того, как её открыли, {@code @Version}. */
	public static final String VERSION_CONFLICT = "error.version-conflict";

	/** 409: нарушен уникальный индекс. */
	public static final String DUPLICATE = "error.duplicate";

	public static final String PAYLOAD_TOO_LARGE = "error.payload-too-large";

	public static final String UNSUPPORTED_MEDIA_TYPE = "error.unsupported-media-type";

	/** 422 без подробностей. Правила модулей заводят свои коды. */
	public static final String BUSINESS_RULE = "error.business-rule";

	public static final String TOO_MANY_REQUESTS = "error.too-many-requests";

	/** 429 на входе: неверных попыток больше лимита; аргумент — через сколько минут можно снова. */
	public static final String TOO_MANY_LOGIN_ATTEMPTS = "error.too-many-login-attempts";

	/** 500: текст без подробностей, пользователь сообщает поддержке traceId. */
	public static final String INTERNAL = "error.internal";

	public static final String UNAVAILABLE = "error.unavailable";

	/** Поле: значение неверно, общий текст — когда у кода ошибки поля нет своего текста. */
	public static final String FIELD_INVALID = "validation.invalid";

	/** Поле: обязательный параметр запроса не передан. */
	public static final String FIELD_REQUIRED = "validation.required";

	/** Поле: значение не приводится к типу, например буквы в числе или дата не в ISO 8601. */
	public static final String FIELD_TYPE_MISMATCH = "validation.type-mismatch";

	/** Поле {@code page}: не целое число от 0. */
	public static final String PAGE_INVALID = "pagination.page";

	/** Поле {@code size}: не целое число от 1. Больше предела — не ошибка, вернётся предел. */
	public static final String PAGE_SIZE_INVALID = "pagination.size";

	/** Поле {@code sort}: сортировка по этому полю не разрешена эндпоинтом. */
	public static final String SORT_UNSUPPORTED = "pagination.sort";

	/** Поле {@code q}: строка поиска длиннее предела. */
	public static final String SEARCH_TOO_LONG = "search.q.too-long";

	private ErrorCodes() {
	}

}
