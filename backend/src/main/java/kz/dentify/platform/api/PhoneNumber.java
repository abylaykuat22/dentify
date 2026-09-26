package kz.dentify.platform.api;

import java.util.Optional;
import java.util.regex.Pattern;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber;
import org.jspecify.annotations.Nullable;

/**
 * Номер телефона в формате E.164: плюс, код страны и номер, только цифры, например {@code +77071234567}. Так номер
 * хранится в БД и сравнивается: один и тот же номер, как бы его ни ввели, — одно значение (IAM-01).
 * <p>
 * Ввод разбирает libphonenumber (Google, R021) по метаданным номеров всех стран; номер без кода страны считается
 * казахстанским ({@link #DEFAULT_REGION}): «8 707 123 45 67», «+7 (707) 123-45-67», «7071234567» и «87071234567»
 * дают {@code +77071234567}. Номер, которого не бывает по метаданным, с буквами или добавочным — не номер.
 * <p>
 * Номер — персональные данные: в лог только маской (её ставит платформа), в сообщения ошибок не попадает.
 *
 * @param value номер в E.164
 */
public record PhoneNumber(String value) {

	/** Код ошибки поля: введённое не номер телефона. */
	public static final String INVALID = "phone.invalid";

	/** Регион номера без кода страны. */
	public static final String DEFAULT_REGION = "KZ";

	/** Ввод длиннее — не номер, разбирать не нужно. */
	static final int MAX_INPUT_LENGTH = 40;

	private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{6,14}");

	private static final PhoneNumberUtil PHONES = PhoneNumberUtil.getInstance();

	/**
	 * @throws IllegalArgumentException если значение не в E.164: разбирать ввод пользователя — {@link #parse}
	 */
	public PhoneNumber {
		if (!E164.matcher(value).matches()) {
			throw new IllegalArgumentException("Номер телефона не в формате E.164");
		}
	}

	/**
	 * Номер из ввода пользователя в любой записи.
	 *
	 * @return пусто, если ввод пустой или это не номер телефона: код ошибки поля — {@link #INVALID}
	 */
	public static Optional<PhoneNumber> parse(@Nullable String input) {
		if (input == null || input.isBlank() || input.length() > MAX_INPUT_LENGTH
				|| input.chars().anyMatch(Character::isLetter)) {
			return Optional.empty();
		}
		try {
			Phonenumber.PhoneNumber parsed = PHONES.parse(input, DEFAULT_REGION);
			if (parsed.hasExtension() || !PHONES.isValidNumber(parsed)) {
				return Optional.empty();
			}
			return Optional.of(new PhoneNumber(PHONES.format(parsed, PhoneNumberFormat.E164)));
		}
		catch (NumberParseException ex) {
			return Optional.empty();
		}
	}

	/** Казахстанский мобильный номер: +7 7XX по метаданным libphonenumber. Такие номера — у аккаунтов (IAM-01). */
	public boolean isKazakhstanMobile() {
		try {
			Phonenumber.PhoneNumber parsed = PHONES.parse(this.value, null);
			PhoneNumberType type = PHONES.getNumberType(parsed);
			return DEFAULT_REGION.equals(PHONES.getRegionCodeForNumber(parsed))
					&& (type == PhoneNumberType.MOBILE || type == PhoneNumberType.FIXED_LINE_OR_MOBILE);
		}
		catch (NumberParseException ex) {
			return false;
		}
	}

}
