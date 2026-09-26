package kz.dentify.platform.api;

/**
 * Права вошедшего аккаунта в текущей работе. Без входа и в фоновой задаче прав нет. Сейчас — authorities контекста
 * Spring Security (их выдают только тесты). С T-IAM-07 — эффективные права члена клиники контекста: считаются на запрос
 * по клинике и членству через SPI, который реализует iam, с кэшем в Valkey; в {@code Authentication} сессии права не
 * хранятся (docs/architecture.md, Права).
 */
@FunctionalInterface
public interface CurrentPermissions {

	/** Есть ли право, например {@link Permissions#PATIENT_PHONES}. */
	boolean has(String permission);

}
