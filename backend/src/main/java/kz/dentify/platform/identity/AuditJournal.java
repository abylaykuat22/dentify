package kz.dentify.platform.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import kz.dentify.platform.api.BusinessRuleException;
import kz.dentify.platform.api.InvalidRequestException;
import kz.dentify.platform.api.MissingTenantContextException;
import kz.dentify.platform.api.Permissions;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Чтение журнала действий клиники контекста (PLT-09) для модуля iam: экран журнала и его выгрузка. Всегда только
 * клиника контекста, без неё — {@link MissingTenantContextException}. Право на чтение журнала проверяет эндпоинт; ИИН,
 * телефон и e-mail без права {@link Permissions#PATIENT_PHONES} отдаются маской.
 */
public interface AuditJournal {

	/**
	 * Страница событий, сначала новые. Сортировка — только по {@code at}. Период по умолчанию и предел периода поиска —
	 * в {@link AuditQuery}.
	 *
	 * @throws InvalidRequestException конец периода не позже начала; период поиска по тексту длиннее предела
	 */
	Page<AuditEntry> search(AuditQuery query, Pageable pageable);

	/** Событие по id или пусто: нет или другой клиники. */
	Optional<AuditEntry> find(UUID id);

	/**
	 * Все события по фильтру для выгрузки, сначала новые, не больше {@code dentify.audit.export-max-rows}; сама
	 * выгрузка тоже пишется в журнал.
	 *
	 * @throws BusinessRuleException {@code audit.export.too-many-rows}, если событий больше предела: сузить период
	 */
	List<AuditEntry> export(AuditQuery query);

}
