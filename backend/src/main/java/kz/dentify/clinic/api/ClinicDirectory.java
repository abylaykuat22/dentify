package kz.dentify.clinic.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import kz.dentify.platform.api.MissingTenantContextException;

/**
 * Клиника контекста и её филиалы — названия для шапки, выбора клиники и филиала ({@code GET /api/v1/me}, T-IAM-02).
 * Только клиника текущего контекста ({@code TenantContext}): чужую клинику RLS не показывает, списка всех клиник нет.
 * Карточка клиники с реквизитами, адресами и часами филиалов — T-CLN-01, T-CLN-02.
 */
public interface ClinicDirectory {

	/**
	 * Клиника контекста с филиалами по названию; пусто, если такой клиники нет.
	 *
	 * @throws MissingTenantContextException если контекста клиники нет
	 */
	Optional<ClinicInfo> current();

	/**
	 * Клиника.
	 *
	 * @param id клиника
	 * @param name название
	 * @param city город
	 * @param branches филиалы по названию
	 */
	record ClinicInfo(UUID id, String name, String city, List<BranchInfo> branches) {

		public ClinicInfo {
			branches = List.copyOf(branches);
		}

		/** Филиал клиники по id. */
		public Optional<BranchInfo> branch(UUID branchId) {
			return this.branches.stream().filter((branch) -> branch.id().equals(branchId)).findFirst();
		}

	}

	/**
	 * Филиал клиники.
	 *
	 * @param id филиал
	 * @param name название, например адрес
	 * @param city город
	 */
	record BranchInfo(UUID id, String name, String city) {
	}

}
