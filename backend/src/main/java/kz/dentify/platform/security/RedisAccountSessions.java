package kz.dentify.platform.security;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;
import kz.dentify.platform.api.Hashes;
import kz.dentify.platform.identity.AccountSession;
import kz.dentify.platform.identity.AccountSessions;
import kz.dentify.platform.identity.TenantSession;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Активные сессии аккаунта по индексу Spring Session {@code FindByIndexNameSessionRepository}: имя в индексе — id
 * аккаунта, его ставит вход ({@link SessionAccountSignIn}). Завершение необратимо: сначала отметка отзыва в Valkey
 * ({@link SessionRevocations}), потом {@code deleteById} — сессия помечается истёкшей и убирается из индекса сразу,
 * следующий запрос с её cookie — 401. Отметка нужна, потому что {@code deleteById} сессию не удаляет, а запрос этой
 * сессии, который шёл в тот момент, в конце записал бы её обратно. Завершение всех ставит ещё и отметку аккаунта: она
 * гасит и сессии, которых в индексе нет. Сессия текущего запроса завершается как выход, {@code HttpSession.invalidate()}:
 * иначе конец запроса записал бы её обратно, а ответ не стёр бы cookie.
 */
class RedisAccountSessions implements AccountSessions {

	/** Открытый id — первые 16 байт SHA-256 от id сессии, base64url: 22 символа. */
	static final int PUBLIC_ID_BYTES = 16;

	private static final Logger log = LoggerFactory.getLogger(RedisAccountSessions.class);

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	private final SessionClients clients;

	private final SessionLifetime lifetime;

	private final SessionRevocations revocations;

	private final Clock clock;

	RedisAccountSessions(FindByIndexNameSessionRepository<? extends Session> sessions, SessionClients clients,
			SessionLifetime lifetime, SessionRevocations revocations, Clock clock) {
		this.sessions = sessions;
		this.clients = clients;
		this.lifetime = lifetime;
		this.revocations = revocations;
		this.clock = clock;
	}

	/**
	 * Сессии аккаунта, сначала последние по активности. Отозванная сессия и сессия, у которой истёк срок от входа, в
	 * список не попадают и удаляются: войти ими уже нельзя, а в индексе они могли остаться — отозванную мог вернуть туда
	 * запрос в полёте, у истёкшей ключ в Valkey мог ещё не истечь, если с неё давно не приходили.
	 */
	@Override
	public List<AccountSession> list(UUID accountId, @Nullable String currentSessionId) {
		Locale locale = LocaleContextHolder.getLocale();
		Instant now = this.clock.instant();
		Collection<? extends Session> found = find(accountId).values();
		Set<String> revoked = this.revocations.revoked(accountId, found);
		List<Session> alive = new ArrayList<>();
		for (Session session : found) {
			if (revoked.contains(session.getId())) {
				delete(session.getId());
				log.info("Отозванная сессия аккаунта {} снова удалена из индекса", accountId);
			}
			else if (this.lifetime.expired(SessionClients.signedInAt(session), SessionClients.rememberMe(session),
					now)) {
				delete(session.getId());
				log.debug("Сессия аккаунта {} с истёкшим сроком от входа удалена из списка", accountId);
			}
			else {
				alive.add(session);
			}
		}
		return alive.stream()
			.sorted(Comparator.comparing(Session::getLastAccessedTime).reversed())
			.map((session) -> describe(session, session.getId().equals(currentSessionId), locale))
			.toList();
	}

	@Override
	public boolean terminate(UUID accountId, String sessionId) {
		for (Session session : find(accountId).values()) {
			if (publicId(session.getId()).equals(sessionId)) {
				end(session);
				log.info("Сессия аккаунта {} завершена", accountId);
				return true;
			}
		}
		return false;
	}

	@Override
	public int terminateAll(UUID accountId) {
		return terminateExcept(accountId, null);
	}

	@Override
	public int terminateOthers(UUID accountId, String currentSessionId) {
		return terminateExcept(accountId, currentSessionId);
	}

	/**
	 * Сессии индекса аккаунта, в которых выбрана клиника ({@link TenantSession#CLINIC_ID}), — каждая своей отметкой и
	 * удалением, как {@link #terminate}. Сессия текущего запроса тоже: она завершается как выход.
	 */
	@Override
	public int terminateInClinic(UUID accountId, UUID clinicId) {
		int terminated = 0;
		for (Session session : find(accountId).values()) {
			if (clinicId.equals(session.getAttribute(TenantSession.CLINIC_ID))) {
				end(session);
				terminated++;
			}
		}
		log.info("Завершены сессии аккаунта {} в клинике {}: {}", accountId, clinicId, terminated);
		return terminated;
	}

	/**
	 * Сначала отметка аккаунта: она гасит и сессии, которых нет в индексе, — сессию, которую запрос в полёте оживил до
	 * этой защиты, и вход, шедший одновременно, если его время входа не позже отметки. Потом каждая сессия из индекса —
	 * своей отметкой и удалением: так гаснут и сессии со входом позже отметки аккаунта, найденные в индексе.
	 */
	private int terminateExcept(UUID accountId, @Nullable String keep) {
		this.revocations.revokeAccount(accountId, keep);
		int terminated = 0;
		for (Session session : find(accountId).values()) {
			if (!session.getId().equals(keep)) {
				end(session);
				terminated++;
			}
		}
		log.info("Завершены сессии аккаунта {}: {}, текущая оставлена: {}", accountId, terminated, keep != null);
		return terminated;
	}

	/** Завершить сессию необратимо: отметка отзыва до удаления, иначе запрос в полёте записал бы её обратно. */
	private void end(Session session) {
		this.revocations.revoke(session);
		delete(session.getId());
	}

	private void delete(String id) {
		HttpSession current = currentRequestSession();
		if (current != null && current.getId().equals(id)) {
			current.invalidate();
		}
		else {
			this.sessions.deleteById(id);
		}
	}

	private static @Nullable HttpSession currentRequestSession() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			return attributes.getRequest().getSession(false);
		}
		return null;
	}

	private Map<String, ? extends Session> find(UUID accountId) {
		return this.sessions.findByPrincipalName(accountId.toString());
	}

	private AccountSession describe(Session session, boolean current, Locale locale) {
		UserAgents.Client client = this.clients.client(session);
		return new AccountSession(publicId(session.getId()), current, client.browser(), client.os(),
				this.clients.maskedIp(session), this.clients.city(session, locale), SessionClients.signedInAt(session),
				session.getLastAccessedTime(), SessionClients.rememberMe(session));
	}

	/** Открытый id сессии: по нему нельзя восстановить настоящий, а настоящий — пароль на время сессии. */
	static String publicId(String sessionId) {
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(Arrays.copyOf(Hashes.sha256(sessionId), PUBLIC_ID_BYTES));
	}

}
