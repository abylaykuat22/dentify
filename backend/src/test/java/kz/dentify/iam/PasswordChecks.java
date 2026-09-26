package kz.dentify.iam;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import kz.dentify.platform.api.TooManyRequestsException;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Только для тестов: проверки пароля бином {@link PasswordEncoder}. Оборачивает бин и для паролей, которые тест
 * отметил, считает вызовы {@code matches} — так тест видит, что пароль неизвестного логина проверен хэшем-пустышкой, не
 * полагаясь на замер времени, — или отвечает на них 429, как предел одновременных хэшей ({@code BoundedPasswordEncoder}),
 * не считая хэш. Счёт — по самому паролю, а не по потоку: запросы теста обслуживает сервер в своих потоках, и тесты
 * с разными паролями друг другу не мешают. Остальные пароли — просто вызов настоящего бина. Бин — в тестовых исходниках
 * для всех тестов, без {@code @MockitoBean}: контекст один.
 */
@Component
class PasswordChecks implements BeanPostProcessor {

	private static final Map<String, AtomicInteger> CHECKS = new ConcurrentHashMap<>();

	private static final Set<String> BUSY = ConcurrentHashMap.newKeySet();

	/** Для паролей с {@link #count}: шла ли транзакция БД, когда считался хэш ({@code encode}). */
	private static final Map<String, Boolean> ENCODED_IN_TRANSACTION = new ConcurrentHashMap<>();

	/** Считать проверки этого пароля с нуля. */
	static void count(String password) {
		CHECKS.put(password, new AtomicInteger());
	}

	/** Сколько раз пароль проверен с {@link #count}. */
	static int checks(String password) {
		AtomicInteger checks = CHECKS.get(password);
		return (checks != null) ? checks.get() : 0;
	}

	/**
	 * Шла ли транзакция БД в потоке, когда хэшировали пароль, отмеченный {@link #count}; null — не хэшировали. Хэш
	 * Argon2id вне транзакции: иначе она держала бы соединение пула всё время хэша и ожидания места.
	 */
	static @Nullable Boolean encodedInTransaction(String password) {
		return ENCODED_IN_TRANSACTION.get(password);
	}

	/** Отвечать на проверку этого пароля 429 {@code error.too-many-requests}: места для хэша нет. */
	static void busy(String password) {
		BUSY.add(password);
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof PasswordEncoder encoder && !(bean instanceof Watched)) {
			return new Watched(encoder);
		}
		return bean;
	}

	private record Watched(PasswordEncoder delegate) implements PasswordEncoder {

		@Override
		public @Nullable String encode(@Nullable CharSequence rawPassword) {
			if (rawPassword != null && CHECKS.containsKey(rawPassword.toString())) {
				ENCODED_IN_TRANSACTION.put(rawPassword.toString(),
						TransactionSynchronizationManager.isActualTransactionActive());
			}
			return this.delegate.encode(rawPassword);
		}

		@Override
		public boolean matches(@Nullable CharSequence rawPassword, @Nullable String encodedPassword) {
			if (rawPassword != null) {
				String password = rawPassword.toString();
				if (BUSY.contains(password)) {
					throw new TooManyRequestsException(Duration.ofSeconds(1));
				}
				AtomicInteger checks = CHECKS.get(password);
				if (checks != null) {
					checks.incrementAndGet();
				}
			}
			return this.delegate.matches(rawPassword, encodedPassword);
		}

		@Override
		public boolean upgradeEncoding(@Nullable String encodedPassword) {
			return this.delegate.upgradeEncoding(encodedPassword);
		}

	}

}
