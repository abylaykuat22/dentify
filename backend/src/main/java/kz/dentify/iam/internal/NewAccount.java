package kz.dentify.iam.internal;

import org.jspecify.annotations.Nullable;

/**
 * Данные нового аккаунта, как их ввёл человек: телефон и e-mail в любой записи (нормализует {@link Accounts#create}),
 * ФИО и пароль. Нужен хотя бы один контакт.
 * <p>
 * {@link #toString()} не выводит значений: в них пароль и персональные данные.
 *
 * @param phone телефон или null
 * @param email e-mail или null
 * @param fullName ФИО
 * @param password пароль, проверяется правилами {@code PasswordPolicy} и хэшируется бином {@code PasswordEncoder}
 */
public record NewAccount(@Nullable String phone, @Nullable String email, String fullName, String password) {

	@Override
	public String toString() {
		return "NewAccount[•••]";
	}

}
