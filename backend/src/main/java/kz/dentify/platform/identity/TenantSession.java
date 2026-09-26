package kz.dentify.platform.identity;

import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import org.jspecify.annotations.Nullable;

/**
 * Клиника и филиал в HTTP-сессии — источник контекста клиники для запросов. Пишут их вход и выбор клиники (T-IAM-02)
 * после проверки, что аккаунт — сотрудник этой клиники; читает платформа в начале каждого запроса и ставит
 * {@link TenantContext}. Клиника из тела или параметров запроса контекстом не становится никогда.
 * <p>
 * В атрибутах — только идентификаторы ({@link UUID}), а не объекты: сессия хранится в Valkey и не должна зависеть
 * от классов приложения.
 */
public final class TenantSession {

	/** Атрибут сессии: id выбранной клиники. */
	public static final String CLINIC_ID = "dentify.tenant.clinicId";

	/** Атрибут сессии: id выбранного филиала, может отсутствовать. */
	public static final String BRANCH_ID = "dentify.tenant.branchId";

	private TenantSession() {
	}

	/** Выбрать клинику и филиал для следующих запросов этой сессии. */
	public static void select(HttpSession session, Tenant tenant) {
		session.setAttribute(CLINIC_ID, tenant.clinicId());
		if (tenant.branchId() != null) {
			session.setAttribute(BRANCH_ID, tenant.branchId());
		}
		else {
			session.removeAttribute(BRANCH_ID);
		}
	}

	/** Снять выбор клиники: следующие запросы идут без контекста клиники. */
	public static void clear(HttpSession session) {
		session.removeAttribute(CLINIC_ID);
		session.removeAttribute(BRANCH_ID);
	}

	/** Клиника и филиал, выбранные в сессии, или пусто. */
	public static Optional<Tenant> read(HttpSession session) {
		if (!(session.getAttribute(CLINIC_ID) instanceof UUID clinicId)) {
			return Optional.empty();
		}
		@Nullable UUID branchId = (session.getAttribute(BRANCH_ID) instanceof UUID branch) ? branch : null;
		return Optional.of(new Tenant(clinicId, branchId));
	}

}
