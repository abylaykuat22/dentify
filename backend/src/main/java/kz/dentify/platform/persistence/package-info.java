/**
 * Данные платформы: изоляция клиник в JPA и БД (PLT-01, PLT-02) — клиника в {@code @TenantId} Hibernate и в
 * настройке соединения {@code app.clinic_id} для RLS, проверка контекста в репозиториях данных клиники, аудит
 * сущностей и последняя правка записи для ответа о конфликте версии (PLT-08). Внутренний пакет модуля platform.
 */
@NullMarked
package kz.dentify.platform.persistence;

import org.jspecify.annotations.NullMarked;
