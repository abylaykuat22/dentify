package kz.dentify.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import kz.dentify.platform.api.ErrorCodes;
import kz.dentify.platform.api.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Предел одновременных хэшей пароля (PLT-05): Argon2id берёт 19 МиБ на вычисление, а виртуальные потоки не ограничивают
 * число одновременных запросов. Лишний хэш ждёт свободного места, а если не дождался — 429 с Retry-After. Хэширование в
 * тестах — заглушка, которая ждёт команды и считает одновременные вычисления. Пароли — вымышленные.
 */
class PasswordHashingLimitTests {

	/** Бин {@code PasswordEncoder} — с пределом, по умолчанию по числу ядер, ожидание — 3 секунды. */
	@Test
	void passwordEncoderBeanIsBounded() {
		SecurityProperties properties = new SecurityProperties();

		assertThat(new SecurityConfiguration().passwordEncoder(properties)).isInstanceOf(BoundedPasswordEncoder.class);
		assertThat(properties.getPasswordHashing().effectiveConcurrency())
			.isEqualTo(Runtime.getRuntime().availableProcessors());
		assertThat(properties.getPasswordHashing().getMaxWait()).isEqualTo(Duration.ofSeconds(3));
		properties.getPasswordHashing().setConcurrency(3);
		assertThat(properties.getPasswordHashing().effectiveConcurrency()).isEqualTo(3);
	}

	/** Предел 2: из трёх одновременных хэшей третий ждёт, пока освободится место, и затем считается. */
	@Test
	void extraHashWaitsForFreePlace() throws Exception {
		SlowHashing slow = new SlowHashing();
		PasswordEncoder encoder = new BoundedPasswordEncoder(slow, 2, Duration.ofSeconds(30));
		List<Future<String>> hashes = new ArrayList<>();

		try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
			for (String password : List.of("Кресло-у-окна-1", "Кресло-у-окна-2", "Кресло-у-окна-3")) {
				hashes.add(threads.submit(() -> encoder.encode(password)));
			}
			assertThat(slow.started.tryAcquire(2, 10, TimeUnit.SECONDS)).as("двое начали").isTrue();
			assertThat(slow.started.tryAcquire(1, 300, TimeUnit.MILLISECONDS)).as("третий ждёт").isFalse();

			slow.finish.countDown();
			for (Future<String> hash : hashes) {
				assertThat(hash.get(10, TimeUnit.SECONDS)).startsWith("{test}Кресло-у-окна-");
			}
		}

		assertThat(slow.maxRunning).as("одновременно не больше предела").hasValue(2);
	}

	/**
	 * Предел 2 и ожидание 200 мс: третья проверка пароля, пока идут две, ждёт и получает 429 {@code error.too-many-requests}
	 * с Retry-After 1 с; когда места освободились, проверки снова идут.
	 */
	@Test
	void extraHashIsRejectedAfterMaxWait() throws Exception {
		SlowHashing slow = new SlowHashing();
		PasswordEncoder encoder = new BoundedPasswordEncoder(slow, 2, Duration.ofMillis(200));

		try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
			Future<Boolean> first = threads.submit(() -> encoder.matches("Пломба-2019", "{test}Пломба-2019"));
			Future<Boolean> second = threads.submit(() -> encoder.matches("Пломба-2020", "{test}Пломба-2020"));
			assertThat(slow.started.tryAcquire(2, 10, TimeUnit.SECONDS)).isTrue();

			long started = System.nanoTime();
			TooManyRequestsException rejected = catchThrowableOfType(TooManyRequestsException.class,
					() -> encoder.matches("Пломба-2021", "{test}Пломба-2021"));

			assertThat(Duration.ofNanos(System.nanoTime() - started)).as("ждал места")
				.isGreaterThanOrEqualTo(Duration.ofMillis(150));
			assertThat(rejected).isNotNull();
			assertThat(rejected.getCode()).isEqualTo(ErrorCodes.TOO_MANY_REQUESTS);
			assertThat(rejected.getRetryAfter()).isEqualTo(Duration.ofSeconds(1));

			slow.finish.countDown();
			assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
			assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
		}

		assertThat(encoder.matches("Пломба-2021", "{test}Пломба-2021")).as("места освободились").isTrue();
		assertThat(slow.maxRunning).hasValue(2);
	}

	/** Ошибка хэширования (неизвестный формат хэша) освобождает место: предел не «протекает». */
	@Test
	void failedHashFreesItsPlace() {
		PasswordEncoder broken = new PasswordEncoder() {

			@Override
			public String encode(CharSequence rawPassword) {
				throw new IllegalArgumentException("неизвестный формат");
			}

			@Override
			public boolean matches(CharSequence rawPassword, String encodedPassword) {
				throw new IllegalArgumentException("неизвестный формат");
			}

		};
		PasswordEncoder encoder = new BoundedPasswordEncoder(broken, 1, Duration.ZERO);

		for (int i = 0; i < 3; i++) {
			assertThatIllegalArgumentException().isThrownBy(() -> encoder.matches("Пломба-2019", "{md4}…"));
			assertThatIllegalArgumentException().isThrownBy(() -> encoder.encode("Пломба-2019"));
		}
	}

	/** Хэширование, которое ждёт команды {@link #finish}: считает начатые и одновременные вычисления. */
	static final class SlowHashing implements PasswordEncoder {

		final Semaphore started = new Semaphore(0);

		final CountDownLatch finish = new CountDownLatch(1);

		final AtomicInteger running = new AtomicInteger();

		final AtomicInteger maxRunning = new AtomicInteger();

		@Override
		public String encode(CharSequence rawPassword) {
			hash();
			return "{test}" + rawPassword;
		}

		@Override
		public boolean matches(CharSequence rawPassword, String encodedPassword) {
			hash();
			return encodedPassword.equals("{test}" + rawPassword);
		}

		private void hash() {
			this.maxRunning.accumulateAndGet(this.running.incrementAndGet(), Math::max);
			this.started.release();
			try {
				this.finish.await(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			finally {
				this.running.decrementAndGet();
			}
		}

	}

}
