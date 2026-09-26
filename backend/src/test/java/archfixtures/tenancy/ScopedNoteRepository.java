package archfixtures.tenancy;

import java.util.List;

import org.springframework.data.jpa.repository.NativeQuery;

/** Фикстура ArchUnit: нативные запросы с условием по клинике, репозиторий через промежуточный интерфейс. */
public interface ScopedNoteRepository extends NoteRepositoryBase {

	@NativeQuery("select * from note where clinic_id = current_clinic_id() and title = ?1")
	List<GoodNote> byTitle(String title);

}
