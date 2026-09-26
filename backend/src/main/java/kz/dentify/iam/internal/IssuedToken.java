package kz.dentify.iam.internal;

import java.time.Instant;

/**
 * Выданный токен: его значение есть только здесь, в момент выдачи, — для ссылки в письме. В БД лежит только SHA-256,
 * поэтому значение не логируется и больше нигде не хранится.
 *
 * @param token 256 бит случайности в base64url без выравнивания, 43 символа
 * @param expiresAt до какого момента действует
 */
public record IssuedToken(String token, Instant expiresAt) {

	@Override
	public String toString() {
		return "IssuedToken[token=***, expiresAt=" + this.expiresAt + "]";
	}

}
