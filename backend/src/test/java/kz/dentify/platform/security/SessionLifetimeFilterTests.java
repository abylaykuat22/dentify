package kz.dentify.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/**
 * Фильтр срока сессии от входа без приложения: бездействие сессии пишется не на каждом запросе, а раз в минуту с
 * Запомнить и только в последние 12 часов без него, и ключ сессии в Valkey всё равно не переживает срок от входа.
 */
class SessionLifetimeFilterTests {

	static final Instant SIGNED_IN = Instant.parse("2026-09-01T08:00:00Z");

	static final Duration IDLE = Duration.ofHours(12);

	static final Duration MAX_AGE = Duration.ofDays(30);

	final SessionLifetime lifetime = new SessionLifetime(IDLE, MAX_AGE, MAX_AGE);

	final MutableClock clock = new MutableClock(SIGNED_IN);

	final SessionLifetimeFilter filter = new SessionLifetimeFilter(this.lifetime, this.clock);

	/** С Запомнить остаток до срока убывает каждую секунду, а бездействие пишется не чаще раза в минуту. */
	@Test
	void rememberMeWritesIdleAtMostOncePerMinute() throws Exception {
		CountingSession session = signedIn(true);

		for (int second = 1; second <= 600; second++) {
			this.clock.set(SIGNED_IN.plusSeconds(second));
			request(session);
			assertNotBeyondDeadline(session, true);
		}

		assertThat(session.writes).isBetween(9, 11);
	}

	/**
	 * Без Запомнить бездействие — 12 часов, пока до срока от входа дальше: запросы каждые 10 минут почти 30 дней его не
	 * переписывают. В последние 12 часов бездействие подрезается до срока — раз в минуту при запросах каждую секунду.
	 */
	@Test
	void withoutRememberMeIdleIsRewrittenOnlyInLastTwelveHours() throws Exception {
		CountingSession session = signedIn(false);
		Instant lastTwelveHours = SIGNED_IN.plus(MAX_AGE).minus(IDLE);

		for (Instant now = SIGNED_IN; now.isBefore(lastTwelveHours); now = now.plus(Duration.ofMinutes(10))) {
			this.clock.set(now);
			request(session);
		}
		assertThat(session.writes).isZero();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(IDLE.toSeconds());

		for (int second = 1; second <= 600; second++) {
			this.clock.set(lastTwelveHours.plusSeconds(second));
			request(session);
			assertNotBeyondDeadline(session, false);
		}
		assertThat(session.writes).isBetween(9, 11);
	}

	/** В последнюю минуту бездействие — ровно остаток, без запаса: сессия не гаснет раньше срока. */
	@Test
	void lastMinuteIdleIsExactRemainder() throws Exception {
		CountingSession session = signedIn(true);
		this.clock.set(SIGNED_IN.plus(MAX_AGE).minusSeconds(30));

		request(session);

		assertThat(session.getMaxInactiveInterval()).isEqualTo(30);
	}

	@Test
	void pastDeadlineSessionIsInvalidated() throws Exception {
		CountingSession session = signedIn(true);
		this.clock.set(SIGNED_IN.plus(MAX_AGE));

		MockHttpServletRequest request = request(session);

		assertThat(session.isInvalid()).isTrue();
		assertThat(request.getAttribute(ProblemSecurityHandler.SESSION_ENDED_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
	}

	@Test
	void nextMaxInactiveKeepsStoredInsideWindow() {
		assertThat(SessionLifetimeFilter.nextMaxInactive(1000, 1000)).isEqualTo(1000);
		assertThat(SessionLifetimeFilter.nextMaxInactive(880, 1000)).isEqualTo(880);
		assertThat(SessionLifetimeFilter.nextMaxInactive(879, 1000)).as("короче нужного больше двух минут")
			.isEqualTo(940);
		assertThat(SessionLifetimeFilter.nextMaxInactive(1001, 1000)).as("ключ пережил бы срок").isEqualTo(940);
		assertThat(SessionLifetimeFilter.nextMaxInactive(45, 40)).isEqualTo(40);
	}

	private CountingSession signedIn(boolean rememberMe) {
		CountingSession session = new CountingSession();
		session.setAttribute(SessionClients.SIGNED_IN_AT, SIGNED_IN);
		session.setAttribute(SessionClients.REMEMBER_ME, rememberMe);
		session.setMaxInactiveInterval(
				SessionLifetimeFilter.seconds(this.lifetime.maxInactive(SIGNED_IN, rememberMe, SIGNED_IN)));
		session.writes = 0;
		return session;
	}

	private MockHttpServletRequest request(MockHttpSession session) throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/account/sessions");
		request.setSession(session);
		this.filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
		return request;
	}

	/** Ключ сессии после этого запроса истечёт не позже срока от входа: последний запрос сейчас. */
	private void assertNotBeyondDeadline(MockHttpSession session, boolean rememberMe) {
		Instant keyExpires = this.clock.instant().plusSeconds(session.getMaxInactiveInterval());
		assertThat(keyExpires).isBeforeOrEqualTo(this.lifetime.deadline(SIGNED_IN, rememberMe));
	}

	/** Сессия, которая считает записи бездействия: каждая — изменение, которое Spring Session пишет в Valkey. */
	static final class CountingSession extends MockHttpSession {

		int writes;

		@Override
		public void setMaxInactiveInterval(int interval) {
			super.setMaxInactiveInterval(interval);
			this.writes++;
		}

	}

	static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void set(Instant now) {
			this.now = now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.now;
		}

	}

}
