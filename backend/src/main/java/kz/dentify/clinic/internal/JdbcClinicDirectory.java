package kz.dentify.clinic.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import kz.dentify.clinic.api.ClinicDirectory;
import kz.dentify.platform.api.TenantContext;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Клиника контекста и её филиалы запросами SQL: сущностей клиники до T-CLN-02 нет. Условие по клинике контекста —
 * в самом запросе ({@code current_clinic_id()}), вторым рубежом — политика RLS: другой клиники запрос не видит.
 */
@Component
class JdbcClinicDirectory implements ClinicDirectory {

	private final JdbcTemplate jdbc;

	JdbcClinicDirectory(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ClinicInfo> current() {
		UUID clinicId = TenantContext.clinicId();
		List<BranchInfo> branches = this.jdbc.query(
				"select id, name, city from branch where clinic_id = current_clinic_id() order by name, id",
				(row, number) -> new BranchInfo(row.getObject("id", UUID.class), row.getString("name"),
						row.getString("city")));
		return this.jdbc
			.query("select id, name, city from clinic where id = current_clinic_id() and id = ?",
					(row, number) -> new ClinicInfo(row.getObject("id", UUID.class), row.getString("name"),
							row.getString("city"), branches),
					clinicId)
			.stream()
			.findFirst();
	}

}
