package kz.dentify.platform.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import kz.dentify.platform.api.Languages;
import kz.dentify.platform.api.LocalizedText;
import org.junit.jupiter.api.Test;

import org.springframework.context.i18n.LocaleContextHolder;

/**
 * Язык вне HTTP-запроса — фоновые задачи, письма, обработчики событий — русский, а не язык ОС сервера: на машине с
 * казахской локалью {@code LocalizedText.text()} не должен отдать казахский при выключенном {@code dentify.i18n.kk}.
 */
class I18nConfigurationTests {

	@Test
	void outsideRequestLanguageIsRussianNotServerLocale() {
		Locale server = Locale.getDefault();
		try {
			Locale.setDefault(Languages.KAZAKH);
			LocaleContextHolder.resetLocaleContext();

			new I18nConfiguration().russianOutsideRequests().afterSingletonsInstantiated();

			assertThat(LocaleContextHolder.getLocale()).isEqualTo(Languages.RUSSIAN);
			assertThat(new LocalizedText("Пломба", "Пломба салу").text()).isEqualTo("Пломба");
		}
		finally {
			Locale.setDefault(server);
		}
	}

}
