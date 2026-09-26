package kz.dentify.platform.api;

import jakarta.persistence.MappedSuperclass;

/**
 * Базовая сущность глобальной таблицы — без {@code clinic_id} и RLS, общей для всех клиник: аккаунт (iam),
 * справочник МКБ-10 (medical), реестр клиник (clinic). Наследование от этого класса и есть явная пометка
 * глобальной таблицы (PLT-01); модули, где такие сущности разрешены, и причина перечислены в ArchUnit-тесте
 * {@code TenancyRulesTests}, а таблицы — в тесте каталога БД {@code RowLevelSecurityTests}.
 * <p>
 * Данные пациентов, записей, склада и прочее, что принадлежит клинике, — только {@link TenantEntity}.
 */
@MappedSuperclass
public abstract class GlobalEntity extends BaseEntity {

	protected GlobalEntity() {
	}

}
