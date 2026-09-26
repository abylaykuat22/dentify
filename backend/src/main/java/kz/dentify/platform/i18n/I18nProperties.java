package kz.dentify.platform.i18n;

import java.util.List;
import java.util.Locale;

import kz.dentify.platform.api.Languages;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки языков, {@code dentify.i18n}. Русский есть всегда.
 *
 * @param kk включён ли казахский (PLT-34): вместе со словарями {@code messages_kk}. Пока false — все ответы на
 * русском, какой бы язык ни пришёл, а переключатель языка на фронте скрыт
 */
@ConfigurationProperties("dentify.i18n")
public record I18nProperties(boolean kk) {

	/** Поддерживаемые языки: первый — язык по умолчанию. */
	public List<Locale> supportedLocales() {
		return this.kk ? List.of(Languages.RUSSIAN, Languages.KAZAKH) : List.of(Languages.RUSSIAN);
	}

}
