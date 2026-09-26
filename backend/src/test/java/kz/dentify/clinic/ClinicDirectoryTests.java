package kz.dentify.clinic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import kz.dentify.AbstractIntegrationTest;
import kz.dentify.clinic.api.ClinicDirectory;
import kz.dentify.clinic.api.ClinicDirectory.BranchInfo;
import kz.dentify.clinic.api.ClinicDirectory.ClinicInfo;
import kz.dentify.platform.api.MissingTenantContextException;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * Клиника контекста и её филиалы для выбора клиники (T-IAM-02): только своя клиника, филиалы по названию; без
 * контекста — отказ, а не пусто. Клиники — вымышленные.
 */
class ClinicDirectoryTests extends AbstractIntegrationTest {

	@Autowired
	ClinicDirectory directory;

	@Autowired
	TestClinics clinics;

	@Test
	void currentClinicWithBranchesByName() {
		Tenant clinic = this.clinics.create();
		UUID second = this.clinics.addBranch(clinic.clinicId());
		Tenant other = this.clinics.create();

		ClinicInfo info = TenantContext.call(Tenant.of(clinic.clinicId()), () -> this.directory.current()).orElseThrow();

		assertThat(info.id()).isEqualTo(clinic.clinicId());
		assertThat(info.name()).isEqualTo("Тестовая клиника");
		assertThat(info.branches()).extracting(BranchInfo::id).containsExactly(clinic.branchId(), second)
			.doesNotContain(other.branchId());
		assertThat(info.branches()).extracting(BranchInfo::name).containsExactly("Кенесары, 40", "Сарыарка, 3");
		assertThat(info.branch(second)).map(BranchInfo::city).contains("Астана");
		assertThat(info.branch(other.branchId())).isEmpty();
	}

	@Test
	void unknownClinicIsEmptyAndNoContextIsRefused() {
		assertThat(TenantContext.call(Tenant.of(UUID.randomUUID()), () -> this.directory.current())).isEmpty();
		assertThatThrownBy(() -> this.directory.current()).isInstanceOf(MissingTenantContextException.class);
	}

}
