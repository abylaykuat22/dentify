package archfixtures.tenancy;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;

/** Фикстура ArchUnit: нативный запрос к глобальной таблице clinic_id не требует. */
public interface TermRepository extends JpaRepository<DictionaryTerm, UUID> {

	@NativeQuery("select * from dictionary_term")
	List<DictionaryTerm> all();

}
