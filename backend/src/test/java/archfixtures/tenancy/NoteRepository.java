package archfixtures.tenancy;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;

/** Фикстура ArchUnit: репозиторий сущности клиники с нативным запросом без clinic_id — нарушение. */
public interface NoteRepository extends JpaRepository<GoodNote, UUID> {

	@Query(value = "select * from note", nativeQuery = true)
	List<GoodNote> everything();

	@NativeQuery("select * from note where clinic_id = current_clinic_id()")
	List<GoodNote> scoped();

	@Query("select n from GoodNote n")
	List<GoodNote> jpql();

}
