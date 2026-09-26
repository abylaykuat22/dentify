package kz.dentify.platform.api;

/**
 * Права, на которые опирается платформа. Каталог прав, роли и выдача — модуль iam (T-IAM-06); коды прав модули
 * объявляют сами через SPI платформы, а не импортом iam; здесь — только права механизмов платформы. В коде —
 * {@link CurrentPermissions}; на эндпоинте сейчас {@code hasAuthority('audit:read')}, с T-IAM-07 — бин платформы для
 * SpEL (docs/architecture.md, Права).
 */
public final class Permissions {

	/** Чтение журнала действий клиники. */
	public static final String AUDIT_READ = "audit:read";

	/** Видеть телефоны, ИИН и e-mail пациентов без маски, в том числе в разнице журнала. */
	public static final String PATIENT_PHONES = "patient:phones";

	private Permissions() {
	}

}
