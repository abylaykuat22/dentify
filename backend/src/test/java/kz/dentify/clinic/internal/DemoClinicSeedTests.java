package kz.dentify.clinic.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import kz.dentify.AbstractIntegrationTest;
import kz.dentify.clinic.api.DemoClinic;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dev-сид демо-клиники: бин есть только в профиле dev, поэтому тест собирает его сам на тестовой базе. Повторный запуск
 * ничего не меняет.
 */
class DemoClinicSeedTests extends AbstractIntegrationTest {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TransactionTemplate transactions;

	@Test
	void createsDemoClinicWithBranchOnce() {
		DemoClinicSeed seed = new DemoClinicSeed(this.jdbc, this.transactions);

		Tenant first = seed.ensure();
		Tenant second = seed.ensure();

		assertThat(first).isEqualTo(second).isEqualTo(new Tenant(DemoClinic.CLINIC_ID, DemoClinic.BRANCH_ID));
		TenantContext.run(Tenant.of(DemoClinic.CLINIC_ID), () -> {
			assertThat(this.jdbc.queryForList("select name, city from clinic")).containsExactly(
					Map.of("name", "Dentify Demo", "city", "Алматы"));
			assertThat(this.jdbc.queryForList("select id, name, city from branch")).containsExactly(
					Map.of("id", DemoClinic.BRANCH_ID, "name", "Абая, 150", "city", "Алматы"));
		});
	}

}
