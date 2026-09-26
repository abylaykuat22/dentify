package kz.dentify.platform.api;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Правила пароля (PLT-05): от {@value #MIN_LENGTH} до {@value #MAX_LENGTH} символов, без требований к составу; пароль
 * из списка 10 000 самых частых и совпадающий с телефоном или e-mail аккаунта отклоняется. Сравнение со списком — без
 * учёта регистра.
 * <p>
 * Проверяют регистрация, приглашение, сброс и смена пароля (модуль iam) до хэширования. Хэш — бином Spring Security
 * {@code PasswordEncoder}: Argon2id, в БД формат {@code {argon2}…}. Пароль нигде не логируется и в сообщения ошибок не
 * попадает.
 */
public interface PasswordPolicy {

	int MIN_LENGTH = 8;

	int MAX_LENGTH = 128;

	/** Короче {@value #MIN_LENGTH} символов; аргумент — минимум. */
	String TOO_SHORT = "password.too-short";

	/** Длиннее {@value #MAX_LENGTH} символов; аргумент — максимум. */
	String TOO_LONG = "password.too-long";

	/** Пароль из списка самых частых. */
	String COMMON = "password.common";

	/** Совпадает с телефоном аккаунта. */
	String MATCHES_PHONE = "password.matches-phone";

	/** Совпадает с e-mail аккаунта или его частью до @. */
	String MATCHES_EMAIL = "password.matches-email";

	/**
	 * Нарушения правил пароля: пусто — пароль годится.
	 *
	 * @param field имя поля формы, например password или newPassword: оно уходит в {@code errors[].field}
	 * @param phone телефон аккаунта, если есть
	 * @param email e-mail аккаунта, если есть
	 */
	List<FieldViolation> violations(String field, String password, @Nullable String phone, @Nullable String email);

	/**
	 * Проверить пароль.
	 *
	 * @throws InvalidRequestException 400 с ошибкой поля, если пароль не годится
	 */
	default void check(String field, String password, @Nullable String phone, @Nullable String email) {
		List<FieldViolation> violations = violations(field, password, phone, email);
		if (!violations.isEmpty()) {
			throw new InvalidRequestException(violations);
		}
	}

}
