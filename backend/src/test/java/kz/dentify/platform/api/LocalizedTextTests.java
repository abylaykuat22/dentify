package kz.dentify.platform.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** PLT-11: двуязычное значение справочника, без казахского показывается русский. */
class LocalizedTextTests {

	static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void kazakhWhenPresent() {
		LocalizedText text = new LocalizedText("Пломба", "Пломба салу");

		assertThat(text.text(Languages.KAZAKH)).isEqualTo("Пломба салу");
		assertThat(text.text(Locale.forLanguageTag("kk-KZ"))).isEqualTo("Пломба салу");
		assertThat(text.text(Languages.RUSSIAN)).isEqualTo("Пломба");
	}

	@Test
	void russianWithoutKazakh() {
		assertThat(LocalizedText.of("Консультация").text(Languages.KAZAKH)).isEqualTo("Консультация");
		assertThat(new LocalizedText("Консультация", "  ").text(Languages.KAZAKH)).isEqualTo("Консультация");
		assertThat(new LocalizedText("Консультация", "  ").kk()).isNull();
	}

	@Test
	void otherLanguagesGetRussian() {
		assertThat(new LocalizedText("Пломба", "Пломба салу").text(Locale.ENGLISH)).isEqualTo("Пломба");
	}

	@Test
	void jsonIsObjectWithRuAndKk() {
		String json = JSON.writeValueAsString(new LocalizedText("Удаление зуба", "Тіс жұлу"));

		assertThat(json).isEqualTo("{\"ru\":\"Удаление зуба\",\"kk\":\"Тіс жұлу\"}");
		assertThat(JSON.readValue("{\"ru\":\"Удаление зуба\"}", LocalizedText.class))
			.isEqualTo(LocalizedText.of("Удаление зуба"));
	}

}
