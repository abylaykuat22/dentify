package kz.dentify.iam.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Вход (IAM-02): одно поле «Телефон или e-mail», пароль, «Запомнить это устройство».
 *
 * @param login телефон в любой записи или e-mail: есть {@code @} — e-mail
 * @param password пароль
 * @param rememberMe Запомнить устройство: cookie и сессия на 30 дней от входа; без галочки cookie до закрытия
 * браузера и выход после 12 часов бездействия. Обязательно: {@code Boolean} с {@code @NotNull}, а не {@code boolean}, —
 * без поля ответ 400 с кодом поля {@code validation.not-null}, а не ошибка формата значения
 */
@Schema(description = "Вход по телефону или e-mail и паролю")
record LoginRequest(
		@Schema(description = "Телефон в любой записи или e-mail: есть @ — e-mail, иначе телефон",
				example = "+7 707 123 45 67") @NotBlank @Size(max = 320) String login,
		@Schema(description = "Пароль", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
		@NotEmpty @Size(max = 1024) String password,
		@Schema(description = "Запомнить устройство: сессия и cookie на 30 дней от входа. Без галочки cookie живёт до "
				+ "закрытия браузера, а сессия — до 12 часов бездействия. В интерфейсе галочка включена по умолчанию")
		@NotNull Boolean rememberMe) {

	/** Без логина и пароля: запрос не должен попасть в лог со значениями. */
	@Override
	public String toString() {
		return "LoginRequest[rememberMe=" + this.rememberMe + "]";
	}

}
