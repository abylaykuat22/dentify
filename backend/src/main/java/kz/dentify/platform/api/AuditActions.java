package kz.dentify.platform.api;

/**
 * Стандартные действия журнала аудита (PLT-09). Действие — часть ключа текста события
 * {@code audit.<тип объекта>.<действие>}, общий текст — {@code audit.action.<действие>}. Модуль может завести своё
 * действие: строчные латинские буквы, цифры и дефис.
 */
public final class AuditActions {

	public static final String CREATED = "created";

	public static final String UPDATED = "updated";

	/** Физическое удаление — редкость: данные клиники архивируются ({@link #ARCHIVED}). */
	public static final String DELETED = "deleted";

	public static final String ARCHIVED = "archived";

	public static final String RESTORED = "restored";

	public static final String STATUS_CHANGED = "status-changed";

	/** Перенос записи на другое время или кресло. */
	public static final String RESCHEDULED = "rescheduled";

	/** Проведение документа склада. */
	public static final String POSTED = "posted";

	/** Выдача роли или права. */
	public static final String GRANTED = "granted";

	public static final String REVOKED = "revoked";

	/** Просмотр медкарты: {@link AuditService#recordView}. */
	public static final String VIEWED = "viewed";

	/** Выгрузка с ПД: {@link AuditService#recordExport}. */
	public static final String EXPORTED = "exported";

	/** Печать с ПД: {@link AuditService#recordPrint}. */
	public static final String PRINTED = "printed";

	private AuditActions() {
	}

}
