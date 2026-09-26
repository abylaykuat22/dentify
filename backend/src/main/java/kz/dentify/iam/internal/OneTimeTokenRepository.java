package kz.dentify.iam.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {

	/** Токен по хэшу с блокировкой строки: из двух одновременных использований одной ссылки проходит одно. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<OneTimeToken> findByTokenHash(byte[] tokenHash);

	/** Токен по хэшу без блокировки: проверить ссылку, не используя её. */
	Optional<OneTimeToken> readByTokenHash(byte[] tokenHash);

	List<OneTimeToken> findBySubjectIdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(UUID subjectId, TokenPurpose purpose);

}
