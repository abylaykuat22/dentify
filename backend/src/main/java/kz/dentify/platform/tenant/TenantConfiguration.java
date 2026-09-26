package kz.dentify.platform.tenant;

import org.springframework.aop.Advisor;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMethodMatcher;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Тенант-контекст (PLT-01): откуда он берётся и как переходит между потоками.
 * <ul>
 * <li>HTTP-запрос — {@link TenantContextFilter} из сессии;</li>
 * <li>{@code @Async}, асинхронные обработчики событий и задачи исполнителей Spring Boot — перенос через
 * {@link TenantThreadLocalAccessor} и {@code ContextPropagatingTaskDecorator}
 * ({@code spring.task.execution.propagate-context: true}). {@code @EnableAsync} включён здесь: на нём работает и
 * {@code @ApplicationModuleListener};</li>
 * <li>обработчики событий клиники — {@link TenantEventListenerInterceptor}.</li>
 * </ul>
 * Задачи по расписанию контекста не получают: клинику выбирает сама задача.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class TenantConfiguration {

	/** После Spring Session (MIN_VALUE + 50) и Spring Security (-100): их решения о сессии — раньше. */
	static final int FILTER_ORDER = 0;

	@Bean
	FilterRegistrationBean<TenantContextFilter> tenantContextFilter() {
		FilterRegistrationBean<TenantContextFilter> registration = new FilterRegistrationBean<>(new TenantContextFilter());
		registration.setOrder(FILTER_ORDER);
		return registration;
	}

	/**
	 * Обработчики событий приложения ({@code @EventListener} и аннотации на его основе: {@code @TransactionalEventListener},
	 * {@code @ApplicationModuleListener}) в классах Dentify. Советник с наивысшим приоритетом: раньше транзакции
	 * обработчика, но после {@code @Async} (его советник всегда первый).
	 */
	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static Advisor tenantEventListenerAdvisor() {
		ClassFilter dentifyClasses = (type) -> type.getName().startsWith("kz.dentify.");
		ComposablePointcut pointcut = new ComposablePointcut(dentifyClasses,
				new AnnotationMethodMatcher(EventListener.class, true));
		DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, new TenantEventListenerInterceptor());
		advisor.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return advisor;
	}

}
