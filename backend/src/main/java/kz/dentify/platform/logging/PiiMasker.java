package kz.dentify.platform.logging;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Маскирует персональные данные в произвольном тексте лога (PLT-10): ИИН, телефон, e-mail.
 * <ul>
 * <li>ИИН — 12 цифр подряд или группами 4-4-4 через пробел: {@code 900101300123 → ••••0123};</li>
 * <li>телефон — {@code +7}, {@code 7} или {@code 8} и 10 цифр, а также местный номер из 10 цифр с кодом на 7
 * ({@code 7011234567}, {@code (701) 123-45-67}). Цифры сгруппированы как угодно — пробелы, дефисы, скобки,
 * группы 3-3-2-2 мобильного и 4-2-2-2 городского номера ({@code 8 (7172) 55-66-77}) — или идут подряд:
 * {@code +7 (701) 123-45-67 → +7••••4567};</li>
 * <li>e-mail, в том числе на кириллице, — остаётся первая буква и домен:
 * {@code aigerim@example.kz → a•••@example.kz}.</li>
 * </ul>
 * URL-кодированный текст ({@code q=%2B77011234567}, {@code q=%2B7%20707%20123%2045%2067},
 * {@code q=8+707+123+45+67}, {@code aigerim%40example.kz}, {@code %22iin%22%3A%22900101300123%22}) маскируется
 * так же: {@code %XX} считается границей, а {@code %20}, {@code %28}, {@code %29} и {@code +} внутри номера —
 * разделителями.
 * <p>
 * Чего маска не портит: 12 цифр внутри hex-идентификатора — traceId/spanId, UUID
 * ({@code 01a0d780-…-123456789012}) — остаются как есть; 13-значные числа (миллисекунды) не принимаются за
 * ИИН. ИИН рядом с другими буквами ({@code IIN900101300123}, {@code patient-900101300123}) маскируется. Точка
 * и подчёркивание — не разделители номера: даты, суммы и версии миграций ({@code 2026.10.01.1200},
 * {@code V2026_10_01_1200__clinic_…}) остаются как есть.
 * <p>
 * ФИО регулярным выражением не найти: их в лог не пишем (в логах только id), а поля с ФИО по имени ключа
 * закрывает {@link PiiMaskingJsonMembersCustomizer}. Маска — защитная сетка: ПД в логи не пишем вовсе.
 */
public final class PiiMasker {

	/** Символы маски: одинаковы для всех видов ПД, чтобы их было легко искать в логах. */
	public static final String MASK = "••••";

	/** Левая граница номера: слева не буква, цифра, «+» или «-», либо сразу после {@code %XX}. */
	private static final String NUMBER_START = "(?:(?<=%[0-9A-Fa-f]{2})|(?<![\\p{Alnum}+-]))";

	private static final String NUMBER_END = "(?![\\p{Alnum}-])";

	/**
	 * Разделитель между цифрами номера, до четырёх подряд: пробел (и неразрывный), скобка, дефис; в URL —
	 * {@code +} вместо пробела, {@code %20}, {@code %28}, {@code %29}. Точки и подчёркивания нет: иначе номером
	 * станут версии и даты.
	 */
	private static final String SEP = "(?:[\\s\\u00A0()-]|\\+|%20|%28|%29){0,4}";

	/** С кодом страны: +7, 7, 8 или URL-кодированный плюс ({@code %2B7}) и 10 цифр с любой группировкой. */
	private static final Pattern PHONE = Pattern
		.compile(NUMBER_START + "(?:\\+7|%2[Bb]7|7|8)" + SEP + "\\d(?:" + SEP + "\\d){9}" + NUMBER_END);

	/** Местный номер без кода страны: 10 цифр, код оператора или города в РК начинается с 7. */
	private static final Pattern LOCAL_PHONE = Pattern
		.compile(NUMBER_START + "(?:\\(|%28)?7(?:" + SEP + "\\d){9}" + NUMBER_END);

	/**
	 * 12 цифр, не окружённых цифрами, или 4-4-4 через пробел. Буквы рядом допустимы — hex-контекст отсекает
	 * {@link #isInsideHexId}.
	 */
	private static final Pattern IIN = Pattern
		.compile("(?:(?<=%[0-9A-Fa-f]{2})|(?<!\\d))(?:\\d{12}|\\d{4}[ \\u00A0]\\d{4}[ \\u00A0]\\d{4})(?!\\d)");

	/** Локальная часть и домен — любые буквы Unicode (кириллические адреса); разделитель — @ или %40. */
	private static final Pattern EMAIL = Pattern.compile("(?<![\\p{Alnum}._%+-])([\\p{Alnum}_%+-])[\\p{Alnum}._%+-]*"
			+ "(@|%40)([\\p{Alnum}-]+(?:\\.[\\p{Alnum}-]+)*\\.\\p{Alpha}{2,})", Pattern.UNICODE_CHARACTER_CLASS);

	/** hex-идентификатор: traceId, spanId, UUID — hex-группы через дефис. */
	private static final Pattern HEX_ID = Pattern.compile("[0-9A-Fa-f]+(?:-[0-9A-Fa-f]+)*");

	private static final Pattern UUID = Pattern
		.compile("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}");

	private static final Pattern HEX_LETTER = Pattern.compile("[A-Fa-f]");

	private static final Pattern NON_DIGITS = Pattern.compile("\\D");

	/** URL-кодированный символ: его цифры ({@code %2B}, {@code %20}) — не цифры номера. */
	private static final Pattern URL_ESCAPE = Pattern.compile("%[0-9A-Fa-f]{2}");

	private PiiMasker() {
	}

	/**
	 * Возвращает текст с замаскированными ИИН, телефонами и e-mail; {@code null} остаётся {@code null}.
	 */
	public static @Nullable String mask(@Nullable String text) {
		if (text == null || !mayContainPii(text)) {
			return text;
		}
		String result = PHONE.matcher(text).replaceAll(PiiMasker::maskPhone);
		result = LOCAL_PHONE.matcher(result).replaceAll(PiiMasker::maskPhone);
		String withoutPhones = result;
		result = IIN.matcher(result)
			.replaceAll((match) -> isInsideHexId(withoutPhones, match.start(), match.end())
					? Matcher.quoteReplacement(match.group()) : MASK + lastFourDigits(match.group()));
		return EMAIL.matcher(result)
			.replaceAll((match) -> Matcher.quoteReplacement(match.group(1) + "•••" + match.group(2) + match.group(3)));
	}

	private static String maskPhone(MatchResult match) {
		return Matcher.quoteReplacement("+7" + MASK + lastFourDigits(match.group()));
	}

	private static String lastFourDigits(String text) {
		String digits = NON_DIGITS.matcher(URL_ESCAPE.matcher(text).replaceAll("")).replaceAll("");
		return digits.substring(digits.length() - 4);
	}

	/**
	 * Цифры с позиции {@code start} до {@code end} — часть hex-идентификатора, а не ИИН: слово вокруг них
	 * (латиница, цифры, дефис) длиннее самих цифр, целиком hex и содержит hex-букву или имеет вид UUID.
	 */
	private static boolean isInsideHexId(String text, int start, int end) {
		int from = start;
		while (from > 0 && isWordChar(text.charAt(from - 1)) && !isAfterPercentEscape(text, from)) {
			from--;
		}
		int to = end;
		while (to < text.length() && isWordChar(text.charAt(to))) {
			to++;
		}
		if (from == start && to == end) {
			return false;
		}
		String word = text.substring(from, to);
		return HEX_ID.matcher(word).matches() && (HEX_LETTER.matcher(word).find() || UUID.matcher(word).matches());
	}

	private static boolean isWordChar(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '-';
	}

	/** Перед позицией стоит {@code %XX}: URL-кодированный символ — граница слова. */
	private static boolean isAfterPercentEscape(String text, int position) {
		return position >= 3 && text.charAt(position - 3) == '%' && isHexDigit(text.charAt(position - 2))
				&& isHexDigit(text.charAt(position - 1));
	}

	private static boolean isHexDigit(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	/** Быстрый отсев: в тексте без цифр и без «@» маскировать нечего. */
	private static boolean mayContainPii(String text) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '@' || (c >= '0' && c <= '9')) {
				return true;
			}
		}
		return false;
	}

}
