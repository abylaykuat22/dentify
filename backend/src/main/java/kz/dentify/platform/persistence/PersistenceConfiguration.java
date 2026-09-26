package kz.dentify.platform.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import kz.dentify.platform.api.CurrentAccountProvider;
import org.hibernate.cfg.MultiTenancySettings;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA платформы:
 * <ul>
 * <li>{@code @TenantId} берёт клинику из тенант-контекста — {@link ClinicTenantIdentifierResolver} в настройке
 * Hibernate {@code hibernate.tenant_identifier_resolver};</li>
 * <li>аудит Spring Data JPA для {@code BaseEntity}: время — {@link Instant}, автор — id аккаунта из
 * {@link CurrentAccountProvider} (точка расширения для T-PLT-04), без реализации — пусто.</li>
 * </ul>
 * Клинику в соединение ставит {@link ClinicConnectionDataSource}, проверку контекста в репозиториях —
 * {@link TenantRepositoryGuard}.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = PersistenceConfiguration.AUDITOR, dateTimeProviderRef = PersistenceConfiguration.AUDIT_TIME)
class PersistenceConfiguration {

	static final String AUDITOR = "accountAuditor";

	static final String AUDIT_TIME = "auditTime";

	@Bean(AUDITOR)
	AuditorAware<UUID> accountAuditor(ObjectProvider<CurrentAccountProvider> accounts) {
		return () -> {
			CurrentAccountProvider provider = accounts.getIfUnique();
			return (provider != null) ? provider.currentAccountId() : Optional.empty();
		};
	}

	/** До микросекунд — точность timestamptz: значение в сущности совпадает с тем, что прочитается из БД. */
	@Bean(AUDIT_TIME)
	DateTimeProvider auditTime() {
		return () -> Optional.of(Instant.now().truncatedTo(ChronoUnit.MICROS));
	}

	@Bean
	HibernatePropertiesCustomizer tenantIdentifierResolver() {
		return (properties) -> properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER,
				new ClinicTenantIdentifierResolver());
	}

}
