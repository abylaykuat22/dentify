package kz.dentify.platform.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Событие журнала действий для показа ({@link AuditJournal}): тексты уже на языке запроса, значения разницы
 * отформатированы, ИИН, телефон и e-mail без права {@code patient:phones} — маской.
 *
 * @param id id события
 * @param at когда
 * @param accountId кто: аккаунт, null — система
 * @param staffId кто: сотрудник или null
 * @param actorName имя сотрудника в клинике, для системы и неизвестного аккаунта — общий текст
 * @param ip IP клиента маской или null вне HTTP-запроса
 * @param module модуль объекта
 * @param entityType тип объекта
 * @param entityId id объекта или null
 * @param entityLabel понятное имя объекта на момент события
 * @param action действие
 * @param text текст события
 * @param changes разница было и стало по полям
 */
public record AuditEntry(UUID id, Instant at, @Nullable UUID accountId, @Nullable UUID staffId, String actorName,
		@Nullable String ip, String module, String entityType, @Nullable UUID entityId, @Nullable String entityLabel,
		String action, String text, List<Change> changes) {

	public AuditEntry {
		changes = List.copyOf(changes);
	}

	/**
	 * Поле в разнице.
	 *
	 * @param field путь поля в снимке: {@code date}, {@code name.ru}
	 * @param label название поля на языке запроса
	 * @param type вид значения: text, number, boolean, date, time, datetime, instant, enum, list, json; secret —
	 * секрет, значений нет
	 * @param from было или null
	 * @param to стало или null
	 */
	public record Change(String field, String label, String type, @Nullable String from, @Nullable String to) {

		public static final String SECRET = "secret";

	}

}
