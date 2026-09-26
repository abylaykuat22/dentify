package kz.dentify.iam.internal;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import kz.dentify.platform.api.AccountNameProvider;
import kz.dentify.platform.api.TenantContext;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Имя аккаунта для показа в клинике контекста — ФИО аккаунта (PLT-08: автор правки в ответе 409, PLT-09: автор события
 * журнала), пока нет имени из карточки сотрудника (T-IAM-26): тот провайдер встанет раньше этого, этот останется
 * запасным. Только аккаунты с членством в клинике контекста — любым, в том числе отключённым: прежний сотрудник остаётся
 * автором своих правок. Имя чужого для клиники аккаунта не отдаётся, без контекста клиники — ничего. Страница — одним
 * запросом ({@link #displayNames}).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class AccountDisplayNames implements AccountNameProvider {

	private final NamedParameterJdbcTemplate jdbc;

	AccountDisplayNames(JdbcTemplate jdbc) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
	}

	@Override
	public Optional<String> displayName(UUID accountId) {
		return Optional.ofNullable(displayNames(List.of(accountId)).get(accountId));
	}

	/** Членство читается под RLS клиники контекста, условие по клинике — ещё и в самом запросе. */
	@Override
	public Map<UUID, String> displayNames(Collection<UUID> accountIds) {
		if (accountIds.isEmpty() || TenantContext.current().isEmpty()) {
			return Map.of();
		}
		Map<UUID, String> names = new HashMap<>();
		this.jdbc.query("""
				select a.id, a.full_name from account a
				where a.id in (:ids)
				  and exists (select 1 from membership m where m.clinic_id = current_clinic_id() and m.account_id = a.id)""",
				Map.of("ids", accountIds),
				(row) -> {
					names.put(row.getObject("id", UUID.class), row.getString("full_name"));
				});
		return names;
	}

}
