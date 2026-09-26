package kz.dentify.iam.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import kz.dentify.platform.api.TenantEntity;
import org.jspecify.annotations.Nullable;

/**
 * Членство — доступ аккаунта к одной клинике (IAM-01): роль, филиалы (пусто — все филиалы клиники), статус, карточка
 * сотрудника (0..1) и время последнего входа. Данные клиники: клиника из контекста ({@code @TenantId}), членство другой
 * клиники не находится. Одно на аккаунт в клинике. Создаёт {@link Memberships#add}.
 * <p>
 * Роль появится с каталогом прав (T-IAM-06), связь с карточкой сотрудника — T-IAM-26; до этого {@code roleId} и
 * {@code staffId} пустые.
 */
@Entity
@Table(name = "membership")
public class Membership extends TenantEntity {

	@Column(name = "account_id", nullable = false, updatable = false)
	private UUID accountId;

	@Column(name = "role_id")
	private @Nullable UUID roleId;

	@Column(name = "staff_id")
	private @Nullable UUID staffId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private MembershipStatus status;

	@Column(name = "last_sign_in_at")
	private @Nullable Instant lastSignInAt;

	/**
	 * Филиалы — коллекция самого членства (внешний ключ у строки филиала), а не обратная сторона связи: изменение
	 * филиалов меняет версию членства, и одновременная правка ролей и филиалов даёт 409 (T-IAM-11).
	 */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "membership_id", nullable = false, updatable = false)
	private Set<MembershipBranch> branches = new HashSet<>();

	protected Membership() {
		this.accountId = new UUID(0, 0);
		this.status = MembershipStatus.INVITED;
	}

	Membership(UUID accountId, MembershipStatus status) {
		this.accountId = accountId;
		this.status = status;
	}

	public UUID getAccountId() {
		return this.accountId;
	}

	/** Роль в клинике или null, пока ролей нет (T-IAM-06). */
	public @Nullable UUID getRoleId() {
		return this.roleId;
	}

	/** Карточка сотрудника или null (T-IAM-26). */
	public @Nullable UUID getStaffId() {
		return this.staffId;
	}

	public MembershipStatus getStatus() {
		return this.status;
	}

	/**
	 * Последний вход в эту клинику или null. Пишет его вход и выбор клиники запросом мимо сущности
	 * ({@code Memberships.recordSignIn}): вход не правка членства и не меняет его версию.
	 */
	public @Nullable Instant getLastSignInAt() {
		return this.lastSignInAt;
	}

	/** Филиалы, к которым есть доступ; пусто — все филиалы клиники. */
	public Set<UUID> getBranchIds() {
		return this.branches.stream().map(MembershipBranch::getBranchId).collect(Collectors.toUnmodifiableSet());
	}

	/** Есть ли доступ к филиалу: он в списке или список пуст (все филиалы). */
	public boolean coversBranch(UUID branchId) {
		return this.branches.isEmpty() || getBranchIds().contains(branchId);
	}

	/**
	 * Ограничить доступ филиалами; пустой набор — все филиалы. Филиалы, которые остались, не пересоздаются: иначе в одной
	 * записи Hibernate вставил бы строку раньше, чем удалил прежнюю, и нарушил уникальность.
	 */
	void limitToBranches(Collection<UUID> branchIds) {
		Set<UUID> wanted = Set.copyOf(branchIds);
		this.branches.removeIf((branch) -> !wanted.contains(branch.getBranchId()));
		Set<UUID> present = getBranchIds();
		wanted.stream()
			.filter((branchId) -> !present.contains(branchId))
			.forEach((branchId) -> this.branches.add(new MembershipBranch(branchId)));
	}

}
