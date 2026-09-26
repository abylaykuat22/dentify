package kz.dentify.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import kz.dentify.platform.api.ErrorCodes;
import kz.dentify.platform.api.FieldViolation;
import kz.dentify.platform.api.InvalidRequestException;
import kz.dentify.platform.api.PasswordPolicy;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Пароли (PLT-05): хэш Argon2id с параметрами OWASP в формате {@code {argon2}…}, старые хэши проверяются и
 * перехэшируются; правила пароля — длина, список частых, совпадение с телефоном и e-mail, у каждого нарушения — код и
 * русский текст. Пароли в тестах — вымышленные.
 */
class PasswordTests {

	static final PasswordEncoder ENCODER = new SecurityConfiguration().passwordEncoder(new SecurityProperties());

	static final CommonPasswordPolicy POLICY = new CommonPasswordPolicy(CommonPasswordPolicy.loadCommonPasswords());

	static final String PHONE = "+7 707 123 45 67";

	static final String EMAIL = "Dinara.Alieva@example.kz";

	@Test
	void hashIsArgon2idWithOwaspParameters() {
		String hash = ENCODER.encode("Кресло-у-окна-2026");

		assertThat(hash).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$");
		assertThat(ENCODER.matches("Кресло-у-окна-2026", hash)).isTrue();
		assertThat(ENCODER.matches("кресло-у-окна-2026", hash)).isFalse();
		assertThat(ENCODER.upgradeEncoding(hash)).isFalse();
	}

	@Test
	void samePasswordGetsDifferentSalt() {
		assertThat(ENCODER.encode("Зубная-фея-42")).isNotEqualTo(ENCODER.encode("Зубная-фея-42"));
	}

	/** Хэш другого алгоритма или со слабыми параметрами проверяется, и его нужно перехэшировать при входе. */
	@Test
	void olderHashesMatchAndNeedUpgrade() {
		String bcrypt = "{bcrypt}" + new BCryptPasswordEncoder().encode("Пломба-2019");
		String weakArgon2 = "{argon2}" + Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("Пломба-2019");

		assertThat(ENCODER.matches("Пломба-2019", bcrypt)).isTrue();
		assertThat(ENCODER.upgradeEncoding(bcrypt)).isTrue();
		assertThat(ENCODER.matches("Пломба-2019", weakArgon2)).isTrue();
		assertThat(ENCODER.upgradeEncoding(weakArgon2)).isTrue();
	}

	@Test
	void goodPasswordPasses() {
		assertThat(POLICY.violations("password", "Кресло-у-окна-2026", PHONE, EMAIL)).isEmpty();
		assertThat(POLICY.violations("password", "correct horse battery staple", null, null)).isEmpty();
	}

	/** Приёмка PLT-05: 12345678 отклоняется понятной ошибкой поля. */
	@Test
	void commonPasswordIsRejected() {
		assertThat(POLICY.violations("password", "12345678", PHONE, EMAIL))
			.containsExactly(FieldViolation.of("password", PasswordPolicy.COMMON));
		assertThat(POLICY.violations("newPassword", "Password1", null, null))
			.containsExactly(FieldViolation.of("newPassword", PasswordPolicy.COMMON));
		assertThat(POLICY.violations("password", "QWERTYUIOP", null, null))
			.as("без учёта регистра")
			.containsExactly(FieldViolation.of("password", PasswordPolicy.COMMON));
	}

	@Test
	void commonPasswordsListIsTopTenThousand() {
		assertThat(POLICY.commonPasswords()).isBetween(9_900, 10_001);
	}

	@Test
	void lengthIsEightToOneHundredTwentyEightCharacters() {
		assertThat(POLICY.violations("password", "Кр-2026", null, null))
			.containsExactly(FieldViolation.of("password", PasswordPolicy.TOO_SHORT, 8));
		assertThat(POLICY.violations("password", "к".repeat(129), null, null))
			.containsExactly(FieldViolation.of("password", PasswordPolicy.TOO_LONG, 128));
		assertThat(POLICY.violations("password", "Кр-2026!", null, null)).isEmpty();
		assertThat(POLICY.violations("password", "к".repeat(128), null, null)).isEmpty();
	}

	/** Длина — в символах, а не в единицах UTF-16: семь эмодзи — семь символов. */
	@Test
	void lengthCountsUnicodeCharacters() {
		String sevenEmoji = "🦷🦷🦷🦷🦷🦷🦷";

		assertThat(sevenEmoji.length()).isEqualTo(14);
		assertThat(POLICY.violations("password", sevenEmoji, null, null))
			.containsExactly(FieldViolation.of("password", PasswordPolicy.TOO_SHORT, 8));
	}

	/** Приёмка PLT-05: пароль, совпадающий с телефоном, отклоняется — в любой записи номера. */
	@Test
	void passwordMatchingPhoneIsRejected() {
		for (String password : List.of("87071234567", "+77071234567", "7071234567", "8 (707) 123-45-67")) {
			assertThat(POLICY.violations("password", password, PHONE, EMAIL)).as(password)
				.containsExactly(FieldViolation.of("password", PasswordPolicy.MATCHES_PHONE));
		}
		assertThat(POLICY.violations("password", "87071234560", PHONE, EMAIL)).isEmpty();
	}

	@Test
	void passwordMatchingEmailIsRejected() {
		assertThat(POLICY.violations("password", "dinara.alieva@example.kz", PHONE, EMAIL))
			.containsExactly(FieldViolation.of("password", PasswordPolicy.MATCHES_EMAIL));
		assertThat(POLICY.violations("password", "DINARA.ALIEVA", PHONE, EMAIL))
			.containsExactly(FieldViolation.of("password", PasswordPolicy.MATCHES_EMAIL));
		assertThat(POLICY.violations("password", "dinara.alieva.2026", PHONE, EMAIL)).isEmpty();
	}

	@Test
	void checkThrowsValidationErrorWithFieldViolations() {
		assertThatExceptionOfType(InvalidRequestException.class)
			.isThrownBy(() -> POLICY.check("password", "12345678", null, null))
			.satisfies((ex) -> {
				assertThat(ex.getCode()).isEqualTo(ErrorCodes.VALIDATION);
				assertThat(ex.getViolations()).containsExactly(FieldViolation.of("password", PasswordPolicy.COMMON));
			});
	}

	/** Пароль в сообщение исключения не попадает: там только код, а сообщения уходят в логи. */
	@Test
	void passwordIsNotInExceptionMessage() {
		assertThatExceptionOfType(InvalidRequestException.class)
			.isThrownBy(() -> POLICY.check("password", "87071234567", PHONE, null))
			.satisfies((ex) -> assertThat(String.valueOf(ex.getMessage()) + ex.getViolations())
				.doesNotContain("87071234567"));
	}

	@Test
	void everyPasswordCodeHasRussianText() throws Exception {
		Properties messages = PropertiesLoaderUtils
			.loadProperties(new EncodedResource(new ClassPathResource("messages.properties"), StandardCharsets.UTF_8));

		assertThat(List.of(PasswordPolicy.TOO_SHORT, PasswordPolicy.TOO_LONG, PasswordPolicy.COMMON,
				PasswordPolicy.MATCHES_PHONE, PasswordPolicy.MATCHES_EMAIL))
			.allSatisfy((code) -> assertThat(messages.getProperty(code)).as(code).isNotBlank());
	}

}
