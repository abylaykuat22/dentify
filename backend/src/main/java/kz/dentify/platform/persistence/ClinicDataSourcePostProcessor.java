package kz.dentify.platform.persistence;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Оборачивает пул приложения (бин {@value #DATA_SOURCE}) в {@link ClinicConnectionDataSource}: JPA, {@code JdbcTemplate}
 * и менеджер транзакций получают одну и ту же обёртку, и любое соединение приложения несёт клинику контекста.
 * Настройки пула, метрики Hikari и health видят исходный пул: Spring Boot разворачивает {@code DelegatingDataSource}.
 * Flyway строит свой источник по URL пула с ролью владельца схемы и обёртку не получает.
 */
@Component
class ClinicDataSourcePostProcessor implements BeanPostProcessor {

	/** Имя бина пула приложения в автоконфигурации Spring Boot. */
	static final String DATA_SOURCE = "dataSource";

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (DATA_SOURCE.equals(beanName) && bean instanceof DataSource dataSource
				&& !(bean instanceof ClinicConnectionDataSource)) {
			return new ClinicConnectionDataSource(dataSource);
		}
		return bean;
	}

}
