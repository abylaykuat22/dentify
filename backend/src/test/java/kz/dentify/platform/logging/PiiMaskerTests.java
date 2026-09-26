package kz.dentify.platform.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Все данные вымышленные. */
class PiiMaskerTests {

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource(delimiter = '|', textBlock = """
			ИИН 900101300123                     | ИИН ••••0123
			iin=900101300123,                    | iin=••••0123,
			ИИН900101300123                      | ИИН••••0123
			IIN900101300123                      | IIN••••0123
			patient-900101300123                 | patient-••••0123
			ИИН 9001 0130 0123                   | ИИН ••••0123
			+77011234567                         | +7••••4567
			тел.: +7 701 123 45 67               | тел.: +7••••4567
			+7 (701) 123-45-67.                  | +7••••4567.
			8 701 123 45 67                      | +7••••4567
			87011234567                          | +7••••4567
			77011234567                          | +7••••4567
			7011234567                           | +7••••4567
			тел. (701) 123-45-67                 | тел. +7••••4567
			701 123 45 67                        | +7••••4567
			aigerim.test@example.kz              | a•••@example.kz
			to=<Nurlan_1990@mail.example.com>    | to=<N•••@mail.example.com>
			айгерим@почта.кз                     | а•••@почта.кз
			Иван@пример.рф                       | И•••@пример.рф
			""")
	void masksPersonalData(String raw, String masked) {
		assertThat(PiiMasker.mask(raw)).isEqualTo(masked);
	}

	/**
	 * Телефон в любой записи: E.164, через 8, без кода страны, группы 3-3-2-2 и 4-2-2-2 (городской), с пробелами,
	 * дефисами, скобками, неразрывным пробелом или без разделителей — и в окружении JSON, query, текста.
	 */
	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource(delimiter = '|', textBlock = """
			+77071234567                          | +7••••4567
			87071234567                           | +7••••4567
			8 707 123 45 67                       | +7••••4567
			8(707)1234567                         | +7••••4567
			+7-707-123-45-67                      | +7••••4567
			+7 707 1234567                        | +7••••4567
			8-707-123-4567                        | +7••••4567
			+7 (7172) 55-66-77                    | +7••••6677
			8 (7172) 55-66-77                     | +7••••6677
			(7172) 55-66-77                       | +7••••6677
			phone=7071234567                     | phone=+7••••4567
			{"phone":"7071234567"}                | {"phone":"+7••••4567"}
			тел 7071234567, звонить после 18:00   | тел +7••••4567, звонить после 18:00
			tel:+77071234567;                     | tel:+7••••4567;
			""")
	void masksPhoneInAnyFormat(String raw, String masked) {
		assertThat(PiiMasker.mask(raw)).isEqualTo(masked);
	}

	/** Неразрывный пробел — так номер форматируют текстовые редакторы и копирование из мессенджеров. */
	@Test
	void masksPhoneWithNonBreakingSpaces() {
		assertThat(PiiMasker.mask("тел. 8 707 123 45 67")).isEqualTo("тел. +7••••4567");
	}

	/** Серверный поиск по телефону и e-mail (R001): значения приходят в логи URL-кодированными. */
	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource(delimiter = '|', textBlock = """
			/api/patients?q=%2B77011234567            | /api/patients?q=+7••••4567
			/api/patients?q=%2b77011234567            | /api/patients?q=+7••••4567
			/api/patients?q=%2B7 707 123 45 67        | /api/patients?q=+7••••4567
			/api/patients?q=%2B7%20707%20123%2045%2067 | /api/patients?q=+7••••4567
			/api/patients?q=%2B7+707+123+45+67        | /api/patients?q=+7••••4567
			/api/patients?q=8%20%28707%29%20123-45-67 | /api/patients?q=+7••••4567
			/api/patients?q=8+707+123+45+67&page=2    | /api/patients?q=+7••••4567&page=2
			tel%3A%2B77071234567                      | tel%3A+7••••4567
			/api/patients?q=aigerim%40example.kz      | /api/patients?q=a•••%40example.kz
			%22iin%22%3A%22900101300123%22            | %22iin%22%3A%22••••0123%22
			""")
	void masksUrlEncodedPersonalData(String raw, String masked) {
		assertThat(PiiMasker.mask(raw)).isEqualTo(masked);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			// traceId/spanId — hex, 12 цифр подряд внутри не маскируются
			"4bf92f3577b34da6123456789012a3ce", "a123456789012b", "deadbeef-123456789012",
			"78123456789012345678901234567890", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
			// UUID с последней группой из цифр (clinicId, accountId в логах), в том числе целиком из цифр и с 7/8 в
			// начале группы
			"01a0d780-19fc-78b8-9c3e-123456789012", "12345678-1234-1234-1234-123456789012",
			"70123456-7890-1234-5678-901234567890", "81234567-8123-8123-8123-812345678123",
			// не 12 и не 10–11 цифр: миллисекунды, суммы, короткие коды, время
			"1727261234567", "12345678901234", "12 500 ₸", "7 500 000 ₸", "1 250 000,00 ₸", "12500.00",
			"78 000.50 ₸", "HTTP 404", "Flyway V1", "took 8 ms", "page 7 of 12",
			// даты и время
			"2026-09-25T10:15:30.123456789Z", "25.09.2026 18:00", "2026-10-01 12:00", "PT8H30M",
			"http-nio-8080-exec-1",
			// версии миграций: метка времени через точку и подчёркивание, а не 12 цифр подряд
			"2026.10.01.1200", "Migrating schema dentify to version 2026.10.01.1200 - clinic create branch chair",
			"db/migration/clinic/V2026_10_01_1200__clinic_create_branch_chair.sql",
			"Successfully applied 2 migrations to schema dentify, now at version v2026.11.05.1000",
			// объекты и адреса без e-mail
			"kz.dentify.Foo@1a2b3c", "jdbc:postgresql://127.0.0.1:5433/dentify", "10.0.0.7:8081", "" })
	void leavesNonPersonalDataIntact(String text) {
		assertThat(PiiMasker.mask(text)).isEqualTo(text);
	}

	@ParameterizedTest
	@ValueSource(strings = { "Пациент 900101300123, +7 701 123 45 67, aigerim@example.kz" })
	void masksEveryKindInOneLine(String text) {
		assertThat(PiiMasker.mask(text))
			.isEqualTo("Пациент ••••0123, +7••••4567, a•••@example.kz")
			.doesNotContain("900101300123", "123 45 67", "aigerim");
	}

	@Test
	void keepsNull() {
		assertThat(PiiMasker.mask(null)).isNull();
	}

}
