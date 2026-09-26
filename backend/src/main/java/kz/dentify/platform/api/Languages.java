package kz.dentify.platform.api;

import java.util.Locale;

/**
 * Языки Dentify. Русский — основной и запасной: нет перевода или язык не поддержан — отвечаем по-русски. Казахский
 * включается флагом {@code dentify.i18n.kk} вместе со словарями {@code messages_kk} (PLT-34).
 */
public final class Languages {

	public static final Locale RUSSIAN = Locale.of("ru");

	public static final Locale KAZAKH = Locale.of("kk");

	private Languages() {
	}

	/** Казахский ли язык локали, с регионом или без: {@code kk}, {@code kk-KZ}. */
	public static boolean isKazakh(Locale locale) {
		return KAZAKH.getLanguage().equals(locale.getLanguage());
	}

}
