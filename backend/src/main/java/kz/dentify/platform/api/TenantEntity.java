package kz.dentify.platform.api;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/**
 * Базовая сущность данных клиники (PLT-01): всё, что принадлежит клинике, — пациенты, записи, медзаписи, склад, роли
 * и настройки клиники. Кроме общих колонок {@link BaseEntity} несёт {@code clinic_id} с {@code @TenantId} Hibernate:
 * <ul>
 * <li>при сохранении Hibernate сам ставит клинику текущего контекста ({@link TenantContext}); сеттера нет, и клиника
 * из тела запроса в сущность не попадает;</li>
 * <li>при чтении — запросы, поиск по id, связи — Hibernate добавляет условие {@code clinic_id = клиника контекста}:
 * объект другой клиники не находится, и сервис отвечает 404;</li>
 * <li>{@code clinic_id} не обновляется: перенести объект в другую клинику нельзя.</li>
 * </ul>
 * Второй рубеж — RLS в PostgreSQL: таблица сущности создаётся по шаблону docs/architecture.md, «Таблица арендатора».
 * Репозиторий такой сущности без контекста клиники бросает {@link MissingTenantContextException}.
 */
@MappedSuperclass
public abstract class TenantEntity extends BaseEntity {

	@TenantId
	@Column(name = "clinic_id", nullable = false, updatable = false)
	private @Nullable UUID clinicId;

	protected TenantEntity() {
	}

	/** Клиника-владелец; null до первого сохранения. */
	public @Nullable UUID getClinicId() {
		return this.clinicId;
	}

}
