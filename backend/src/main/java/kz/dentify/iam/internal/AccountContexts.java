package kz.dentify.iam.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;
import kz.dentify.clinic.api.ClinicDirectory.BranchInfo;
import kz.dentify.clinic.api.ClinicDirectory.ClinicInfo;
import kz.dentify.clinic.api.ClinicDirectory;
import kz.dentify.iam.internal.AccountMemberships.ClinicMembership;
import kz.dentify.iam.internal.AccountOverview.ClinicAccess;
import kz.dentify.iam.internal.AccountOverview.Context;
import kz.dentify.iam.internal.AccountOverview.Profile;
import kz.dentify.platform.api.ForbiddenException;
import kz.dentify.platform.api.NotFoundException;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import kz.dentify.platform.identity.TenantSession;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

/**
 * Клиника и филиал сессии вошедшего аккаунта (IAM-02, IAM-08): что показывает {@code GET /api/v1/me}, выбор клиники
 * после входа и смена клиники. Клиника попадает в сессию ({@link TenantSession}) только здесь и только после проверки,
 * что у аккаунта в ней активное членство, а филиал ему доступен. Всё, что читается из клиники — название, филиалы,
 * членство, — читается в контексте этой клиники ({@code TenantContext.call}), поэтому методы нетранзакционные:
 * клинику нельзя сменить внутри транзакции.
 */
@Service
public class AccountContexts {

	/** 403: членство в выбранной клинике отключено. */
	public static final String MEMBERSHIP_DISABLED = "iam.membership.disabled";

	private static final Logger log = LoggerFactory.getLogger(AccountContexts.class);

	private final Accounts accounts;

	private final AccountMemberships accountMemberships;

	private final Memberships memberships;

	private final ClinicDirectory clinics;

	AccountContexts(Accounts accounts, AccountMemberships accountMemberships, Memberships memberships,
			ClinicDirectory clinics) {
		this.accounts = accounts;
		this.accountMemberships = accountMemberships;
		this.memberships = memberships;
		this.clinics = clinics;
	}

	/**
	 * Аккаунт, его членства во всех клиниках и контекст сессии. Известная цена MVP: на каждое членство — две транзакции
	 * в контексте его клиники (клиника с филиалами, членство), каждая со своей выдачей соединения и set_config, плюс
	 * клиника контекста; у врача в 2–3 клиниках это 15–20 обращений к БД на запрос (docs/architecture.md, Вход, выход
	 * и /api/v1/me).
	 *
	 * @param context клиника и филиал сессии или null
	 */
	public AccountOverview overview(UUID accountId, @Nullable Tenant context) {
		Account account = this.accounts.get(accountId);
		List<ClinicAccess> access = new ArrayList<>();
		for (ClinicMembership membership : this.accountMemberships.of(accountId)) {
			TenantContext.call(Tenant.of(membership.clinicId()), () -> this.clinics.current()
				.map((clinic) -> new ClinicAccess(membership.membershipId(), membership.status(), withoutBranches(clinic),
						(membership.status() == MembershipStatus.ACTIVE) ? available(clinic, membership) : List.of())))
				.ifPresent(access::add);
		}
		Profile profile = new Profile(accountId, account.getFullName(), AccountOverview.initials(account.getFullName()),
				account.getLanguage(), account.getPhone(), account.getEmail());
		return new AccountOverview(profile, access, (context != null) ? context(context) : null);
	}

	/**
	 * Выбрать клинику и филиал сессии: экран выбора клиники после входа и смена клиники.
	 *
	 * @param branchId филиал или null: у членства с одним доступным филиалом выбирается он, иначе филиал не выбран
	 * @throws NotFoundException 404: у аккаунта нет членства в клинике (или только приглашение) или филиал недоступен
	 * @throws ForbiddenException 403 {@link #MEMBERSHIP_DISABLED}: членство отключено
	 */
	public AccountOverview select(UUID accountId, UUID clinicId, @Nullable UUID branchId, HttpSession session) {
		ClinicMembership membership = this.accountMemberships.of(accountId)
			.stream()
			.filter((candidate) -> candidate.clinicId().equals(clinicId))
			.filter((candidate) -> candidate.status() != MembershipStatus.INVITED)
			.findFirst()
			.orElseThrow(NotFoundException::new);
		if (membership.status() == MembershipStatus.DISABLED) {
			throw new ForbiddenException(MEMBERSHIP_DISABLED);
		}
		Tenant tenant = TenantContext.call(Tenant.of(clinicId), () -> {
			ClinicInfo clinic = this.clinics.current().orElseThrow(NotFoundException::new);
			List<BranchInfo> available = available(clinic, membership);
			UUID branch;
			if (branchId != null) {
				branch = available.stream()
					.map(BranchInfo::id)
					.filter(branchId::equals)
					.findFirst()
					.orElseThrow(NotFoundException::new);
			}
			else {
				branch = (available.size() == 1) ? available.getFirst().id() : null;
			}
			this.memberships.recordSignIn(accountId);
			return new Tenant(clinicId, branch);
		});
		TenantSession.select(session, tenant);
		log.info("Аккаунт {} выбрал клинику {}, филиал {}", accountId, tenant.clinicId(), tenant.branchId());
		return overview(accountId, tenant);
	}

	/**
	 * Клиника и филиал сразу после входа (IAM-02): одно активное членство и один доступный филиал — они, иначе пусто, и
	 * интерфейс показывает выбор клиники. Выбранную клинику отмечает время последнего входа.
	 */
	Optional<Tenant> autoSelect(UUID accountId, List<ClinicMembership> memberships) {
		List<ClinicMembership> active = memberships.stream()
			.filter((membership) -> membership.status() == MembershipStatus.ACTIVE)
			.toList();
		if (active.size() != 1) {
			return Optional.empty();
		}
		ClinicMembership membership = active.getFirst();
		return TenantContext.call(Tenant.of(membership.clinicId()), () -> {
			List<BranchInfo> available = this.clinics.current()
				.map((clinic) -> available(clinic, membership))
				.orElse(List.of());
			if (available.size() != 1) {
				return Optional.empty();
			}
			this.memberships.recordSignIn(accountId);
			return Optional.of(new Tenant(membership.clinicId(), available.getFirst().id()));
		});
	}

	/** Филиалы клиники, доступные членству: все, если у членства нет ограничения. Только в контексте клиники. */
	private List<BranchInfo> available(ClinicInfo clinic, ClinicMembership access) {
		Membership membership = this.memberships.get(access.membershipId());
		return clinic.branches().stream().filter((branch) -> membership.coversBranch(branch.id())).toList();
	}

	private @Nullable Context context(Tenant tenant) {
		return TenantContext.call(tenant, () -> this.clinics.current())
			.map((clinic) -> new Context(withoutBranches(clinic),
					(tenant.branchId() != null) ? clinic.branch(Objects.requireNonNull(tenant.branchId())).orElse(null)
							: null))
			.orElse(null);
	}

	private static ClinicInfo withoutBranches(ClinicInfo clinic) {
		return new ClinicInfo(clinic.id(), clinic.name(), clinic.city(), List.of());
	}

}
