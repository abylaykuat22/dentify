package kz.dentify.platform.security;

import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

import org.springframework.session.MapSession;

/**
 * Срок сессии после входа (PLT-04), два предела:
 * <ul>
 * <li>бездействие: без Запомнить — 12 часов ({@code server.servlet.session.timeout}); с Запомнить отдельного предела
 * бездействия нет;</li>
 * <li>срок от входа, который активность не продлевает: с Запомнить — {@code dentify.security.remember-me}, 30 дней, как
 * у cookie; без Запомнить — {@code dentify.security.session-max-age}, тоже 30 дней.</li>
 * </ul>
 * Бездействие сессии в Valkey никогда не заходит за срок от входа ({@link #maxInactive}): ключ сессии истекает не позже
 * этого срока, и сессия уходит из индекса аккаунта сама.
 */
class SessionLifetime {

	private final Duration idle;

	private final Duration rememberMe;

	private final Duration maxAge;

	/**
	 * @param idle бездействие без Запомнить; null — значение Spring Session по умолчанию, 30 минут
	 * @param rememberMe срок сессии с Запомнить от входа
	 * @param maxAge срок сессии без Запомнить от входа
	 */
	SessionLifetime(@Nullable Duration idle, Duration rememberMe, Duration maxAge) {
		this.idle = (idle != null) ? idle : MapSession.DEFAULT_MAX_INACTIVE_INTERVAL;
		this.rememberMe = rememberMe;
		this.maxAge = maxAge;
	}

	/** Когда сессия гаснет, как бы активно ею ни пользовались. */
	Instant deadline(Instant signedInAt, boolean rememberMe) {
		return signedInAt.plus(rememberMe ? this.rememberMe : this.maxAge);
	}

	/** Самый долгий срок сессии от входа, с Запомнить или без: дольше не живёт ни одна сессия. */
	Duration longest() {
		return (this.rememberMe.compareTo(this.maxAge) >= 0) ? this.rememberMe : this.maxAge;
	}

	boolean expired(Instant signedInAt, boolean rememberMe, Instant now) {
		return !now.isBefore(deadline(signedInAt, rememberMe));
	}

	/**
	 * Сколько сессия может бездействовать, считая от {@code now}: предел бездействия, но не дальше срока от входа. С
	 * Запомнить — ровно до срока от входа.
	 */
	Duration maxInactive(Instant signedInAt, boolean rememberMe, Instant now) {
		Duration untilDeadline = Duration.between(now, deadline(signedInAt, rememberMe));
		if (untilDeadline.isNegative()) {
			return Duration.ZERO;
		}
		return (rememberMe || untilDeadline.compareTo(this.idle) < 0) ? untilDeadline : this.idle;
	}

}
