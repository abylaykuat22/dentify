package kz.dentify.iam.internal;

/** Статус аккаунта во всём Dentify; доступ к клинике отдельно задаёт статус членства ({@link MembershipStatus}). */
public enum AccountStatus {

	/** Может входить. */
	ACTIVE,

	/** Заблокирован во всех клиниках: вход закрыт. */
	DISABLED

}
