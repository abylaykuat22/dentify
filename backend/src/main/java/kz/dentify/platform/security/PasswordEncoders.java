package kz.dentify.platform.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * Параметры Argon2id по рекомендации OWASP Password Storage Cheat Sheet (минимальная конфигурация): память 19 МиБ,
 * 2 прохода, 1 поток. Соль 16 байт и хэш 32 байта — как у Spring Security по умолчанию. Хэш одного пароля — десятки
 * миллисекунд и 19 МиБ памяти.
 */
final class PasswordEncoders {

	/** Id алгоритма в префиксе хэша DelegatingPasswordEncoder: {@code {argon2}…}. */
	static final String ARGON2 = "argon2";

	static final int SALT_LENGTH = 16;

	static final int HASH_LENGTH = 32;

	static final int PARALLELISM = 1;

	/** Память в КиБ: 19 МиБ. */
	static final int MEMORY_KIB = 19 * 1024;

	static final int ITERATIONS = 2;

	private PasswordEncoders() {
	}

	static Argon2PasswordEncoder argon2id() {
		return new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB, ITERATIONS);
	}

}
