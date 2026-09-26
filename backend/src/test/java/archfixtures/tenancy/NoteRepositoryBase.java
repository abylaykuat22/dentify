package archfixtures.tenancy;

import java.util.UUID;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/** Фикстура ArchUnit: промежуточный интерфейс репозитория. */
@NoRepositoryBean
public interface NoteRepositoryBase extends Repository<GoodNote, UUID> {

}
