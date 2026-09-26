package kz.dentify.platform.api;

import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;

/**
 * Нарушение ограничения БД в исключении доступа к данным. Уникальность сервис проверяет сам и отвечает своим кодом с
 * полем, а ограничение в БД — страховка на гонку: одновременные запросы проходят проверку раньше, чем любой запишет.
 * Сработавшее ограничение сервис переводит в тот же ответ по имени:
 *
 * <pre>
 * catch (DataIntegrityViolationException ex) {
 *     if (DataIntegrity.isViolation(ex, PHONE_INDEX)) {
 *         throw ConflictException.ofFields(PHONE_TAKEN, List.of(FieldViolation.of(phone, PHONE_TAKEN)), ex);
 *     }
 *     throw ex;
 * }
 * </pre>
 */
public final class DataIntegrity {

	private DataIntegrity() {
	}

	/** Имя нарушенного ограничения из исключения Hibernate в цепочке причин или null. */
	public static @Nullable String constraintName(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return violation.getConstraintName();
			}
		}
		return null;
	}

	/** Нарушено ли именно это ограничение; имя — без учёта регистра, как его отдаёт PostgreSQL. */
	public static boolean isViolation(Throwable ex, String constraint) {
		return constraint.equalsIgnoreCase(constraintName(ex));
	}

}
