package kz.dentify.platform.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import kz.dentify.platform.tenant.TenantContextHolder;
import org.jspecify.annotations.Nullable;

/**
 * Контекст клиники текущей работы (PLT-01). Всё, что читает и пишет данные клиники, делает это внутри контекста:
 * <ul>
 * <li>HTTP-запрос — контекст ставит платформа из сессии (клинику в сессию кладёт вход и выбор клиники через
 * {@code identity.TenantSession});</li>
 * <li>{@code @Async}, обработчики событий, в том числе {@code @ApplicationModuleListener}, — контекст переносится из
 * потока, который запустил задачу или опубликовал событие, а для события {@link TenantEvent} берётся из события;</li>
 * <li>задача по расписанию и прочая фоновая работа — клинику выбирают явно: {@link #run(Tenant, Runnable)}.</li>
 * </ul>
 * Без контекста данные клиники недоступны: репозиторий сущности {@link TenantEntity} бросает
 * {@link MissingTenantContextException}, Hibernate не находит ни одной строки, а RLS в БД не показывает ни одной
 * строки. «Все клиники сразу» не бывает никогда.
 * <p>
 * Клиника соединения с БД и сессии Hibernate фиксируется, когда они открываются. Поэтому сменить клинику нельзя, пока
 * они привязаны к потоку: внутри транзакции, в области {@code @Transactional(propagation = SUPPORTS)} без транзакции,
 * в обработчике после завершения транзакции — {@link #run(Tenant, Runnable)} бросит {@link IllegalStateException}.
 * Задача по клиникам открывает транзакцию внутри {@code run} на каждую клинику.
 */
public final class TenantContext {

	private TenantContext() {
	}

	/** Арендатор текущей работы или пусто, если контекста нет. */
	public static Optional<Tenant> current() {
		return Optional.ofNullable(TenantContextHolder.get());
	}

	/**
	 * Арендатор текущей работы.
	 *
	 * @throws MissingTenantContextException если контекста клиники нет
	 */
	public static Tenant require() {
		Tenant tenant = TenantContextHolder.get();
		if (tenant == null) {
			throw new MissingTenantContextException();
		}
		return tenant;
	}

	/**
	 * Клиника текущей работы.
	 *
	 * @throws MissingTenantContextException если контекста клиники нет
	 */
	public static UUID clinicId() {
		return require().clinicId();
	}

	/**
	 * Выбранный филиал или null.
	 *
	 * @throws MissingTenantContextException если контекста клиники нет
	 */
	public static @Nullable UUID branchId() {
		return require().branchId();
	}

	/**
	 * Выполняет действие в контексте клиники и возвращает прежний контекст, даже если действие бросило исключение.
	 *
	 * @throws IllegalStateException если к потоку привязаны транзакция, её синхронизация, сессия Hibernate или
	 * соединение с БД другой клиники или без клиники
	 */
	public static void run(Tenant tenant, Runnable action) {
		call(tenant, () -> {
			action.run();
			return null;
		});
	}

	/** То же, что {@link #run(Tenant, Runnable)}, с результатом. */
	public static <T extends @Nullable Object> T call(Tenant tenant, Supplier<T> action) {
		Objects.requireNonNull(tenant, "tenant");
		Tenant previous = TenantContextHolder.get();
		if (tenant.equals(previous)) {
			return action.get();
		}
		boolean sameClinic = previous != null && previous.clinicId().equals(tenant.clinicId());
		if (!sameClinic && TenantContextHolder.pinnedByTransaction()) {
			throw new IllegalStateException("Смена клиники, когда к потоку привязаны транзакция, сессия Hibernate или "
					+ "соединение с БД прежней клиники. Откройте транзакцию внутри TenantContext.run");
		}
		TenantContextHolder.set(tenant);
		try {
			return action.get();
		}
		finally {
			TenantContextHolder.set(previous);
		}
	}

}
