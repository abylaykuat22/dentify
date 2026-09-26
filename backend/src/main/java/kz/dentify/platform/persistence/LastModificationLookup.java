package kz.dentify.platform.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.metamodel.EntityType;
import kz.dentify.platform.api.BaseEntity;
import org.hibernate.Hibernate;
import org.hibernate.StaleObjectStateException;
import org.jspecify.annotations.Nullable;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Кто и когда изменил запись последним — для ответа 409 о конфликте версии (PLT-08). По исключению конфликта
 * находит сущность и id, читает её текущие {@code updated_at} и {@code updated_by} в отдельной транзакции только для
 * чтения — транзакция запроса к этому моменту уже откатилась. Чтение идёт в контексте клиники запроса: сущность
 * другой клиники не найдётся.
 */
@Component
public class LastModificationLookup {

	private final EntityManager entityManager;

	private final EntityManagerFactory entityManagerFactory;

	private final TransactionTemplate readOnly;

	LastModificationLookup(EntityManager entityManager, EntityManagerFactory entityManagerFactory,
			PlatformTransactionManager transactionManager) {
		this.entityManager = entityManager;
		this.entityManagerFactory = entityManagerFactory;
		this.readOnly = new TransactionTemplate(transactionManager);
		this.readOnly.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.readOnly.setReadOnly(true);
	}

	/** Последняя правка сущности из исключения конфликта версии или пусто: сущность не наша, удалена, не найдена. */
	public Optional<LastModification> find(Throwable conflict) {
		Target target = target(conflict);
		if (target == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(this.readOnly.execute((status) -> {
			Object entity = this.entityManager.find(target.type(), target.id());
			if (entity instanceof BaseEntity current && current.getUpdatedAt() != null) {
				return new LastModification(current.getUpdatedAt(), current.getUpdatedBy());
			}
			return null;
		}));
	}

	private @Nullable Target target(Throwable conflict) {
		for (Throwable cause = conflict; cause != null; cause = cause.getCause()) {
			Target target = switch (cause) {
				case ObjectOptimisticLockingFailureException spring -> target(
						(spring.getPersistentClass() != null) ? spring.getPersistentClass()
								: entityType(spring.getPersistentClassName()),
						spring.getIdentifier());
				case StaleObjectStateException hibernate -> target(entityType(hibernate.getEntityName()),
						hibernate.getIdentifier());
				case OptimisticLockException jpa when jpa.getEntity() != null -> target(
						Hibernate.getClassLazy(jpa.getEntity()),
						this.entityManagerFactory.getPersistenceUnitUtil().getIdentifier(jpa.getEntity()));
				default -> null;
			};
			if (target != null) {
				return target;
			}
		}
		return null;
	}

	private static @Nullable Target target(@Nullable Class<?> type, @Nullable Object id) {
		if (type == null || id == null || !BaseEntity.class.isAssignableFrom(type)) {
			return null;
		}
		return new Target(type, id);
	}

	/** Класс сущности по имени Hibernate: полное имя класса или имя сущности JPA. */
	private @Nullable Class<?> entityType(@Nullable String name) {
		if (name == null) {
			return null;
		}
		for (EntityType<?> entity : this.entityManagerFactory.getMetamodel().getEntities()) {
			if (entity.getJavaType().getName().equals(name) || entity.getName().equals(name)) {
				return entity.getJavaType();
			}
		}
		return null;
	}

	/**
	 * Последняя правка записи.
	 *
	 * @param at время правки
	 * @param by id аккаунта автора или null, если правка системная или без входа
	 */
	public record LastModification(Instant at, @Nullable UUID by) {
	}

	private record Target(Class<?> type, Object id) {
	}

}
