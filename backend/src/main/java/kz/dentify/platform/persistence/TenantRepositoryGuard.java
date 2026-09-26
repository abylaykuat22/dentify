package kz.dentify.platform.persistence;

import kz.dentify.platform.api.MissingTenantContextException;
import kz.dentify.platform.api.TenantEntity;
import kz.dentify.platform.tenant.TenantContextHolder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;
import org.springframework.stereotype.Component;

/**
 * Репозиторий сущности {@link TenantEntity} без контекста клиники бросает {@link MissingTenantContextException} до
 * транзакции и запроса (PLT-01): фоновая задача или обработчик события, забывшие выбрать клинику, падают, а не
 * получают молча пустой результат. Проверка встраивается первой в прокси каждого такого репозитория через
 * {@code RepositoryProxyPostProcessor} Spring Data; репозитории глобальных сущностей не затронуты.
 * <p>
 * В обход репозитория — {@code EntityManager}, {@code JdbcTemplate} — без контекста данные клиники тоже не видны
 * (Hibernate и RLS), но ошибки нет: к данным клиники ходят через репозитории.
 */
@Component
class TenantRepositoryGuard implements BeanPostProcessor {

	private static final MethodInterceptor REQUIRE_TENANT = TenantRepositoryGuard::requireTenant;

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		if (bean instanceof RepositoryFactoryBeanSupport<?, ?, ?> repositoryFactory) {
			repositoryFactory.addRepositoryFactoryCustomizer((factory) -> factory
				.addRepositoryProxyPostProcessor((proxy, repository) -> {
					if (TenantEntity.class.isAssignableFrom(repository.getDomainType())) {
						proxy.addAdvice(0, REQUIRE_TENANT);
					}
				}));
		}
		return bean;
	}

	private static @Nullable Object requireTenant(MethodInvocation invocation) throws Throwable {
		if (TenantContextHolder.get() == null && !isObjectMethod(invocation)) {
			throw new MissingTenantContextException();
		}
		return invocation.proceed();
	}

	/** toString, equals, hashCode прокси — не обращения к данным. */
	private static boolean isObjectMethod(MethodInvocation invocation) {
		return invocation.getMethod().getDeclaringClass() == Object.class;
	}

}
