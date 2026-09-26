package kz.dentify.platform.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Общие колонки сущностей Dentify: {@code id}, {@code created_at/by}, {@code updated_at/by}, {@code version}
 * (docs/architecture.md, «Колонки»). Сущность модуля наследует не этот класс, а {@link TenantEntity} (данные клиники)
 * или {@link GlobalEntity} (глобальная таблица) — это проверяет ArchUnit-тест {@code TenancyRulesTests}.
 * <ul>
 * <li>{@code id} — UUIDv7, создаёт Hibernate при сохранении ({@code @UuidGenerator} со стилем {@code VERSION_7}):
 * id известен до INSERT, строки сортируются по времени создания. У колонки есть и {@code DEFAULT uuidv7()} — для
 * вставок SQL в миграциях;</li>
 * <li>кто и когда создал и изменил — аудит Spring Data JPA. Время — {@link Instant}, кто — id аккаунта из
 * {@link CurrentAccountProvider}; вне входа — null;</li>
 * <li>{@code version} — оптимистическая блокировка: устаревшая версия даёт 409 {@code error.version-conflict} с тем,
 * кто и когда изменил запись последним (PLT-08).</li>
 * </ul>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private @Nullable UUID id;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private @Nullable Instant createdAt;

	@CreatedBy
	@Column(name = "created_by", updatable = false)
	private @Nullable UUID createdBy;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private @Nullable Instant updatedAt;

	@LastModifiedBy
	@Column(name = "updated_by")
	private @Nullable UUID updatedBy;

	/** null — новая, ещё не сохранённая сущность: Spring Data тогда вызывает persist, а не merge. */
	@Version
	@Column(name = "version", nullable = false)
	private @Nullable Long version;

	protected BaseEntity() {
	}

	/** Id сущности; null до первого сохранения. */
	public @Nullable UUID getId() {
		return this.id;
	}

	public @Nullable Instant getCreatedAt() {
		return this.createdAt;
	}

	public @Nullable UUID getCreatedBy() {
		return this.createdBy;
	}

	public @Nullable Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public @Nullable UUID getUpdatedBy() {
		return this.updatedBy;
	}

	/** Версия для оптимистической блокировки; её клиент присылает обратно при изменении. */
	public @Nullable Long getVersion() {
		return this.version;
	}

	/**
	 * Проверяет версию, которую клиент видел при редактировании. Вызывается в сервисе до изменения загруженной
	 * сущности: Hibernate сверяет версию только с той, что загрузил сам, и о правке после открытия формы не узнает.
	 *
	 * @throws ObjectOptimisticLockingFailureException если запись уже изменили: ответ 409 с автором и временем
	 * последней правки
	 */
	public void checkVersion(long expected) {
		if (this.version == null || this.version != expected) {
			throw new ObjectOptimisticLockingFailureException(getClass(), Objects.requireNonNull(this.id, "id"));
		}
	}

	/**
	 * Равенство по id и классу сущности, в том числе для прокси Hibernate; у новой сущности без id равна только она
	 * сама. Поля читаются через геттеры: у прокси свои поля пустые.
	 */
	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof BaseEntity entity) || Hibernate.getClassLazy(this) != Hibernate.getClassLazy(other)) {
			return false;
		}
		UUID ownId = getId();
		return ownId != null && ownId.equals(entity.getId());
	}

	/** Постоянный для сущности хэш: id появляется только при сохранении. */
	@Override
	public int hashCode() {
		return Hibernate.getClassLazy(this).hashCode();
	}

}
