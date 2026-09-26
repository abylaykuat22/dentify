package kz.dentify.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import kz.dentify.platform.api.AuditSecret;
import kz.dentify.platform.api.LocalizedText;
import kz.dentify.platform.audit.AuditDiff.Change;
import kz.dentify.platform.audit.AuditDiff.Value;
import kz.dentify.platform.identity.AuditEntry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Разница было и стало по снимкам (PLT-09): порядок и пути полей, вложенные record, служебные поля, виды значений,
 * сравнение сумм и секреты — без значений ни в разнице, ни в JSON колонки.
 */
class AuditDiffTests {

	final AuditDiff diff = new AuditDiff(JsonMapper.builder().build());

	@Test
	void changedFieldsInDeclarationOrderWithNestedPaths() {
		Appointment before = new Appointment(UUID.randomUUID(), 3L, LocalDate.of(2026, 9, 24), LocalTime.of(11, 30),
				new LocalizedText("Осмотр", null), new BigDecimal("12500.50"), Status.EXPECTED, List.of("первичный"),
				Instant.parse("2026-09-24T06:30:00Z"));
		Appointment after = new Appointment(UUID.randomUUID(), 4L, LocalDate.of(2026, 9, 25), LocalTime.of(10, 0),
				new LocalizedText("Осмотр", "Тексеру"), new BigDecimal("12500.5"), Status.ARRIVED,
				List.of("первичный", "срочно"), Instant.parse("2026-09-25T05:00:00Z"));

		List<Change> changes = compare(before, after);

		assertThat(changes).extracting(Change::field)
			.as("id, version, updatedAt не сравниваются; 12500.50 и 12500.5 — одна сумма; name.ru не изменилось")
			.containsExactly("date", "time", "service.kk", "status", "tags");
		assertThat(changes).extracting(Change::type).containsExactly("date", "time", "text", "enum", "list");
		assertThat(changes.get(0).from().asString()).isEqualTo("2026-09-24");
		assertThat(changes.get(0).to().asString()).isEqualTo("2026-09-25");
		assertThat(changes.get(2).from()).isNull();
		assertThat(changes.get(3).to().asString()).isEqualTo("ARRIVED");
		assertThat(changes.get(4).to().toString()).isEqualTo("[\"первичный\",\"срочно\"]");
		assertThat(this.diff.toJson(changes).get(2).has("from")).as("пустое было не пишется").isFalse();
	}

	@Test
	void creationAndDeletionListNonEmptyFields() {
		Appointment appointment = new Appointment(UUID.randomUUID(), 0L, LocalDate.of(2026, 9, 24), null,
				new LocalizedText("Осмотр", null), null, Status.EXPECTED, List.of(), null);

		assertThat(compare(null, appointment)).extracting(Change::field)
			.containsExactly("date", "service.ru", "status", "tags");
		assertThat(compare(appointment, null)).extracting(Change::field)
			.containsExactly("date", "service.ru", "status", "tags");
		assertThat(compare(appointment, appointment)).isEmpty();
		assertThat(compare(null, null)).isEmpty();
	}

	@Test
	void nullNestedRecordIsComparedByItsFields() {
		assertThat(compare(new Patient(null, "Заметка"), new Patient(new Address("Алматы", null), "Заметка")))
			.extracting(Change::field)
			.containsExactly("address.city");
	}

	/** Приёмка PLT-09: пароль, хэш, токен в разницу не попадают; отмечено только, что поле изменилось. */
	@Test
	void secretsAreMarkedWithoutValues() {
		String hash = "{argon2}$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA";
		Account before = new Account("aigerim@example.kz", "старый", "{bcrypt}$2a$10$abcdefghijklmnopqrstuv",
				new byte[] { 1 }, "tok-1", "секрет-1", "обычный", "$2a$10$abcdefghijklmnopqrstuvwxyz");
		Account after = new Account("aigerim@example.kz", "новый", hash, new byte[] { 2 }, "tok-2", "секрет-2", hash,
				"$2a$10$abcdefghijklmnopqrstuvwxyz");

		List<Change> changes = compare(before, after);

		assertThat(changes).extracting(Change::field)
			.containsExactly("password", "passwordHash", "salt", "apiToken", "recoveryCode", "note");
		assertThat(changes).allSatisfy((change) -> {
			assertThat(change.type()).isEqualTo("secret");
			assertThat(change.from()).isNull();
			assertThat(change.to()).isNull();
		});
		assertThat(this.diff.toJson(changes).toString()).doesNotContain("старый", "новый", "argon2", "bcrypt", "tok-",
				"секрет-", "обычный");
		assertThat(this.diff.flatten(before).get("password").toString()).doesNotContain("старый");
	}

	@Test
	void secretSetFromEmptyIsAChangeAndUnchangedSecretIsNot() {
		Account empty = new Account("a@example.kz", null, null, new byte[0], null, null, null, null);
		Account withPassword = new Account("a@example.kz", "пароль", null, new byte[0], null, null, null, null);

		assertThat(compare(empty, withPassword)).extracting(Change::field).containsExactly("password");
		assertThat(compare(withPassword, withPassword)).isEmpty();
		assertThat(compare(null, withPassword)).extracting(Change::field, Change::type)
			.contains(tuple("password", "secret"));
	}

	/**
	 * Секреты внутри коллекций и Map: компонент record с токеном в списке, значение Map по ключу-паролю, хэш в списке
	 * строк, byte[] в списке — в разнице и в JSON колонки только {@link AuditDiff#HIDDEN}, остальное видно.
	 */
	@Test
	void secretsInsideCollectionsAndMapsAreHidden() {
		String hash = "{argon2}$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA";
		Integrations before = new Integrations(List.of(new ApiKey("sms", "tok-1")),
				Map.of("host", "smtp.example.kz", "smtpPassword", "пароль-1"), List.of("{bcrypt}$2a$10$abcdefghijklmnop"),
				List.of(new byte[] { 1 }));
		Integrations after = new Integrations(List.of(new ApiKey("sms", "tok-2"), new ApiKey("mail", "tok-3")),
				Map.of("host", "smtp2.example.kz", "smtpPassword", "пароль-2"), List.of(hash, "обычная строка"),
				List.of(new byte[] { 2 }));

		List<Change> changes = compare(before, after);

		assertThat(changes).extracting(Change::field, Change::type)
			.containsExactly(tuple("keys", "list"), tuple("smtp", "json"), tuple("history", "list"),
					tuple("blobs", AuditEntry.Change.SECRET));
		String json = this.diff.toJson(changes).toString();
		assertThat(json).doesNotContain("tok-", "пароль-", "argon2", "bcrypt", "AQ", "Ag")
			.contains("smtp2.example.kz", "\"mail\"", "обычная строка");
		assertThat(changes.get(0).to().toString())
			.isEqualTo("[{\"name\":\"sms\",\"token\":\"••••\"},{\"name\":\"mail\",\"token\":\"••••\"}]");
		assertThat(changes.get(1).to().toString()).isEqualTo("{\"host\":\"smtp2.example.kz\",\"smtpPassword\":\"••••\"}");
		assertThat(changes.get(2).to().toString()).isEqualTo("[\"••••\",\"обычная строка\"]");
		assertThat(this.diff.flatten(after).toString()).doesNotContain("tok-", "пароль-", "argon2");
	}

	/** Внутри коллекции или Map изменился только секрет: поле отмечено как секрет, без было и стало. */
	@Test
	void onlyHiddenSecretChangedIsASecretChange() {
		Integrations before = new Integrations(List.of(new ApiKey("sms", "tok-1")), Map.of("smtpPassword", "пароль-1"),
				List.of(), List.of());
		Integrations tokenChanged = new Integrations(List.of(new ApiKey("sms", "tok-2")),
				Map.of("smtpPassword", "пароль-1"), List.of(), List.of());
		Integrations passwordChanged = new Integrations(List.of(new ApiKey("sms", "tok-1")),
				Map.of("smtpPassword", "пароль-2"), List.of(), List.of());

		assertThat(compare(before, tokenChanged)).singleElement().satisfies((change) -> {
			assertThat(change.field()).isEqualTo("keys");
			assertThat(change.type()).isEqualTo(AuditEntry.Change.SECRET);
			assertThat(change.from()).isNull();
			assertThat(change.to()).isNull();
		});
		assertThat(compare(before, passwordChanged)).extracting(Change::field, Change::type)
			.containsExactly(tuple("smtp", AuditEntry.Change.SECRET));
		assertThat(compare(before, before)).isEmpty();
		assertThat(compare(before, new Integrations(List.of(new ApiKey("sms", "tok-1")),
				new HashMap<>(Map.of("smtpPassword", "пароль-1")), List.of(), List.of())))
			.as("порядок ключей Map не важен")
			.isEmpty();
	}

	/** Сущность, прокси или другой объект не record в снимке — ошибка, а не JSON со всеми полями. */
	@Test
	void objectsOtherThanRecordsAreRejected() {
		assertThatThrownBy(() -> this.diff.flatten(new WithObject(new PlainAccount("aigerim", "пароль"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("owner")
			.hasMessageContaining(PlainAccount.class.getName())
			.hasMessageNotContaining("пароль");
		assertThatThrownBy(() -> this.diff.flatten(new WithObject(List.of(new PlainAccount("aigerim", "пароль")))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("owner[0]");
		assertThatThrownBy(() -> this.diff.flatten(new WithObject(Map.of("account", Optional.of("x")))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("owner.account");
		assertThat(this.diff.flatten(new WithObject(new Address("Алматы", null))).get("owner").node().toString())
			.as("record, объявленный не record-типом, — по своим полям")
			.isEqualTo("{\"city\":\"Алматы\",\"street\":null}");
	}

	@Test
	void snapshotIsCopiedWhenFlattened() {
		ArrayList<String> tags = new ArrayList<>(List.of("первичный"));
		Map<String, Value> before = this.diff.flatten(new Tagged(tags));
		tags.add("срочно");

		assertThat(this.diff.compare(before, this.diff.flatten(new Tagged(tags)))).extracting(Change::field)
			.containsExactly("tags");
	}

	@Test
	void fieldNameWords() {
		assertThat(AuditDiff.words("passwordHash")).containsExactly("password", "hash");
		assertThat(AuditDiff.words("phone2")).containsExactly("phone");
		assertThat(AuditDiff.words("contacts.email")).containsExactly("contacts", "email");
		assertThat(AuditDiff.isSecretName("resetToken")).isTrue();
		assertThat(AuditDiff.isSecretName("pinned")).isFalse();
		assertThat(AuditDiff.isSecretName("spinCount")).isFalse();
		assertThat(AuditDiff.isSecretName("tokenHash")).isTrue();
	}

	private List<Change> compare(@Nullable Record before, @Nullable Record after) {
		return this.diff.compare(this.diff.flatten(before), this.diff.flatten(after));
	}

	enum Status {

		EXPECTED, ARRIVED

	}

	record Appointment(UUID id, Long version, LocalDate date, @Nullable LocalTime time, LocalizedText service,
			@Nullable BigDecimal price, Status status, List<String> tags, @Nullable Instant updatedAt) {
	}

	record Address(String city, @Nullable String street) {
	}

	record Patient(@Nullable Address address, String note) {
	}

	record Tagged(List<String> tags) {
	}

	record Account(String email, @Nullable String password, @Nullable String passwordHash, byte[] salt,
			@Nullable String apiToken, @AuditSecret @Nullable String recoveryCode, @Nullable String note,
			@Nullable String legacy) {
	}

	record ApiKey(String name, String token) {
	}

	/** Секреты внутри коллекций и Map: имена полей верхнего уровня не секретные. */
	record Integrations(List<ApiKey> keys, Map<String, String> smtp, List<String> history, List<byte[]> blobs) {
	}

	record WithObject(Object owner) {
	}

	/** Обычный класс, не record: так выглядела бы сущность JPA в снимке. */
	static final class PlainAccount {

		private final String login;

		private final String password;

		PlainAccount(String login, String password) {
			this.login = login;
			this.password = password;
		}

		public String getLogin() {
			return this.login;
		}

		public String getPassword() {
			return this.password;
		}

	}

}
