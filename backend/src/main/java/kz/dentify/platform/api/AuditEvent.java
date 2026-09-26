package kz.dentify.platform.api;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Событие журнала аудита для {@link AuditService#record}: что сделали с каким объектом и разница было и стало.
 *
 * <pre>
 * AppointmentSnapshot before = AppointmentSnapshot.of(appointment);   // до изменения
 * appointment.reschedule(date, time);
 * audit.record(AuditEvent.of(APPOINTMENT, AuditActions.RESCHEDULED)   // APPOINTMENT = schedule.appointment
 *     .entity(appointment.getId(), patientName)
 *     .change(before, AppointmentSnapshot.of(appointment)));
 * </pre>
 *
 * Кто, когда, клиника, сотрудник и IP берутся из контекста, их в событии нет.
 *
 * @param entityType тип объекта {@code <модуль>.<объект>}: {@code schedule.appointment}, {@code medical.card}
 * @param action действие из {@link AuditActions} или своё: строчные латинские буквы, цифры, дефис
 * @param entityId id объекта или null, например у выгрузки списка
 * @param entityLabel понятное имя объекта на момент события: ФИО пациента, номер документа. Длиннее
 * {@value #MAX_LABEL_LENGTH} символов обрезается
 * @param before снимок до изменения — неизменяемый record, обычно DTO; null — объекта не было (создание)
 * @param after снимок после изменения; null — объекта больше нет
 * @param args аргументы текста события после имени объекта и автора, то есть с {@code {2}}: строки и числа
 */
public record AuditEvent(String entityType, String action, @Nullable UUID entityId, @Nullable String entityLabel,
		@Nullable Record before, @Nullable Record after, List<Object> args) {

	public static final int MAX_LABEL_LENGTH = 500;

	/** Тип объекта: модуль и объект через точку. */
	public static final Pattern ENTITY_TYPE = Pattern.compile("[a-z]+\\.[a-z][a-z0-9-]*");

	public static final Pattern ACTION = Pattern.compile("[a-z][a-z0-9-]*");

	public AuditEvent {
		if (!ENTITY_TYPE.matcher(entityType).matches() || entityType.length() > 100) {
			throw new IllegalArgumentException("Тип объекта аудита — <модуль>.<объект>: " + entityType);
		}
		if (!ACTION.matcher(action).matches() || action.length() > 50) {
			throw new IllegalArgumentException("Действие аудита — строчные латинские буквы, цифры и дефис: " + action);
		}
		if (before != null && after != null && before.getClass() != after.getClass()) {
			throw new IllegalArgumentException("Снимки до и после — один record: " + before.getClass().getName()
					+ ", " + after.getClass().getName());
		}
		if (entityLabel != null && entityLabel.length() > MAX_LABEL_LENGTH) {
			entityLabel = entityLabel.substring(0, MAX_LABEL_LENGTH - 1) + "…";
		}
		args = List.copyOf(args);
		for (Object arg : args) {
			if (!(arg instanceof String) && !(arg instanceof Number)) {
				throw new IllegalArgumentException("Аргумент текста события — строка или число: " + arg.getClass());
			}
		}
	}

	/** Событие без объекта и разницы; объект и снимки добавляют {@link #entity} и {@link #change}. */
	public static AuditEvent of(String entityType, String action) {
		return new AuditEvent(entityType, action, null, null, null, null, List.of());
	}

	/** Объект события: id и понятное имя. */
	public AuditEvent entity(@Nullable UUID id, @Nullable String label) {
		return new AuditEvent(this.entityType, this.action, id, label, this.before, this.after, this.args);
	}

	/**
	 * Снимки до и после: разница по полям record, вложенные record — по своим полям ({@code name.ru}). Поля
	 * {@code id}, {@code version}, {@code createdAt/By}, {@code updatedAt/By} верхнего уровня в разницу не идут; пароли,
	 * токены, хэши и {@link AuditSecret} — только отметка, что изменились, без значений.
	 */
	public AuditEvent change(@Nullable Record before, @Nullable Record after) {
		return new AuditEvent(this.entityType, this.action, this.entityId, this.entityLabel, before, after, this.args);
	}

	/** Аргументы текста события с {@code {2}}. */
	public AuditEvent args(Object... args) {
		return new AuditEvent(this.entityType, this.action, this.entityId, this.entityLabel, this.before, this.after,
				Arrays.asList(args));
	}

}
