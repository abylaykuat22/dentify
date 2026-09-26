package kz.dentify.iam.internal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kz.dentify.iam.internal.AccountMemberships.ClinicMembership;
import kz.dentify.iam.internal.Accounts.Credentials;
import kz.dentify.platform.api.CurrentAccountProvider;
import kz.dentify.platform.api.ForbiddenException;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TooManyRequestsException;
import kz.dentify.platform.api.UnauthorizedException;
import kz.dentify.platform.identity.AccountSignIn;
import kz.dentify.platform.identity.RateLimits;
import kz.dentify.platform.identity.TenantSession;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Вход по телефону или e-mail и паролю и выход (IAM-02, IAM-03, PLT-05). Порядок входа:
 * <ol>
 * <li>лимиты входа до проверки пароля ({@link RateLimits#checkLogin}): 429 даже с верным паролем; в журнал входов —
 * первый отказ на логин за время отказа ({@link RateLimits#firstLoginRejection});</li>
 * <li>аккаунт по нормализованному телефону или e-mail ({@link LoginName}) и проверка пароля. Логина нет — пароль всё
 * равно проверяется, против заранее посчитанного хэша с теми же параметрами Argon2id: ответ и время у неизвестного
 * логина те же, что у неверного пароля. Предел одновременных хэшей ответил 429 — пароль не проверен, место попытки в
 * лимите входа освобождается ({@link RateLimits#abandonLogin}), в журнал входов попытка не пишется;</li>
 * <li>пароль неверный — неверная попытка в лимитах, событие журнала входов, 401 {@link #INVALID};</li>
 * <li>пароль верный, но аккаунт заблокирован или все членства отключены — 403 {@link #ACCESS_DISABLED}: только после
 * верного пароля, иначе ответ выдал бы, что аккаунт есть;</li>
 * <li>старый хэш перехэшируется, сессия создаётся ({@link AccountSignIn#signIn}: новый id сессии и CSRF-токен);</li>
 * <li>аккаунт и членства перечитываются новыми запросами к БД: вход, у которого пароль и статус прочитаны до коммита
 * деактивации или смены пароля, а время входа поставлено после завершения всех сессий аккаунта, иначе пережил бы
 * деактивацию (docs/architecture.md, Активные сессии). Изменились — выход и тот же ответ, что у неверного пароля или
 * отключённого доступа;</li>
 * <li>язык аккаунта — в сессию ({@link AccountLanguage}): на нём отвечает API;</li>
 * <li>одно активное членство с одним доступным филиалом — клиника и филиал сразу в сессии, иначе выбор клиники.</li>
 * </ol>
 * Методы нетранзакционные: сессия создаётся вне транзакции, а перечитывание — отдельными запросами.
 */
@Service
public class SignIns {

	/** 401: неверный телефон, e-mail или пароль — один ответ на неверный пароль и неизвестный логин. */
	public static final String INVALID = "iam.login.invalid";

	/** 403: пароль верный, но аккаунт заблокирован или все его членства отключены. */
	public static final String ACCESS_DISABLED = "iam.login.access-disabled";

	private static final Logger log = LoggerFactory.getLogger(SignIns.class);

	private final Accounts accounts;

	private final AccountMemberships accountMemberships;

	private final AccountContexts contexts;

	private final AuthEvents events;

	private final RateLimits rateLimits;

	private final AccountSignIn accountSignIn;

	private final PasswordEncoder passwordEncoder;

	private final CurrentAccountProvider currentAccount;

	/**
	 * Хэш случайного пароля с текущими параметрами, против которого проверяется пароль неизвестного логина. Считается
	 * при старте, а не при первом неизвестном логине: иначе первый ответ был бы вдвое медленнее и выдал бы, что логина
	 * нет.
	 */
	private final String dummyHash;

	SignIns(Accounts accounts, AccountMemberships accountMemberships, AccountContexts contexts, AuthEvents events,
			RateLimits rateLimits, AccountSignIn accountSignIn, PasswordEncoder passwordEncoder,
			CurrentAccountProvider currentAccount) {
		this.accounts = accounts;
		this.accountMemberships = accountMemberships;
		this.contexts = contexts;
		this.events = events;
		this.rateLimits = rateLimits;
		this.accountSignIn = accountSignIn;
		this.passwordEncoder = passwordEncoder;
		this.currentAccount = currentAccount;
		this.dummyHash = Objects.requireNonNull(passwordEncoder.encode(UUID.randomUUID().toString()), "dummyHash");
	}

	/**
	 * Войти.
	 *
	 * @param login телефон в любой записи или e-mail
	 * @param rememberMe Запомнить устройство: cookie и сессия на 30 дней от входа, без галочки — до закрытия браузера и
	 * 12 часов бездействия
	 * @return вошедший аккаунт, его членства и клиника сессии, если она выбрана сразу
	 * @throws TooManyRequestsException 429: лимит входа на логин или с IP, предел одновременных хэшей пароля
	 * @throws UnauthorizedException 401 {@link #INVALID}: неверный логин или пароль
	 * @throws ForbiddenException 403 {@link #ACCESS_DISABLED}: доступ отключён
	 */
	public AccountOverview login(String login, String password, boolean rememberMe, HttpServletRequest request,
			HttpServletResponse response) {
		LoginName name = LoginName.parse(login);
		try {
			this.rateLimits.checkLogin(name.limitKey(), request.getRemoteAddr());
		}
		catch (TooManyRequestsException ex) {
			// отказ ничего не стоит нападающему, а поиск аккаунта и строка журнала стоят БД: в журнал входов пишется
			// первый отказ на логин за время отказа
			if (this.rateLimits.firstLoginRejection(name.limitKey(), ex.getRetryAfter())) {
				this.events.record(AuthEventKind.LOCKED_OUT, find(name).map(SignIns::id).orElse(null), request);
			}
			throw ex;
		}

		Optional<Account> found = find(name);
		boolean matches;
		try {
			matches = passwordMatches(found.orElse(null), password);
		}
		catch (TooManyRequestsException ex) {
			// предел одновременных хэшей: пароль не проверен — место попытки в лимите входа освобождается, иначе пять
			// таких повторов под нагрузкой закрыли бы вход на 15 минут без единого неверного пароля
			this.rateLimits.abandonLogin(name.limitKey());
			throw ex;
		}
		if (!matches) {
			UUID accountId = found.map(SignIns::id).orElse(null);
			boolean locked = this.rateLimits.recordLoginFailure(name.limitKey());
			this.events.record(AuthEventKind.WRONG_PASSWORD, accountId, request);
			if (locked) {
				this.events.record(AuthEventKind.LOCKED_OUT, accountId, request);
			}
			throw new UnauthorizedException(INVALID);
		}

		Account account = found.get();
		UUID accountId = id(account);
		this.rateLimits.resetLogin(name.limitKey());
		if (account.getStatus() != AccountStatus.ACTIVE || accessDisabled(this.accountMemberships.of(accountId))) {
			throw accessDisabled(accountId, request);
		}
		Credentials verified = new Credentials(account.getStatus(),
				this.accounts.upgradePasswordHash(account, password));

		this.accountSignIn.signIn(request, response, accountId, rememberMe);

		Credentials current = this.accounts.credentials(accountId).orElse(null);
		if (current == null || !current.passwordHash().equals(verified.passwordHash())) {
			this.accountSignIn.signOut(request, response);
			this.events.record(AuthEventKind.WRONG_PASSWORD, accountId, request);
			log.info("Вход аккаунта {} отменён: пароль сменили, пока шёл вход", accountId);
			throw new UnauthorizedException(INVALID);
		}
		List<ClinicMembership> memberships = this.accountMemberships.of(accountId);
		if (current.status() != AccountStatus.ACTIVE || accessDisabled(memberships)) {
			this.accountSignIn.signOut(request, response);
			log.info("Вход аккаунта {} отменён: доступ отключили, пока шёл вход", accountId);
			throw accessDisabled(accountId, request);
		}

		// язык аккаунта — в сессию: цепочка языка запроса читает его оттуда, без запроса к БД (AccountLanguage)
		AccountLanguage.remember(request.getSession(), account.getLanguage());
		Optional<Tenant> context = this.contexts.autoSelect(accountId, memberships);
		context.ifPresent((tenant) -> TenantSession.select(request.getSession(), tenant));
		this.events.record(AuthEventKind.SIGN_IN, accountId, request);
		return this.contexts.overview(accountId, context.orElse(null));
	}

	/**
	 * Выйти: сессия завершается необратимо, cookie сессии и CSRF-токен стираются. Без входа — ничего, кроме стёртых
	 * cookie: повторный выход не ошибка.
	 */
	public void logout(HttpServletRequest request, HttpServletResponse response) {
		Optional<UUID> accountId = this.currentAccount.currentAccountId();
		this.accountSignIn.signOut(request, response);
		accountId.ifPresent((id) -> this.events.record(AuthEventKind.SIGN_OUT, id, request));
	}

	/**
	 * Проверка пароля. Аккаунта нет — пароль всё равно проверяется, против хэша-пустышки: та же работа, что у настоящей
	 * проверки, и время ответа не выдаёт, что логина нет.
	 *
	 * @param account аккаунт логина или null — логина нет
	 * @throws TooManyRequestsException 429 предела одновременных хэшей: пароль не проверен
	 */
	private boolean passwordMatches(@Nullable Account account, String password) {
		if (account != null) {
			return this.accounts.passwordMatches(account, password);
		}
		this.passwordEncoder.matches(password, this.dummyHash);
		return false;
	}

	private Optional<Account> find(LoginName name) {
		if (name.phone() != null) {
			return this.accounts.findByPhone(name.phone());
		}
		if (name.email() != null) {
			return this.accounts.findByEmail(name.email());
		}
		return Optional.empty();
	}

	/** Все членства отключены: войти некуда. Без членств или с приглашением вход открыт — принять приглашение. */
	private static boolean accessDisabled(List<ClinicMembership> memberships) {
		return !memberships.isEmpty()
				&& memberships.stream().allMatch((membership) -> membership.status() == MembershipStatus.DISABLED);
	}

	private ForbiddenException accessDisabled(UUID accountId, HttpServletRequest request) {
		this.events.record(AuthEventKind.ACCESS_DISABLED, accountId, request);
		return new ForbiddenException(ACCESS_DISABLED);
	}

	private static UUID id(Account account) {
		return Objects.requireNonNull(account.getId(), "id");
	}

}
