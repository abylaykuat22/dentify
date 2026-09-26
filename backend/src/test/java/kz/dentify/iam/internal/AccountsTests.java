package kz.dentify.iam.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import kz.dentify.AbstractIntegrationTest;
import kz.dentify.platform.api.ConflictException;
import kz.dentify.platform.api.EmailAddress;
import kz.dentify.platform.api.FieldViolation;
import kz.dentify.platform.api.InvalidRequestException;
import kz.dentify.platform.api.PasswordPolicy;
import kz.dentify.platform.api.PhoneNumber;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Аккаунты (IAM-01): нормализация телефона и e-mail, уникальность во всём Dentify (проверка сервиса и индекс на
 * гонку — один ответ 409 с полем), все ошибки ввода разом, хэш пароля общим {@code PasswordEncoder} (Argon2id).
 * Номера и адреса вымышленные.
 */
class AccountsTests extends AbstractIntegrationTest {

	static final String PASSWORD = "Жёлтый-трамвай-42";

	@Autowired
	Accounts accounts;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MessageSource messages;

	@Test
	void createsAccountWithNormalizedContactsAndArgon2Hash() {
		String digits = randomMobileDigits();
		String email = "Aigerim." + UUID.randomUUID() + "@Example.KZ";

		Account account = this.accounts.create(new NewAccount(spaced(digits), "  " + email + " ", " Айгерим Демо ", PASSWORD));

		assertThat(account.getPhone()).isEqualTo(new PhoneNumber("+7" + digits));
		assertThat(account.getEmail()).isEqualTo(new EmailAddress(email.toLowerCase(Locale.ROOT)));
		assertThat(account.getFullName()).isEqualTo("Айгерим Демо");
		assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(account.getLanguage()).isEqualTo(Locale.of("ru"));
		assertThat(account.getVersion()).isZero();
		assertThat(account.getCreatedAt()).isNotNull();

		Map<String, Object> row = this.jdbc.queryForMap("select phone, email, password_hash from account where id = ?",
				account.getId());
		assertThat(row).containsEntry("phone", "+7" + digits).containsEntry("email", email.toLowerCase(Locale.ROOT));
		assertThat((String) row.get("password_hash")).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$")
			.doesNotContain(PASSWORD);
		assertThat(this.accounts.passwordMatches(account, PASSWORD)).isTrue();
		assertThat(this.accounts.passwordMatches(account, PASSWORD + "!")).isFalse();
	}

	/** Приёмка IAM-01: «8 707 123 45 67», «+7(707)1234567» и «7071234567» — один номер, один аккаунт. */
	@Test
	void findsAccountByAnyNotationOfPhoneAndAnyCaseOfEmail() {
		String digits = randomMobileDigits();
		String email = "dinara." + UUID.randomUUID() + "@example.kz";
		Account account = this.accounts.create(new NewAccount("8 " + spaced(digits), email, "Динара Демо", PASSWORD));

		for (String notation : List.of("8 " + spaced(digits), "+7(" + digits.substring(0, 3) + ")" + digits.substring(3),
				digits)) {
			assertThat(this.accounts.findByPhone(PhoneNumber.parse(notation).orElseThrow())).as(notation)
				.map(Account::getId)
				.contains(account.getId());
		}
		assertThat(this.accounts.findByEmail(EmailAddress.parse(" " + email.toUpperCase(Locale.ROOT)).orElseThrow()))
			.map(Account::getId)
			.contains(account.getId());
		assertThat(this.accounts.get(account.getId()).getId()).isEqualTo(account.getId());
	}

	/** Приёмка IAM-01: телефон или e-mail не принадлежат двум аккаунтам — 409 с кодом и полем. */
	@Test
	void phoneAndEmailBelongToOneAccountOnly() {
		String digits = randomMobileDigits();
		String email = "askar." + UUID.randomUUID() + "@example.kz";
		this.accounts.create(new NewAccount("8" + digits, email, "Аскар Демо", PASSWORD));

		assertConflict(new NewAccount("+7 (" + digits.substring(0, 3) + ") " + digits.substring(3), null, "Другой",
				PASSWORD), Accounts.PHONE_TAKEN, FieldViolation.of("phone", Accounts.PHONE_TAKEN));
		assertConflict(new NewAccount(null, email.toUpperCase(Locale.ROOT), "Другой", PASSWORD), Accounts.EMAIL_TAKEN,
				FieldViolation.of("email", Accounts.EMAIL_TAKEN));
		assertConflict(new NewAccount(digits, email, "Другой", PASSWORD), Accounts.PHONE_TAKEN,
				FieldViolation.of("phone", Accounts.PHONE_TAKEN), FieldViolation.of("email", Accounts.EMAIL_TAKEN));
		assertThat(this.jdbc.queryForObject("select count(*) from account where phone = ? or lower(email) = ?",
				Long.class, "+7" + digits, email))
			.isOne();
	}

	/**
	 * Гонка: одновременные регистрации с одним телефоном проходят проверку сервиса раньше, чем любая запишет аккаунт.
	 * Индекс пропускает одну, остальные получают тот же 409 с полем, что и от проверки сервиса, а не общий дубль.
	 */
	@Test
	void concurrentCreatesWithOnePhoneGiveOneAccountAndSameConflict() throws Exception {
		String digits = randomMobileDigits();
		int requests = 6;
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Object>> results = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
			for (int i = 0; i < requests; i++) {
				String email = "race." + UUID.randomUUID() + "@example.kz";
				results.add(pool.submit(() -> {
					start.await();
					try {
						return this.accounts.create(new NewAccount(digits, email, "Гонка Демо", PASSWORD));
					}
					catch (ConflictException ex) {
						return ex;
					}
				}));
			}
			start.countDown();
			List<Object> outcomes = new ArrayList<>();
			for (Future<Object> result : results) {
				outcomes.add(result.get());
			}

			assertThat(outcomes).filteredOn(Account.class::isInstance).hasSize(1);
			assertThat(outcomes).filteredOn(ConflictException.class::isInstance)
				.hasSize(requests - 1)
				.allSatisfy((conflict) -> {
					assertThat(((ConflictException) conflict).getCode()).isEqualTo(Accounts.PHONE_TAKEN);
					assertThat(((ConflictException) conflict).getViolations())
						.containsExactly(FieldViolation.of("phone", Accounts.PHONE_TAKEN));
				});
		}
		assertThat(this.jdbc.queryForObject("select count(*) from account where phone = ?", Long.class, "+7" + digits))
			.isOne();
	}

	/** Индексы и CHECK — страховка: запись в обход сервиса с тем же телефоном или e-mail в другом регистре БД не примет. */
	@Test
	void databaseRejectsDuplicatesWrittenAroundTheService() {
		String phone = "+7" + randomMobileDigits();
		String email = "bypass." + UUID.randomUUID() + "@example.kz";
		insertAccount(phone, email);

		assertThat(violated(() -> insertAccount(phone, null))).containsExactly("23505", "ux_account_phone");
		assertThat(violated(() -> insertAccount(null, email.toUpperCase(Locale.ROOT))))
			.containsExactly("23505", "ux_account_email");
		assertThat(violated(() -> insertAccount(null, null))).containsExactly("23514", "ck_account_contact");
		assertThat(violated(() -> insertAccount("87071234567", null))).containsExactly("23514", "ck_account_phone_e164");
	}

	/** Все ошибки ввода — разом, одним 400 с полями. */
	@Test
	void invalidInputGivesAllFieldErrorsAtOnce() {
		assertThatThrownBy(() -> this.accounts.create(new NewAccount("123", "not-an-email", "  ", "1234567")))
			.isInstanceOfSatisfying(InvalidRequestException.class, (ex) -> assertThat(ex.getViolations())
				.extracting(FieldViolation::field, FieldViolation::code)
				.containsExactlyInAnyOrder(tuple("phone", PhoneNumber.INVALID),
						tuple("email", EmailAddress.INVALID),
						tuple("fullName", Accounts.FULL_NAME_LENGTH),
						tuple("password", PasswordPolicy.TOO_SHORT)));
		assertThat(violations(new NewAccount(null, "  ", "Без контактов", PASSWORD))).containsExactlyInAnyOrder(
				FieldViolation.of("phone", Accounts.CONTACT_REQUIRED), FieldViolation.of("email", Accounts.CONTACT_REQUIRED));
		assertThat(violations(new NewAccount("+7 912 345 67 89", null, "Не казахстанский", PASSWORD)))
			.containsExactly(FieldViolation.of("phone", Accounts.PHONE_NOT_KZ_MOBILE));
		assertThat(violations(new NewAccount("8 (7172) 55-66-77", null, "Городской", PASSWORD)))
			.containsExactly(FieldViolation.of("phone", Accounts.PHONE_NOT_KZ_MOBILE));
		assertThat(violations(new NewAccount(null, "a".repeat(250) + "@x.kz", "Длинный", PASSWORD)))
			.containsExactly(FieldViolation.of("email", EmailAddress.TOO_LONG, EmailAddress.MAX_LENGTH));
		assertThat(violations(new NewAccount(null, "demo@example.kz", "Я".repeat(201), PASSWORD)))
			.containsExactly(FieldViolation.of("fullName", Accounts.FULL_NAME_LENGTH, Accounts.FULL_NAME_MAX_LENGTH));
		String digits = randomMobileDigits();
		assertThat(violations(new NewAccount(digits, null, "Пароль-телефон", "8" + digits)))
			.containsExactly(FieldViolation.of("password", PasswordPolicy.MATCHES_PHONE));
	}

	/** Коды ошибок аккаунтов и членства, телефона и e-mail — с русским текстом (dentify R003). */
	@Test
	void everyErrorCodeHasRussianText() throws IllegalAccessException {
		List<String> codes = new ArrayList<>(List.of(PhoneNumber.INVALID, EmailAddress.INVALID, EmailAddress.TOO_LONG));
		for (Class<?> type : List.of(Accounts.class, Memberships.class)) {
			for (Field field : type.getFields()) {
				if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
					codes.add((String) field.get(null));
				}
			}
		}

		assertThat(codes).hasSizeGreaterThan(6)
			.allSatisfy((code) -> assertThat(this.messages.getMessage(code, new Object[] { 200 }, null, Locale.of("ru")))
				.as(code)
				.isNotBlank()
				.doesNotContain("{"));
	}

	private void assertConflict(NewAccount request, String code, FieldViolation... violations) {
		assertThatThrownBy(() -> this.accounts.create(request)).isInstanceOfSatisfying(ConflictException.class, (ex) -> {
			assertThat(ex.getCode()).isEqualTo(code);
			assertThat(ex.getViolations()).containsExactly(violations);
		});
	}

	private List<FieldViolation> violations(NewAccount request) {
		try {
			this.accounts.create(request);
		}
		catch (InvalidRequestException ex) {
			return ex.getViolations();
		}
		throw new AssertionError("ожидали 400 с ошибками полей");
	}

	private void insertAccount(@Nullable String phone, @Nullable String email) {
		this.jdbc.update("insert into account (phone, email, full_name, password_hash) values (?, ?, 'В обход', '{noop}x')",
				phone, email);
	}

	private static List<String> violated(Runnable insert) {
		try {
			insert.run();
		}
		catch (DataAccessException ex) {
			for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
				if (cause instanceof PSQLException sql && sql.getServerErrorMessage() != null) {
					return List.of(String.valueOf(sql.getSQLState()),
							String.valueOf(sql.getServerErrorMessage().getConstraint()));
				}
			}
		}
		throw new AssertionError("ожидали нарушение ограничения БД");
	}

	/** Десять цифр вымышленного мобильного номера Казахстана: 707 и семь случайных. */
	static String randomMobileDigits() {
		return "707" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
	}

	/** «707 123 45 67». */
	static String spaced(String digits) {
		return digits.substring(0, 3) + " " + digits.substring(3, 6) + " " + digits.substring(6, 8) + " "
				+ digits.substring(8);
	}

}
