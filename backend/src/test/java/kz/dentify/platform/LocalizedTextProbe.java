package kz.dentify.platform;

import java.util.UUID;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kz.dentify.platform.api.LocalizedText;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Пробная сущность только для тестов: {@link LocalizedText} как JSON-агрегат Hibernate 7 в колонке {@code jsonb} —
 * так его будут хранить справочники модулей. Таблица — в тестовой миграции {@code db/test-migration}.
 */
@Entity
@Table(name = "test_localized_probe")
class LocalizedTextProbe {

	@Id
	private UUID id;

	@Embedded
	@JdbcTypeCode(SqlTypes.JSON)
	private LocalizedText name;

	protected LocalizedTextProbe() {
		this(UUID.randomUUID(), LocalizedText.of("-"));
	}

	LocalizedTextProbe(UUID id, LocalizedText name) {
		this.id = id;
		this.name = name;
	}

	UUID getId() {
		return this.id;
	}

	LocalizedText getName() {
		return this.name;
	}

}
