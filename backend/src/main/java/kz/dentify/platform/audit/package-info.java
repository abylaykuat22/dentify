/**
 * Журнал действий (PLT-09): запись событий в {@code audit_log} в транзакции изменения, аннотация {@code Audited},
 * разница по снимкам без секретов, просмотры с окном в Valkey, чтение с маской контактов и партиции заранее.
 * Внутренний пакет модуля platform: модули пишут журнал через {@code AuditService} и {@code Audited} из
 * {@code platform.api}, iam читает его через {@code AuditJournal} из {@code platform.identity}.
 */
@NullMarked
package kz.dentify.platform.audit;

import org.jspecify.annotations.NullMarked;
