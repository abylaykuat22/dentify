package kz.dentify.platform.api;

import java.util.List;

/**
 * 403: пользователь вошёл, но права на действие нет. Объект чужой клиники — не 403, а {@link NotFoundException}:
 * ответ не должен выдавать, что такой объект существует.
 */
public non-sealed class ForbiddenException extends DentifyException {

	public ForbiddenException() {
		this(ErrorCodes.FORBIDDEN);
	}

	public ForbiddenException(String code, Object... args) {
		super(code, args, List.of(), null);
	}

}
