package kz.dentify.platform.tenancy;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Репозиторий пробной сущности клиники {@link TestNote}, только для тестов. */
interface TestNoteRepository extends JpaRepository<TestNote, UUID> {

	/** Метод запроса без {@code @Transactional}: Spring Data выполняет его вне транзакции. */
	List<TestNote> findByTitle(String title);

	/**
	 * Нативный запрос без условия по клинике — так в рабочем коде нельзя (ArchUnit-правило {@code TenancyRulesTests}).
	 * Здесь он показывает второй рубеж: фильтр Hibernate к нативному SQL не применяется, строки чужих клиник скрывает
	 * RLS.
	 */
	@Query(value = "select count(*) from test_note", nativeQuery = true)
	long countVisibleRows();

}
