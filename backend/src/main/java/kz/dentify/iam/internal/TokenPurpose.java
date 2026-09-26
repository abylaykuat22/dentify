package kz.dentify.iam.internal;

import java.time.Duration;

/**
 * Назначение одноразового токена и его срок (PLT-05). Токен одного назначения не подходит для другого.
 */
public enum TokenPurpose {

	/** Ссылка сброса пароля, цель — аккаунт; действует 1 час. */
	PASSWORD_RESET(Duration.ofHours(1)),

	/** Ссылка приглашения в клинику, цель — приглашение; действует 7 дней. */
	INVITATION(Duration.ofDays(7));

	private final Duration ttl;

	TokenPurpose(Duration ttl) {
		this.ttl = ttl;
	}

	/** Сколько действует токен с момента выдачи. */
	public Duration ttl() {
		return this.ttl;
	}

}
