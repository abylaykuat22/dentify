package kz.dentify.platform.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import kz.dentify.platform.api.FieldViolation;
import kz.dentify.platform.api.PasswordPolicy;
import org.jspecify.annotations.Nullable;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;

/**
 * Правила пароля PLT-05 без требований к составу, как у NIST SP 800-63B: длина, список частых паролей, совпадение с
 * контактами аккаунта.
 * <ul>
 * <li>длина — в символах Unicode, а не в UTF-16: эмодзи — один символ;</li>
 * <li>список — {@value #COMMON_PASSWORDS}, 10 000 самых частых паролей из SecLists (MIT, атрибуция — рядом, в
 * {@code common-passwords.NOTICE.txt}), сравнение без учёта регистра;</li>
 * <li>телефон — по цифрам: +7 707 123 45 67, 87071234567 и 7071234567 для пароля — один номер;</li>
 * <li>e-mail — целиком или часть до @, без учёта регистра.</li>
 * </ul>
 */
class CommonPasswordPolicy implements PasswordPolicy {

	static final String COMMON_PASSWORDS = "security/common-passwords.txt";

	/** Номер без кода страны: 10 цифр, по ним сравниваются +7, 8 и местный формат. */
	private static final int NATIONAL_NUMBER_DIGITS = 10;

	private final Set<String> common;

	CommonPasswordPolicy(Set<String> common) {
		this.common = Set.copyOf(common);
	}

	static Set<String> loadCommonPasswords() {
		Set<String> passwords = new HashSet<>();
		ClassPathResource resource = new ClassPathResource(COMMON_PASSWORDS);
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.isEmpty()) {
					passwords.add(line.toLowerCase(Locale.ROOT));
				}
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Не прочитан список частых паролей " + COMMON_PASSWORDS, ex);
		}
		return passwords;
	}

	int commonPasswords() {
		return this.common.size();
	}

	@Override
	public List<FieldViolation> violations(String field, String password, @Nullable String phone,
			@Nullable String email) {
		List<FieldViolation> violations = new ArrayList<>();
		int length = password.codePointCount(0, password.length());
		if (length < MIN_LENGTH) {
			violations.add(FieldViolation.of(field, TOO_SHORT, MIN_LENGTH));
			return violations;
		}
		if (length > MAX_LENGTH) {
			violations.add(FieldViolation.of(field, TOO_LONG, MAX_LENGTH));
			return violations;
		}
		if (this.common.contains(password.toLowerCase(Locale.ROOT))) {
			violations.add(FieldViolation.of(field, COMMON));
		}
		if (matchesPhone(password, phone)) {
			violations.add(FieldViolation.of(field, MATCHES_PHONE));
		}
		if (matchesEmail(password, email)) {
			violations.add(FieldViolation.of(field, MATCHES_EMAIL));
		}
		return violations;
	}

	/** Пароль — тот же номер: только цифры и знаки записи номера, и последние 10 цифр совпадают. */
	static boolean matchesPhone(String password, @Nullable String phone) {
		if (!StringUtils.hasText(phone) || !password.matches("[+\\d\\s()\\-.]+")) {
			return false;
		}
		String phoneDigits = nationalNumber(phone);
		return phoneDigits.length() == NATIONAL_NUMBER_DIGITS && phoneDigits.equals(nationalNumber(password));
	}

	static boolean matchesEmail(String password, @Nullable String email) {
		if (!StringUtils.hasText(email)) {
			return false;
		}
		String candidate = password.strip().toLowerCase(Locale.ROOT);
		String address = email.strip().toLowerCase(Locale.ROOT);
		int at = address.lastIndexOf('@');
		return candidate.equals(address) || (at > 0 && candidate.equals(address.substring(0, at)));
	}

	private static String nationalNumber(String value) {
		String digits = value.replaceAll("\\D", "");
		return (digits.length() > NATIONAL_NUMBER_DIGITS) ? digits.substring(digits.length() - NATIONAL_NUMBER_DIGITS)
				: digits;
	}

}
