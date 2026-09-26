package kz.dentify.platform.audit;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kz.dentify.platform.api.AuditService;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Пробный перенос записи только для тестов (приёмка PLT-09): событие журнала с разницей по дате и времени пишется
 * в транзакции переноса через настоящий {@link AuditService} — с автором из сессии и IP клиента. С {@code fail}
 * перенос после записи события бросает ошибку правила: транзакция откатывается вместе с событием. Путь — пробная
 * цепочка {@code /test/**}, вне {@code api/openapi.json}.
 */
@RestController
class AuditProbeController {

	static final String PATH = "/test/audit";

	static final String RESCHEDULE = PATH + "/appointments/{id}/reschedule";

	private final AuditProbeAppointments appointments;

	AuditProbeController(AuditProbeAppointments appointments) {
		this.appointments = appointments;
	}

	@PostMapping(RESCHEDULE)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reschedule(@PathVariable UUID id, @Validated @RequestBody Reschedule body) {
		this.appointments.reschedule(id, body);
	}

	record Reschedule(@NotBlank String patient, @NotNull LocalDate fromDate, @NotNull LocalTime fromTime,
			@NotNull LocalDate toDate, @NotNull LocalTime toTime, boolean fail) {
	}

}
