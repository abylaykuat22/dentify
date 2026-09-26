package kz.dentify.platform;

import static kz.dentify.platform.ProblemDetailsTests.fieldErrors;
import static kz.dentify.platform.ProblemDetailsTests.problem;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import kz.dentify.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * PLT-11: казахский включается словарём и флагом без правок кода. В профиле test лежит словарь
 * {@code messages_kk.properties} и стоит {@code dentify.i18n.kk=true}: тот же эндпоинт отдаёт ProblemDetail на
 * казахском по {@code Accept-Language: kk} или cookie, код ошибки не меняется. Ключа нет в kk — русский текст.
 * Выключенный флаг (только ru) проверяет {@code DentifyLocaleResolverTests}.
 */
class LocalizationTests extends AbstractIntegrationTest {

	static final String NOT_FOUND = ProblemProbeController.PATH + "/not-found/" + UUID.randomUUID();

	@Test
	void russianByDefault() {
		JsonNode problem = problem(this.http.get().uri(NOT_FOUND).exchange(), 404, "error.not-found");

		assertThat(problem.path("title").asString()).isEqualTo("Не найдено");
		assertThat(problem.path("detail").asString()).isEqualTo("Запись не найдена или удалена.");
	}

	@Test
	void kazakhByAcceptLanguage() {
		JsonNode problem = problem(
				this.http.get().uri(NOT_FOUND).header(HttpHeaders.ACCEPT_LANGUAGE, "kk-KZ,kk;q=0.9,ru;q=0.8").exchange(),
				404, "error.not-found");

		assertThat(problem.path("title").asString()).isEqualTo("Табылмады");
		assertThat(problem.path("detail").asString()).isEqualTo("Жазба табылмады немесе жойылды.");
	}

	@Test
	void kazakhByCookieOverAcceptLanguage() {
		JsonNode problem = problem(this.http.get()
			.uri(NOT_FOUND)
			.cookie("dentify-lang", "kk")
			.header(HttpHeaders.ACCEPT_LANGUAGE, "ru")
			.exchange(), 404, "error.not-found");

		assertThat(problem.path("detail").asString()).isEqualTo("Жазба табылмады немесе жойылды.");
	}

	@Test
	void unsupportedLanguageFallsBackToRussian() {
		JsonNode problem = problem(
				this.http.get().uri(NOT_FOUND).header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9").exchange(), 404,
				"error.not-found");

		assertThat(problem.path("detail").asString()).isEqualTo("Запись не найдена или удалена.");
	}

	/** Нет перевода ключа — русский текст из базового словаря, а не код и не язык ОС сервера. */
	@Test
	void missingKazakhKeyFallsBackToRussian() {
		JsonNode problem = problem(
				this.http.get().uri(ProblemProbeController.PATH + "/forbidden").header(HttpHeaders.ACCEPT_LANGUAGE, "kk").exchange(),
				403, "error.forbidden");

		assertThat(problem.path("detail").asString()).isEqualTo("Недостаточно прав для этого действия.");
	}

	/** Тексты Bean Validation тоже из MessageSource на языке запроса: и стандартные ограничения, и свои ключи. */
	@Test
	void fieldErrorsInKazakh() {
		JsonNode problem = problem(this.http.post()
			.uri(ProblemProbeController.PATH + "/validate")
			.header(HttpHeaders.ACCEPT_LANGUAGE, "kk")
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"name": "", "iin": "123"}""")
			.exchange(), 400, "error.validation");

		assertThat(problem.path("title").asString()).isEqualTo("Қате сұрау");
		assertThat(problem.path("detail").asString()).isEqualTo("Өрістердің дұрыс толтырылғанын тексеріңіз.");
		assertThat(fieldErrors(problem)).containsExactlyInAnyOrder(
				Map.of("field", "name", "code", "validation.not-blank", "message", "Өрісті толтырыңыз."),
				Map.of("field", "iin", "code", "probe.iin.format", "message", "ЖСН 12 саннан тұрады."));
	}

}
