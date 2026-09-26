/**
 * Тенант-контекст платформы (PLT-01): хранилище клиники потока, её источник в HTTP-запросе (сессия), перенос в
 * {@code @Async} и обработчики событий. Внутренний пакет модуля platform: модули работают с контекстом через
 * {@code TenantContext} из {@code platform.api}.
 */
@NullMarked
package kz.dentify.platform.tenant;

import org.jspecify.annotations.NullMarked;
