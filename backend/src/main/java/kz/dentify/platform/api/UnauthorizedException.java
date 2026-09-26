package kz.dentify.platform.api;

import java.util.List;

/** 401: нет сессии или она истекла. */
public non-sealed class UnauthorizedException extends DentifyException {

	public UnauthorizedException() {
		this(ErrorCodes.UNAUTHORIZED);
	}

	public UnauthorizedException(String code, Object... args) {
		super(code, args, List.of(), null);
	}

}
