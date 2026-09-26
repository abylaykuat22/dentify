package kz.dentify.platform.api;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Снимки объекта одного типа для {@link Audited}: модуль объявляет бин на каждый тип объекта, который пишет в журнал
 * аннотацией. Снимок — неизменяемый record, обычно DTO объекта без служебных полей; по двум снимкам аудит считает
 * разницу было и стало.
 *
 * <pre>
 * &#64;Component
 * class AppointmentSnapshots implements AuditSnapshots&lt;AppointmentSnapshot&gt; {
 *     public String entityType() { return APPOINTMENT; }                            // schedule.appointment
 *     public Optional&lt;AppointmentSnapshot&gt; load(UUID id) {
 *         return this.appointments.findById(id).map(AppointmentSnapshot::of);
 *     }
 *     public String label(AppointmentSnapshot snapshot) { return snapshot.patientName(); }
 * }
 * </pre>
 *
 * @param <S> record снимка
 */
public interface AuditSnapshots<S extends Record> {

	/** Тип объекта {@code <модуль>.<объект>}: тот же, что в {@link Audited#entity()}. Один бин на тип. */
	String entityType();

	/**
	 * Снимок объекта в текущей транзакции или пусто, если его нет. Вызывается до метода и после него: после — по
	 * изменённой, ещё не сохранённой сущности. Значения копируются в record, коллекции — {@code List.copyOf}.
	 */
	Optional<S> load(UUID id);

	/** Понятное имя объекта в журнале: ФИО пациента, номер документа. */
	@Nullable String label(S snapshot);

}
