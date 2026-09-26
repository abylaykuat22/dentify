package kz.dentify.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Базовая сущность клиники {@code TenantEntity} (PLT-01): id — UUIDv7 от Hibernate, клиника — из контекста, аудит
 * создания и изменения, версия; фильтр Hibernate по клинике для запросов Spring Data с транзакцией и без неё.
 */
class TenantEntityTests extends TenantIntegrationTest {

	@Autowired
	TestNoteRepository notes;

	@Autowired
	EntityManager entityManager;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	TestNoteService service;

	@Test
	void newEntityGetsUuidV7AuditAndVersion() {
		UUID clinic = newClinic();
		UUID branch = UUID.randomUUID();
		String dinara = enter(clinic, branch, TestAccounts.DINARA);
		String askar = enter(clinic, branch, TestAccounts.ASKAR);
		Instant before = Instant.now().minusSeconds(1);

		JsonNode created = ok(this.http.post()
			.uri(NOTES)
			.header(HttpHeaders.COOKIE, dinara)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("title", "Новая"))
			.exchange());

		assertThat(UUID.fromString(created.path("id").asString()).version()).as("UUIDv7").isEqualTo(7);
		assertThat(created.path("version").asLong()).isZero();
		assertThat(created.path("branchId").asString()).as("филиал из сессии").isEqualTo(branch.toString());
		assertThat(created.path("createdBy").asString()).isEqualTo(TestAccounts.DINARA.toString());
		assertThat(created.path("updatedBy").asString()).isEqualTo(TestAccounts.DINARA.toString());
		assertThat(Instant.parse(created.path("createdAt").asString())).isAfter(before)
			.isEqualTo(Instant.parse(created.path("updatedAt").asString()));

		JsonNode renamed = ok(this.http.put()
			.uri(NOTES + "/" + created.path("id").asString())
			.header(HttpHeaders.COOKIE, askar)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("title", "Переименована", "version", 0))
			.exchange());

		assertThat(renamed.path("version").asLong()).isEqualTo(1);
		assertThat(renamed.path("createdBy").asString()).isEqualTo(TestAccounts.DINARA.toString());
		assertThat(renamed.path("updatedBy").asString()).isEqualTo(TestAccounts.ASKAR.toString());
		assertThat(renamed.path("createdAt").asString()).isEqualTo(created.path("createdAt").asString());
		assertThat(Instant.parse(renamed.path("updatedAt").asString()))
			.isAfterOrEqualTo(Instant.parse(created.path("updatedAt").asString()));
	}

	/** Методы запросов Spring Data по умолчанию без транзакции — клиника всё равно своя (фильтр Hibernate и RLS). */
	@Test
	void queryMethodWithoutTransactionSeesOwnClinicOnly() {
		UUID clinicA = newClinic();
		UUID clinicB = newClinic();
		String title = "Одинаковое имя " + UUID.randomUUID();
		String own = createNote(enter(clinicA), title).path("id").asString();
		createNote(enter(clinicB), title);

		List<TestNote> found = TenantContext.call(Tenant.of(clinicA), () -> this.notes.findByTitle(title));

		assertThat(found).extracting((note) -> String.valueOf(note.getId())).containsExactly(own);
		assertThat(found).allSatisfy((note) -> assertThat(note.getClinicId()).isEqualTo(clinicA));
	}

	/** Нативный SQL обходит фильтр Hibernate, но не RLS: видны только строки своей клиники. */
	@Test
	void nativeQueryIsLimitedByRowLevelSecurity() {
		UUID clinic = newClinic();
		String session = enter(clinic);
		createNote(session, "Раз");
		createNote(session, "Два");
		createNote(enter(newClinic()), "Чужая");

		assertThat(TenantContext.call(Tenant.of(clinic), () -> this.notes.countVisibleRows())).isEqualTo(2L);
	}

	/** EntityManager в обход репозитория без контекста: ошибки нет, но и данных клиник нет — не «все клиники». */
	@Test
	void entityManagerWithoutClinicSeesNothing() {
		createNote(enter(newClinic()), "Есть строка");

		Long count = this.transactions.execute((status) -> this.entityManager
			.createQuery("select count(n) from TestNote n", Long.class)
			.getSingleResult());

		assertThat(count).isZero();
	}

	/**
	 * Находка проверки T-PLT-03: в области {@code SUPPORTS} без транзакции после первого запроса сессия Hibernate
	 * клиники A привязана к потоку, и запрос «в клинике B» молча вернул бы строки A. Смена клиники там запрещена так
	 * же, как внутри транзакции; после выхода из области — снова можно.
	 */
	@Test
	void clinicCannotChangeWhileHibernateSessionIsBoundWithoutTransaction() {
		UUID clinicA = newClinic();
		UUID clinicB = newClinic();
		String title = "SUPPORTS " + UUID.randomUUID();
		createNote(enter(clinicA), title);
		createNote(enter(clinicB), title);

		List<UUID> inScope = TenantContext.call(Tenant.of(clinicA), () -> this.service.inSupportsScope(() -> {
			assertThatThrownBy(() -> TenantContext.run(Tenant.of(clinicB), () -> {
			})).as("синхронизация Spring уже открыта").isInstanceOf(IllegalStateException.class);
			List<UUID> own = clinics(this.notes.findByTitle(title));
			assertThatThrownBy(() -> TenantContext.call(Tenant.of(clinicB), () -> this.notes.findByTitle(title)))
				.as("сессия Hibernate клиники A привязана к потоку")
				.isInstanceOf(IllegalStateException.class);
			return own;
		}));

		assertThat(inScope).containsExactly(clinicA);
		assertThat(TenantContext.call(Tenant.of(clinicB), () -> clinics(this.notes.findByTitle(title))))
			.as("вне области клиника меняется")
			.containsExactly(clinicB);
	}

	private static List<UUID> clinics(List<TestNote> notes) {
		return notes.stream().map(TestNote::getClinicId).toList();
	}

}
