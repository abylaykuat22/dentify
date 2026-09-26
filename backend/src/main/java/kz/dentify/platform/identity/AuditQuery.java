package kz.dentify.platform.identity;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Фильтры журнала действий ({@link AuditJournal}). Пустой фильтр не ограничивает; все заданные — вместе. Исключение —
 * период: без фильтра по объекту лента всегда за период. Начало не задано — последние
 * {@code dentify.audit.default-period} (30 дней) до конца периода; поиск по тексту — за период не длиннее
 * {@code dentify.audit.search-max-period} (366 дней).
 *
 * @param from начало периода включительно; null — {@code dentify.audit.default-period} до конца, у истории объекта —
 * без начала
 * @param to конец периода не включительно; должен быть позже начала
 * @param accountId кто сделал: аккаунт
 * @param staffId кто сделал: сотрудник
 * @param module категория объектов — модуль: {@code schedule}, {@code patient}, {@code medical}, {@code inventory},
 * {@code iam}
 * @param entityType тип объекта {@code <модуль>.<объект>}
 * @param entityId объект — история одного объекта
 * @param action действие
 * @param text поиск по имени объекта и тексту события без учёта регистра; ИИН, телефон и e-mail в них — маской, и по
 * точному номеру событие не находится
 */
public record AuditQuery(@Nullable Instant from, @Nullable Instant to, @Nullable UUID accountId, @Nullable UUID staffId,
		@Nullable String module, @Nullable String entityType, @Nullable UUID entityId, @Nullable String action,
		@Nullable String text) {

	/** Без фильтров. */
	public static AuditQuery all() {
		return new AuditQuery(null, null, null, null, null, null, null, null, null);
	}

}
