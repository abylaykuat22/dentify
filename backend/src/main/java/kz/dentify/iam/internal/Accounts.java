package kz.dentify.iam.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import kz.dentify.platform.api.ConflictException;
import kz.dentify.platform.api.DataIntegrity;
import kz.dentify.platform.api.EmailAddress;
import kz.dentify.platform.api.FieldViolation;
import kz.dentify.platform.api.InvalidRequestException;
import kz.dentify.platform.api.NotFoundException;
import kz.dentify.platform.api.PasswordPolicy;
import kz.dentify.platform.api.PhoneNumber;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Аккаунты (IAM-01): один на человека для всех клиник.
 * <ul>
 * <li>телефон — любой ввод, «8 707 123 45 67», «+7 (707) 123-45-67», «7071234567», хранится в E.164
 * ({@link PhoneNumber}); у аккаунта — только казахстанский мобильный;</li>
 * <li>e-mail — без крайних пробелов, в нижнем регистре, до 254 символов ({@link EmailAddress});</li>
 * <li>нужен хотя бы один контакт, оба уникальны во всём Dentify: занят — 409 с кодом {@link #PHONE_TAKEN} или
 * {@link #EMAIL_TAKEN} и полем в {@code errors[]}. Проверяет сервис до записи, уникальные индексы
 * {@code ux_account_phone} и {@code ux_account_email} — страховка на гонку, и она даёт тот же ответ;</li>
 * <li>пароль — правила {@link PasswordPolicy} и общий бин {@link PasswordEncoder} (Argon2id), своего кодировщика нет.</li>
 * </ul>
 * Все ошибки входных данных — разом, одним 400 с {@code errors[]}.
 */
@Service
public class Accounts {

	/** Нет ни телефона, ни e-mail. */
	public static final String CONTACT_REQUIRED = "iam.account.contact-required";

	/** Телефон — не казахстанский мобильный. */
	public static final String PHONE_NOT_KZ_MOBILE = "iam.account.phone-not-kz-mobile";

	/** ФИО пустое или длиннее предела; аргумент — предел. */
	public static final String FULL_NAME_LENGTH = "iam.account.full-name-length";

	/** 409: телефон уже у другого аккаунта. */
	public static final String PHONE_TAKEN = "iam.account.phone-taken";

	/** 409: e-mail уже у другого аккаунта. */
	public static final String EMAIL_TAKEN = "iam.account.email-taken";

	static final int FULL_NAME_MAX_LENGTH = 200;

	static final String PHONE_INDEX = "ux_account_phone";

	static final String EMAIL_INDEX = "ux_account_email";

	private static final Logger log = LoggerFactory.getLogger(Accounts.class);

	private final AccountRepository accounts;

	private final PasswordEncoder passwordEncoder;

	private final PasswordPolicy passwordPolicy;

	private final JdbcTemplate jdbc;

	Accounts(AccountRepository accounts, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
			JdbcTemplate jdbc) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicy = passwordPolicy;
		this.jdbc = jdbc;
	}

	/**
	 * Новый аккаунт: {@link #prepare} и {@link #create(PreparedAccount)}. Хэш пароля считается вне транзакции БД, если её
	 * нет у вызывающего (раздел Пароли в docs/architecture.md): Argon2id — десятки миллисекунд и до 3 секунд ожидания
	 * места, и всё это время открытая транзакция держала бы соединение пула.
	 *
	 * @throws InvalidRequestException 400: контакт, ФИО или пароль не годятся — поля {@code phone}, {@code email},
	 * {@code fullName}, {@code password}
	 * @throws ConflictException 409: телефон или e-mail уже у другого аккаунта
	 */
	public Account create(NewAccount request) {
		return create(prepare(request));
	}

	/**
	 * Проверить ввод нового аккаунта и посчитать хэш пароля — без БД. Вызывающий со своей транзакцией (регистрация
	 * клиники, принятие приглашения) зовёт его до неё, а в транзакции — {@link #create(PreparedAccount)}: так хэш не
	 * держит соединение пула.
	 *
	 * @throws InvalidRequestException 400: контакт, ФИО или пароль не годятся — все ошибки разом
	 */
	public PreparedAccount prepare(NewAccount request) {
		List<FieldViolation> violations = new ArrayList<>();
		PhoneNumber phone = phone(request.phone(), violations);
		EmailAddress email = email(request.email(), violations);
		if (!StringUtils.hasText(request.phone()) && !StringUtils.hasText(request.email())) {
			violations.add(FieldViolation.of("phone", CONTACT_REQUIRED));
			violations.add(FieldViolation.of("email", CONTACT_REQUIRED));
		}
		String fullName = request.fullName().strip();
		if (fullName.isEmpty() || fullName.length() > FULL_NAME_MAX_LENGTH) {
			violations.add(FieldViolation.of("fullName", FULL_NAME_LENGTH, FULL_NAME_MAX_LENGTH));
		}
		violations.addAll(this.passwordPolicy.violations("password", request.password(),
				(phone != null) ? phone.value() : null, (email != null) ? email.value() : null));
		if (!violations.isEmpty()) {
			throw new InvalidRequestException(violations);
		}
		String hash = Objects.requireNonNull(this.passwordEncoder.encode(request.password()), "passwordHash");
		return new PreparedAccount(phone, email, fullName, hash);
	}

	/**
	 * Записать проверенный аккаунт. Занятость телефона и e-mail сервис проверяет сам, уникальные индексы — страховка на
	 * гонку. Внутри транзакции вызывающего (регистрация клиники, принятие приглашения) конфликт откатывает её целиком; без
	 * неё запись — своей транзакцией репозитория.
	 *
	 * @throws ConflictException 409: телефон или e-mail уже у другого аккаунта
	 */
	public Account create(PreparedAccount prepared) {
		List<FieldViolation> taken = new ArrayList<>();
		if (prepared.phone() != null && this.accounts.existsByPhone(prepared.phone().value())) {
			taken.add(FieldViolation.of("phone", PHONE_TAKEN));
		}
		if (prepared.email() != null && this.accounts.existsByEmail(prepared.email().value())) {
			taken.add(FieldViolation.of("email", EMAIL_TAKEN));
		}
		if (!taken.isEmpty()) {
			throw ConflictException.ofFields(taken.getFirst().code(), taken, null);
		}

		Account account = new Account(prepared.phone(), prepared.email(), prepared.fullName(), prepared.passwordHash());
		try {
			account = this.accounts.saveAndFlush(account);
		}
		catch (DataIntegrityViolationException ex) {
			throw takenByConcurrentRequest(ex);
		}
		log.info("Создан аккаунт {}", account.getId());
		return account;
	}

	/**
	 * Новый аккаунт после проверки ввода: нормализованные контакты, ФИО без крайних пробелов и хэш пароля. Сам пароль не
	 * хранится; {@link #toString()} без значений — в них ПД и хэш.
	 */
	public record PreparedAccount(@Nullable PhoneNumber phone, @Nullable EmailAddress email, String fullName,
			String passwordHash) {

		@Override
		public String toString() {
			return "PreparedAccount[•••]";
		}

	}

	@Transactional(readOnly = true)
	public Optional<Account> find(UUID id) {
		return this.accounts.findById(id);
	}

	/**
	 * @throws NotFoundException 404, если аккаунта нет
	 */
	@Transactional(readOnly = true)
	public Account get(UUID id) {
		return find(id).orElseThrow(NotFoundException::new);
	}

	@Transactional(readOnly = true)
	public Optional<Account> findByPhone(PhoneNumber phone) {
		return this.accounts.findByPhone(phone.value());
	}

	@Transactional(readOnly = true)
	public Optional<Account> findByEmail(EmailAddress email) {
		return this.accounts.findByEmail(email.value());
	}

	/** Подходит ли пароль к аккаунту: общий {@link PasswordEncoder}, хэш {@code {argon2}…}. */
	public boolean passwordMatches(Account account, String password) {
		return this.passwordEncoder.matches(password, account.getPasswordHash());
	}

	/**
	 * Перехэшировать пароль, если хэш старого алгоритма или со старыми параметрами ({@code upgradeEncoding}): вход по
	 * верному паролю переводит его на текущий Argon2id. Запись условная — только если хэш в БД всё ещё тот, что
	 * проверил вход: смену пароля, которая успела закоммититься между проверкой и записью, перехэширование не затрёт.
	 * Версия аккаунта растёт: форма, открытая до перехэширования, не вернёт старый хэш, а получит 409.
	 *
	 * @param account аккаунт с проверенным хэшем
	 * @param password верный пароль
	 * @return хэш, который должен быть в БД после входа: новый или прежний
	 */
	String upgradePasswordHash(Account account, String password) {
		String current = account.getPasswordHash();
		if (!this.passwordEncoder.upgradeEncoding(current)) {
			return current;
		}
		String upgraded = this.passwordEncoder.encode(password);
		if (upgraded == null || this.jdbc.update("""
				update account set password_hash = ?, version = version + 1, updated_at = now()
				where id = ? and password_hash = ?""", upgraded, account.getId(), current) == 0) {
			return current;
		}
		log.info("Пароль аккаунта {} перехэширован в Argon2id с текущими параметрами", account.getId());
		return upgraded;
	}

	/**
	 * Статус и хэш пароля аккаунта новым запросом к БД — не из сессии Hibernate и не из транзакции вызывающего: вход
	 * после создания сессии сверяет их с проверенными (docs/architecture.md, «Активные сессии»).
	 */
	Optional<Credentials> credentials(UUID accountId) {
		return this.jdbc
			.query("select status, password_hash from account where id = ?",
					(row, number) -> new Credentials(AccountStatus.valueOf(row.getString("status")),
							row.getString("password_hash")),
					accountId)
			.stream()
			.findFirst();
	}

	/**
	 * Что проверяет вход: статус аккаунта и хэш пароля. Смена и сброс пароля меняют хэш (новая соль), деактивация —
	 * статус. Наружу не отдаётся, {@link #toString()} без хэша.
	 */
	record Credentials(AccountStatus status, String passwordHash) {

		static Credentials of(Account account) {
			return new Credentials(account.getStatus(), account.getPasswordHash());
		}

		@Override
		public String toString() {
			return "Credentials[status=" + this.status + "]";
		}

	}

	private static @Nullable PhoneNumber phone(@Nullable String input, List<FieldViolation> violations) {
		if (!StringUtils.hasText(input)) {
			return null;
		}
		Optional<PhoneNumber> phone = PhoneNumber.parse(input);
		if (phone.isEmpty()) {
			violations.add(FieldViolation.of("phone", PhoneNumber.INVALID));
			return null;
		}
		if (!phone.get().isKazakhstanMobile()) {
			violations.add(FieldViolation.of("phone", PHONE_NOT_KZ_MOBILE));
			return null;
		}
		return phone.get();
	}

	private static @Nullable EmailAddress email(@Nullable String input, List<FieldViolation> violations) {
		if (!StringUtils.hasText(input)) {
			return null;
		}
		Optional<FieldViolation> violation = EmailAddress.violation("email", input);
		violation.ifPresent(violations::add);
		return EmailAddress.parse(input).orElse(null);
	}

	/**
	 * Уникальный индекс сработал: такой же контакт записал параллельный запрос между проверкой и записью. Ответ тот
	 * же, что у проверки сервиса; другие нарушения — как есть.
	 */
	private static RuntimeException takenByConcurrentRequest(DataIntegrityViolationException ex) {
		if (DataIntegrity.isViolation(ex, PHONE_INDEX)) {
			return ConflictException.ofFields(PHONE_TAKEN, List.of(FieldViolation.of("phone", PHONE_TAKEN)), ex);
		}
		if (DataIntegrity.isViolation(ex, EMAIL_INDEX)) {
			return ConflictException.ofFields(EMAIL_TAKEN, List.of(FieldViolation.of("email", EMAIL_TAKEN)), ex);
		}
		return ex;
	}

}
