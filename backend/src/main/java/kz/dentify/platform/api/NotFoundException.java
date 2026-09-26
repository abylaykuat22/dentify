package kz.dentify.platform.api;

import java.util.List;

/** 404: объекта нет, он в архиве или принадлежит другой клинике. */
public non-sealed class NotFoundException extends DentifyException {

	public NotFoundException() {
		this(ErrorCodes.NOT_FOUND);
	}

	public NotFoundException(String code, Object... args) {
		super(code, args, List.of(), null);
	}

}
