package kz.dentify.platform.api;

import java.time.Duration;
import java.util.List;

/** 429: превышен лимит запросов. Через сколько повторить, уходит в заголовок {@code Retry-After}. */
public non-sealed class TooManyRequestsException extends DentifyException {

	private final Duration retryAfter;

	public TooManyRequestsException(Duration retryAfter) {
		this(retryAfter, ErrorCodes.TOO_MANY_REQUESTS);
	}

	public TooManyRequestsException(Duration retryAfter, String code, Object... args) {
		super(code, args, List.of(), null);
		this.retryAfter = retryAfter;
	}

	public Duration getRetryAfter() {
		return this.retryAfter;
	}

}
