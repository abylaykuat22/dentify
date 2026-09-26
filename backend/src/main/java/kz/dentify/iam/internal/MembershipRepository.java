package kz.dentify.iam.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Членства клиники контекста: Hibernate добавляет условие по клинике ко всем запросам, без контекста клиники —
 * {@code MissingTenantContextException}. Свои членства аккаунта во всех клиниках — {@link AccountMemberships}.
 * Филиалы членства загружаются вместе с ним: сервис отдаёт членство за пределы своей транзакции.
 */
interface MembershipRepository extends JpaRepository<Membership, UUID> {

	@Override
	@EntityGraph(attributePaths = "branches")
	Optional<Membership> findById(UUID id);

	@Override
	@EntityGraph(attributePaths = "branches")
	List<Membership> findAll();

	/** Членство аккаунта в клинике контекста. */
	@EntityGraph(attributePaths = "branches")
	Optional<Membership> findByAccountId(UUID accountId);

	boolean existsByAccountId(UUID accountId);

	/**
	 * Время последнего входа аккаунта в клинику — запросом, мимо сущности: вход не правка членства, версия и время
	 * изменения не меняются, и одновременная правка ролей администратором не получит ложный 409. Клиника — явным
	 * условием, вторым рубежом — фильтр Hibernate и RLS.
	 */
	@Modifying
	@Query("update Membership m set m.lastSignInAt = :at where m.accountId = :accountId and m.clinicId = :clinicId")
	int recordSignIn(UUID accountId, UUID clinicId, Instant at);

}
