package kz.dentify.platform.api;

import java.util.List;

/**
 * Нарушение в поле запроса, которое нашёл сервис, а не Bean Validation: например, контрольная сумма ИИН или дата
 * задним числом. Превращается в элемент {@code errors[]} ответа с текстом из {@code MessageSource} по коду.
 *
 * @param field путь поля в теле или имя параметра запроса: {@code iin}, {@code items[0].quantity}, {@code size}
 * @param code код и ключ сообщения, например {@code patient.iin.checksum}
 * @param args аргументы сообщения
 */
public record FieldViolation(String field, String code, List<Object> args) {

	public FieldViolation {
		args = List.copyOf(args);
	}

	public static FieldViolation of(String field, String code, Object... args) {
		return new FieldViolation(field, code, List.of(args));
	}

}
