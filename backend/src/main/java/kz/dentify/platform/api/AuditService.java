package kz.dentify.platform.api;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Запись в журнал действий клиники (PLT-09). Изменения данных — {@link #record} в транзакции изменения или аннотация
 * {@link Audited}; просмотр медкарты, выгрузка и печать с ПД — {@link #recordView}, {@link #recordExport},
 * {@link #recordPrint}. Клиника, аккаунт, сотрудник, время и IP запроса берутся из контекста, без клиники —
 * {@link MissingTenantContextException}. Журнал только дописывается: изменить или удалить событие нельзя ни
 * приложению, ни владельцу схемы.
 */
public interface AuditService {

	/**
	 * Событие изменения в текущей транзакции: откатилась транзакция — нет и события. Разница было и стало считается по
	 * снимкам события; если оба снимка есть и равны, событие не пишется.
	 *
	 * @throws IllegalStateException если нет транзакции записи: событие и изменение должны сохраниться вместе
	 * @throws IllegalArgumentException если в снимке объект, который не record, не коллекция, не Map и не простое
	 * значение, например сущность JPA: в таком объекте секреты не найти
	 */
	void record(AuditEvent event);

	/**
	 * Просмотр объекта, прежде всего медкарты пациента ({@code medical.card}, id пациента): не чаще одного события на
	 * аккаунт и объект за {@code dentify.audit.view-dedup-window} (15 минут). Метка окна — в Valkey; Valkey недоступен —
	 * событие пишется без проверки. Пишется в текущей транзакции записи, а без транзакции — в своей; откат транзакции
	 * снимает и метку, следующий просмотр запишется.
	 *
	 * @return true — событие записано, false — такой просмотр уже есть в журнале за окно
	 * @throws IllegalStateException в транзакции только для чтения
	 */
	boolean recordView(String entityType, UUID entityId, @Nullable String label);

	/**
	 * Выгрузка с ПД: список пациентов в Excel, журнал действий. Транзакция — как у {@link #recordView}.
	 *
	 * @param entityType что выгружено, например {@code patient.patient}
	 * @param label понятное описание выгрузки или null
	 * @param rows сколько строк выгружено
	 */
	void recordExport(String entityType, @Nullable String label, long rows);

	/** Печать с ПД: план лечения, договор. Транзакция — как у {@link #recordView}. */
	void recordPrint(String entityType, UUID entityId, @Nullable String label);

}
