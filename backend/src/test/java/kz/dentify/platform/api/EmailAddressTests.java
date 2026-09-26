package kz.dentify.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Нормализация e-mail (IAM-01): без крайних пробелов, нижний регистр, до 254 символов. Адреса вымышленные. */
class EmailAddressTests {

	@ParameterizedTest
	@ValueSource(strings = { "owner@demo.dentify.kz", "  Owner@Demo.Dentify.KZ ", "OWNER@DEMO.DENTIFY.KZ", "\towner@demo.dentify.kz\n",
			" owner@demo.dentify.kz " })
	void inputIsTrimmedAndLowercased(String input) {
		assertThat(EmailAddress.parse(input)).contains(new EmailAddress("owner@demo.dentify.kz"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "a.b+tag@example.kz", "first_last-1@sub.example.com", "Айгерим@Почта.Қаз", "user@xn--80ao21a.xn--p1ai",
			"o'neil@example.kz", "x@a1.io" })
	void validAddresses(String input) {
		assertThat(EmailAddress.parse(input)).isPresent();
		assertThat(EmailAddress.violation("email", input)).isEmpty();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "owner", "owner@", "@demo.kz", "owner@demo", "owner@@demo.kz", "ow ner@demo.kz", "owner@demo..kz",
			".owner@demo.kz", "owner.@demo.kz", "ow..ner@demo.kz", "owner@-demo.kz", "owner@demo-.kz", "\"owner\"@demo.kz",
			"owner@[127.0.0.1]", "owner@demo.k", "owner@demo.kz.", "owner(x)@demo.kz", "own\u0000er@demo.kz" })
	void invalidAddresses(String input) {
		assertThat(EmailAddress.parse(input)).isEmpty();
		assertThat(EmailAddress.violation("email", input)).contains(FieldViolation.of("email", EmailAddress.INVALID));
	}

	@Test
	void longerThan254IsTooLong() {
		String local = "a".repeat(64);
		String domain = ("b".repeat(60) + ".").repeat(4) + "kz";
		String address254 = local + "@" + domain.substring(domain.length() - (254 - 65));

		assertThat(address254).hasSize(254);
		assertThat(EmailAddress.parse(address254)).isPresent();
		assertThat(EmailAddress.parse(address254 + "x")).isEmpty();
		assertThat(EmailAddress.violation("email", "x" + address254))
			.contains(FieldViolation.of("email", EmailAddress.TOO_LONG, EmailAddress.MAX_LENGTH));
	}

	@Test
	void localPartIsAtMost64Characters() {
		assertThat(EmailAddress.parse("a".repeat(64) + "@demo.kz")).isPresent();
		assertThat(EmailAddress.parse("a".repeat(65) + "@demo.kz")).isEmpty();
	}

	@Test
	void constructorAcceptsOnlyNormalizedAddress() {
		assertThat(new EmailAddress("owner@demo.kz").value()).isEqualTo("owner@demo.kz");
		assertThatIllegalArgumentException().isThrownBy(() -> new EmailAddress("Owner@demo.kz"));
		assertThatIllegalArgumentException().isThrownBy(() -> new EmailAddress(" owner@demo.kz"));
		assertThatIllegalArgumentException().isThrownBy(() -> new EmailAddress("owner"))
			.withMessageNotContaining("owner");
	}

}
