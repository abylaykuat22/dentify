package kz.dentify.iam.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import kz.dentify.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Журнал входов пишется в своей транзакции: задачи смены и сброса пароля и приглашений (T-IAM-13, 16, 24) вызывают его
 * внутри своей транзакции, и её откат не должен снимать событие неудачной попытки.
 */
class AuthEventsTests extends AbstractIntegrationTest {

	@Autowired
	AuthEvents events;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void eventSurvivesRollbackOfCallerTransaction() {
		String userAgent = "AuthEventsTests/" + UUID.randomUUID();
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("198.51.100.23");
		request.addHeader(HttpHeaders.USER_AGENT, userAgent);

		assertThatThrownBy(() -> new TransactionTemplate(this.transactionManager).executeWithoutResult((status) -> {
			this.events.record(AuthEventKind.WRONG_PASSWORD, null, request);
			throw new IllegalStateException("откат транзакции вызывающего");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(this.jdbc.queryForObject(
				"select count(*) from auth_event where user_agent = ? and kind = 'WRONG_PASSWORD' and ip = '198.51.100.23'",
				Integer.class, userAgent))
			.isEqualTo(1);
	}

}
