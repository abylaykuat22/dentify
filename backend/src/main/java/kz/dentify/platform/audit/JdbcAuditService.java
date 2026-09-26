package kz.dentify.platform.audit;

import java.net.InetAddress;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import kz.dentify.platform.api.AuditActions;
import kz.dentify.platform.api.AuditEvent;
import kz.dentify.platform.api.AuditService;
import kz.dentify.platform.api.CurrentAccountProvider;
import kz.dentify.platform.api.CurrentStaffProvider;
import kz.dentify.platform.api.IpAddresses;
import kz.dentify.platform.api.Languages;
import kz.dentify.platform.api.TenantContext;
import kz.dentify.platform.audit.AuditDiff.Change;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Запись журнала действий в {@code audit_log} (PLT-09) через JDBC в транзакции, к которой привязан поток: с JPA это
 * то же соединение, что у изменения, и событие фиксируется или откатывается вместе с ним. Клиника — из контекста, RLS
 * не даст записать событие чужой клинике; аккаунт — {@link CurrentAccountProvider}; сотрудник —
 * {@link CurrentStaffProvider}, если его реализует iam; IP — {@code getRemoteAddr()} HTTP-запроса потока (за прокси —
 * клиент из X-Forwarded-For), вне запроса — пусто.
 * <p>
 * Время события — часы приложения с точностью до микросекунд (как {@code timestamptz}), id — {@code uuidv7()} БД.
 * Для поиска в {@code search_text} — имя объекта и текст события на русском в нижнем регистре, ИИН, телефоны и e-mail
 * в нём — маской.
 */
class JdbcAuditService implements AuditService {

	private static final Logger log = LoggerFactory.getLogger(JdbcAuditService.class);

	private static final String INSERT = """
			insert into audit_log (clinic_id, at, account_id, staff_id, ip, entity_type, entity_id, entity_label, action,
			                       diff, args, search_text)
			values (:clinicId, :at, :accountId, :staffId, cast(:ip as inet), :entityType, :entityId, :entityLabel, :action,
			        cast(:diff as jsonb), cast(:args as jsonb), :searchText)""";

	private final JdbcClient jdbc;

	private final AuditDiff diff;

	private final AuditTexts texts;

	private final JsonMapper json;

	private final CurrentAccountProvider accounts;

	private final ObjectProvider<CurrentStaffProvider> staff;

	private final ViewWindows views;

	private final TransactionTemplate ownTransaction;

	JdbcAuditService(JdbcClient jdbc, AuditDiff diff, AuditTexts texts, JsonMapper json, CurrentAccountProvider accounts,
			ObjectProvider<CurrentStaffProvider> staff, ViewWindows views, PlatformTransactionManager transactions) {
		this.jdbc = jdbc;
		this.diff = diff;
		this.texts = texts;
		this.json = json;
		this.accounts = accounts;
		this.staff = staff;
		this.views = views;
		this.ownTransaction = new TransactionTemplate(transactions);
	}

	@Override
	public void record(AuditEvent event) {
		requireWriteTransaction();
		List<Change> changes = this.diff.compare(this.diff.flatten(event.before()), this.diff.flatten(event.after()));
		if (event.before() != null && event.after() != null && changes.isEmpty()) {
			log.debug("Событие аудита {} {} без разницы не пишется", event.entityType(), event.action());
			return;
		}
		insert(event, changes);
	}

	/** Событие с уже посчитанной разницей: {@link AuditedInterceptor}. */
	void record(AuditEvent event, List<Change> changes) {
		requireWriteTransaction();
		insert(event, changes);
	}

	@Override
	public boolean recordView(String entityType, UUID entityId, @Nullable String label) {
		AuditEvent event = AuditEvent.of(entityType, AuditActions.VIEWED).entity(entityId, label);
		UUID clinicId = TenantContext.clinicId();
		UUID accountId = this.accounts.currentAccountId().orElse(null);
		return inWriteTransaction(() -> {
			String window = this.views.claim(clinicId, accountId, entityType, entityId);
			if (window == null) {
				log.debug("Просмотр {} {} уже в журнале за окно", entityType, entityId);
				return false;
			}
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					if (status != STATUS_COMMITTED) {
						JdbcAuditService.this.views.release(window);
					}
				}
			});
			insert(event, List.of());
			return true;
		});
	}

	@Override
	public void recordExport(String entityType, @Nullable String label, long rows) {
		AuditEvent event = AuditEvent.of(entityType, AuditActions.EXPORTED).entity(null, label).args(rows);
		inWriteTransaction(() -> insert(event, List.of()));
	}

	@Override
	public void recordPrint(String entityType, UUID entityId, @Nullable String label) {
		AuditEvent event = AuditEvent.of(entityType, AuditActions.PRINTED).entity(entityId, label);
		inWriteTransaction(() -> insert(event, List.of()));
	}

	private boolean insert(AuditEvent event, List<Change> changes) {
		UUID clinicId = TenantContext.clinicId();
		String text = this.texts.eventText(event.entityType(), event.action(), event.entityLabel(), "", event.args(),
				Languages.RUSSIAN);
		// ИИН, телефоны и e-mail — маской, как их видит сотрудник без права patient:phones: иначе поиском по точному
		// номеру он узнал бы, чей это номер, не видя его
		String search = Objects.requireNonNull(
				AuditTexts.mask(((event.entityLabel() != null) ? event.entityLabel() + " " : "") + text, false));
		this.jdbc.sql(INSERT)
			.param("clinicId", clinicId)
			.param("at", OffsetDateTime.ofInstant(Instant.now().truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC))
			.param("accountId", this.accounts.currentAccountId().orElse(null))
			.param("staffId", currentStaffId())
			.param("ip", currentIp())
			.param("entityType", event.entityType())
			.param("entityId", event.entityId())
			.param("entityLabel", event.entityLabel())
			.param("action", event.action())
			.param("diff", changes.isEmpty() ? null : this.diff.toJson(changes).toString())
			.param("args", event.args().isEmpty() ? null : this.json.writeValueAsString(event.args()))
			.param("searchText", search.toLowerCase(Locale.ROOT))
			.update();
		log.debug("Событие аудита {} {} {}", event.entityType(), event.action(), event.entityId());
		return true;
	}

	/**
	 * Действие в текущей транзакции записи, без транзакции — в своей. В транзакции только для чтения записать нельзя:
	 * PostgreSQL отклонит INSERT, а своя транзакция заняла бы второе соединение пула.
	 */
	private <T> T inWriteTransaction(Supplier<T> action) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			requireWriteTransaction();
			return action.get();
		}
		T result = this.ownTransaction.execute((status) -> action.get());
		if (result == null) {
			throw new IllegalStateException("Транзакция записи журнала аудита не вернула результат");
		}
		return result;
	}

	private static void requireWriteTransaction() {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("Событие аудита пишется в транзакции изменения: нет активной транзакции. "
					+ "Метод с изменением должен быть @Transactional");
		}
		if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
			throw new IllegalStateException("Событие аудита нельзя записать в транзакции только для чтения: "
					+ "метод, который пишет событие, — @Transactional без readOnly");
		}
	}

	private @Nullable UUID currentStaffId() {
		CurrentStaffProvider provider = this.staff.getIfAvailable();
		return (provider != null) ? provider.currentStaffId().orElse(null) : null;
	}

	private static @Nullable String currentIp() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (!(attributes instanceof ServletRequestAttributes servlet)) {
			return null;
		}
		HttpServletRequest request = servlet.getRequest();
		InetAddress address = IpAddresses.parse(request.getRemoteAddr());
		return (address != null) ? address.getHostAddress() : null;
	}

}
