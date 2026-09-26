package kz.dentify.iam.internal;

import kz.dentify.platform.api.BusinessRuleException;

/**
 * 422 {@value #CODE}: ссылка не действует — её токен уже использован, истёк, погашен сменой пароля, выдан для другого
 * назначения или выдуман. Причину ответ не различает: интерфейс показывает «Ссылка устарела» и предлагает запросить
 * новую.
 */
public class InvalidTokenException extends BusinessRuleException {

	public static final String CODE = "iam.token.invalid";

	public InvalidTokenException() {
		super(CODE);
	}

}
