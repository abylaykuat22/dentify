package kz.dentify.platform.tenancy;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import kz.dentify.platform.api.TenantEntity;
import org.jspecify.annotations.Nullable;

/**
 * Пробная сущность клиники только для тестов изоляции (T-PLT-03): наследует {@link TenantEntity}, как любая сущность
 * данных клиники. Таблица — в тестовой миграции {@code db/test-migration}, по шаблону таблицы арендатора.
 */
@Entity
@Table(name = "test_note")
class TestNote extends TenantEntity {

	@Column(name = "branch_id")
	private @Nullable UUID branchId;

	private String title;

	@Column(name = "archived_at")
	private @Nullable Instant archivedAt;

	protected TestNote() {
		this.title = "";
	}

	TestNote(String title, @Nullable UUID branchId) {
		this.title = title;
		this.branchId = branchId;
	}

	@Nullable UUID getBranchId() {
		return this.branchId;
	}

	String getTitle() {
		return this.title;
	}

	@Nullable Instant getArchivedAt() {
		return this.archivedAt;
	}

	void rename(String title) {
		this.title = title;
	}

	void archive() {
		this.archivedAt = Instant.now();
	}

}
