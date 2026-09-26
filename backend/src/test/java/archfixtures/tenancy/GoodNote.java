package archfixtures.tenancy;

import jakarta.persistence.Entity;
import kz.dentify.platform.api.TenantEntity;

/** Фикстура ArchUnit: правильная сущность клиники. Вне пакета kz.dentify — JPA и Spring её не видят. */
@Entity
public class GoodNote extends TenantEntity {

}
