package kz.dentify.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import kz.dentify.platform.ProblemProbeController.PeriodRequest;

/**
 * Пробное ограничение класса только для тестов: конец периода не раньше начала. Ошибка относится ко всей форме, а
 * не к полю, и в {@code errors[]} у неё {@code field: null}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ProbePeriod.Validator.class)
@interface ProbePeriod {

	String message() default "{probe.period.order}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<ProbePeriod, PeriodRequest> {

		@Override
		public boolean isValid(PeriodRequest value, ConstraintValidatorContext context) {
			return value.from() == null || value.to() == null || !value.to().isBefore(value.from());
		}

	}

}
