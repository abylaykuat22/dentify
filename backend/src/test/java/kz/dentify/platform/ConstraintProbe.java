package kz.dentify.platform;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.jspecify.annotations.Nullable;

/**
 * Пробная сущность только для тестов: настоящие ошибки БД и Hibernate для {@link DataProbeController} — нарушение
 * уникальности, NOT NULL и CHECK, конфликт {@code @Version}. Проверок Bean Validation у неё нет намеренно: так
 * значение доходит до ограничения БД, как при пропущенной проверке в сервисе. Таблица — в тестовой миграции
 * {@code db/test-migration}.
 */
@Entity
@Table(name = "test_constraint_probe")
class ConstraintProbe {

	@Id
	private UUID id;

	private @Nullable String code;

	private int qty;

	/** null — новая запись: Spring Data тогда вызывает persist, иначе merge с проверкой версии. */
	@Version
	private @Nullable Long version;

	protected ConstraintProbe() {
		this(UUID.randomUUID(), null, 0, null);
	}

	ConstraintProbe(UUID id, @Nullable String code, int qty, @Nullable Long version) {
		this.id = id;
		this.code = code;
		this.qty = qty;
		this.version = version;
	}

	UUID getId() {
		return this.id;
	}

	@Nullable String getCode() {
		return this.code;
	}

	int getQty() {
		return this.qty;
	}

	@Nullable Long getVersion() {
		return this.version;
	}

}
