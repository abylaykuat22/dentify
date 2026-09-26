package kz.dentify.platform.audit;

import kz.dentify.platform.api.AccountNameProvider;
import kz.dentify.platform.api.Audited;
import kz.dentify.platform.api.AuditSnapshots;
import kz.dentify.platform.api.CurrentAccountProvider;
import kz.dentify.platform.api.CurrentPermissions;
import kz.dentify.platform.api.CurrentStaffProvider;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionAttributeSource;

/**
 * Журнал действий (PLT-09): запись ({@code AuditService}, {@link Audited}), чтение для iam ({@code AuditJournal}) и
 * партиции заранее. {@code @EnableScheduling} включён здесь: партиции — первая задача по расписанию платформы;
 * планировщик — автоконфигурации Spring Boot, на виртуальных потоках.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(AuditProperties.class)
class AuditConfiguration {

	@Bean
	AuditDiff auditDiff(JsonMapper json) {
		return new AuditDiff(json);
	}

	@Bean
	AuditTexts auditTexts(MessageSource messages) {
		return new AuditTexts(messages);
	}

	@Bean
	ViewWindows auditViewWindows(StringRedisTemplate redis, AuditProperties properties) {
		return new ViewWindows(redis, properties.getViewDedupWindow());
	}

	@Bean
	JdbcAuditService auditService(JdbcClient jdbc, AuditDiff diff, AuditTexts texts, JsonMapper json,
			CurrentAccountProvider accounts, ObjectProvider<CurrentStaffProvider> staff, ViewWindows views,
			PlatformTransactionManager transactions) {
		return new JdbcAuditService(jdbc, diff, texts, json, accounts, staff, views, transactions);
	}

	@Bean
	JdbcAuditJournal auditJournal(JdbcClient jdbc, AuditTexts texts, JsonMapper json, CurrentPermissions permissions,
			ObjectProvider<AccountNameProvider> names, JdbcAuditService audit, AuditProperties properties) {
		return new JdbcAuditJournal(jdbc, texts, json, permissions, names, audit, properties);
	}

	@Bean
	AuditPartitions auditPartitions(JdbcClient jdbc, AuditProperties properties) {
		return new AuditPartitions(jdbc, properties.getPartitions().getMonthsAhead());
	}

	/**
	 * {@link Audited} на методах бинов. Приоритет — сразу перед транзакцией ({@code @Transactional} — самый низкий):
	 * после {@code @PreAuthorize}, то есть снимки не читаются для того, кому метод запрещён. Интерцептор сам открывает
	 * транзакцию по атрибуту метода, поэтому от порядка с советником транзакций не зависит.
	 */
	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static Advisor auditedAdvisor(ObjectProvider<JdbcAuditService> audit, ObjectProvider<AuditDiff> diff,
			ObjectProvider<AuditSnapshots<?>> snapshots, ObjectProvider<TransactionAttributeSource> transactionAttributes,
			ObjectProvider<PlatformTransactionManager> transactionManager) {
		AuditedInterceptor interceptor = new AuditedInterceptor(audit, diff, snapshots, transactionAttributes,
				transactionManager);
		DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(
				new AnnotationMatchingPointcut(null, Audited.class, true), interceptor);
		advisor.setOrder(Ordered.LOWEST_PRECEDENCE - 1);
		return advisor;
	}

}
