package kz.dentify.iam.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import kz.dentify.platform.api.EmailAddress;
import kz.dentify.platform.api.PhoneNumber;
import org.junit.jupiter.api.Test;

/**
 * Логин из одного поля (IAM-02): есть {@code @} — e-mail, иначе телефон; ключ лимитов — нормализованное значение, так
 * что любые записи одного номера и регистр e-mail делят один счётчик неверных попыток. Инициалы для аватара. Данные —
 * вымышленные.
 */
class LoginNameTests {

	@Test
	void phoneInAnyNotationIsOneLogin() {
		for (String notation : List.of("8 707 123 45 67", "+7 (707) 123-45-67", "7071234567", " 87071234567 ")) {
			LoginName name = LoginName.parse(notation);

			assertThat(name.phone()).as(notation).isEqualTo(new PhoneNumber("+77071234567"));
			assertThat(name.email()).isNull();
			assertThat(name.limitKey()).isEqualTo("+77071234567");
			assertThat(name.isContact()).isTrue();
		}
	}

	@Test
	void emailIsTrimmedAndLowerCased() {
		LoginName name = LoginName.parse("  Owner@Demo.Dentify.KZ ");

		assertThat(name.email()).isEqualTo(new EmailAddress("owner@demo.dentify.kz"));
		assertThat(name.phone()).isNull();
		assertThat(name.limitKey()).isEqualTo("owner@demo.dentify.kz");
	}

	/** Ни телефон, ни e-mail — неизвестный логин: аккаунт не ищется, лимиты — по вводу. */
	@Test
	void nonsenseIsNotAContact() {
		for (String input : List.of("не телефон", "@", "a@b@c", "12")) {
			LoginName name = LoginName.parse(" " + input.toUpperCase() + " ");

			assertThat(name.isContact()).as(input).isFalse();
			assertThat(name.limitKey()).isEqualTo(input.toUpperCase().toLowerCase(java.util.Locale.ROOT));
		}
	}

	@Test
	void toStringHasNoLogin() {
		assertThat(LoginName.parse("dinara@example.kz")).hasToString("LoginName[email]");
		assertThat(LoginName.parse("8 707 123 45 67")).hasToString("LoginName[phone]");
		assertThat(LoginName.parse("xyz").toString()).doesNotContain("xyz");
	}

	@Test
	void initialsAreFirstLettersOfTwoWords() {
		assertThat(AccountOverview.initials("Алиева Динара Сериковна")).isEqualTo("АД");
		assertThat(AccountOverview.initials("  мадина  ")).isEqualTo("М");
		assertThat(AccountOverview.initials("әлия өмірзақова")).isEqualTo("ӘӨ");
		assertThat(AccountOverview.initials("Демо Владелец")).isEqualTo("ДВ");
	}

}
