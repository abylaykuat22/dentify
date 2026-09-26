package kz.dentify.iam.internal;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import kz.dentify.platform.api.TenantEntity;

/**
 * Филиал, к которому членство даёт доступ. Строки нет ни одной — доступ ко всем филиалам клиники. Составной внешний
 * ключ в БД не даёт связать членство с филиалом другой клиники. Живёт внутри {@link Membership}: колонку
 * {@code membership_id} ведёт коллекция членства.
 */
@Entity
@Table(name = "membership_branch")
class MembershipBranch extends TenantEntity {

	@Column(name = "branch_id", nullable = false, updatable = false)
	private UUID branchId;

	protected MembershipBranch() {
		this.branchId = new UUID(0, 0);
	}

	MembershipBranch(UUID branchId) {
		this.branchId = branchId;
	}

	UUID getBranchId() {
		return this.branchId;
	}

}
