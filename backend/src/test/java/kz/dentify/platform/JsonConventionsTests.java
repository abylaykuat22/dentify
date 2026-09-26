package kz.dentify.platform;

import static org.assertj.core.api.Assertions.assertThat;

import kz.dentify.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.http.MediaType;

/**
 * PLT-23: деньги в JSON — десятичной строкой без потери нулей и точности, в запросе принимаются строка и число;
 * даты — строки ISO 8601.
 */
class JsonConventionsTests extends AbstractIntegrationTest {

	@Test
	void decimalsAreStringsBothWays() {
		assertThat(echo("""
				{"name": "Пломба", "price": "12500.50", "birthDate": "1990-01-01"}""").path("price").asString())
			.isEqualTo("12500.50");
		assertThat(echo("""
				{"name": "Пломба", "price": 0.10}""").path("price").isString()).isTrue();
	}

	@Test
	void datesAreIsoStrings() {
		assertThat(echo("""
				{"name": "Пломба", "birthDate": "1990-01-01"}""").path("birthDate").asString()).isEqualTo("1990-01-01");
	}

	private JsonNode echo(String json) {
		String body = this.http.post()
			.uri(ProblemProbeController.PATH + "/validate")
			.contentType(MediaType.APPLICATION_JSON)
			.body(json)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		return ProblemDetailsTests.read(body);
	}

}
