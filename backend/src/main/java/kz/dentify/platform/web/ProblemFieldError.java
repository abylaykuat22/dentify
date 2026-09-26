package kz.dentify.platform.web;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import org.jspecify.annotations.Nullable;

/**
 * Элемент {@code errors[]} ProblemDetail: ошибка одного поля формы или параметра запроса.
 *
 * @param field путь поля: {@code iin}, {@code items[0].quantity}, {@code name.ru}; null — ошибка всей формы
 * @param code машинный код, например {@code validation.not-blank} или {@code patient.iin.checksum}
 * @param message текст на языке пользователя
 */
@Schema(name = "ProblemFieldError", description = "Ошибка поля формы или параметра запроса")
public record ProblemFieldError(
		@Schema(description = "Путь поля: iin, items[0].quantity, name.ru; null — ошибка всей формы",
				example = "iin") @Nullable String field,
		@Schema(description = "Машинный код ошибки поля", example = "validation.not-blank",
				requiredMode = RequiredMode.REQUIRED) String code,
		@Schema(description = "Текст ошибки на языке пользователя", example = "Заполните поле.",
				requiredMode = RequiredMode.REQUIRED) String message) {
}
