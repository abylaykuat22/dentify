package kz.dentify.platform.audit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки журнала действий {@code dentify.audit.*}.
 */
@ConfigurationProperties("dentify.audit")
public class AuditProperties {

	/** Окно просмотров: не больше одного события просмотра на аккаунт и объект за это время. */
	private Duration viewDedupWindow = Duration.ofMinutes(15);

	/** Предел строк выгрузки журнала: больше — 422, нужно сузить период или фильтры. */
	private int exportMaxRows = 10_000;

	/**
	 * Период ленты журнала, если начало не задано: последние 30 дней до конца периода или до сейчас. История одного
	 * объекта (фильтр по объекту) — за всё время.
	 */
	private Duration defaultPeriod = Duration.ofDays(30);

	/** Самый длинный период поиска по тексту: у поиска нет индекса, он просматривает все события периода. */
	private Duration searchMaxPeriod = Duration.ofDays(366);

	private final Partitions partitions = new Partitions();

	public Duration getViewDedupWindow() {
		return this.viewDedupWindow;
	}

	public void setViewDedupWindow(Duration viewDedupWindow) {
		this.viewDedupWindow = viewDedupWindow;
	}

	public int getExportMaxRows() {
		return this.exportMaxRows;
	}

	public void setExportMaxRows(int exportMaxRows) {
		this.exportMaxRows = exportMaxRows;
	}

	public Duration getDefaultPeriod() {
		return this.defaultPeriod;
	}

	public void setDefaultPeriod(Duration defaultPeriod) {
		this.defaultPeriod = defaultPeriod;
	}

	public Duration getSearchMaxPeriod() {
		return this.searchMaxPeriod;
	}

	public void setSearchMaxPeriod(Duration searchMaxPeriod) {
		this.searchMaxPeriod = searchMaxPeriod;
	}

	public Partitions getPartitions() {
		return this.partitions;
	}

	/** Партиции {@code audit_log} заранее. */
	public static class Partitions {

		/** На сколько месяцев вперёд держать партиции, от 1 до 24. */
		private int monthsAhead = 3;

		/** Когда проверять партиции: cron в UTC, по умолчанию раз в сутки ночью. */
		private String cron = "0 17 3 * * *";

		public int getMonthsAhead() {
			return this.monthsAhead;
		}

		public void setMonthsAhead(int monthsAhead) {
			this.monthsAhead = monthsAhead;
		}

		public String getCron() {
			return this.cron;
		}

		public void setCron(String cron) {
			this.cron = cron;
		}

	}

}
