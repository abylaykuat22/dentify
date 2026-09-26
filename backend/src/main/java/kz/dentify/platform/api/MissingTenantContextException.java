package kz.dentify.platform.api;

/**
 * Код обратился к данным клиники без контекста клиники. Бросается вместо того, чтобы читать без фильтра или молча
 * вернуть пусто (PLT-01): репозиторий сущности {@link TenantEntity}, кэш данных клиники и {@link TenantContext#require()}
 * без контекста. В фоновой задаче или обработчике события это дефект: задача должна явно выбрать клинику через
 * {@link TenantContext#run(Tenant, Runnable)} или получить её из события ({@link TenantEvent}).
 * <p>
 * В HTTP-запросе контекст ставит платформа из сессии; нет клиники в сессии — ответ 401 с кодом
 * {@link ErrorCodes#CLINIC_NOT_SELECTED}. Наследует {@link RuntimeException}, а не {@link IllegalStateException}:
 * Spring переводит {@code IllegalStateException} из репозиториев в {@code InvalidDataAccessApiUsageException}.
 */
public class MissingTenantContextException extends RuntimeException {

	public MissingTenantContextException() {
		super("Нет контекста клиники: данные клиники доступны только внутри TenantContext");
	}

}
