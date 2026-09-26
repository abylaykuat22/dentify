package kz.dentify.iam.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kz.dentify.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Одноразовые токены ссылок (PLT-05): 256 бит случайности, в БД только SHA-256, срок по назначению, одноразовые,
 * гаснут при смене пароля. Повторное открытие ссылки — {@code iam.token.invalid}: интерфейс покажет «Ссылка устарела».
 */
class OneTimeTokensTests extends AbstractIntegrationTest {

	@Autowired
	OneTimeTokens tokens;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void tokenHas256BitsAndLivesByPurpose() {
		Instant before = Instant.now();
		IssuedToken reset = this.tokens.issue(TokenPurpose.PASSWORD_RESET, UUID.randomUUID());
		IssuedToken invitation = this.tokens.issue(TokenPurpose.INVITATION, UUID.randomUUID());

		assertThat(reset.token()).matches("[A-Za-z0-9_-]{43}");
		assertThat(Base64.getUrlDecoder().decode(reset.token())).hasSize(32);
		assertThat(reset.token()).isNotEqualTo(invitation.token());
		assertThat(Duration.between(before, reset.expiresAt())).isBetween(Duration.ofHours(1).minusSeconds(1),
				Duration.ofHours(1).plusMinutes(1));
		assertThat(Duration.between(before, invitation.expiresAt())).isBetween(Duration.ofDays(7).minusSeconds(1),
				Duration.ofDays(7).plusMinutes(1));
		assertThat(reset.toString()).doesNotContain(reset.token());
	}

	/** Приёмка PLT-05: в БД нет токена в открытом виде, только его SHA-256. */
	@Test
	void databaseStoresOnlySha256OfToken() {
		UUID account = UUID.randomUUID();
		IssuedToken issued = this.tokens.issue(TokenPurpose.PASSWORD_RESET, account);

		Map<String, Object> row = this.jdbc.queryForMap("select * from one_time_token where subject_id = ?", account);
		byte[] hash = (byte[]) row.get("token_hash");

		assertThat(hash).isEqualTo(OneTimeTokens.hash(issued.token()));
		assertThat(HexFormat.of().formatHex(hash)).hasSize(64);
		assertThat(row.values()).noneMatch((value) -> String.valueOf(value).contains(issued.token()));
		String everything = this.jdbc.queryForObject(
				"select string_agg(t::text, ',') from one_time_token t where subject_id = ?", String.class, account);
		assertThat(everything).doesNotContain(issued.token())
			.doesNotContain(HexFormat.of().formatHex(Base64.getUrlDecoder().decode(issued.token())));
	}

	/** Приёмка PLT-05: повторное открытие ссылки — «Ссылка устарела». */
	@Test
	void tokenWorksOnlyOnce() {
		UUID account = UUID.randomUUID();
		IssuedToken issued = this.tokens.issue(TokenPurpose.PASSWORD_RESET, account);

		assertThat(this.tokens.peek(TokenPurpose.PASSWORD_RESET, issued.token())).contains(account);
		assertThat(this.tokens.consume(TokenPurpose.PASSWORD_RESET, issued.token())).isEqualTo(account);

		assertThatExceptionOfType(InvalidTokenException.class)
			.isThrownBy(() -> this.tokens.consume(TokenPurpose.PASSWORD_RESET, issued.token()))
			.satisfies((ex) -> assertThat(ex.getCode()).isEqualTo("iam.token.invalid"));
		assertThat(this.tokens.peek(TokenPurpose.PASSWORD_RESET, issued.token())).isEmpty();
	}

	@Test
	void expiredTokenIsInvalid() {
		UUID invitation = UUID.randomUUID();
		IssuedToken issued = this.tokens.issue(TokenPurpose.INVITATION, invitation);
		this.jdbc.update("update one_time_token set expires_at = now() - interval '1 second' where subject_id = ?",
				invitation);

		assertThat(this.tokens.peek(TokenPurpose.INVITATION, issued.token())).isEmpty();
		assertThatExceptionOfType(InvalidTokenException.class)
			.isThrownBy(() -> this.tokens.consume(TokenPurpose.INVITATION, issued.token()));
	}

	/** Смена пароля гасит все ссылки сброса аккаунта, приглашения не трогает. */
	@Test
	void passwordChangeRevokesResetTokens() {
		UUID account = UUID.randomUUID();
		IssuedToken first = this.tokens.issue(TokenPurpose.PASSWORD_RESET, account);
		IssuedToken second = this.tokens.issue(TokenPurpose.PASSWORD_RESET, account);
		IssuedToken used = this.tokens.issue(TokenPurpose.PASSWORD_RESET, account);
		this.tokens.consume(TokenPurpose.PASSWORD_RESET, used.token());
		IssuedToken invitation = this.tokens.issue(TokenPurpose.INVITATION, account);

		assertThat(this.tokens.revokeAll(TokenPurpose.PASSWORD_RESET, account)).isEqualTo(2);

		for (IssuedToken revoked : List.of(first, second)) {
			assertThatExceptionOfType(InvalidTokenException.class)
				.isThrownBy(() -> this.tokens.consume(TokenPurpose.PASSWORD_RESET, revoked.token()));
		}
		assertThat(this.tokens.peek(TokenPurpose.INVITATION, invitation.token())).contains(account);
	}

	@Test
	void tokenOfOtherPurposeOrUnknownIsInvalid() {
		IssuedToken invitation = this.tokens.issue(TokenPurpose.INVITATION, UUID.randomUUID());

		assertThatExceptionOfType(InvalidTokenException.class)
			.isThrownBy(() -> this.tokens.consume(TokenPurpose.PASSWORD_RESET, invitation.token()));
		assertThatExceptionOfType(InvalidTokenException.class)
			.isThrownBy(() -> this.tokens.consume(TokenPurpose.PASSWORD_RESET, "выдуманная-ссылка"));
		assertThat(this.tokens.peek(TokenPurpose.INVITATION, invitation.token())).as("не израсходован").isPresent();
	}

	/** Из одновременных открытий одной ссылки срабатывает ровно одно: строка токена блокируется. */
	@Test
	void concurrentConsumeSucceedsOnce() throws Exception {
		UUID account = UUID.randomUUID();
		IssuedToken issued = this.tokens.issue(TokenPurpose.PASSWORD_RESET, account);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();

		try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < 6; i++) {
				results.add(pool.submit(() -> {
					start.await();
					try {
						this.tokens.consume(TokenPurpose.PASSWORD_RESET, issued.token());
						return true;
					}
					catch (InvalidTokenException ex) {
						return false;
					}
				}));
			}
			start.countDown();
		}

		int succeeded = 0;
		for (Future<Boolean> result : results) {
			succeeded += result.get() ? 1 : 0;
		}
		assertThat(succeeded).isEqualTo(1);
	}

}
