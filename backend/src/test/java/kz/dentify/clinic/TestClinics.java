package kz.dentify.clinic;

import java.util.UUID;

import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Вымышленные клиники и филиалы для тестов, пока у модуля clinic нет своего API создания (T-CLN-02, T-CLN-04). Строки
 * вставляются в контексте самой клиники: иначе их не пропустят политики RLS. Только тестовые исходники.
 */
@Component
public class TestClinics {

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transactions;

	TestClinics(JdbcTemplate jdbc, TransactionTemplate transactions) {
		this.jdbc = jdbc;
		this.transactions = transactions;
	}

	/** Новая клиника с одним филиалом. */
	public Tenant create() {
		return ensure(UUID.randomUUID(), UUID.randomUUID());
	}

	/** Клиника и филиал с этими id, если их ещё нет. */
	public Tenant ensure(UUID clinicId, UUID branchId) {
		Tenant tenant = new Tenant(clinicId, branchId);
		TenantContext.run(tenant, () -> this.transactions.executeWithoutResult((status) -> {
			this.jdbc.update("insert into clinic (id, name, city) values (?, 'Тестовая клиника', 'Астана') "
					+ "on conflict (id) do nothing", clinicId);
			this.jdbc.update("insert into branch (id, clinic_id, name, city) values (?, ?, 'Кенесары, 40', 'Астана') "
					+ "on conflict (id) do nothing", branchId, clinicId);
		}));
		return tenant;
	}

	/** Ещё один филиал клиники. */
	public UUID addBranch(UUID clinicId) {
		UUID branchId = UUID.randomUUID();
		TenantContext.run(Tenant.of(clinicId), () -> this.jdbc
			.update("insert into branch (id, clinic_id, name, city) values (?, ?, 'Сарыарка, 3', 'Астана')", branchId,
					clinicId));
		return branchId;
	}

}
