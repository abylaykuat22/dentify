package kz.dentify.iam.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Выбор клиники и филиала сессии — единственный запрос, в котором клиника приходит от клиента: это выбор, а не
 * данные. Клиника становится контекстом сессии только после проверки, что у аккаунта в ней активное членство
 * (TenancyRulesTests, реестр CLINIC_SELECTION_DTOS).
 *
 * @param clinicId клиника из своих членств
 * @param branchId филиал клиники или null
 */
@Schema(description = "Выбор клиники и филиала сессии")
record SelectContextRequest(
		@Schema(description = "Клиника из memberships ответа /api/v1/me") @NotNull UUID clinicId,
		@Schema(description = "Филиал из branches этого членства; null — единственный доступный филиал, если он один, "
				+ "иначе филиал не выбран") @Nullable UUID branchId) {
}
