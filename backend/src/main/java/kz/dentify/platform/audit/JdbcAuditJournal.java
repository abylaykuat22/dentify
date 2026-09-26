package kz.dentify.platform.audit;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import kz.dentify.platform.api.AccountNameProvider;
import kz.dentify.platform.api.AuditService;
import kz.dentify.platform.api.BusinessRuleException;
import kz.dentify.platform.api.CurrentPermissions;
import kz.dentify.platform.api.FieldViolation;
import kz.dentify.platform.api.InvalidRequestException;
import kz.dentify.platform.api.IpAddresses;
import kz.dentify.platform.api.Permissions;
import kz.dentify.platform.api.TenantContext;
import kz.dentify.platform.identity.AuditEntry;
import kz.dentify.platform.identity.AuditJournal;
import kz.dentify.platform.identity.AuditQuery;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Чтение журнала действий клиники контекста (PLT-09). Запрос всегда с {@code clinic_id} клиники контекста — это и
 * изоляция вместе с RLS, и первая колонка индексов; период отсекает лишние партиции. Лента без фильтра по объекту
 * всегда ограничена периодом: без начала — последние {@code dentify.audit.default-period} (30 дней), поиск по тексту — не
 * длиннее {@code dentify.audit.search-max-period} (366 дней): у него нет индекса. Тексты — на языке запроса;
 * ИИН, телефоны и e-mail без права {@link Permissions#PATIENT_PHONES} — маской в имени объекта, аргументах и разнице;
 * IP — маской всегда.
 */
@Transactional(readOnly = true)
class JdbcAuditJournal implements AuditJournal {

	/** Код ошибки выгрузки больше предела строк; аргумент — предел. */
	static final String EXPORT_TOO_LARGE = "audit.export.too-many-rows";

	/** Код ошибки поля {@code to}: конец периода не позже начала. */
	static final String PERIOD_INVALID = "audit.period.invalid";

	/** Код ошибки поля {@code from}: период поиска по тексту длиннее предела; аргумент — предел в днях. */
	static final String PERIOD_TOO_LONG = "audit.period.too-long";

	/** Тип объекта события о выгрузке самого журнала. */
	static final String AUDIT_LOG = "platform.audit-log";

	private static final String COLUMNS = """
			select id, at, account_id, staff_id, host(ip) as ip, module, entity_type, entity_id, entity_label, action,
			       diff::text as diff, args::text as args
			from audit_log""";

	private final JdbcClient jdbc;

	private final AuditTexts texts;

	private final JsonMapper json;

	private final CurrentPermissions permissions;

	private final ObjectProvider<AccountNameProvider> names;

	private final AuditService audit;

	private final AuditProperties properties;

	JdbcAuditJournal(JdbcClient jdbc, AuditTexts texts, JsonMapper json, CurrentPermissions permissions,
			ObjectProvider<AccountNameProvider> names, AuditService audit, AuditProperties properties) {
		this.jdbc = jdbc;
		this.texts = texts;
		this.json = json;
		this.permissions = permissions;
		this.names = names;
		this.audit = audit;
		this.properties = properties;
	}

	@Override
	public Page<AuditEntry> search(AuditQuery query, Pageable pageable) {
		Filter filter = filter(query);
		long total = count(filter);
		if (total == 0 || pageable.getOffset() >= total) {
			return new PageImpl<>(List.of(), pageable, total);
		}
		List<Row> rows = this.jdbc.sql(COLUMNS + " where " + filter.sql() + " order by " + order(pageable.getSort())
				+ " limit :limit offset :offset")
			.params(filter.params())
			.param("limit", pageable.getPageSize())
			.param("offset", pageable.getOffset())
			.query(JdbcAuditJournal::row)
			.list();
		return new PageImpl<>(render(rows), pageable, total);
	}

	@Override
	public Optional<AuditEntry> find(UUID id) {
		return this.jdbc.sql(COLUMNS + " where clinic_id = :clinicId and id = :id")
			.param("clinicId", TenantContext.clinicId())
			.param("id", id)
			.query(JdbcAuditJournal::row)
			.optional()
			.map((row) -> render(List.of(row)).getFirst());
	}

	@Override
	@Transactional
	public List<AuditEntry> export(AuditQuery query) {
		Filter filter = filter(query);
		int limit = this.properties.getExportMaxRows();
		long total = count(filter);
		if (total > limit) {
			throw new BusinessRuleException(EXPORT_TOO_LARGE, limit);
		}
		List<Row> rows = this.jdbc.sql(COLUMNS + " where " + filter.sql() + " order by at desc, id desc limit :limit")
			.params(filter.params())
			.param("limit", limit)
			.query(JdbcAuditJournal::row)
			.list();
		List<AuditEntry> entries = render(rows);
		this.audit.recordExport(AUDIT_LOG, null, entries.size());
		return entries;
	}

	private long count(Filter filter) {
		Long total = this.jdbc.sql("select count(*) from audit_log where " + filter.sql())
			.params(filter.params())
			.query(Long.class)
			.single();
		return (total != null) ? total : 0;
	}

	/**
	 * Условия запроса: только заданные фильтры, значения — параметрами. Без фильтра по объекту период ограничен: без
	 * начала — {@link AuditProperties#getDefaultPeriod()} до конца периода, поиск по тексту — не длиннее
	 * {@link AuditProperties#getSearchMaxPeriod()}.
	 */
	private Filter filter(AuditQuery query) {
		Instant from = query.from();
		Instant to = query.to();
		if (from != null && to != null && !to.isAfter(from)) {
			throw new InvalidRequestException(FieldViolation.of("to", PERIOD_INVALID));
		}
		boolean text = query.text() != null && !query.text().isBlank();
		if (query.entityId() == null) {
			Instant end = (to != null) ? to : Instant.now();
			if (from == null) {
				from = end.minus(this.properties.getDefaultPeriod());
			}
			Duration longest = this.properties.getSearchMaxPeriod();
			if (text && Duration.between(from, end).compareTo(longest) > 0) {
				throw new InvalidRequestException(FieldViolation.of("from", PERIOD_TOO_LONG, longest.toDays()));
			}
		}
		StringBuilder sql = new StringBuilder("clinic_id = :clinicId");
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("clinicId", TenantContext.clinicId());
		if (from != null) {
			sql.append(" and at >= :from");
			params.put("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC));
		}
		if (to != null) {
			sql.append(" and at < :to");
			params.put("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC));
		}
		equal(sql, params, "account_id", "accountId", query.accountId());
		equal(sql, params, "staff_id", "staffId", query.staffId());
		equal(sql, params, "module", "module", query.module());
		equal(sql, params, "entity_type", "entityType", query.entityType());
		equal(sql, params, "entity_id", "entityId", query.entityId());
		equal(sql, params, "action", "action", query.action());
		if (query.text() != null && !query.text().isBlank()) {
			sql.append(" and search_text like :text escape '\\'");
			params.put("text", "%" + likeEscape(query.text().strip().toLowerCase(Locale.ROOT)) + "%");
		}
		return new Filter(sql.toString(), params);
	}

	private static void equal(StringBuilder sql, Map<String, Object> params, String column, String name,
			@Nullable Object value) {
		if (value != null) {
			sql.append(" and ").append(column).append(" = :").append(name);
			params.put(name, value);
		}
	}

	private static String likeEscape(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/** Сортировка журнала — только по времени; при равном времени — по id (UUIDv7 растёт со временем). */
	private static String order(Sort sort) {
		Sort.Direction direction = Sort.Direction.DESC;
		for (Sort.Order order : sort) {
			if (!order.getProperty().equals("at")) {
				throw new IllegalArgumentException("Журнал сортируется только по at: " + order.getProperty());
			}
			direction = order.getDirection();
		}
		String keyword = direction.isAscending() ? "asc" : "desc";
		return "at " + keyword + ", id " + keyword;
	}

	private List<AuditEntry> render(List<Row> rows) {
		Locale locale = LocaleContextHolder.getLocale();
		boolean masked = !this.permissions.has(Permissions.PATIENT_PHONES);
		// имена авторов страницы — одним пакетом на страницу, а не запросом на каждого автора
		Map<UUID, String> actors = AccountNameProvider.resolve(this.names.orderedStream(),
				rows.stream().map(Row::accountId).filter(Objects::nonNull).collect(Collectors.toSet()));
		List<AuditEntry> entries = new ArrayList<>(rows.size());
		for (Row row : rows) {
			String actor = actor(row.accountId(), actors, locale);
			String label = masked ? AuditTexts.mask(row.entityLabel(), false) : row.entityLabel();
			List<Object> args = args(row.args(), masked);
			String text = this.texts.eventText(row.entityType(), row.action(), label, actor, args, locale);
			String ip = (row.ip() != null) ? IpAddresses.mask(row.ip()) : null;
			entries.add(new AuditEntry(row.id(), row.at().toInstant(), row.accountId(), row.staffId(), actor, ip,
					row.module(), row.entityType(), row.entityId(), label, row.action(), text,
					changes(row, locale, masked)));
		}
		return entries;
	}

	private String actor(@Nullable UUID accountId, Map<UUID, String> actors, Locale locale) {
		if (accountId == null) {
			return this.texts.text("audit.actor.system", locale);
		}
		String name = actors.get(accountId);
		return (name != null) ? name : this.texts.text("audit.actor.unknown", locale);
	}

	private List<Object> args(@Nullable String json, boolean masked) {
		if (json == null) {
			return List.of();
		}
		List<Object> args = new ArrayList<>();
		for (JsonNode node : this.json.readTree(json)) {
			if (node.isIntegralNumber()) {
				args.add(node.asLong());
			}
			else if (node.isNumber()) {
				args.add(new BigDecimal(node.asString()));
			}
			else {
				String text = node.asString();
				args.add(masked ? AuditTexts.mask(text, false) : text);
			}
		}
		return args;
	}

	private List<AuditEntry.Change> changes(Row row, Locale locale, boolean masked) {
		if (row.diff() == null) {
			return List.of();
		}
		List<AuditEntry.Change> changes = new ArrayList<>();
		for (JsonNode node : this.json.readTree(row.diff())) {
			String field = node.path("field").asString();
			String type = node.path("type").asString();
			String label = this.texts.fieldLabel(row.entityType(), field, locale);
			if (AuditEntry.Change.SECRET.equals(type)) {
				changes.add(new AuditEntry.Change(field, label, type, null, null));
				continue;
			}
			changes.add(new AuditEntry.Change(field, label, type,
					this.texts.value(row.entityType(), field, type, node.get("from"), locale, masked),
					this.texts.value(row.entityType(), field, type, node.get("to"), locale, masked)));
		}
		return changes;
	}

	private static Row row(ResultSet rs, int rowNum) throws SQLException {
		return new Row(rs.getObject("id", UUID.class), rs.getObject("at", OffsetDateTime.class),
				rs.getObject("account_id", UUID.class), rs.getObject("staff_id", UUID.class), rs.getString("ip"),
				rs.getString("module"), rs.getString("entity_type"), rs.getObject("entity_id", UUID.class),
				rs.getString("entity_label"), rs.getString("action"), rs.getString("diff"), rs.getString("args"));
	}

	private record Filter(String sql, Map<String, Object> params) {
	}

	private record Row(UUID id, OffsetDateTime at, @Nullable UUID accountId, @Nullable UUID staffId, @Nullable String ip,
			String module, String entityType, @Nullable UUID entityId, @Nullable String entityLabel, String action,
			@Nullable String diff, @Nullable String args) {
	}

}
