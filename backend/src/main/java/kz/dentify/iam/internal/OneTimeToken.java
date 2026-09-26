package kz.dentify.iam.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import kz.dentify.platform.api.GlobalEntity;
import org.jspecify.annotations.Nullable;

/**
 * Одноразовый токен ссылки: только SHA-256 значения, назначение, цель, срок и отметки использования и отзыва.
 * Глобальная таблица: ссылку открывают до входа, без клиники.
 */
@Entity
@Table(name = "one_time_token")
class OneTimeToken extends GlobalEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "purpose", nullable = false, updatable = false)
	private TokenPurpose purpose;

	@Column(name = "subject_id", nullable = false, updatable = false)
	private UUID subjectId;

	@Column(name = "token_hash", nullable = false, updatable = false)
	private byte[] tokenHash;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private @Nullable Instant usedAt;

	@Column(name = "revoked_at")
	private @Nullable Instant revokedAt;

	protected OneTimeToken() {
		this.purpose = TokenPurpose.PASSWORD_RESET;
		this.subjectId = new UUID(0, 0);
		this.tokenHash = new byte[0];
		this.expiresAt = Instant.EPOCH;
	}

	OneTimeToken(TokenPurpose purpose, UUID subjectId, byte[] tokenHash, Instant expiresAt) {
		this.purpose = purpose;
		this.subjectId = subjectId;
		this.tokenHash = tokenHash.clone();
		this.expiresAt = expiresAt;
	}

	UUID getSubjectId() {
		return this.subjectId;
	}

	TokenPurpose getPurpose() {
		return this.purpose;
	}

	Instant getExpiresAt() {
		return this.expiresAt;
	}

	/** Действует: не использован, не отозван и не истёк к моменту {@code now}. */
	boolean isActive(Instant now) {
		return this.usedAt == null && this.revokedAt == null && now.isBefore(this.expiresAt);
	}

	void use(Instant now) {
		this.usedAt = now;
	}

	void revoke(Instant now) {
		this.revokedAt = now;
	}

}
