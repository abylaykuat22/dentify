package kz.dentify.platform.api;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Адрес e-mail без крайних пробелов и в нижнем регистре, не длиннее {@value #MAX_LENGTH} символов (IAM-01): так он
 * хранится в БД и сравнивается, «Owner@Demo.KZ » и «owner@demo.kz» — один адрес.
 * <p>
 * Проверка — по форме адреса, а не доставкой: одна {@code @}, часть до неё — до 64 символов без пробелов, кавычек и
 * скобок, домен — из частей через точку, буквы любого алфавита, цифры и дефис, последняя часть — буквы или punycode
 * {@code xn--}. Адрес в кавычках
 * и IP-адрес вместо домена не принимаются. Существует ли ящик, проверяет письмо (приглашение, сброс пароля).
 * <p>
 * Адрес — персональные данные: в лог только маской (её ставит платформа), в сообщения ошибок не попадает.
 *
 * @param value адрес, уже приведённый: {@link #parse}
 */
public record EmailAddress(String value) {

	/** Предел длины адреса (RFC 5321: путь до 256 символов вместе с угловыми скобками). */
	public static final int MAX_LENGTH = 254;

	/** Код ошибки поля: введённое не адрес e-mail. */
	public static final String INVALID = "email.invalid";

	/** Код ошибки поля: адрес длиннее {@value #MAX_LENGTH} символов; аргумент — предел. */
	public static final String TOO_LONG = "email.too-long";

	private static final Pattern FORMAT = Pattern.compile("(?U)[^\\s\\p{Cntrl}@\"(),:;<>\\[\\]\\\\]{1,64}"
			+ "@(?:[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]{0,61}[\\p{L}\\p{N}])?\\.)+(?:\\p{L}{2,63}|xn--[a-z0-9-]{1,59})");

	private static final Pattern EDGE_SPACES = Pattern.compile("(?U)^[\\s\\p{Z}\\p{Cf}]+|[\\s\\p{Z}\\p{Cf}]+$");

	/**
	 * @throws IllegalArgumentException если значение не приведено или не адрес: разбирать ввод — {@link #parse}
	 */
	public EmailAddress {
		if (!value.equals(normalize(value)) || violation(value) != null) {
			throw new IllegalArgumentException("Адрес e-mail не приведён или неверен");
		}
	}

	/**
	 * Адрес из ввода пользователя: без крайних пробелов, в нижнем регистре.
	 *
	 * @return пусто, если ввод пустой или это не адрес: код ошибки — {@link #violation(String, String)}
	 */
	public static Optional<EmailAddress> parse(@Nullable String input) {
		if (input == null) {
			return Optional.empty();
		}
		String normalized = normalize(input);
		return (violation(normalized) == null) ? Optional.of(new EmailAddress(normalized)) : Optional.empty();
	}

	/**
	 * Ошибка поля для введённого адреса: {@link #TOO_LONG} с пределом или {@link #INVALID}.
	 *
	 * @return пусто, если адрес годится
	 */
	public static Optional<FieldViolation> violation(String field, @Nullable String input) {
		String code = (input != null) ? violation(normalize(input)) : INVALID;
		if (code == null) {
			return Optional.empty();
		}
		return Optional.of(TOO_LONG.equals(code) ? FieldViolation.of(field, TOO_LONG, MAX_LENGTH)
				: FieldViolation.of(field, INVALID));
	}

	/** Без крайних пробелов любого вида (и неразрывного из скопированного текста) и невидимых символов, в нижнем регистре. */
	private static String normalize(String input) {
		return EDGE_SPACES.matcher(input).replaceAll("").toLowerCase(Locale.ROOT);
	}

	private static @Nullable String violation(String normalized) {
		if (normalized.length() > MAX_LENGTH) {
			return TOO_LONG;
		}
		String local = normalized.substring(0, Math.max(0, normalized.lastIndexOf('@')));
		boolean dots = local.startsWith(".") || local.endsWith(".") || local.contains("..");
		return (dots || !FORMAT.matcher(normalized).matches()) ? INVALID : null;
	}

}
