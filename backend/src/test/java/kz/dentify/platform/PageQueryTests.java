package kz.dentify.platform;

import static kz.dentify.platform.ProblemDetailsTests.fieldErrors;
import static kz.dentify.platform.ProblemDetailsTests.problem;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import kz.dentify.AbstractIntegrationTest;
import kz.dentify.platform.api.PageQuery;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * PLT-23: параметры списка page, size, sort, q и ответ {@code {items, page, size, total}}. Разбирает их
 * резолвер платформы; ошибки — 400 с {@code errors[]}.
 */
class PageQueryTests extends AbstractIntegrationTest {

	static final String PAGE = ProblemProbeController.PATH + "/page";

	static final String QUERY = ProblemProbeController.PATH + "/page-query";

	@Test
	void defaults() {
		JsonNode page = get(PAGE);

		assertThat(page.path("page").asInt()).isZero();
		assertThat(page.path("size").asInt()).isEqualTo(PageQuery.DEFAULT_SIZE);
		assertThat(page.path("total").asLong()).isEqualTo(ProblemProbeController.ITEMS);
		assertThat(page.path("items")).hasSize(PageQuery.DEFAULT_SIZE);
		assertThat(page.path("items").get(0).asString()).isEqualTo("item-1");
	}

	/** Приёмка PLT-23: size=1000 возвращает не больше 100 элементов. */
	@Test
	void sizeAboveLimitIsCappedNotRejected() {
		JsonNode page = get(PAGE + "?size=1000");

		assertThat(page.path("size").asInt()).isEqualTo(PageQuery.MAX_SIZE);
		assertThat(page.path("items")).hasSize(PageQuery.MAX_SIZE);
	}

	@Test
	void secondPage() {
		JsonNode page = get(PAGE + "?page=2&size=100");

		assertThat(page.path("page").asInt()).isEqualTo(2);
		assertThat(page.path("items")).hasSize(50);
		assertThat(page.path("items").get(0).asString()).isEqualTo("item-201");
	}

	@Test
	void sortAndSearch() {
		JsonNode query = get(QUERY + "?sort={sort}&q={q}", "name,-createdAt", " Айгерим ");

		assertThat(strings(query.path("sort"))).containsExactly("name", "-createdAt");
		assertThat(query.path("q").asString()).isEqualTo("Айгерим");
	}

	@Test
	void repeatedSortParameter() {
		JsonNode query = get(QUERY + "?sort=-name&sort=createdAt");

		assertThat(strings(query.path("sort"))).containsExactly("-name", "createdAt");
	}

	@Test
	void defaultSortAndBlankSearch() {
		JsonNode query = get(QUERY + "?q={q}", "  ");

		assertThat(strings(query.path("sort"))).containsExactly("-createdAt");
		assertThat(query.path("q").isNull()).isTrue();
	}

	@Test
	void invalidParametersAreReportedTogether() {
		JsonNode problem = problem(
				this.http.get().uri(PAGE + "?page=-1&size=0&sort=iin&q={q}", "я".repeat(101)).exchange(), 400,
				"error.validation");

		assertThat(fieldErrors(problem)).containsExactlyInAnyOrder(
				Map.of("field", "page", "code", "pagination.page", "message", "Номер страницы — целое число от 0."),
				Map.of("field", "size", "code", "pagination.size", "message",
						"Размер страницы — целое число от 1 до 100."),
				Map.of("field", "sort", "code", "pagination.sort", "message", "Сортировка по этому полю недоступна."),
				Map.of("field", "q", "code", "search.q.too-long", "message", "Строка поиска длиннее 100 символов."));
	}

	/**
	 * Смещение page * size больше предела int в JPA — 400 по полю page, а не 500 из Spring Data. Последняя страница,
	 * которая в предел помещается, — обычный ответ.
	 */
	@Test
	void pageBeyondJpaOffsetIsRejected() {
		assertThat(fieldErrors(problem(this.http.get().uri(PAGE + "?page=2147483647").exchange(), 400,
				"error.validation")))
			.containsExactly(
					Map.of("field", "page", "code", "pagination.page", "message", "Номер страницы — целое число от 0."));
		assertThat(fieldErrors(problem(this.http.get().uri(PAGE + "?page=30000000&size=100").exchange(), 400,
				"error.validation")))
			.extracting((error) -> error.get("field"))
			.containsExactly("page");

		JsonNode last = get(PAGE + "?page=21474836&size=100");
		assertThat(last.path("page").asInt()).isEqualTo(21474836);
		assertThat(last.path("items")).isEmpty();
	}

	@Test
	void nonNumericPageAndSize() {
		JsonNode problem = problem(this.http.get().uri(PAGE + "?page=first&size=all").exchange(), 400,
				"error.validation");

		assertThat(fieldErrors(problem)).extracting((error) -> error.get("field")).containsExactly("page", "size");
	}

	/** Без {@code @SortBy} сортировка не принимается: сортировать можно только по полям с индексом (PLT-22). */
	@Test
	void sortWithoutSortByIsRejected() {
		assertThat(get(ProblemProbeController.PATH + "/unsorted").path("sort")).isEmpty();
		problem(this.http.get().uri(ProblemProbeController.PATH + "/unsorted?sort=name").exchange(), 400,
				"error.validation");
	}

	private JsonNode get(String uri, Object... variables) {
		String body = this.http.get()
			.uri(uri, variables)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		return ProblemDetailsTests.read(body);
	}

	static List<String> strings(JsonNode array) {
		return array.valueStream().map(JsonNode::asString).toList();
	}

}
