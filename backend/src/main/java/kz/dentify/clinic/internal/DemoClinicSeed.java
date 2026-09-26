package kz.dentify.clinic.internal;

import kz.dentify.clinic.api.DemoClinic;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dev-сид демо-клиники (только профиль {@code dev}): клиника «Dentify Demo» и филиал «Абая, 150» с постоянными id,
 * данные вымышленные. Строки вставляются SQL, а не через сущности: сущностей клиники до T-CLN-02 нет, а id сущности
 * создаёт Hibernate и задать его нельзя. Вставка — в контексте самой демо-клиники: без него политики RLS клиники и
 * филиала запись не пропустят. Уже есть — ничего не меняется.
 */
@Component
@Profile("dev")
class DemoClinicSeed implements DemoClinic {

	static final String NAME = "Dentify Demo";

	static final String CITY = "Алматы";

	static final String BRANCH_NAME = "Абая, 150";

	private static final Logger log = LoggerFactory.getLogger(DemoClinicSeed.class);

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transactions;

	DemoClinicSeed(JdbcTemplate jdbc, TransactionTemplate transactions) {
		this.jdbc = jdbc;
		this.transactions = transactions;
	}

	@Override
	public Tenant ensure() {
		Tenant demo = new Tenant(CLINIC_ID, BRANCH_ID);
		TenantContext.run(demo, () -> this.transactions.executeWithoutResult((status) -> {
			int created = this.jdbc.update("insert into clinic (id, name, city) values (?, ?, ?) on conflict (id) do nothing",
					CLINIC_ID, NAME, CITY);
			created += this.jdbc.update(
					"insert into branch (id, clinic_id, name, city) values (?, ?, ?, ?) on conflict (id) do nothing",
					BRANCH_ID, CLINIC_ID, BRANCH_NAME, CITY);
			if (created > 0) {
				log.info("Dev-сид: создана демо-клиника {}", CLINIC_ID);
			}
		}));
		return demo;
	}

}
