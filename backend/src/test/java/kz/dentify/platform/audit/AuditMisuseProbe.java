package kz.dentify.platform.audit;

import java.util.UUID;

import kz.dentify.platform.api.AuditActions;
import kz.dentify.platform.api.Audited;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Неверное применение {@link Audited} только для тестов: без транзакции, в транзакции только для чтения, с
 * REQUIRES_NEW, без бина снимков, с выражением id не по параметру метода и с id, вычисленным в null, — каждый вызов
 * бросает {@link IllegalStateException}, событие не пишется.
 */
@Component
class AuditMisuseProbe {

	static final String NOTE = "platform.test-note";

	@Audited(entity = NOTE, action = AuditActions.UPDATED)
	void withoutTransaction(UUID id) {
	}

	@Transactional(readOnly = true)
	@Audited(entity = NOTE, action = AuditActions.UPDATED)
	void readOnly(UUID id) {
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@Audited(entity = NOTE, action = AuditActions.UPDATED)
	void requiresNew(UUID id) {
	}

	@Transactional
	@Audited(entity = "platform.without-snapshots", action = AuditActions.UPDATED)
	void withoutSnapshots(UUID id) {
	}

	/** id по умолчанию — {@code #id}, а параметр называется иначе: SpEL дал бы null, и событие пропало бы молча. */
	@Transactional
	@Audited(entity = NOTE, action = AuditActions.RESCHEDULED)
	void reschedule(UUID appointmentId) {
	}

	@Transactional
	@Audited(entity = NOTE, action = AuditActions.UPDATED)
	void nullId(@Nullable UUID id) {
	}

	/** Результат без id: создание, которое не записать. */
	@Transactional
	@Audited(entity = NOTE, action = AuditActions.CREATED, id = Audited.RESULT_ID)
	Created createWithoutId() {
		return new Created(null);
	}

	record Created(@Nullable UUID id) {
	}

}
