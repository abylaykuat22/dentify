package kz.dentify.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.UUID;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import kz.dentify.AbstractIntegrationTest;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Клиника в соединениях пула (PLT-02): {@link ClinicConnectionDataSource} ставит {@code app.clinic_id} при каждой
 * выдаче соединения. Пул из одного соединения — все обращения идут через одно и то же физическое соединение
 * ({@code pg_backend_pid()}), и видно, что клиника прежнего владельца не переходит к следующему.
 */
class ClinicConnectionDataSourceTests extends AbstractIntegrationTest {

	@Autowired
	DataSource applicationDataSource;

	HikariDataSource pool;

	JdbcTemplate jdbc;

	@BeforeEach
	void singleConnectionPool() {
		this.pool = new HikariDataSource();
		this.pool.setJdbcUrl(POSTGRES.getJdbcUrl());
		this.pool.setUsername(POSTGRES.getUsername());
		this.pool.setPassword(POSTGRES.getPassword());
		this.pool.setMaximumPoolSize(1);
		this.pool.setPoolName("clinic-connection-test");
		this.jdbc = new JdbcTemplate(new ClinicConnectionDataSource(this.pool));
	}

	@AfterEach
	void closePool() {
		this.pool.close();
	}

	@Test
	void applicationDataSourceIsWrapped() {
		assertThat(this.applicationDataSource).isInstanceOf(ClinicConnectionDataSource.class);
		assertThat(((DelegatingDataSource) this.applicationDataSource).getTargetDataSource())
			.isInstanceOf(HikariDataSource.class);
	}

	/** Приёмка PLT-02: два запроса разных клиник подряд в одном соединении видят каждый свои данные. */
	@Test
	void consecutiveClinicsOnOneConnectionSeeOnlyTheirRows() {
		UUID clinicA = UUID.randomUUID();
		UUID clinicB = UUID.randomUUID();
		insert(clinicA, "Запись A");
		insert(clinicB, "Запись B");

		Integer pid = this.jdbc.queryForObject("select pg_backend_pid()", Integer.class);
		assertThat(TenantContext.call(Tenant.of(clinicA), () -> titles(clinicA, clinicB))).isEqualTo("Запись A");
		assertThat(titles(clinicA, clinicB)).as("следующий запрос без контекста").isEmpty();
		assertThat(TenantContext.call(Tenant.of(clinicB), () -> titles(clinicA, clinicB))).isEqualTo("Запись B");
		assertThat(this.jdbc.queryForObject("select current_setting('app.clinic_id', true)", String.class))
			.as("без контекста в соединении пусто").isEmpty();
		assertThat(this.jdbc.queryForObject("select pg_backend_pid()", Integer.class)).as("то же соединение")
			.isEqualTo(pid);
	}

	/** Внутри транзакции соединение одно, клиника — та, что была при открытии; сменить её нельзя. */
	@Test
	void transactionKeepsClinicOfItsStart() {
		UUID clinic = UUID.randomUUID();
		insert(clinic, "В транзакции");
		TransactionTemplate transactions = new TransactionTemplate(
				new DataSourceTransactionManager(this.jdbc.getDataSource()));

		String titles = TenantContext.call(Tenant.of(clinic), () -> transactions.execute((status) -> {
			assertThatThrownBy(() -> TenantContext.run(Tenant.of(UUID.randomUUID()), () -> {
			})).as("сменить клинику внутри транзакции нельзя").isInstanceOf(IllegalStateException.class);
			TenantContext.run(new Tenant(clinic, UUID.randomUUID()), () -> {
				// та же клиника с филиалом — можно
			});
			return titles(clinic);
		}));

		assertThat(titles).isEqualTo("В транзакции");
	}

	private void insert(UUID clinic, String title) {
		TenantContext.run(Tenant.of(clinic), () -> this.jdbc
			.update("insert into test_note (clinic_id, title) values (?, ?)", clinic, title));
	}

	/** Видимые записи этих клиник через запятую; условие по клинике не мешает RLS, а ограничивает чужие тесты. */
	private String titles(UUID... clinics) {
		String placeholders = String.join(", ", Collections.nCopies(clinics.length, "?"));
		return String.join(",", this.jdbc.queryForList(
				"select title from test_note where clinic_id in (" + placeholders + ") order by title", String.class,
				(Object[]) clinics));
	}

}
