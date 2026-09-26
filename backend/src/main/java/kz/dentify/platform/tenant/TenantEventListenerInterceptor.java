package kz.dentify.platform.tenant;

import java.lang.reflect.Method;

import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantEvent;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;

import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;

/**
 * Обработчик события клиники ({@link TenantEvent}) выполняется в контексте клиники события. Стоит в цепочке прокси
 * раньше транзакции обработчика, поэтому соединение с БД и сессия Hibernate открываются уже с клиникой события. Для
 * асинхронного обработчика — внутри потока исполнителя, после {@code @Async}.
 * <ul>
 * <li>контекст той же клиники — без изменений;</li>
 * <li>контекст другой клиники — {@link IllegalStateException};</li>
 * <li>контекста нет, и к потоку не привязана транзакция — ставится клиника события, филиал не выбран. Так работают
 * асинхронные обработчики, в том числе {@code @ApplicationModuleListener}, переотправка журнала публикаций и
 * синхронный обработчик события, опубликованного вне транзакции;</li>
 * <li>контекста нет, но к потоку привязана транзакция без клиники: синхронный обработчик внутри транзакции
 * публикации или {@code @TransactionalEventListener} после её завершения, когда её ресурсы ещё привязаны к потоку.
 * Клиника события ставится, только если обработчик открывает свою транзакцию, отложив прежнюю:
 * {@code @Transactional(propagation = REQUIRES_NEW)} или {@code NOT_SUPPORTED}. Иначе обработчик читал бы через сессию
 * и соединение без клиники — {@link IllegalStateException}. В фазе после коммита Spring пишет его в лог ERROR и не
 * пробрасывает; публикация в журнале остаётся незавершённой.</li>
 * </ul>
 */
class TenantEventListenerInterceptor implements MethodInterceptor {

	private final TransactionAttributeSource transactions = new AnnotationTransactionAttributeSource(false);

	@Override
	public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
		TenantEvent event = tenantEvent(invocation.getArguments());
		if (event == null) {
			return invocation.proceed();
		}
		Tenant current = TenantContextHolder.get();
		if (current != null) {
			if (!current.clinicId().equals(event.clinicId())) {
				throw new IllegalStateException("Событие клиники " + event.clinicId()
						+ " обрабатывается в контексте клиники " + current.clinicId() + ": " + invocation.getMethod());
			}
			return invocation.proceed();
		}
		if (TenantContextHolder.pinnedByTransaction() && !opensOwnTransaction(invocation)) {
			throw new IllegalStateException("Событие клиники " + event.clinicId() + " обрабатывается внутри "
					+ "транзакции без клиники, а обработчик не открывает свою: " + invocation.getMethod()
					+ ". Нужен @ApplicationModuleListener или @Transactional(propagation = REQUIRES_NEW)");
		}
		TenantContextHolder.set(Tenant.of(event.clinicId()));
		try {
			return invocation.proceed();
		}
		finally {
			TenantContextHolder.set(null);
		}
	}

	/** Транзакция обработчика откладывает транзакцию потока: к данным он пойдёт через новые сессию и соединение. */
	private boolean opensOwnTransaction(MethodInvocation invocation) {
		Method method = invocation.getMethod();
		Object target = invocation.getThis();
		Class<?> targetClass = (target != null) ? AopUtils.getTargetClass(target) : method.getDeclaringClass();
		TransactionAttribute attribute = this.transactions.getTransactionAttribute(method, targetClass);
		if (attribute == null) {
			return false;
		}
		int propagation = attribute.getPropagationBehavior();
		return propagation == TransactionDefinition.PROPAGATION_REQUIRES_NEW
				|| propagation == TransactionDefinition.PROPAGATION_NOT_SUPPORTED;
	}

	private static @Nullable TenantEvent tenantEvent(@Nullable Object[] arguments) {
		for (Object argument : arguments) {
			if (argument instanceof TenantEvent event) {
				return event;
			}
		}
		return null;
	}

}
