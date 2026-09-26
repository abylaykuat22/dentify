package kz.dentify.iam.internal;

import java.util.List;
import java.util.UUID;

import kz.dentify.platform.api.NotFoundException;
import kz.dentify.platform.identity.AccountSession;
import kz.dentify.platform.identity.AccountSessions;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;

/**
 * Активные сессии аккаунта (PLT-04): что показать и что завершить. Хранит и гасит сессии платформа
 * ({@link AccountSessions}), здесь — правила выбора: какие сессии завершает «все» и «все, кроме текущей». Экран своих
 * сессий и действия администратора (T-IAM-11) зовут одни и те же методы, а не повторяют выбор в контроллерах.
 */
@Service
public class AccountSessionCommands {

	private final AccountSessions sessions;

	AccountSessionCommands(AccountSessions sessions) {
		this.sessions = sessions;
	}

	/**
	 * Сессии аккаунта, сначала последние по активности.
	 *
	 * @param currentSessionId настоящий id сессии запроса — она помечается текущей; null — запрос не из сессии аккаунта
	 */
	public List<AccountSession> list(UUID accountId, @Nullable String currentSessionId) {
		return this.sessions.list(accountId, currentSessionId);
	}

	/**
	 * Завершить одну сессию аккаунта по открытому id из списка. Текущую тоже можно — это выход.
	 *
	 * @throws NotFoundException 404: у аккаунта нет такой сессии — чужая, уже завершённая или выдуманная
	 */
	public void terminate(UUID accountId, String publicSessionId) {
		if (!this.sessions.terminate(accountId, publicSessionId)) {
			throw new NotFoundException();
		}
	}

	/**
	 * Завершить сессии аккаунта: все, кроме текущей, или все, включая текущую, — выход везде. Без текущей сессии (вызов
	 * не из сессии этого аккаунта: администратор, фоновая работа) оставлять нечего, и завершаются все.
	 *
	 * @param currentSessionId настоящий id сессии запроса или null
	 * @param includeCurrent завершить и текущую
	 * @return число завершённых сессий
	 */
	public int terminateAll(UUID accountId, @Nullable String currentSessionId, boolean includeCurrent) {
		if (includeCurrent || currentSessionId == null) {
			return this.sessions.terminateAll(accountId);
		}
		return this.sessions.terminateOthers(accountId, currentSessionId);
	}

}
