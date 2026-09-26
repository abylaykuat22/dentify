package kz.dentify.iam.internal;

import java.util.Locale;
import java.util.Optional;

import kz.dentify.platform.api.EmailAddress;
import kz.dentify.platform.api.PhoneNumber;
import org.jspecify.annotations.Nullable;

/**
 * Логин из одного поля «Телефон или e-mail» (IAM-02): есть {@code @} — e-mail, иначе телефон. Нормализуется так же,
 * как хранится у аккаунта: телефон в E.164, e-mail в нижнем регистре. По нормализованному значению считаются и лимиты
 * входа: «8 707 123 45 67» и «+77071234567» — один логин и один счётчик неверных попыток. Ввод, который не
 * разбирается ни как телефон, ни как e-mail, — неизвестный логин: ответ тот же, что у неверного пароля.
 *
 * @param limitKey ключ лимитов входа: нормализованный телефон или e-mail, иначе ввод без крайних пробелов в нижнем
 * регистре
 * @param phone телефон или null
 * @param email e-mail или null
 */
record LoginName(String limitKey, @Nullable PhoneNumber phone, @Nullable EmailAddress email) {

	static LoginName parse(String input) {
		String login = input.strip();
		if (login.contains("@")) {
			Optional<EmailAddress> email = EmailAddress.parse(login);
			return email.map((value) -> new LoginName(value.value(), null, value))
				.orElseGet(() -> new LoginName(login.toLowerCase(Locale.ROOT), null, null));
		}
		Optional<PhoneNumber> phone = PhoneNumber.parse(login);
		return phone.map((value) -> new LoginName(value.value(), value, null))
			.orElseGet(() -> new LoginName(login.toLowerCase(Locale.ROOT), null, null));
	}

	/** Логин — телефон или e-mail, а не бессмысленный ввод. */
	boolean isContact() {
		return this.phone != null || this.email != null;
	}

	/** Без значения: логин — телефон или e-mail человека. */
	@Override
	public String toString() {
		return "LoginName[" + ((this.phone != null) ? "phone" : (this.email != null) ? "email" : "unparsed") + "]";
	}

}
