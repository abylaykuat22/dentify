package kz.dentify.platform;

import static kz.dentify.platform.ProblemDetailsTests.body;
import static kz.dentify.platform.ProblemDetailsTests.problem;
import static kz.dentify.platform.ProblemDetailsTests.read;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import kz.dentify.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tools.jackson.databind.JsonNode;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/**
 * PLT-07: ошибки настоящей БД через JPA и Hibernate, а не подделанные исключения. Пробная таблица
 * {@code test_constraint_probe} и {@link DataProbeController}:
 * <ul>
 * <li>уникальность — 409 {@code error.duplicate}, конфликт {@code @Version} — 409 {@code error.version-conflict};</li>
 * <li>NOT NULL и CHECK — проверка входных данных пропущена, это дефект: 500 и ERROR с SQLSTATE и именем
 * ограничения;</li>
 * <li>объект, которого нет (или его скрыла RLS чужой клиники), — 404;</li>
 * <li>значения строки из сообщения PostgreSQL не попадают ни в ответ, ни в лог.</li>
 * </ul>
 */
@ExtendWith(OutputCaptureExtension.class)
class DatabaseProblemsTests extends AbstractIntegrationTest {

	static final String DATA = DataProbeController.PATH;

	@Test
	void realUniqueViolationIsDuplicateConflict(CapturedOutput output) {
		String code = "dup-" + UUID.randomUUID();
		create(code, 1).expectStatus().isOk();

		String body = body(create(code, 1), 409);

		assertThat(read(body).path("code").asString()).isEqualTo("error.duplicate");
		assertThat(body).doesNotContain(code, "ux_test_constraint_probe_code", "insert", "SQL");
		assertThat(logLines(output, "23505")).isNotEmpty()
			.allSatisfy((line) -> assertThat(line).contains("\"WARN\"", "ux_test_constraint_probe_code"));
		assertThat(output.getOut()).doesNotContain(code);
	}

	/** NOT NULL в БД — сервис пропустил проверку: 500, а не 409, и ERROR, чтобы дефект был виден. */
	@Test
	void notNullViolationIsServerError(CapturedOutput output) {
		String body = body(create(null, 1), 500);

		assertThat(read(body).path("code").asString()).isEqualTo("error.internal");
		assertThat(body).doesNotContain("test_constraint_probe", "not-null");
		assertThat(logLines(output, "23502")).isNotEmpty()
			.allSatisfy((line) -> assertThat(line).contains("\"ERROR\""));
	}

	@Test
	void checkViolationIsServerError(CapturedOutput output) {
		String code = "chk-" + UUID.randomUUID();

		String body = body(create(code, -1), 500);

		assertThat(read(body).path("code").asString()).isEqualTo("error.internal");
		assertThat(body).doesNotContain("ck_test_constraint_probe_qty", code);
		assertThat(logLines(output, "23514")).isNotEmpty()
			.allSatisfy((line) -> assertThat(line).contains("\"ERROR\"", "ck_test_constraint_probe_qty"));
		assertThat(output.getOut()).doesNotContain(code);
	}

	/** Клиент прислал версию, которую уже обновили: Hibernate отклоняет merge, ответ — 409 конфликт версии. */
	@Test
	void staleVersionFromHibernateIsVersionConflict() {
		JsonNode created = ok(create("ver-" + UUID.randomUUID(), 1));
		String id = created.path("id").asString();
		assertThat(created.path("version").asLong()).isZero();

		JsonNode updated = ok(update(id, created.path("code").asString(), 2, 0));
		assertThat(updated.path("version").asLong()).isEqualTo(1);

		problem(update(id, created.path("code").asString(), 3, 0), 409, "error.version-conflict");
	}

	/**
	 * Объекта нет или его не видно из-за RLS: Hibernate бросает EntityNotFoundException при загрузке ссылки. Напрямую и
	 * через репозиторий Spring Data, где оно переведено в JpaObjectRetrievalFailureException, ответ — 404.
	 */
	@Test
	void missingEntityIsNotFound() {
		UUID id = UUID.randomUUID();

		problem(this.http.get().uri(DATA + "/" + id).exchange(), 404, "error.not-found");
		problem(this.http.get().uri(DATA + "/" + id + "/code").exchange(), 404, "error.not-found");
	}

	private ResponseSpec create(String code, int qty) {
		return this.http.post()
			.uri(DATA)
			.contentType(MediaType.APPLICATION_JSON)
			.body(new DataProbeController.ProbeWrite(code, qty, null))
			.exchange();
	}

	private ResponseSpec update(String id, String code, int qty, long version) {
		return this.http.put()
			.uri(DATA + "/" + id)
			.contentType(MediaType.APPLICATION_JSON)
			.body(new DataProbeController.ProbeWrite(code, qty, version))
			.exchange();
	}

	private static JsonNode ok(ResponseSpec response) {
		return read(response.expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody());
	}

	/** Записи обработчика ошибок платформы об ограничении БД с этим SQLSTATE. */
	private static List<String> logLines(CapturedOutput output, String sqlState) {
		return output.getOut()
			.lines()
			.filter((line) -> line.contains("ProblemDetailsExceptionHandler") && line.contains(sqlState))
			.toList();
	}

}
