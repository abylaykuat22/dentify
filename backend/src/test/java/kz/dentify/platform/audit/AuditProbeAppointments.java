package kz.dentify.platform.audit;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import kz.dentify.platform.api.AuditActions;
import kz.dentify.platform.api.AuditEvent;
import kz.dentify.platform.api.AuditService;
import kz.dentify.platform.api.BusinessRuleException;
import kz.dentify.platform.audit.AuditProbeController.Reschedule;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Пробный перенос записи только для тестов: как будет в модуле schedule — снимок до, изменение, снимок после, событие
 * в той же транзакции.
 */
@Service
class AuditProbeAppointments {

	static final String APPOINTMENT = "schedule.appointment";

	private final AuditService audit;

	AuditProbeAppointments(AuditService audit) {
		this.audit = audit;
	}

	@Transactional
	void reschedule(UUID id, Reschedule request) {
		Snapshot before = new Snapshot(request.fromDate(), request.fromTime(), "Кресло 1", Status.EXPECTED);
		Snapshot after = new Snapshot(request.toDate(), request.toTime(), "Кресло 1", Status.EXPECTED);
		this.audit.record(AuditEvent.of(APPOINTMENT, AuditActions.RESCHEDULED).entity(id, request.patient())
			.change(before, after));
		if (request.fail()) {
			throw new BusinessRuleException("probe.rule.backdated", 1);
		}
	}

	/** Снимок записи: дата, время, кресло и статус. */
	record Snapshot(LocalDate date, LocalTime time, String chair, Status status) {
	}

	enum Status {

		EXPECTED, ARRIVED

	}

}
