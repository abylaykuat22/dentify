package kz.dentify.platform;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/** Репозиторий пробной сущности {@link ConstraintProbe}, только для тестов. */
interface ConstraintProbeRepository extends JpaRepository<ConstraintProbe, UUID> {

	/**
	 * Код через ссылку {@code getReferenceById}: записи нет — прокси не загрузится. Исключение выходит через прокси
	 * репозитория, и Spring переводит {@code EntityNotFoundException} в {@code JpaObjectRetrievalFailureException}.
	 */
	@Transactional(readOnly = true)
	default @Nullable String codeByReference(UUID id) {
		return getReferenceById(id).getCode();
	}

}
