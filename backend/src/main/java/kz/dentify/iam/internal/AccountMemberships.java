package kz.dentify.iam.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Свои членства аккаунта во всех клиниках — единственное чтение данных клиники поверх границы клиник (IAM-01:
 * «аккаунт с членством в двух клиниках входит одним паролем и видит обе клиники»). Нужно входу и выбору клиники
 * (T-IAM-02, T-IAM-08), когда клиника ещё не выбрана и обычная политика RLS не показывает ничего.
 * <p>
 * Как устроено: в своей транзакции ставит {@code app.account_id} (транзакционно, {@code set_config(…, true)}), читает
 * членства аккаунта и сразу снимает настройку. Строки пропускает политика {@code own_memberships}
 * ({@code account_id = current_account_id()}, только SELECT) — изменить или удалить членство чужой клиники ею нельзя.
 * Отдаёт только клинику, id и статус: название клиники и филиалы читаются уже в контексте каждой клиники
 * ({@code TenantContext.run}).
 * <p>
 * JdbcTemplate, а не репозиторий: репозиторий данных клиники без контекста клиники отказывает, а фильтр Hibernate
 * оставил бы только клинику контекста. {@code app.account_id} ставит только этот класс.
 */
@Component
public class AccountMemberships {

	private final JdbcTemplate jdbc;

	AccountMemberships(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Членства аккаунта во всех клиниках, в порядке создания. Статус не фильтруется: отключённые нужны входу для
	 * сообщения «Доступ отключён» (IAM-02).
	 */
	@Transactional(readOnly = true)
	public List<ClinicMembership> of(UUID accountId) {
		this.jdbc.queryForObject("select set_config('app.account_id', ?, true)", String.class, accountId.toString());
		List<ClinicMembership> memberships = this.jdbc.query(
				"select id, clinic_id, status from membership where account_id = ? order by created_at, id",
				(row, number) -> new ClinicMembership(row.getObject("id", UUID.class),
						row.getObject("clinic_id", UUID.class), MembershipStatus.valueOf(row.getString("status"))),
				accountId);
		// Снять сразу, а не в конце транзакции: вызвавшая транзакция может читать членства дальше. Ошибка запроса выше
		// откатывает транзакцию, и настройка исчезает вместе с ней (снимать в finally по прерванной транзакции нельзя).
		this.jdbc.queryForObject("select set_config('app.account_id', '', true)", String.class);
		return memberships;
	}

	/**
	 * Членство аккаунта в одной из клиник.
	 *
	 * @param membershipId членство
	 * @param clinicId клиника членства
	 * @param status статус членства
	 */
	public record ClinicMembership(UUID membershipId, UUID clinicId, MembershipStatus status) {
	}

}
