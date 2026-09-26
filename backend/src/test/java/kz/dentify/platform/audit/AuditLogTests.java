package kz.dentify.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import kz.dentify.AbstractIntegrationTest;
import kz.dentify.DentifyPostgresContainer;
import kz.dentify.platform.api.AuditActions;
import kz.dentify.platform.api.AuditEvent;
import kz.dentify.platform.api.AuditSecret;
import kz.dentify.platform.api.AuditService;
import kz.dentify.platform.api.BusinessRuleException;
import kz.dentify.platform.api.InvalidRequestException;
import kz.dentify.platform.api.LocalizedText;
import kz.dentify.platform.api.MissingTenantContextException;
import kz.dentify.platform.api.Permissions;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import kz.dentify.platform.identity.AuditEntry;
import kz.dentify.platform.identity.AuditJournal;
import kz.dentify.platform.identity.AuditQuery;
import kz.dentify.platform.tenancy.TestAccounts;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Журнал действий (PLT-09): приёмка требования и механизм T-PLT-13 — событие в транзакции изменения, неизменяемость
 * журнала для роли приложения и владельца, окно просмотров в Valkey, секреты и маска контактов, API чтения с фильтрами и
 * страницами, изоляция клиник, выгрузка с пределом, партиции заранее.
 */
class AuditLogTests extends AbstractIntegrationTest {

	static final JsonMapper JSON = JsonMapper.builder().build();

	static final String EVENTS = "/api/v1/audit-events";

	/** Вымышленный сотрудник из TestAccounts (имя — для плашки автора). */
	static final UUID DINARA = UUID.fromString("0199a000-0000-7000-8000-000000000001");

	static final UUID ASKAR = UUID.fromString("0199a000-0000-7000-8000-000000000002");

	static final String NOTE = "platform.test-note";

	@Autowired
	AuditService audit;

	@Autowired
	AuditJournal journal;

	@Autowired
	AuditProperties properties;

	@Autowired
	AuditPartitions partitions;

	@Autowired
	AuditMisuseProbe misuse;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	PlatformTransactionManager transactions;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	/**
	 * Приёмка PLT-09: перенос записи — событие с разницей «Дата 24.09.2026 → 25.09.2026, Время 11:30 → 10:00», автором и
	 * IP; неизменённые поля (кресло, статус) в разницу не попадают.
	 */
	@Test
	void rescheduleIsRecordedWithDiffAuthorAndIp() {
		UUID clinic = UUID.randomUUID();
		UUID appointment = UUID.randomUUID();
		String session = enterClinic(clinic, DINARA, Permissions.AUDIT_READ);

		reschedule(session, appointment, false).expectStatus().isNoContent();

		JsonNode page = events(session, "entityId=" + appointment);
		assertThat(page.path("total").asLong()).isEqualTo(1);
		JsonNode event = page.path("items").get(0);
		assertThat(event.path("accountId").asString()).isEqualTo(DINARA.toString());
		assertThat(event.path("actorName").asString()).isEqualTo("Динара Алиева");
		assertThat(event.path("ip").asString()).isEqualTo("95.56.•.•");
		assertThat(event.path("module").asString()).isEqualTo("schedule");
		assertThat(event.path("entityType").asString()).isEqualTo("schedule.appointment");
		assertThat(event.path("action").asString()).isEqualTo(AuditActions.RESCHEDULED);
		assertThat(event.path("entityLabel").asString()).isEqualTo("Айгерим Сейтқазы");
		assertThat(event.path("text").asString()).isEqualTo("Перенос записи: Айгерим Сейтқазы");
		assertThat(changes(event)).containsExactly("date|Дата|date|24.09.2026|25.09.2026",
				"time|Время|time|11:30|10:00");
		assertThat(Instant.parse(event.path("at").asString())).isBetween(Instant.now().minusSeconds(60), Instant.now());

		Map<String, Object> stored = TenantContext.call(Tenant.of(clinic), () -> this.jdbc.queryForMap(
				"select host(ip) as ip, clinic_id, tableoid::regclass::text as partition from audit_log where entity_id = ?",
				appointment));
		assertThat(stored).containsEntry("ip", "95.56.12.34").containsEntry("clinic_id", clinic);
		assertThat((String) stored.get("partition")).matches("audit_log_\\d{4}_\\d{2}");
	}

	/**
	 * Имена авторов страницы — одним пакетом на страницу ({@code AccountNameProvider.displayNames}), а не запросом на
	 * каждое событие: страница из 100 событий иначе давала бы до 100 запросов к БД.
	 */
	@Test
	void actorNamesAreResolvedOncePerPage() {
		UUID clinic = UUID.randomUUID();
		String dinara = enterClinic(clinic, DINARA, Permissions.AUDIT_READ);
		String askar = enterClinic(clinic, ASKAR, Permissions.AUDIT_READ);
		reschedule(dinara, UUID.randomUUID(), false).expectStatus().isNoContent();
		reschedule(askar, UUID.randomUUID(), false).expectStatus().isNoContent();
		reschedule(dinara, UUID.randomUUID(), false).expectStatus().isNoContent();
		int before = TestAccounts.batches();

		JsonNode page = events(dinara, "");

		assertThat(page.path("items").valueStream().map((event) -> event.path("actorName").asString()))
			.containsExactlyInAnyOrder("Динара Алиева", "Аскар Нурланов", "Динара Алиева");
		assertThat(TestAccounts.batches() - before).as("обращений за именами на страницу").isEqualTo(1);
	}

	/** Приёмка PLT-09: транзакция откатилась — события нет. */
	@Test
	void rolledBackTransactionLeavesNoEvent() {
		UUID clinic = UUID.randomUUID();
		UUID appointment = UUID.randomUUID();
		String session = enterClinic(clinic, DINARA, Permissions.AUDIT_READ);

		expectProblem(reschedule(session, appointment, true), 422, "probe.rule.backdated");

		assertThat(events(session, "entityId=" + appointment).path("total").asLong()).isZero();
		assertThat(TenantContext.call(Tenant.of(clinic),
				() -> this.jdbc.queryForObject("select count(*) from audit_log where entity_id = ?", Long.class,
						appointment)))
			.isZero();
	}

	/**
	 * {@code @Audited} на сервисе: создание — поля объекта, изменение — разница, сохранение без изменений — без события,
	 * конфликт версии откатывает и событие, архивирование и удаление.
	 */
	@Test
	void auditedServiceMethodsRecordChanges() {
		UUID clinic = UUID.randomUUID();
		String session = enterClinic(clinic, ASKAR, Permissions.AUDIT_READ);
		JsonNode note = post(session, "/test/notes", Map.of("title", "Первичный осмотр"));
		String id = note.path("id").asString();

		rename(session, id, "Повторный осмотр", note.path("version").asLong()).expectStatus().isOk();
		rename(session, id, "Повторный осмотр", note.path("version").asLong() + 1).expectStatus().isOk();
		rename(session, id, "Устаревшая версия", 0).expectStatus().isEqualTo(409);
		this.http.delete().uri("/test/notes/" + id).header(HttpHeaders.COOKIE, session).exchange()
			.expectStatus()
			.isNoContent();

		JsonNode page = events(session, "entityId=" + id + "&sort=at");
		assertThat(page.path("items").valueStream().map((event) -> event.path("action").asString()))
			.containsExactly(AuditActions.CREATED, AuditActions.UPDATED, AuditActions.DELETED);
		JsonNode created = page.path("items").get(0);
		assertThat(created.path("text").asString()).isEqualTo("Создание заметки «Первичный осмотр»");
		assertThat(created.path("actorName").asString()).isEqualTo("Аскар Нурланов");
		assertThat(changes(created)).containsExactly("title|Заголовок|text||Первичный осмотр");
		JsonNode updated = page.path("items").get(1);
		assertThat(updated.path("text").asString()).isEqualTo("Изменение заметки «Повторный осмотр»");
		assertThat(changes(updated)).containsExactly("title|Заголовок|text|Первичный осмотр|Повторный осмотр");
		JsonNode deleted = page.path("items").get(2);
		assertThat(deleted.path("text").asString()).isEqualTo("Удаление: Повторный осмотр");
		assertThat(changes(deleted)).containsExactly("title|Заголовок|text|Повторный осмотр|");
	}

	@Test
	void auditedMethodRequiresWriteTransactionAndSnapshots() {
		UUID clinic = UUID.randomUUID();
		UUID id = UUID.randomUUID();

		TenantContext.run(Tenant.of(clinic), () -> {
			assertThatThrownBy(() -> this.misuse.withoutTransaction(id)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("@Transactional");
			assertThatThrownBy(() -> this.misuse.readOnly(id)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("readOnly");
			assertThatThrownBy(() -> this.misuse.requiresNew(id)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("REQUIRED");
			assertThatThrownBy(() -> this.misuse.withoutSnapshots(id)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("AuditSnapshots");
			assertThatThrownBy(() -> this.misuse.reschedule(id)).as("#id по умолчанию, а параметр — appointmentId")
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("#id")
				.hasMessageContaining("[appointmentId]");
			assertThatThrownBy(() -> this.misuse.reschedule(id)).as("и при повторном вызове")
				.isInstanceOf(IllegalStateException.class);
			assertThatThrownBy(() -> this.misuse.nullId(null)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("null");
			assertThatThrownBy(() -> this.misuse.createWithoutId()).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("#result.id")
				.hasMessageContaining("null");
			assertThatThrownBy(() -> this.audit.record(AuditEvent.of(NOTE, AuditActions.UPDATED)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("транзакции");
			assertThatThrownBy(() -> readOnly(() -> this.audit.recordView("medical.card", id, "Карта")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("только для чтения");
		});
		assertThat(count(clinic)).isZero();
		assertThatThrownBy(() -> write(() -> {
			this.audit.record(AuditEvent.of(NOTE, AuditActions.UPDATED));
			return null;
		})).isInstanceOf(MissingTenantContextException.class);
	}

	/** Приёмка PLT-09: UPDATE и DELETE по audit_log от роли приложения — ошибка БД, в том числе по партиции напрямую. */
	@Test
	void applicationRoleCannotChangeAuditLog() {
		UUID clinic = UUID.randomUUID();
		inClinic(clinic, () -> this.audit.recordPrint("medical.treatment-plan", UUID.randomUUID(), "План лечения"));
		String partition = partitionOf(clinic);

		TenantContext.run(Tenant.of(clinic), () -> {
			assertThat(this.jdbc.queryForObject("select count(*) from audit_log", Long.class)).isEqualTo(1);
			for (String sql : List.of("update audit_log set action = 'updated'", "delete from audit_log",
					"update " + partition + " set action = 'updated'", "delete from " + partition,
					"select count(*) from " + partition, "truncate audit_log")) {
				assertThatThrownBy(() -> this.jdbc.execute(sql)).as(sql)
					.rootCause()
					.hasMessageContaining("permission denied");
			}
			assertThat(this.jdbc.queryForObject("select count(*) from audit_log", Long.class)).isEqualTo(1);
		});
	}

	/** Триггер запрещает правки и владельцу схемы, в том числе TRUNCATE партиции напрямую. */
	@Test
	void triggerForbidsChangesEvenForSchemaOwner() throws SQLException {
		UUID clinic = UUID.randomUUID();
		inClinic(clinic, () -> this.audit.recordPrint("medical.treatment-plan", UUID.randomUUID(), "План лечения"));
		String partition = partitionOf(clinic);

		try (Connection owner = DriverManager.getConnection(POSTGRES.getJdbcUrl(), DentifyPostgresContainer.OWNER_ROLE,
				DentifyPostgresContainer.OWNER_PASSWORD)) {
			try (Statement statement = owner.createStatement()) {
				// FORCE RLS действует и на владельца: без клиники он не увидел бы строк, и триггеру нечего запрещать
				statement.execute("set app.clinic_id = '" + clinic + "'");
				for (String sql : List.of("update dentify.audit_log set action = 'updated'",
						"delete from dentify.audit_log", "update dentify." + partition + " set action = 'updated'",
						"delete from dentify." + partition, "truncate dentify.audit_log",
						"truncate dentify." + partition)) {
					assertThatThrownBy(() -> statement.execute(sql)).as(sql)
						.isInstanceOf(SQLException.class)
						.hasMessageContaining("audit_log is append-only")
						.satisfies((ex) -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("42501"));
				}
				try (var rows = statement.executeQuery("select count(*) from dentify.audit_log")) {
					rows.next();
					assertThat(rows.getLong(1)).isEqualTo(1);
				}
			}
		}
	}

	/** Приёмка PLT-09: врач открывает одну медкарту дважды за 15 минут — одно событие «Просмотр». */
	@Test
	void secondViewWithinWindowIsNotRecorded() {
		UUID clinic = UUID.randomUUID();
		UUID patient = UUID.randomUUID();

		assertThat(viewAs(DINARA, clinic, patient)).isTrue();
		assertThat(viewAs(DINARA, clinic, patient)).as("второй просмотр за окно").isFalse();
		assertThat(viewAs(ASKAR, clinic, patient)).as("другой врач").isTrue();
		assertThat(viewAs(DINARA, clinic, UUID.randomUUID())).as("другая карта").isTrue();

		String key = ViewWindows.key(clinic, DINARA, "medical.card", patient);
		Long ttl = this.redis.getExpire(key);
		assertThat(ttl).isNotNull();
		assertThat(Duration.ofSeconds(ttl)).isBetween(Duration.ofMinutes(14), this.properties.getViewDedupWindow());

		this.redis.delete(key);
		assertThat(viewAs(DINARA, clinic, patient)).as("окно прошло").isTrue();

		List<AuditEntry> views = TenantContext.call(Tenant.of(clinic), () -> this.journal
			.search(new AuditQuery(null, null, DINARA, null, null, null, patient, AuditActions.VIEWED, null),
					PageRequest.of(0, 10))
			.getContent());
		assertThat(views).hasSize(2).allSatisfy((view) -> assertThat(view.text()).isEqualTo("Просмотр: Карта пациента"));
	}

	/** Откат транзакции снимает метку окна: следующий просмотр записывается. */
	@Test
	void rolledBackViewReleasesWindow() {
		UUID clinic = UUID.randomUUID();
		UUID patient = UUID.randomUUID();
		signInAs(DINARA);

		TenantContext.run(Tenant.of(clinic), () -> {
			TransactionTemplate transaction = new TransactionTemplate(this.transactions);
			transaction.executeWithoutResult((status) -> {
				assertThat(this.audit.recordView("medical.card", patient, "Карта пациента")).isTrue();
				status.setRollbackOnly();
			});
		});

		assertThat(this.redis.hasKey(ViewWindows.key(clinic, DINARA, "medical.card", patient))).isFalse();
		assertThat(count(clinic)).isZero();
		assertThat(viewAs(DINARA, clinic, patient)).isTrue();
		assertThat(count(clinic)).isEqualTo(1);
	}

	/** Приёмка PLT-09: смена пароля не пишет пароль или хэш в разницу — только отметку, что изменился. */
	@Test
	void secretsNeverReachTheLog() {
		UUID clinic = UUID.randomUUID();
		UUID account = UUID.randomUUID();
		String oldHash = "{argon2}$argon2id$v=19$m=19456,t=2,p=1$b2xkc2FsdA$b2xkaGFzaA";
		String newHash = "{argon2}$argon2id$v=19$m=19456,t=2,p=1$bmV3c2FsdA$bmV3aGFzaA";
		AccountSnapshot before = new AccountSnapshot("aigerim@example.kz", oldHash, null, "old-link-token", "1111",
				"старое", "Заметка");
		AccountSnapshot after = new AccountSnapshot("aigerim@example.kz", newHash, "Qwerty-2026!", "new-link-token",
				"2222", "новое", newHash);

		inClinic(clinic, () -> this.audit.record(AuditEvent.of("iam.account", "password-changed")
			.entity(account, "Айгерим Сейтқазы")
			.change(before, after)));

		String diff = TenantContext.call(Tenant.of(clinic),
				() -> this.jdbc.queryForObject("select diff::text from audit_log where entity_id = ?", String.class,
						account));
		assertThat(diff).doesNotContain(oldHash, newHash, "argon2", "Qwerty", "link-token", "1111", "2222", "старое",
				"новое", "Заметка", "aigerim@");
		AuditEntry entry = read(clinic, account, Permissions.PATIENT_PHONES);
		assertThat(entry.changes()).extracting(AuditEntry.Change::field)
			.containsExactly("passwordHash", "password", "resetToken", "pin", "comment", "remark");
		assertThat(entry.changes()).allSatisfy((change) -> {
			assertThat(change.type()).isEqualTo(AuditEntry.Change.SECRET);
			assertThat(change.from()).isNull();
			assertThat(change.to()).isNull();
		});
		assertThat(entry.changes().get(1).label()).isEqualTo("Пароль");
	}

	/**
	 * Приёмка PLT-09: без права {@code patient:phones} телефон и ИИН в разнице — маской, с правом — полностью. Маска и в
	 * имени объекта; поле-контакт в непривычном формате скрыто целиком.
	 */
	@Test
	void contactsAreMaskedWithoutPermission() {
		UUID clinic = UUID.randomUUID();
		UUID patient = UUID.randomUUID();
		PatientSnapshot before = new PatientSnapshot(new LocalizedText("Айгерим", null), "+7 701 111 22 33",
				"900101300123", null, "Тел. для связи 8 702 555 66 77", false, List.of("vip"));
		PatientSnapshot after = new PatientSnapshot(new LocalizedText("Айгерим", "Әйгерім"), "+7 707 123 45 67",
				"900101300124", "доб. 12", "Тел. для связи 8 702 555 66 78", true, List.of("vip", "ортодонтия"));

		inClinic(clinic, () -> this.audit.record(AuditEvent.of("patient.patient", AuditActions.UPDATED)
			.entity(patient, "Айгерим, +77071234567")
			.change(before, after)));

		AuditEntry masked = read(clinic, patient);
		assertThat(masked.entityLabel()).isEqualTo("Айгерим, +7••••4567");
		assertThat(changes(masked)).containsExactly("name.kk|Название · каз.|text||Әйгерім",
				"phone|Телефон|text|+7••••2233|+7••••4567", "iin|ИИН|text|••••0123|••••0124",
				"phone2|Второй телефон|text||••••", "note|note|text|Тел. для связи +7••••6677|Тел. для связи +7••••6678",
				"vip|vip|boolean|нет|да", "tags|tags|list|vip|vip, ортодонтия");

		AuditEntry full = read(clinic, patient, Permissions.PATIENT_PHONES);
		assertThat(full.entityLabel()).isEqualTo("Айгерим, +77071234567");
		assertThat(changes(full)).contains("phone|Телефон|text|+7 701 111 22 33|+7 707 123 45 67",
				"iin|ИИН|text|900101300123|900101300124", "phone2|Второй телефон|text||доб. 12");
	}

	/**
	 * Поиск не выдаёт ИИН и телефон тому, кто их не видит: в {@code search_text} они маской, и по точному номеру событие
	 * не находится, а по видимой части маски — находится.
	 */
	@Test
	void contactsAreNotSearchable() {
		UUID clinic = UUID.randomUUID();
		UUID patient = UUID.randomUUID();
		inClinic(clinic, () -> this.audit.recordPrint("patient.patient", patient, "Айгерим, +77071234567, 900101300123"));

		String stored = TenantContext.call(Tenant.of(clinic), () -> this.jdbc
			.queryForObject("select search_text from audit_log where entity_id = ?", String.class, patient));
		assertThat(stored).doesNotContain("7071234567", "900101300123").contains("+7••••4567", "••••0123");
		for (String exact : List.of("+77071234567", "7071234567", "87071234567", "900101300123", "1234567")) {
			assertThat(searchText(clinic, exact)).as(exact).isEmpty();
		}
		assertThat(searchText(clinic, "айгерим")).hasSize(1);
		assertThat(searchText(clinic, "4567")).as("видимая часть маски").hasSize(1);
	}

	/**
	 * Объём ленты: без начала периода — последние dentify.audit.default-period, история объекта — за всё время; поиск по
	 * тексту — за период не длиннее dentify.audit.search-max-period.
	 */
	@Test
	void feedIsLimitedToAPeriod() {
		UUID clinic = UUID.randomUUID();
		UUID plan = UUID.randomUUID();
		Instant now = Instant.now();
		// событие двухчасовой давности — прямой вставкой: время события ставит приложение
		inClinic(clinic, () -> this.jdbc.update("""
				insert into audit_log (clinic_id, at, entity_type, entity_id, entity_label, action, search_text)
				values (?, ?, 'medical.treatment-plan', ?, 'План', 'printed', 'план печать: план')""", clinic,
				now.minus(Duration.ofHours(2)).atOffset(ZoneOffset.UTC), plan));
		inClinic(clinic, () -> this.audit.recordPrint("medical.treatment-plan", plan, "План"));
		Duration defaultPeriod = this.properties.getDefaultPeriod();
		this.properties.setDefaultPeriod(Duration.ofHours(1));
		try {
			assertThat(journal(clinic, new AuditQuery(null, null, null, null, null, null, null, null, null)))
				.as("без начала — последний час")
				.hasSize(1);
			assertThat(journal(clinic, new AuditQuery(null, null, null, null, null, null, null, null, "план")))
				.hasSize(1);
			assertThat(journal(clinic,
					new AuditQuery(null, now.minus(Duration.ofMinutes(90)), null, null, null, null, null, null, null)))
				.as("без начала — час до конца периода")
				.hasSize(1);
			assertThat(journal(clinic,
					new AuditQuery(now.minus(Duration.ofHours(3)), null, null, null, null, null, null, null, null)))
				.hasSize(2);
			assertThat(journal(clinic, new AuditQuery(null, null, null, null, null, null, plan, null, null)))
				.as("история объекта — за всё время")
				.hasSize(2);
			assertThat(TenantContext.call(Tenant.of(clinic), () -> this.journal.export(AuditQuery.all())))
				.as("выгрузка — тот же период")
				.hasSize(1);
		}
		finally {
			this.properties.setDefaultPeriod(defaultPeriod);
		}

		Instant longAgo = now.minus(this.properties.getSearchMaxPeriod()).minus(Duration.ofDays(1));
		assertThatThrownBy(() -> journal(clinic,
				new AuditQuery(longAgo, null, null, null, null, null, null, null, "план")))
			.isInstanceOfSatisfying(InvalidRequestException.class, (ex) -> assertThat(ex.getViolations())
				.singleElement()
				.satisfies((violation) -> {
					assertThat(violation.field()).isEqualTo("from");
					assertThat(violation.code()).isEqualTo("audit.period.too-long");
					assertThat(violation.args()).containsExactly(366L);
				}));
		assertThat(journal(clinic, new AuditQuery(longAgo, null, null, null, "medical", null, null, null, null)))
			.as("без поиска по тексту длинный период допустим: его ведут индексы")
			.hasSize(2);
		assertThat(journal(clinic, new AuditQuery(longAgo, null, null, null, null, null, plan, null, "план")))
			.as("история объекта с поиском — без предела периода")
			.hasSize(2);
	}

	/** API чтения: фильтры по периоду, сотруднику, категории, типу, объекту, действию, тексту; страницы и сортировка. */
	@Test
	void readApiFiltersAndPaginates() {
		UUID clinic = UUID.randomUUID();
		UUID appointment = UUID.randomUUID();
		UUID card = UUID.randomUUID();
		String session = enterClinic(clinic, DINARA, Permissions.AUDIT_READ);
		Instant start = Instant.now().minusSeconds(1);
		reschedule(session, appointment, false).expectStatus().isNoContent();
		viewAs(ASKAR, clinic, card);
		inClinic(clinic, () -> this.audit.recordExport("patient.patient", "Пациенты", 120));
		inClinic(clinic, () -> this.audit.recordPrint("medical.treatment-plan", card, "План лечения"));
		Instant end = Instant.now().plusSeconds(1);

		assertThat(events(session, "").path("total").asLong()).isEqualTo(4);
		assertThat(ids(events(session, "from=" + start + "&to=" + end))).hasSize(4);
		assertThat(ids(events(session, "to=" + start))).isEmpty();
		assertThat(ids(events(session, "from=" + end))).isEmpty();
		assertThat(actions(events(session, "accountId=" + DINARA))).containsExactly(AuditActions.RESCHEDULED);
		assertThat(actions(events(session, "accountId=" + ASKAR))).containsExactly(AuditActions.VIEWED);
		assertThat(actions(events(session, "module=medical&sort=at")))
			.containsExactly(AuditActions.VIEWED, AuditActions.PRINTED);
		assertThat(actions(events(session, "entityType=medical.card"))).containsExactly(AuditActions.VIEWED);
		assertThat(actions(events(session, "entityId=" + card + "&sort=-at")))
			.containsExactly(AuditActions.PRINTED, AuditActions.VIEWED);
		assertThat(actions(events(session, "action=exported"))).containsExactly(AuditActions.EXPORTED);
		assertThat(actions(search(session, "сейтқазы"))).as("по имени объекта без учёта регистра")
			.containsExactly(AuditActions.RESCHEDULED);
		assertThat(actions(search(session, " ПЕРЕНОС "))).as("по тексту события")
			.containsExactly(AuditActions.RESCHEDULED);
		assertThat(actions(search(session, "строк: 1"))).containsExactly(AuditActions.EXPORTED);
		assertThat(actions(search(session, "_"))).as("_ и % в строке поиска — символы, а не шаблон LIKE").isEmpty();
		assertThat(actions(search(session, "%"))).isEmpty();

		JsonNode first = events(session, "size=3&page=0");
		assertThat(first.path("total").asLong()).isEqualTo(4);
		assertThat(first.path("size").asInt()).isEqualTo(3);
		assertThat(actions(first)).containsExactly(AuditActions.PRINTED, AuditActions.EXPORTED, AuditActions.VIEWED);
		assertThat(actions(events(session, "size=3&page=1"))).containsExactly(AuditActions.RESCHEDULED);
		assertThat(events(session, "page=5").path("items")).isEmpty();

		JsonNode export = events(session, "action=exported").path("items").get(0);
		assertThat(export.path("text").asString()).isEqualTo("Выгрузка: Пациенты, строк: 120");
		assertThat(export.path("actorName").asString()).isEqualTo("Система");
		assertThat(export.path("accountId").isNull()).isTrue();
		assertThat(export.path("ip").isNull()).isTrue();

		String id = ids(events(session, "action=exported")).getFirst();
		assertThat(ok(this.http.get().uri(EVENTS + "/" + id).header(HttpHeaders.COOKIE, session).exchange())
			.path("action")
			.asString()).isEqualTo(AuditActions.EXPORTED);
	}

	@Test
	void readApiRejectsInvalidFilters() {
		String session = enterClinic(UUID.randomUUID(), DINARA, Permissions.AUDIT_READ);
		Instant now = Instant.now();

		JsonNode period = expectProblem(get(session, EVENTS + "?from=" + now + "&to=" + now), 400, "error.validation");
		assertThat(period.path("errors").get(0).path("field").asString()).isEqualTo("to");
		assertThat(period.path("errors").get(0).path("code").asString()).isEqualTo("audit.period.invalid");
		JsonNode tooLong = expectProblem(get(session, EVENTS + "?q=plan&from=" + now.minus(Duration.ofDays(400))), 400,
				"error.validation");
		assertThat(tooLong.path("errors").get(0).path("field").asString()).isEqualTo("from");
		assertThat(tooLong.path("errors").get(0).path("code").asString()).isEqualTo("audit.period.too-long");
		assertThat(tooLong.path("errors").get(0).path("message").asString())
			.isEqualTo("Поиск по тексту — за период не длиннее 366 дн. Сузьте период.");
		expectProblem(get(session, EVENTS + "?module=Schedule'"), 400, "error.validation");
		expectProblem(get(session, EVENTS + "?sort=action"), 400, "error.validation");
		expectProblem(get(session, EVENTS + "?from=yesterday"), 400, "error.validation");
		expectProblem(get(session, EVENTS + "/" + UUID.randomUUID()), 404, "error.not-found");
	}

	/** Журнал — только с правом audit:read и выбранной клиникой. */
	@Test
	void readApiRequiresPermissionAndClinic() {
		UUID clinic = UUID.randomUUID();
		expectProblem(this.http.get().uri(EVENTS).exchange(), 401, "error.unauthorized");
		expectProblem(get(enterClinic(clinic, DINARA), EVENTS), 403, "error.forbidden");
		expectProblem(get(enterClinic(clinic, DINARA, Permissions.PATIENT_PHONES), EVENTS), 403, "error.forbidden");

		String session = enterClinic(clinic, DINARA, Permissions.AUDIT_READ);
		this.http.delete().uri(TENANT_SESSION_PROBE).header(HttpHeaders.COOKIE, session).exchange()
			.expectStatus()
			.isNoContent();
		expectProblem(get(session, EVENTS), 401, "error.clinic-not-selected");
	}

	/** Клиника видит только свои события: список, событие по id, SQL без контекста клиники. */
	@Test
	void clinicsSeeOnlyTheirOwnEvents() {
		UUID clinicA = UUID.randomUUID();
		UUID clinicB = UUID.randomUUID();
		UUID appointment = UUID.randomUUID();
		String sessionA = enterClinic(clinicA, DINARA, Permissions.AUDIT_READ);
		String sessionB = enterClinic(clinicB, DINARA, Permissions.AUDIT_READ);
		reschedule(sessionB, appointment, false).expectStatus().isNoContent();
		String eventB = ids(events(sessionB, "entityId=" + appointment)).getFirst();

		assertThat(events(sessionA, "entityId=" + appointment).path("total").asLong()).isZero();
		expectProblem(get(sessionA, EVENTS + "/" + eventB), 404, "error.not-found");
		assertThat(TenantContext.call(Tenant.of(clinicA), () -> this.journal.find(UUID.fromString(eventB)))).isEmpty();
		assertThat(this.jdbc.queryForObject("select count(*) from audit_log where entity_id = ?", Long.class,
				appointment))
			.as("без клиники в соединении RLS не показывает строк")
			.isZero();
		assertThatThrownBy(() -> inClinic(clinicA, () -> this.jdbc.update("""
				insert into audit_log (clinic_id, entity_type, action, search_text)
				values (?, 'schedule.appointment', 'created', '')""", clinicB)))
			.rootCause()
			.hasMessageContaining("row-level security");
	}

	/** Выгрузка — не больше предела строк (422 и совет сузить период), сама выгрузка — тоже событие журнала. */
	@Test
	void exportIsLimitedAndRecorded() {
		UUID clinic = UUID.randomUUID();
		signInAs(DINARA);
		for (int i = 0; i < 3; i++) {
			inClinic(clinic, () -> this.audit.recordPrint("medical.treatment-plan", UUID.randomUUID(), "План"));
		}
		int limit = this.properties.getExportMaxRows();
		this.properties.setExportMaxRows(2);
		try {
			assertThatThrownBy(() -> TenantContext.run(Tenant.of(clinic), () -> this.journal.export(AuditQuery.all())))
				.isInstanceOfSatisfying(BusinessRuleException.class, (ex) -> {
					assertThat(ex.getCode()).isEqualTo("audit.export.too-many-rows");
					assertThat(ex.getArgs()).containsExactly(2);
				});
			this.properties.setExportMaxRows(3);
			List<AuditEntry> rows = TenantContext.call(Tenant.of(clinic), () -> this.journal.export(AuditQuery.all()));
			assertThat(rows).hasSize(3).allSatisfy((row) -> assertThat(row.action()).isEqualTo(AuditActions.PRINTED));
		}
		finally {
			this.properties.setExportMaxRows(limit);
		}
		List<AuditEntry> exports = TenantContext.call(Tenant.of(clinic), () -> this.journal
			.search(new AuditQuery(null, null, null, null, null, JdbcAuditJournal.AUDIT_LOG, null, null, null),
					PageRequest.of(0, 10))
			.getContent());
		assertThat(exports).singleElement().satisfies((export) -> {
			assertThat(export.text()).isEqualTo("Выгрузка журнала действий, строк: 3");
			assertThat(export.accountId()).isEqualTo(DINARA);
		});
	}

	/**
	 * Партиции заранее: прошлый месяц, текущий и months-ahead вперёд; каждая — с RLS, FORCE, стандартной политикой,
	 * TRUNCATE-триггером и без прав у роли приложения. Повторный вызов ничего не создаёт.
	 */
	@Test
	void partitionsAreCreatedAheadByTheApplication() {
		assertThat(this.partitions.ensure()).as("старт уже создал партиции").isZero();
		List<Map<String, Object>> existing = this.jdbc.queryForList("""
				select c.relname::text as name, c.relrowsecurity as rls, c.relforcerowsecurity as forced,
				       has_table_privilege('dentify_app', c.oid, 'SELECT,INSERT,UPDATE,DELETE,TRUNCATE') as app_access,
				       exists (select 1 from pg_trigger t where t.tgrelid = c.oid and t.tgname = 'audit_log_no_truncate') as truncate_trigger,
				       exists (select 1 from pg_trigger t where t.tgrelid = c.oid and t.tgname = 'audit_log_immutable') as row_trigger
				from pg_inherits i join pg_class c on c.oid = i.inhrelid
				where i.inhparent = 'dentify.audit_log'::regclass order by 1""");
		LocalDate month = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
		List<String> expected = IntStream.rangeClosed(-1, this.properties.getPartitions().getMonthsAhead())
			.mapToObj((offset) -> "audit_log_" + month.plusMonths(offset).toString().substring(0, 7).replace('-', '_'))
			.toList();
		assertThat(existing).extracting((row) -> row.get("name")).containsAll(expected);
		assertThat(existing).allSatisfy((row) -> assertThat(row).as("партиция %s", row.get("name"))
			.containsEntry("rls", true)
			.containsEntry("forced", true)
			.containsEntry("app_access", false)
			.containsEntry("truncate_trigger", true)
			.containsEntry("row_trigger", true));

		Integer created = this.jdbc.queryForObject("select audit_log_create_partitions(?)", Integer.class,
				this.properties.getPartitions().getMonthsAhead() + 2);
		Integer again = this.jdbc.queryForObject("select audit_log_create_partitions(?)", Integer.class,
				this.properties.getPartitions().getMonthsAhead() + 2);
		assertThat(created).as("роль приложения создаёт партиции функцией владельца").isBetween(0, 2);
		assertThat(again).isZero();
		assertThatThrownBy(() -> this.jdbc.queryForObject("select audit_log_create_partitions(40)", Integer.class))
			.rootCause()
			.hasMessageContaining("between 1 and 24");
	}

	private ResponseSpec reschedule(String session, UUID appointment, boolean fail) {
		return this.http.post()
			.uri(AuditProbeController.RESCHEDULE, appointment)
			.header(HttpHeaders.COOKIE, session)
			.header(FORWARDED_FOR, "95.56.12.34")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("patient", "Айгерим Сейтқазы", "fromDate", "2026-09-24", "fromTime", "11:30", "toDate",
					"2026-09-25", "toTime", "10:00", "fail", fail))
			.exchange();
	}

	private ResponseSpec rename(String session, String id, String title, long version) {
		return this.http.put()
			.uri("/test/notes/" + id)
			.header(HttpHeaders.COOKIE, session)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("title", title, "version", version))
			.exchange();
	}

	private JsonNode post(String session, String uri, Map<String, Object> body) {
		return ok(this.http.post()
			.uri(uri)
			.header(HttpHeaders.COOKIE, session)
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.exchange());
	}

	private ResponseSpec get(String session, String uri) {
		return this.http.get().uri(uri).header(HttpHeaders.COOKIE, session).exchange();
	}

	private JsonNode events(String session, String query) {
		return ok(get(session, EVENTS + (query.isEmpty() ? "" : "?" + query)));
	}

	/** Поиск по журналу: строка — переменной URI, её кодирует клиент. */
	private JsonNode search(String session, String q) {
		return ok(this.http.get().uri(EVENTS + "?q={q}", q).header(HttpHeaders.COOKIE, session).exchange());
	}

	private static JsonNode ok(ResponseSpec response) {
		byte[] body = response.expectStatus().isOk().expectBody(byte[].class).returnResult().getResponseBody();
		return JSON.readTree(new String(body, StandardCharsets.UTF_8));
	}

	private static List<String> ids(JsonNode page) {
		return page.path("items").valueStream().map((event) -> event.path("id").asString()).toList();
	}

	private static List<String> actions(JsonNode page) {
		return page.path("items").valueStream().map((event) -> event.path("action").asString()).toList();
	}

	/** Разница события строками {@code поле|название|вид|было|стало}. */
	private static List<String> changes(JsonNode event) {
		return event.path("changes")
			.valueStream()
			.map((change) -> String.join("|", change.path("field").asString(), change.path("label").asString(),
					change.path("type").asString(), change.path("from").asString(""), change.path("to").asString("")))
			.toList();
	}

	private static List<String> changes(AuditEntry entry) {
		return entry.changes()
			.stream()
			.map((change) -> String.join("|", change.field(), change.label(), change.type(),
					(change.from() != null) ? change.from() : "", (change.to() != null) ? change.to() : ""))
			.toList();
	}

	/** Событие по объекту, прочитанное аккаунтом с этими правами. */
	private AuditEntry read(UUID clinic, UUID entityId, String... authorities) {
		SecurityContextHolder.getContext()
			.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(UUID.randomUUID().toString(), null,
					AuthorityUtils.createAuthorityList(authorities)));
		try {
			return TenantContext.call(Tenant.of(clinic), () -> this.journal
				.search(new AuditQuery(null, null, null, null, null, null, entityId, null, null), PageRequest.of(0, 10,
						Sort.by("at")))
				.getContent()
				.getFirst());
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private List<AuditEntry> journal(UUID clinic, AuditQuery query) {
		return TenantContext.call(Tenant.of(clinic), () -> this.journal.search(query, PageRequest.of(0, 10)).getContent());
	}

	private List<AuditEntry> searchText(UUID clinic, String text) {
		return journal(clinic, new AuditQuery(null, null, null, null, null, null, null, null, text));
	}

	private boolean viewAs(UUID account, UUID clinic, UUID patient) {
		signInAs(account);
		try {
			return TenantContext.call(Tenant.of(clinic),
					() -> this.audit.recordView("medical.card", patient, "Карта пациента"));
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private static void signInAs(UUID account) {
		SecurityContextHolder.getContext()
			.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(account.toString(), null, List.of()));
	}

	/** Действие в клинике и в транзакции записи. */
	private void inClinic(UUID clinic, Runnable action) {
		TenantContext.run(Tenant.of(clinic), () -> write(() -> {
			action.run();
			return null;
		}));
	}

	private <T> @Nullable T write(Supplier<@Nullable T> action) {
		return new TransactionTemplate(this.transactions).execute((status) -> action.get());
	}

	private <T> @Nullable T readOnly(Supplier<@Nullable T> action) {
		TransactionTemplate transaction = new TransactionTemplate(this.transactions);
		transaction.setReadOnly(true);
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
		return transaction.execute((status) -> action.get());
	}

	private long count(UUID clinic) {
		Long count = TenantContext.call(Tenant.of(clinic),
				() -> this.jdbc.queryForObject("select count(*) from audit_log", Long.class));
		return (count != null) ? count : 0;
	}

	private String partitionOf(UUID clinic) {
		return TenantContext.call(Tenant.of(clinic),
				() -> this.jdbc.queryForObject("select tableoid::regclass::text from audit_log limit 1", String.class));
	}

	/**
	 * Снимок аккаунта со всеми видами секретов: хэш, пароль, токен, PIN по имени, поле с {@link AuditSecret} и значение
	 * вида хэша пароля в поле с обычным именем.
	 */
	record AccountSnapshot(String email, String passwordHash, @Nullable String password, String resetToken, String pin,
			@AuditSecret String comment, String remark) {
	}

	/** Снимок пациента: вложенный LocalizedText, контакты, свободный текст с телефоном, флаг и список. */
	record PatientSnapshot(LocalizedText name, String phone, String iin, @Nullable String phone2, String note, boolean vip,
			List<String> tags) {
	}

}
