package kz.dentify.iam.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import kz.dentify.platform.api.ConflictException;
import kz.dentify.platform.api.DataIntegrity;
import kz.dentify.platform.api.MissingTenantContextException;
import kz.dentify.platform.api.NotFoundException;
import kz.dentify.platform.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Членства клиники контекста (IAM-01). Всё — только в клинике контекста: членство другой клиники не находится
 * ({@link NotFoundException}, 404), без контекста клиники — {@link MissingTenantContextException}. Свои членства аккаунта
 * во всех клиниках, до выбора клиники, — {@link AccountMemberships}.
 */
@Service
public class Memberships {

	/** 409: у аккаунта уже есть членство в этой клинике. */
	public static final String EXISTS = "iam.membership.exists";

	static final String UNIQUE_ACCOUNT = "ux_membership_clinic_id_account_id";

	private static final Logger log = LoggerFactory.getLogger(Memberships.class);

	private final MembershipRepository memberships;

	Memberships(MembershipRepository memberships) {
		this.memberships = memberships;
	}

	/**
	 * Членство аккаунта в клинике контекста.
	 *
	 * @param branchIds филиалы доступа; пусто — все филиалы клиники. Филиал другой клиники связать нельзя: составной
	 * внешний ключ, ответ 409
	 * @throws ConflictException 409 {@link #EXISTS}, если у аккаунта уже есть членство в этой клинике
	 */
	@Transactional
	public Membership add(UUID accountId, MembershipStatus status, Collection<UUID> branchIds) {
		if (this.memberships.existsByAccountId(accountId)) {
			throw new ConflictException(EXISTS);
		}
		Membership membership = new Membership(accountId, status);
		membership.limitToBranches(branchIds);
		try {
			membership = this.memberships.saveAndFlush(membership);
		}
		catch (DataIntegrityViolationException ex) {
			// параллельный запрос успел записать членство между проверкой и записью: тот же ответ, что у проверки
			if (DataIntegrity.isViolation(ex, UNIQUE_ACCOUNT)) {
				throw new ConflictException(EXISTS);
			}
			throw ex;
		}
		log.info("Аккаунт {} получил членство {} в клинике", accountId, membership.getId());
		return membership;
	}

	/**
	 * @throws NotFoundException 404, если членства нет в клинике контекста, в том числе если оно другой клиники
	 */
	@Transactional(readOnly = true)
	public Membership get(UUID id) {
		return this.memberships.findById(id).orElseThrow(NotFoundException::new);
	}

	/** Членство аккаунта в клинике контекста. */
	@Transactional(readOnly = true)
	public Optional<Membership> ofAccount(UUID accountId) {
		return this.memberships.findByAccountId(accountId);
	}

	/** Все членства клиники контекста. Список с поиском и страницами — T-IAM-11. */
	@Transactional(readOnly = true)
	public List<Membership> list() {
		return this.memberships.findAll();
	}

	/**
	 * Вход аккаунта в клинику контекста: время последнего входа в его членстве. Версия членства не меняется.
	 */
	@Transactional
	public void recordSignIn(UUID accountId) {
		this.memberships.recordSignIn(accountId, TenantContext.clinicId(), Instant.now());
	}

	/**
	 * Ограничить доступ членства филиалами; пусто — все филиалы.
	 *
	 * @throws NotFoundException 404, если членства нет в клинике контекста
	 */
	@Transactional
	public Membership limitToBranches(UUID id, Collection<UUID> branchIds) {
		Membership membership = get(id);
		membership.limitToBranches(branchIds);
		return this.memberships.saveAndFlush(membership);
	}

}
