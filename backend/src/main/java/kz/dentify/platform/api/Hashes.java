package kz.dentify.platform.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * SHA-256 для отпечатков, которые хранятся вместо значения: токены ссылок в БД, логин, IP и id сессии в ключах Valkey,
 * секреты в разнице журнала. Алгоритм есть в каждой JDK, поэтому его отсутствие — {@link IllegalStateException}, а не
 * проверяемое исключение в каждом вызывающем.
 */
public final class Hashes {

	private Hashes() {
	}

	/** SHA-256 байтов. */
	public static byte[] sha256(byte[] bytes) {
		return sha256().digest(bytes);
	}

	/** SHA-256 строки в UTF-8. */
	public static byte[] sha256(String text) {
		return sha256(text.getBytes(StandardCharsets.UTF_8));
	}

	/** SHA-256 строки в UTF-8 — base64url без выравнивания: для ключей Valkey и открытых id. */
	public static String sha256Base64Url(String text) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(text));
	}

	/** Новый {@link MessageDigest} SHA-256 — хэш по частям ({@code update}, затем {@code digest}). */
	public static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 недоступен", ex);
		}
	}

}
