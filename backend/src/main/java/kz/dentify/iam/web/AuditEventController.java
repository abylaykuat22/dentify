package kz.dentify.iam.web;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import kz.dentify.platform.api.NotFoundException;
import kz.dentify.platform.api.PageQuery;
import kz.dentify.platform.api.PageResponse;
import kz.dentify.platform.api.Permissions;
import kz.dentify.platform.api.SortBy;
import kz.dentify.platform.identity.AuditJournal;
import kz.dentify.platform.identity.AuditQuery;
import org.jspecify.annotations.Nullable;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Журнал действий клиники (PLT-09, экран Доступы — Журнал): лента событий с фильтрами и одно событие с разницей было и
 * стало. Журнал ведёт платформа ({@link AuditJournal}), здесь — только HTTP. Право — {@code audit:read}; пока ролей
 * нет (T-IAM), его нет ни у кого. ИИН, телефон и e-mail в ответе без права {@code patient:phones} — маской.
 */
@RestController
@RequestMapping(AuditEventController.PATH)
@Tag(name = "audit", description = "Журнал действий клиники")
@PreAuthorize("hasAuthority('" + Permissions.AUDIT_READ + "')")
class AuditEventController {

	static final String PATH = "/api/v1/audit-events";

	/** Модуль, тип объекта и действие — как в журнале: строчные латинские буквы, цифры, дефис, точка у типа. */
	static final String CODE = "[a-z][a-z0-9.-]{0,99}";

	private final AuditJournal journal;

	AuditEventController(AuditJournal journal) {
		this.journal = journal;
	}

	@GetMapping
	@Operation(operationId = "listAuditEvents", summary = "События журнала",
			description = "Сначала новые. Фильтры — вместе: период [from, to), кто (accountId или staffId), категория "
					+ "(module), тип объекта, объект (entityId — история одного объекта), действие и поиск q по имени "
					+ "объекта и тексту события. Интерфейс всегда передаёт период явно — from и to; без from и "
					+ "entityId сервер берёт последние 30 дней до to; с q период не длиннее 366 дней, иначе 400 "
					+ "audit.period.too-long. IP событий — маской для всех ролей.")
	PageResponse<AuditEventResponse> list(@SortBy(value = "at", defaultSort = "-at") PageQuery query,
			@Parameter(description = "Начало периода включительно, ISO 8601; нет — 30 дней до конца периода, у истории "
					+ "объекта — без начала", example = "2026-09-24T00:00:00+05:00")
			@RequestParam(required = false) @Nullable Instant from,
			@Parameter(description = "Конец периода не включительно, ISO 8601", example = "2026-09-25T00:00:00+05:00")
			@RequestParam(required = false) @Nullable Instant to,
			@Parameter(description = "Кто: аккаунт") @RequestParam(required = false) @Nullable UUID accountId,
			@Parameter(description = "Кто: сотрудник") @RequestParam(required = false) @Nullable UUID staffId,
			@Parameter(description = "Категория — модуль объекта", example = "schedule")
			@RequestParam(required = false) @Nullable @Pattern(regexp = CODE) String module,
			@Parameter(description = "Тип объекта", example = "schedule.appointment")
			@RequestParam(required = false) @Nullable @Pattern(regexp = CODE) String entityType,
			@Parameter(description = "Объект: история одного объекта")
			@RequestParam(required = false) @Nullable UUID entityId,
			@Parameter(description = "Действие", example = "rescheduled")
			@RequestParam(required = false) @Nullable @Pattern(regexp = CODE) String action) {
		AuditQuery filter = new AuditQuery(from, to, accountId, staffId, module, entityType, entityId, action, query.q());
		return PageResponse.of(this.journal.search(filter, query.pageable()), AuditEventResponse::from);
	}

	@GetMapping("/{id}")
	@Operation(operationId = "getAuditEvent", summary = "Событие журнала",
			description = "Событие другой клиники — 404, как несуществующее.")
	AuditEventResponse get(@Parameter(description = "Id события") @PathVariable UUID id) {
		return this.journal.find(id).map(AuditEventResponse::from).orElseThrow(NotFoundException::new);
	}

}
