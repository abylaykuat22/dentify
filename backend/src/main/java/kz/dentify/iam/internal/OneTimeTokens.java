package kz.dentify.iam.internal;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import kz.dentify.platform.api.Hashes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Одноразовые токены ссылок (PLT-05): сброс пароля — 1 час, приглашение — 7 дней ({@link TokenPurpose}).
 * <ul>
 * <li>токен — 256 бит из {@link SecureRandom}, в base64url; в БД только его SHA-256, значение отдаётся один раз при
 * выдаче ({@link IssuedToken}) и не логируется;</li>
 * <li>одноразовый: {@link #consume} помечает токен использованным под блокировкой строки, повторное открытие ссылки —
 * {@link InvalidTokenException};</li>
 * <li>гаснет по сроку и при смене пароля: {@link #revokeAll} отзывает все действующие токены цели.</li>
 * </ul>
 * Смена пароля (iam) вызывает {@code revokeAll(PASSWORD_RESET, accountId)} вместе с
 * {@code AccountSessions.terminateOthers}.
 */
@Service
public class OneTimeTokens {

	/** 256 бит. */
	static final int TOKEN_BYTES = 32;

	private static final Logger log = LoggerFactory.getLogger(OneTimeTokens.class);

	private final SecureRandom random = new SecureRandom();

	private final OneTimeTokenRepository tokens;

	OneTimeTokens(OneTimeTokenRepository tokens) {
		this.tokens = tokens;
	}

	/** Выдать токен для ссылки: действует {@link TokenPurpose#ttl()} с этого момента. */
	@Transactional
	public IssuedToken issue(TokenPurpose purpose, UUID subjectId) {
		byte[] value = new byte[TOKEN_BYTES];
		this.random.nextBytes(value);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(value);
		Instant expiresAt = now().plus(purpose.ttl());
		this.tokens.save(new OneTimeToken(purpose, subjectId, hash(token), expiresAt));
		log.debug("Выдан токен {} для {}", purpose, subjectId);
		return new IssuedToken(token, expiresAt);
	}

	/**
	 * Цель действующего токена, не используя его: например, показать страницу приглашения до принятия.
	 *
	 * @return пусто, если токен не действует
	 */
	@Transactional(readOnly = true)
	public Optional<UUID> peek(TokenPurpose purpose, String token) {
		Instant now = now();
		return this.tokens.readByTokenHash(hash(token))
			.filter((stored) -> stored.getPurpose() == purpose && stored.isActive(now))
			.map(OneTimeToken::getSubjectId);
	}

	/**
	 * Использовать токен: он гаснет в той же транзакции, что и действие по ссылке.
	 *
	 * @return цель токена: аккаунт или приглашение
	 * @throws InvalidTokenException токен уже использован, истёк, отозван, другого назначения или не существует
	 */
	@Transactional
	public UUID consume(TokenPurpose purpose, String token) {
		Instant now = now();
		OneTimeToken stored = this.tokens.findByTokenHash(hash(token))
			.filter((candidate) -> candidate.getPurpose() == purpose && candidate.isActive(now))
			.orElseThrow(() -> {
				log.debug("Недействующий токен {}", purpose);
				return new InvalidTokenException();
			});
		stored.use(now);
		return stored.getSubjectId();
	}

	/**
	 * Отозвать все действующие токены цели: смена пароля гасит ссылки сброса.
	 *
	 * @return сколько токенов отозвано
	 */
	@Transactional
	public int revokeAll(TokenPurpose purpose, UUID subjectId) {
		Instant now = now();
		List<OneTimeToken> active = this.tokens.findBySubjectIdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(subjectId,
				purpose);
		active.forEach((token) -> token.revoke(now));
		log.debug("Отозвано токенов {} для {}: {}", purpose, subjectId, active.size());
		return active.size();
	}

	/** SHA-256 значения токена: так он хранится и ищется. */
	static byte[] hash(String token) {
		return Hashes.sha256(token);
	}

	/** До микросекунд — точность timestamptz. */
	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

}
