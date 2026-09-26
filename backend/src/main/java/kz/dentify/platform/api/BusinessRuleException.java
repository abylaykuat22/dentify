package kz.dentify.platform.api;

import java.util.List;

/**
 * 422: запрос корректен, но нарушает правило клиники: правка задним числом, минус на складе, запись в чужую смену.
 * Если правило про конкретное поле, нарушение можно передать в {@code errors[]}.
 */
public non-sealed class BusinessRuleException extends DentifyException {

	public BusinessRuleException(String code, Object... args) {
		super(code, args, List.of(), null);
	}

	public BusinessRuleException(String code, List<FieldViolation> violations, Object... args) {
		super(code, args, violations, null);
	}

}
