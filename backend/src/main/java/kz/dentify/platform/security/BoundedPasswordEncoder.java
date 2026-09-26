package kz.dentify.platform.security;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import kz.dentify.platform.api.TooManyRequestsException;
import org.jspecify.annotations.Nullable;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.Assert;

/**
 * Хэширование паролей с пределом одновременных вычислений. Argon2id с параметрами OWASP берёт 19 МиБ памяти на каждое
 * вычисление, а с виртуальными потоками Tomcat не ограничивает число одновременных запросов пулом потоков: без предела
 * тысяча одновременных входов, сбросов и смен пароля заняла бы 19 ГиБ. Одновременно считается не больше
 * {@code concurrency} хэшей, остальные ждут в честной очереди не дольше {@code maxWait}; не дождались — 429
 * {@code error.too-many-requests} с Retry-After. {@code upgradeEncoding} хэш не считает, только разбирает префикс, и
 * предела не ждёт.
 * <p>
 * Не {@code @ConcurrencyLimit} Spring Framework 7: у него два режима, ждать без предела времени (BLOCK) или отказывать
 * сразу (REJECT), а здесь нужно ждать ограниченное время.
 */
final class BoundedPasswordEncoder implements PasswordEncoder {

	/** Через сколько повторить, если место так и не освободилось: хэш — десятки миллисекунд, очередь уходит быстро. */
	static final Duration RETRY_AFTER = Duration.ofSeconds(1);

	private final PasswordEncoder delegate;

	private final Semaphore permits;

	private final long maxWaitNanos;

	BoundedPasswordEncoder(PasswordEncoder delegate, int concurrency, Duration maxWait) {
		Assert.isTrue(concurrency > 0, "Число одновременных хэшей пароля должно быть больше нуля");
		Assert.isTrue(!maxWait.isNegative(), "Ожидание места для хэша пароля не может быть отрицательным");
		this.delegate = delegate;
		this.permits = new Semaphore(concurrency, true);
		this.maxWaitNanos = maxWait.toNanos();
	}

	@Override
	public @Nullable String encode(@Nullable CharSequence rawPassword) {
		acquire();
		try {
			return this.delegate.encode(rawPassword);
		}
		finally {
			this.permits.release();
		}
	}

	@Override
	public boolean matches(@Nullable CharSequence rawPassword, @Nullable String encodedPassword) {
		acquire();
		try {
			return this.delegate.matches(rawPassword, encodedPassword);
		}
		finally {
			this.permits.release();
		}
	}

	@Override
	public boolean upgradeEncoding(@Nullable String encodedPassword) {
		return this.delegate.upgradeEncoding(encodedPassword);
	}

	private void acquire() {
		boolean acquired;
		try {
			acquired = this.permits.tryAcquire(this.maxWaitNanos, TimeUnit.NANOSECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			acquired = false;
		}
		if (!acquired) {
			throw new TooManyRequestsException(RETRY_AFTER);
		}
	}

}
