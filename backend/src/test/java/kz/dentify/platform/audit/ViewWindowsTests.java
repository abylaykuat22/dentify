package kz.dentify.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Окно просмотров без Valkey (PLT-09): просмотр пишется без проверки дублей — полнота журнала важнее, — а снимать
 * нечего. Окно с Valkey — в {@link AuditLogTests}.
 */
class ViewWindowsTests {

	static final Duration WINDOW = Duration.ofMinutes(15);

	final StringRedisTemplate redis = mock();

	@SuppressWarnings("unchecked")
	final ValueOperations<String, String> values = mock(ValueOperations.class);

	final ViewWindows windows = new ViewWindows(this.redis, WINDOW);

	@Test
	void unavailableValkeyLetsTheViewBeRecorded() {
		given(this.redis.opsForValue()).willReturn(this.values);
		given(this.values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
			.willThrow(new RedisConnectionFailureException("Valkey недоступен"));

		String window = this.windows.claim(UUID.randomUUID(), UUID.randomUUID(), "medical.card", UUID.randomUUID());

		assertThat(window).isEqualTo(ViewWindows.UNAVAILABLE);
		this.windows.release(window);
		verify(this.redis, never()).delete(anyString());
	}

	@Test
	void timeoutIsTreatedAsUnavailable() {
		given(this.redis.opsForValue()).willReturn(this.values);
		given(this.values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
			.willThrow(new QueryTimeoutException("Valkey не ответил за spring.data.redis.timeout"));

		assertThat(this.windows.claim(UUID.randomUUID(), null, "medical.card", UUID.randomUUID()))
			.isEqualTo(ViewWindows.UNAVAILABLE);
	}

	@Test
	void claimedWindowIsReleasedAndReleaseFailureIsNotAnError() {
		UUID clinic = UUID.randomUUID();
		UUID account = UUID.randomUUID();
		UUID card = UUID.randomUUID();
		String key = ViewWindows.key(clinic, account, "medical.card", card);
		given(this.redis.opsForValue()).willReturn(this.values);
		given(this.values.setIfAbsent(eq(key), anyString(), eq(WINDOW))).willReturn(true, false);

		assertThat(this.windows.claim(clinic, account, "medical.card", card)).isEqualTo(key);
		assertThat(this.windows.claim(clinic, account, "medical.card", card)).as("окно занято").isNull();

		given(this.redis.delete(key)).willThrow(new RedisConnectionFailureException("Valkey недоступен"));
		assertThatNoException().isThrownBy(() -> this.windows.release(key));
		verify(this.redis).delete(key);
	}

}
