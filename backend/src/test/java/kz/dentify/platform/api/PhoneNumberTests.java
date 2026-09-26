package kz.dentify.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/**
 * Нормализация телефона (IAM-01): любой ввод — один номер в E.164 по метаданным libphonenumber, регион по умолчанию
 * KZ. Номера вымышленные.
 */
class PhoneNumberTests {

	/** Приёмка IAM-01: «8 707 123 45 67», «+7(707)1234567» и «7071234567» — один и тот же номер. */
	@ParameterizedTest
	@ValueSource(strings = { "8 707 123 45 67", "+7(707)1234567", "7071234567", "+7 (707) 123-45-67", "87071234567",
			"77071234567", "+77071234567", "8-707-123-45-67", " 8 (707) 123 45 67 ", "+7 707 123 45 67" })
	void anyInputGivesOneE164Number(String input) {
		assertThat(PhoneNumber.parse(input)).contains(new PhoneNumber("+77071234567"));
	}

	@Test
	void kazakhstanMobileIsRecognised() {
		assertThat(PhoneNumber.parse("8 707 123 45 67").orElseThrow().isKazakhstanMobile()).isTrue();
		assertThat(PhoneNumber.parse("8 777 123 45 67").orElseThrow().isKazakhstanMobile()).isTrue();
		assertThat(PhoneNumber.parse("8 747 123 45 67").orElseThrow().isKazakhstanMobile()).isTrue();
	}

	/** Номер, который бывает, но не казахстанский мобильный: городской Астаны, мобильный России, другая страна. */
	@Test
	void otherValidNumbersAreNotKazakhstanMobile() {
		PhoneNumber landline = PhoneNumber.parse("8 (7172) 55-66-77").orElseThrow();
		PhoneNumber russian = PhoneNumber.parse("+7 912 345 67 89").orElseThrow();
		PhoneNumber german = PhoneNumber.parse("+49 30 12345678").orElseThrow();

		assertThat(landline.value()).isEqualTo("+77172556677");
		assertThat(landline.isKazakhstanMobile()).isFalse();
		assertThat(russian.value()).isEqualTo("+79123456789");
		assertThat(russian.isKazakhstanMobile()).isFalse();
		assertThat(german.value()).isEqualTo("+493012345678");
		assertThat(german.isKazakhstanMobile()).isFalse();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "12345", "8 707 123 45", "8 707 123 45 67 89 00", "707-ABC-45-67", "+7 707 123 45 67 доб. 12",
			"+7 707 123 45 67 ext 12", "телефон", "+0 707 123 45 67", "1234567890123456789012345678901234567890123" })
	void notAPhoneNumber(String input) {
		assertThat(PhoneNumber.parse(input)).isEmpty();
	}

	@Test
	void constructorAcceptsOnlyE164() {
		assertThat(new PhoneNumber("+77071234567").value()).isEqualTo("+77071234567");
		assertThatIllegalArgumentException().isThrownBy(() -> new PhoneNumber("87071234567"));
		assertThatIllegalArgumentException().isThrownBy(() -> new PhoneNumber("+7 707 123 45 67"));
		assertThatIllegalArgumentException().isThrownBy(() -> new PhoneNumber(""));
	}

	@Test
	void exceptionMessageHasNoNumber() {
		assertThatIllegalArgumentException().isThrownBy(() -> new PhoneNumber("8 707 123 45 67"))
			.withMessageNotContaining("707");
	}

}
