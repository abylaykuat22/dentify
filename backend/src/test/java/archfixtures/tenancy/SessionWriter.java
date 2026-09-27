package archfixtures.tenancy;

import java.util.UUID;

import jakarta.servlet.http.HttpSession;

/**
 * Фикстура ArchUnit: модуль сам кладёт клинику в HTTP-сессию строкой атрибута, мимо iam и проверки членства, и сам
 * гасит сессию мимо отметок отзыва, — нельзя. Ссылки на {@code TenantSession} в байткоде нет, поэтому Modulith этого не
 * видит.
 */
public class SessionWriter {

	public void selectClinic(HttpSession session, UUID clinicId) {
		session.setAttribute("dentify.tenant.clinicId", clinicId);
	}

	public void signOut(HttpSession session) {
		session.invalidate();
	}

}
