package kz.dentify.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/**
 * Конкурентные правки (PLT-08): устаревшая версия — 409 {@code error.version-conflict} с тем, кто и когда изменил
 * запись последним; из параллельных обновлений одной строки успешно ровно одно.
 */
class VersionConflictTests extends TenantIntegrationTest {

	static final int PARALLEL = 8;

	@Test
	void staleVersionIsConflictWithAuthorAndTime() {
		UUID clinic = newClinic();
		String dinara = enterAs(clinic, TestAccounts.DINARA);
		String askar = enterAs(clinic, TestAccounts.ASKAR);
		JsonNode created = createNote(dinara, "Первичный осмотр");
		String id = created.path("id").asString();

		JsonNode saved = ok(update(dinara, id, "Осмотр, правка Динары", 0));
		assertThat(saved.path("version").asLong()).isEqualTo(1);
		assertThat(saved.path("updatedBy").asString()).isEqualTo(TestAccounts.DINARA.toString());

		JsonNode conflict = problem(update(askar, id, "Осмотр, правка Аскара", 0), 409,
				"error.version-conflict");

		assertThat(conflict.path("modifiedBy").asString()).isEqualTo(TestAccounts.DINARA.toString());
		assertThat(conflict.path("modifiedByName").asString()).isEqualTo("Динара Алиева");
		assertThat(Instant.parse(conflict.path("modifiedAt").asString()))
			.isEqualTo(Instant.parse(saved.path("updatedAt").asString()));
		assertThat(conflict.path("detail").asString()).isNotBlank();
	}

	/** Автор без входа (системная правка): время есть, автора и имени нет. */
	@Test
	void conflictWithoutAuthorHasOnlyTime() {
		String session = enter(newClinic());
		String id = createNote(session, "Без автора").path("id").asString();
		ok(update(session, id, "Правка", 0));

		JsonNode conflict = problem(update(session, id, "Опоздала", 0), 409, "error.version-conflict");

		assertThat(conflict.has("modifiedAt")).isTrue();
		assertThat(conflict.has("modifiedBy")).isFalse();
		assertThat(conflict.has("modifiedByName")).isFalse();
	}

	/** Приёмка PLT-08: из параллельных обновлений одной строки успешно ровно одно, остальные — 409 с автором. */
	@Test
	void exactlyOneOfParallelUpdatesWins() throws Exception {
		UUID clinic = newClinic();
		String dinara = enterAs(clinic, TestAccounts.DINARA);
		String askar = enterAs(clinic, TestAccounts.ASKAR);
		String id = createNote(dinara, "Параллельно").path("id").asString();
		CountDownLatch start = new CountDownLatch(1);
		List<Future<EntityExchangeResult<String>>> results = new ArrayList<>();

		try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < PARALLEL; i++) {
				String session = (i % 2 == 0) ? dinara : askar;
				String title = "Правка " + i;
				results.add(pool.submit(() -> {
					start.await();
					return update(session, id, title, 0).expectBody(String.class).returnResult();
				}));
			}
			start.countDown();
		}

		List<JsonNode> winners = new ArrayList<>();
		List<JsonNode> conflicts = new ArrayList<>();
		for (Future<EntityExchangeResult<String>> future : results) {
			EntityExchangeResult<String> result = future.get();
			JsonNode body = read(result.getResponseBody());
			switch (result.getStatus().value()) {
				case 200 -> winners.add(body);
				case 409 -> conflicts.add(body);
				default -> throw new AssertionError("неожиданный ответ " + result.getStatus() + ": " + body);
			}
		}

		assertThat(winners).hasSize(1);
		assertThat(conflicts).hasSize(PARALLEL - 1);
		JsonNode winner = winners.getFirst();
		assertThat(winner.path("version").asLong()).isEqualTo(1);
		assertThat(conflicts).allSatisfy((conflict) -> {
			assertThat(conflict.path("code").asString()).isEqualTo("error.version-conflict");
			assertThat(conflict.path("modifiedBy").asString()).isEqualTo(winner.path("updatedBy").asString());
			assertThat(Instant.parse(conflict.path("modifiedAt").asString()))
				.isEqualTo(Instant.parse(winner.path("updatedAt").asString()));
		});
	}

	/** Правка из сессии: автор — аккаунт, вошедший в неё; сессия без входа — правка без автора. */
	private ResponseSpec update(String session, String id, String title, long version) {
		return this.http.put()
			.uri(NOTES + "/" + id)
			.header(HttpHeaders.COOKIE, session)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("title", title, "version", version))
			.exchange();
	}

}
