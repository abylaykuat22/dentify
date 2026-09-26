package archfixtures.tenancy;

import jakarta.persistence.Entity;
import kz.dentify.platform.api.GlobalEntity;

/** Фикстура ArchUnit: глобальная сущность вне модулей, где глобальные таблицы разрешены. */
@Entity
public class DictionaryTerm extends GlobalEntity {

}
