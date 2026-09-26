package kz.dentify.platform.tenant;

import kz.dentify.platform.api.Tenant;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

import org.springframework.transaction.support.ResourceHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Хранилище арендатора потока. Модули его не видят: читают и меняют контекст через {@code TenantContext} из
 * {@code platform.api}, а переносит его в другие потоки {@link TenantThreadLocalAccessor}.
 * <p>
 * Вместе с арендатором ставится ключ MDC {@value #MDC_CLINIC_ID}: id клиники попадает в каждую строку JSON-лога
 * (это идентификатор, не ПД).
 */
public final class TenantContextHolder {

	/** Ключ MDC с id клиники текущей работы. */
	public static final String MDC_CLINIC_ID = "clinicId";

	private static final ThreadLocal<@Nullable Tenant> CURRENT = new ThreadLocal<>();

	private TenantContextHolder() {
	}

	public static @Nullable Tenant get() {
		return CURRENT.get();
	}

	/**
	 * Клиника потока уже закреплена за данными: открыта транзакция, в том числе в фазе после её коммита, идёт
	 * синхронизация транзакции Spring, в том числе в области {@code @Transactional(propagation = SUPPORTS)} без
	 * настоящей транзакции, или к потоку привязан ресурс транзакции — сессия Hibernate ({@code EntityManagerHolder}) либо соединение с БД
	 * ({@code ConnectionHolder}). Их клиника выбрана при открытии, и следующий запрос к данным пойдёт через них, поэтому
	 * сменить клинику сейчас нельзя: контекст показывал бы одну клинику, а данные — другую.
	 */
	public static boolean pinnedByTransaction() {
		if (TransactionSynchronizationManager.isActualTransactionActive()
				|| TransactionSynchronizationManager.isSynchronizationActive()) {
			return true;
		}
		for (Object resource : TransactionSynchronizationManager.getResourceMap().values()) {
			if (resource instanceof ResourceHolder) {
				return true;
			}
		}
		return false;
	}

	/** Ставит арендатора потока; null — снимает. Вызывающий обязан вернуть прежнее значение в {@code finally}. */
	public static void set(@Nullable Tenant tenant) {
		if (tenant == null) {
			CURRENT.remove();
			MDC.remove(MDC_CLINIC_ID);
		}
		else {
			CURRENT.set(tenant);
			MDC.put(MDC_CLINIC_ID, tenant.clinicId().toString());
		}
	}

}
