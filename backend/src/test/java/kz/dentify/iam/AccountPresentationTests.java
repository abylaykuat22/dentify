package kz.dentify.iam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import kz.dentify.iam.IamFixtures.Person;
import kz.dentify.iam.internal.MembershipStatus;
import kz.dentify.platform.api.Tenant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/**
 * Как аккаунт виден другим и на каком языке ему отвечает API (iam: {@code AccountDisplayNames},
 * {@code AccountLanguage}):
 * <ul>
 * <li>PLT-08: автор последней правки в ответе 409 — ФИО аккаунта с членством в клинике; чужому для клиники аккаунту
 * имя не отдаётся;</li>
 * <li>PLT-11: язык аккаунта — первое звено цепочки языка запроса, раньше cookie и {@code Accept-Language} (в профиле
 * test казахский включён, {@code dentify.i18n.kk=true}).</li>
 * </ul>
 * Люди, клиники и записи — вымышленные.
 */
class AccountPresentationTests extends LoginIntegrationTest {

	static final String NOTES = "/test/notes";

	static final String UNKNOWN_PATH = "/api/v1/no-such-resource";

	@Test
	void versionConflictNamesAuthorByAccountFullName() {
		Tenant clinic = this.clinics.create();
		Person dinara = this.fixtures.person("Сейтова Динара");
		Person askar = this.fixtures.person("Нурланов Аскар");
		this.fixtures.join(dinara, clinic, MembershipStatus.ACTIVE);
		this.fixtures.join(askar, clinic, MembershipStatus.ACTIVE);
		String dinaraSession = enterClinic(clinic.clinicId(), dinara.id());
		String askarSession = enterClinic(clinic.clinicId(), askar.id());
		String id = createNote(dinaraSession).path("id").asString();
		assertThat(updateNote(dinaraSession, id, 0).getStatus().value()).isEqualTo(200);

		JsonNode conflict = problem(updateNote(askarSession, id, 0), 409, "error.version-conflict");

		assertThat(conflict.path("modifiedBy").asString()).isEqualTo(dinara.id().toString());
		assertThat(conflict.path("modifiedByName").asString()).isEqualTo("Сейтова Динара");
	}

	/** Имя аккаунта без членства в клинике контекста не отдаётся: iam не раскрывает людей других клиник. */
	@Test
	void nameOfAccountWithoutMembershipInClinicIsNotShown() {
		Tenant clinic = this.clinics.create();
		Person member = this.fixtures.person("Ахметова Сауле");
		Person outsider = this.fixtures.person("Посторонний Демо");
		this.fixtures.join(member, clinic, MembershipStatus.ACTIVE);
		String outsiderSession = enterClinic(clinic.clinicId(), outsider.id());
		String memberSession = enterClinic(clinic.clinicId(), member.id());
		String id = createNote(outsiderSession).path("id").asString();
		assertThat(updateNote(outsiderSession, id, 0).getStatus().value()).isEqualTo(200);

		JsonNode conflict = problem(updateNote(memberSession, id, 0), 409, "error.version-conflict");

		assertThat(conflict.path("modifiedBy").asString()).isEqualTo(outsider.id().toString());
		assertThat(conflict.has("modifiedByName")).isFalse();
	}

	/** Язык аккаунта kk: ответ на казахском, хотя cookie и заголовок просят русский. */
	@Test
	void accountLanguageWinsOverCookieAndAcceptLanguage() {
		Person person = this.fixtures.person("Қайратқызы Жанар");
		this.jdbc.update("update account set language = 'kk' where id = ?", person.id());
		TestSession session = signIn(person);

		JsonNode problem = problem(this.http.get()
			.uri(UNKNOWN_PATH)
			.header(HttpHeaders.COOKIE, session.cookies() + "; dentify-lang=ru")
			.header(HttpHeaders.ACCEPT_LANGUAGE, "ru-RU,ru;q=0.9")
			.exchange()
			.returnResult(String.class), 404, "error.not-found");

		assertThat(problem.path("detail").asString()).isEqualTo("Жазба табылмады немесе жойылды.");
	}

	/** Язык аккаунта ru: ответ на русском, хотя браузер просит казахский. */
	@Test
	void russianAccountLanguageWinsOverKazakhBrowser() {
		TestSession session = signIn(this.fixtures.person("Орыс Тілді"));

		JsonNode problem = problem(this.http.get()
			.uri(UNKNOWN_PATH)
			.header(HttpHeaders.COOKIE, session.cookies())
			.header(HttpHeaders.ACCEPT_LANGUAGE, "kk")
			.exchange()
			.returnResult(String.class), 404, "error.not-found");

		assertThat(problem.path("detail").asString()).isEqualTo("Запись не найдена или удалена.");
	}

	private JsonNode createNote(String session) {
		EntityExchangeResult<String> result = this.http.post()
			.uri(NOTES)
			.header(HttpHeaders.COOKIE, session)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("title", "Осмотр " + UUID.randomUUID()))
			.exchange()
			.returnResult(String.class);
		assertThat(result.getStatus().value()).as("создание: %s", result.getResponseBody()).isEqualTo(200);
		return read(result);
	}

	private EntityExchangeResult<String> updateNote(String session, String id, long version) {
		return this.http.put()
			.uri(NOTES + "/" + id)
			.header(HttpHeaders.COOKIE, session)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("title", "Правка " + UUID.randomUUID(), "version", version))
			.exchange()
			.returnResult(String.class);
	}

}
