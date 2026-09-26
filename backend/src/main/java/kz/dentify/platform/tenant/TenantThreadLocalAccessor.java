package kz.dentify.platform.tenant;

import io.micrometer.context.ThreadLocalAccessor;
import kz.dentify.platform.api.Tenant;
import org.jspecify.annotations.Nullable;

/**
 * Перенос контекста клиники в другие потоки через Micrometer Context Propagation. Зарегистрирован в
 * {@code META-INF/services/io.micrometer.context.ThreadLocalAccessor}, и {@code ContextPropagatingTaskDecorator} Spring
 * вместе с traceId переносит клинику из потока, который поставил задачу, в поток исполнителя: {@code @Async},
 * {@code @ApplicationModuleListener}, асинхронные {@code @EventListener}. Декоратор на исполнителях Spring Boot
 * включает {@code spring.task.execution.propagate-context: true}.
 * <p>
 * Задача, поставленная без контекста, исполняется без контекста, а после неё поток исполнителя возвращается к прежнему
 * значению: чужая клиника в пуле потоков не остаётся.
 */
public final class TenantThreadLocalAccessor implements ThreadLocalAccessor<Tenant> {

	/** Ключ в снимке контекста. */
	public static final String KEY = "dentify.tenant";

	@Override
	public Object key() {
		return KEY;
	}

	@Override
	public @Nullable Tenant getValue() {
		return TenantContextHolder.get();
	}

	@Override
	public void setValue(Tenant value) {
		TenantContextHolder.set(value);
	}

	/** В снимке контекста клиники нет: задача исполняется без контекста. */
	@Override
	public void setValue() {
		TenantContextHolder.set(null);
	}

	@Override
	public void restore(Tenant previousValue) {
		TenantContextHolder.set(previousValue);
	}

	@Override
	public void restore() {
		TenantContextHolder.set(null);
	}

}
