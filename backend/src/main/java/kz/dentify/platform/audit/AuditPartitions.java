package kz.dentify.platform.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Партиции {@code audit_log} заранее: без партиции на момент события вставка падает, а с ней и изменение. Функция БД
 * {@code audit_log_create_partitions} (SECURITY DEFINER, права владельца схемы) создаёт недостающие: прошлый месяц,
 * текущий и {@code dentify.audit.partitions.months-ahead} вперёд. Вызывают её трижды с запасом:
 * <ul>
 * <li>миграция — при первом развёртывании;</li>
 * <li>старт приложения — после простоя или развёртывания без новых миграций;</li>
 * <li>задача по расписанию раз в сутки ({@code dentify.audit.partitions.cron}, UTC) — приложение работает месяцами
 * без рестарта. ShedLock не нужен: функция идемпотентна, одновременные вызовы идут по очереди под advisory-lock.</li>
 * </ul>
 * Сбой не роняет приложение: партиции уже есть на несколько месяцев вперёд, следующий запуск повторит. В лог — ERROR,
 * по нему алерт.
 */
class AuditPartitions implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AuditPartitions.class);

	private final JdbcClient jdbc;

	private final int monthsAhead;

	AuditPartitions(JdbcClient jdbc, int monthsAhead) {
		this.jdbc = jdbc;
		this.monthsAhead = monthsAhead;
	}

	@Override
	public void run(ApplicationArguments args) {
		ensure();
	}

	@Scheduled(cron = "${dentify.audit.partitions.cron:0 17 3 * * *}", zone = "UTC")
	void scheduled() {
		ensure();
	}

	/** Создать недостающие партиции; сколько создано, при сбое — -1. */
	int ensure() {
		try {
			Integer created = this.jdbc.sql("select audit_log_create_partitions(:months)")
				.param("months", this.monthsAhead)
				.query(Integer.class)
				.single();
			if (created != null && created > 0) {
				log.info("Созданы партиции журнала аудита: {}", created);
			}
			return (created != null) ? created : 0;
		}
		catch (DataAccessException ex) {
			log.error("Партиции журнала аудита не созданы, повтор — при следующем запуске задачи", ex);
			return -1;
		}
	}

}
