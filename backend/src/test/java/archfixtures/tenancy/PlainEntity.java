package archfixtures.tenancy;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Фикстура ArchUnit: сущность без TenantEntity и без явной пометки глобальной — правило должно её поймать. */
@Entity
public class PlainEntity {

	@Id
	private UUID id;

}
