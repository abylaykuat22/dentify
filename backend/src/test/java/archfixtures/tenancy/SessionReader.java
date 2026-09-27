package archfixtures.tenancy;

import jakarta.servlet.http.HttpSession;

/** Фикстура ArchUnit: чтение атрибута сессии — не запись, можно. */
public class SessionReader {

	public Object clinic(HttpSession session) {
		return session.getAttribute("dentify.tenant.clinicId");
	}

}
