package kz.dentify.platform.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Событие журнала аудита на метод сервиса (PLT-09): снимок объекта до метода и после, разница, запись в той же
 * транзакции. Снимки даёт бин {@link AuditSnapshots} этого типа объекта.
 *
 * <pre>
 * &#64;Transactional
 * &#64;Audited(entity = APPOINTMENT, action = AuditActions.RESCHEDULED)             // id по умолчанию — аргумент id
 * Appointment reschedule(UUID id, LocalDate date, LocalTime time, long version) { … }
 *
 * &#64;Transactional
 * &#64;Audited(entity = APPOINTMENT, action = AuditActions.CREATED, id = RESULT_ID) // id из результата
 * Appointment create(AppointmentRequest request) { … }
 * </pre>
 *
 * Правила:
 * <ul>
 * <li>метод в транзакции записи: {@code @Transactional} с распространением REQUIRED по умолчанию, не readOnly. Иначе —
 * {@link IllegalStateException} при вызове. Метод, бросивший исключение, откатывает и событие;</li>
 * <li>{@link #id()} — выражение SpEL по аргументам, например {@code #id} или {@code #request.patientId}; если в нём
 * есть {@code #result}, оно считается после метода по результату, и снимка до нет — это создание. Переменная выражения
 * — имя параметра метода ({@code #appointmentId} для {@code reschedule(UUID appointmentId, …)}: {@code #id} по
 * умолчанию там не подойдёт), {@code #a0} или {@code #result}; другая — {@link IllegalStateException} при первом
 * вызове. id, вычисленный в null, — тоже {@link IllegalStateException}: для необязательного id — запись вручную;</li>
 * <li>изменение без разницы, когда оба снимка есть и равны, не пишется;</li>
 * <li>объект меняется только через управляемую сущность JPA. Снимок после читается в той же сессии Hibernate, и
 * массовый JPQL {@code UPDATE} ({@code @Modifying}) или JDBC мимо неё он не увидит: снимки окажутся равны, и события
 * не будет. Такие изменения пишут в журнал вручную — {@link AuditService#record};</li>
 * <li>метод вызывается через прокси Spring: вызов из того же класса аннотацию не видит.</li>
 * </ul>
 * Событие без снимков или со своими снимками — {@link AuditService#record}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

	/** Выражение id по результату метода: результат с методом {@code getId} или компонентом {@code id}. */
	String RESULT_ID = "#result.id";

	/** Тип объекта {@code <модуль>.<объект>}; по нему находится {@link AuditSnapshots}. */
	String entity();

	/** Действие: {@link AuditActions} или своё. */
	String action();

	/** SpEL id объекта: по аргументам метода или по результату ({@link #RESULT_ID}). */
	String id() default "#id";

}
