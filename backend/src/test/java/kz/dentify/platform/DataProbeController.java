package kz.dentify.platform;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.jspecify.annotations.Nullable;

import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Пробные эндпоинты только для тестов: настоящие ошибки PostgreSQL и Hibernate через JPA, без подделанных
 * исключений. Запись без проверок Bean Validation, как при пропущенной проверке в сервисе. Путь — под
 * {@link ProblemProbeController#PATH}: вне {@code /api/**} и в пробной группе springdoc. Top-level класс — контекст
 * остаётся один (см. {@code AbstractIntegrationTest}).
 */
@RestController
@RequestMapping(DataProbeController.PATH)
class DataProbeController {

	static final String PATH = ProblemProbeController.PATH + "/data";

	private final ConstraintProbeRepository repository;

	private final EntityManager entityManager;

	private final TransactionTemplate transactions;

	DataProbeController(ConstraintProbeRepository repository, EntityManager entityManager,
			TransactionTemplate transactions) {
		this.repository = repository;
		this.entityManager = entityManager;
		this.transactions = transactions;
	}

	/** Новая запись: повтор кода — уникальность, отрицательное qty — CHECK, код null — NOT NULL. */
	@PostMapping
	ProbeView create(@RequestBody ProbeWrite request) {
		return ProbeView.of(
				this.repository.saveAndFlush(new ConstraintProbe(UUID.randomUUID(), request.code(), request.qty(), null)));
	}

	/** Изменение с версией, которую клиент видел: устаревшая версия — конфликт {@code @Version} от Hibernate. */
	@PutMapping("/{id}")
	ProbeView update(@PathVariable UUID id, @RequestBody ProbeWrite request) {
		return ProbeView
			.of(this.repository.saveAndFlush(new ConstraintProbe(id, request.code(), request.qty(), request.version())));
	}

	/**
	 * Ссылка на запись, которой нет или которую скрыла RLS, в транзакции без перевода исключений Spring: Hibernate
	 * бросает {@code EntityNotFoundException} JPA при загрузке прокси.
	 */
	@GetMapping("/{id}")
	@Nullable String codeViaEntityManager(@PathVariable UUID id) {
		return this.transactions
			.execute((status) -> this.entityManager.getReference(ConstraintProbe.class, id).getCode());
	}

	/** То же через репозиторий Spring Data: исключение переведено в {@code JpaObjectRetrievalFailureException}. */
	@GetMapping("/{id}/code")
	@Nullable String codeViaRepository(@PathVariable UUID id) {
		return this.repository.codeByReference(id);
	}

	record ProbeWrite(@Nullable String code, int qty, @Nullable Long version) {
	}

	record ProbeView(UUID id, @Nullable String code, int qty, @Nullable Long version) {

		static ProbeView of(ConstraintProbe probe) {
			return new ProbeView(probe.getId(), probe.getCode(), probe.getQty(), probe.getVersion());
		}

	}

}
