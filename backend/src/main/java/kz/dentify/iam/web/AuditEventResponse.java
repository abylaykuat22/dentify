package kz.dentify.iam.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import kz.dentify.platform.identity.AuditEntry;
import org.jspecify.annotations.Nullable;

/**
 * Событие журнала действий клиники.
 *
 * @param id id события
 * @param at когда
 * @param accountId кто: аккаунт, null — система
 * @param staffId кто: сотрудник
 * @param actorName имя сотрудника или Система
 * @param ip IP маской для всех ролей
 * @param module модуль объекта — категория журнала
 * @param entityType тип объекта
 * @param entityId id объекта
 * @param entityLabel понятное имя объекта на момент события
 * @param action действие
 * @param text текст события
 * @param changes разница было и стало
 */
@Schema(description = "Событие журнала действий")
record AuditEventResponse(
		@Schema(description = "Id события") UUID id,
		@Schema(description = "Когда") Instant at,
		@Schema(description = "Кто: аккаунт; null — система") @Nullable UUID accountId,
		@Schema(description = "Кто: сотрудник клиники; null — система или аккаунт без сотрудника") @Nullable UUID staffId,
		@Schema(description = "Имя сотрудника; для системы и неизвестного аккаунта — общий текст", example = "Динара Алиева") String actorName,
		@Schema(description = "IP маской для всех ролей, полный IP наружу не отдаётся; null — событие вне HTTP-запроса", example = "95.56.•.•") @Nullable String ip,
		@Schema(description = "Модуль объекта — категория журнала", example = "schedule") String module,
		@Schema(description = "Тип объекта <модуль>.<объект>", example = "schedule.appointment") String entityType,
		@Schema(description = "Id объекта: ссылка Открыть; null — событие не про один объект") @Nullable UUID entityId,
		@Schema(description = "Понятное имя объекта на момент события; ИИН и телефон без права — маской",
				example = "Айгерим Сейтқазы") @Nullable String entityLabel,
		@Schema(description = "Действие", example = "rescheduled") String action,
		@Schema(description = "Текст события на языке запроса", example = "Перенос записи: Айгерим Сейтқазы") String text,
		@Schema(description = "Разница было → стало по полям; пусто — без разницы") List<AuditChangeResponse> changes) {

	static AuditEventResponse from(AuditEntry entry) {
		return new AuditEventResponse(entry.id(), entry.at(), entry.accountId(), entry.staffId(), entry.actorName(),
				entry.ip(), entry.module(), entry.entityType(), entry.entityId(), entry.entityLabel(), entry.action(),
				entry.text(), entry.changes().stream().map(AuditChangeResponse::from).toList());
	}

	/**
	 * Поле в разнице.
	 *
	 * @param field путь поля
	 * @param label название поля
	 * @param type вид значения; secret — секрет без значений
	 * @param from было
	 * @param to стало
	 */
	@Schema(description = "Поле в разнице было → стало")
	record AuditChangeResponse(
			@Schema(description = "Путь поля в снимке объекта", example = "date") String field,
			@Schema(description = "Название поля на языке запроса", example = "Дата") String label,
			@Schema(description = "Вид значения; secret — пароль или токен: только отметка, что изменился",
					allowableValues = { "text", "number", "boolean", "date", "time", "datetime", "instant", "enum",
							"list", "json", "secret" }) String type,
			@Schema(description = "Было, отформатировано для показа; null — пусто", example = "24.09.2026") @Nullable String from,
			@Schema(description = "Стало, отформатировано для показа; null — пусто", example = "25.09.2026") @Nullable String to) {

		static AuditChangeResponse from(AuditEntry.Change change) {
			return new AuditChangeResponse(change.field(), change.label(), change.type(), change.from(), change.to());
		}

	}

}
