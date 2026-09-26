package kz.dentify.iam.internal;

import java.net.InetAddress;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import kz.dentify.platform.api.IpAddresses;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Журнал входов ({@code auth_event}, IAM-02, IAM-03): каждая попытка входа и выход — с аккаунтом, IP и User-Agent.
 * Глобальная таблица без клиники: попытка входа случается до выбора клиники (docs/architecture.md, «Вход, выход и
 * /api/v1/me»). Запись — сразу, в своей транзакции, отдельной от транзакции вызывающего: событие неудачной попытки
 * остаётся, хотя запрос заканчивается ошибкой, а транзакция вызывающего откатывается.
 * <p>
 * IP — {@code getRemoteAddr()}: за Caddy это клиент из {@code X-Forwarded-For}. Полный IP хранится только здесь и в
 * сессии, наружу — маской. Введённый логин не пишется: у неизвестного логина событие без аккаунта.
 */
@Component
public class AuthEvents {

	/** User-Agent длиннее обрезается, как в сессии: для журнала нужны браузер и ОС. */
	static final int USER_AGENT_LIMIT = 512;

	private static final Logger log = LoggerFactory.getLogger(AuthEvents.class);

	private final JdbcTemplate jdbc;

	AuthEvents(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Записать событие — в своей транзакции ({@code REQUIRES_NEW}): откат транзакции вызывающего (смена и сброс пароля,
	 * принятие приглашения) событие не снимает, неудачная попытка остаётся в журнале.
	 *
	 * @param accountId аккаунт или null — неизвестный логин
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(AuthEventKind kind, @Nullable UUID accountId, HttpServletRequest request) {
		this.jdbc.update("insert into auth_event (account_id, kind, ip, user_agent) values (?, ?, cast(? as inet), ?)",
				accountId, kind.name(), ip(request), userAgent(request));
		log.info("Журнал входов: {}, аккаунт {}", kind, accountId);
	}

	private static @Nullable String ip(HttpServletRequest request) {
		InetAddress address = IpAddresses.parse(request.getRemoteAddr());
		return (address != null) ? address.getHostAddress() : null;
	}

	private static @Nullable String userAgent(HttpServletRequest request) {
		String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
		if (userAgent == null || userAgent.isBlank()) {
			return null;
		}
		return (userAgent.length() > USER_AGENT_LIMIT) ? userAgent.substring(0, USER_AGENT_LIMIT) : userAgent;
	}

}
