package kz.dentify.platform.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Точка расширения: сотрудник вошедшего аккаунта в клинике контекста (членство, T-IAM). Аудит пишет его рядом с
 * аккаунтом. Реализует iam одним бином; пока реализации нет, сотрудника в событии нет.
 */
@FunctionalInterface
public interface CurrentStaffProvider {

	/** Id сотрудника или пусто: нет входа, системная задача, аккаунт без сотрудника. */
	Optional<UUID> currentStaffId();

}
