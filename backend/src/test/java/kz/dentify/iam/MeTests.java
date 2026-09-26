package kz.dentify.iam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import kz.dentify.iam.IamFixtures.Person;
import kz.dentify.iam.internal.AccountContexts;
import kz.dentify.iam.internal.MembershipStatus;
import kz.dentify.platform.api.ErrorCodes;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

/**
 * Вошедший аккаунт и выбор клиники (IAM-08, IAM-02): {@code GET /api/v1/me} — аккаунт, членства во всех клиниках и
 * клиника сессии; {@code POST /api/v1/me/context} — клиника только из своих активных членств, филиал — из доступных
 * членству. Изоляция: чужая клиника и её филиал — 404, данные клиники идут за выбранной клиникой. Люди и клиники —
 * вымышленные.
 */
class MeTests extends LoginIntegrationTest {

	/** Пробный ресурс клиники сессии: {@code platform.tenancy.TestNoteController}. */
	static final String NOTES = "/test/notes";

	@Test
	void meWithoutSessionIsUnauthorized() {
		problem(this.http.get().uri(ME).exchange().returnResult(String.class), 401, ErrorCodes.UNAUTHORIZED);
	}

	/** IAM-08: имя, инициалы, язык, контакты; членства с клиникой и доступными филиалами. */
	@Test
	void meShowsAccountMembershipsAndContext() {
		Tenant clinic = this.clinics.create();
		Person person = this.fixtures.person("Алиева Динара Сериковна");
		UUID membership = this.fixtures.join(person, clinic, MembershipStatus.ACTIVE);
		TestSession session = signIn(person);

		JsonNode me = read(me(session));

		JsonNode account = me.path("account");
		assertThat(account.path("id").asString()).isEqualTo(person.id().toString());
		assertThat(account.path("initials").asString()).isEqualTo("АД");
		assertThat(account.path("language").asString()).isEqualTo("ru");
		assertThat(account.path("phone").asString()).isEqualTo(person.phone());
		assertThat(account.path("email").asString()).isEqualTo(person.email());
		assertThat(me.path("memberships").size()).isOne();
		JsonNode only = me.path("memberships").get(0);
		assertThat(only.path("id").asString()).isEqualTo(membership.toString());
		assertThat(only.path("status").asString()).isEqualTo("ACTIVE");
		assertThat(only.path("clinic").path("name").asString()).isEqualTo("Тестовая клиника");
		assertThat(only.path("clinic").path("city").asString()).isEqualTo("Астана");
		assertThat(only.path("branches").get(0).path("name").asString()).isEqualTo("Кенесары, 40");
		assertThat(me.path("context").path("branch").path("id").asString()).isEqualTo(clinic.branchId().toString());
		assertThat(me.toString()).as("пароль и его хэш наружу не уходят").doesNotContain("password", "argon2");
	}

	/**
	 * Аккаунт в двух клиниках: после входа клиника не выбрана, выбор — из своих членств. Филиал без branchId — единственный
	 * доступный, при нескольких — не выбран, явный — из доступных. Выбор отмечает последний вход в клинику.
	 */
	@Test
	void selectsClinicAndBranchFromOwnMemberships() {
		Tenant a = this.clinics.create();
		UUID aSecond = this.clinics.addBranch(a.clinicId());
		Tenant b = this.clinics.create();
		Person person = this.fixtures.person("Нурланов Аскар");
		this.fixtures.join(person, a, MembershipStatus.ACTIVE);
		UUID inB = this.fixtures.join(person, b, MembershipStatus.ACTIVE);
		TestSession session = signIn(person);
		assertThat(read(me(session)).path("context").isNull()).as("две клиники — выбор после входа").isTrue();

		JsonNode inAWithoutBranch = read(select(session, a.clinicId(), null));
		JsonNode inASecond = read(select(session, a.clinicId(), aSecond));
		JsonNode inBOnlyBranch = read(select(session, b.clinicId(), null));

		assertThat(inAWithoutBranch.path("context").path("clinic").path("id").asString())
			.isEqualTo(a.clinicId().toString());
		assertThat(inAWithoutBranch.path("context").path("branch").isNull()).isTrue();
		assertThat(inASecond.path("context").path("branch").path("id").asString()).isEqualTo(aSecond.toString());
		assertThat(inBOnlyBranch.path("context").path("branch").path("id").asString())
			.isEqualTo(b.branchId().toString());
		assertThat(read(me(session)).path("context")).isEqualTo(inBOnlyBranch.path("context"));
		assertThat(TenantContext.call(b, () -> this.jdbc
			.queryForObject("select last_sign_in_at is not null from membership where id = ?", Boolean.class, inB)))
			.isTrue();
	}

	/**
	 * Изоляция: клиника без своего членства, приглашение и филиал другой клиники или вне доступа членства — 404, как
	 * будто их нет; отключённое членство — 403. Клиника сессии после отказа прежняя.
	 */
	@Test
	void foreignClinicOrBranchCannotBeSelected() {
		Tenant own = this.clinics.create();
		UUID ownSecond = this.clinics.addBranch(own.clinicId());
		Tenant foreign = this.clinics.create();
		Tenant invited = this.clinics.create();
		Tenant disabled = this.clinics.create();
		Person person = this.fixtures.person("Сапарова Жанна");
		this.fixtures.join(person, own, MembershipStatus.ACTIVE, own.branchId());
		this.fixtures.join(person, invited, MembershipStatus.INVITED);
		this.fixtures.join(person, disabled, MembershipStatus.DISABLED);
		Person stranger = this.fixtures.person("Ким Виктор");
		this.fixtures.join(stranger, foreign, MembershipStatus.ACTIVE);
		TestSession session = signIn(person);
		JsonNode before = read(me(session)).path("context");
		assertThat(before.path("clinic").path("id").asString()).isEqualTo(own.clinicId().toString());

		problem(select(session, foreign.clinicId(), null), 404, ErrorCodes.NOT_FOUND);
		problem(select(session, UUID.randomUUID(), null), 404, ErrorCodes.NOT_FOUND);
		problem(select(session, invited.clinicId(), null), 404, ErrorCodes.NOT_FOUND);
		problem(select(session, own.clinicId(), foreign.branchId()), 404, ErrorCodes.NOT_FOUND);
		problem(select(session, own.clinicId(), ownSecond), 404, ErrorCodes.NOT_FOUND);
		problem(select(session, disabled.clinicId(), null), 403, AccountContexts.MEMBERSHIP_DISABLED);

		JsonNode me = read(me(session));
		assertThat(me.path("context")).isEqualTo(before);
		assertThat(me.path("memberships").valueStream().map((membership) -> membership.path("clinic").path("id").asString()))
			.doesNotContain(foreign.clinicId().toString());
		assertThat(me.path("memberships").valueStream().map((membership) -> membership.path("branches").size()))
			.as("филиалы — только у активного членства и только доступные")
			.containsExactly(1, 0, 0);
	}

	/** Данные клиники идут за выбранной клиникой: запись клиники A не видна, пока выбрана клиника B. */
	@Test
	void clinicDataFollowsSelectedClinic() {
		Tenant a = this.clinics.create();
		Tenant b = this.clinics.create();
		Person person = this.fixtures.person("Ахметова Сауле");
		this.fixtures.join(person, a, MembershipStatus.ACTIVE);
		this.fixtures.join(person, b, MembershipStatus.ACTIVE);
		TestSession session = signIn(person);

		select(session, a.clinicId(), null);
		String noteInA = createNote(session, "Запись клиники A");
		select(session, b.clinicId(), null);
		List<String> seenInB = notes(session);
		select(session, a.clinicId(), null);
		List<String> seenInA = notes(session);

		assertThat(seenInB).doesNotContain(noteInA);
		assertThat(seenInA).contains(noteInA);
	}

	/** Выбор клиники меняет сессию: без CSRF-токена — 403, без входа — 401. */
	@Test
	void selectingClinicNeedsCsrfTokenAndSession() {
		Tenant clinic = this.clinics.create();
		Person person = this.fixtures.person("Омарова Гульнар");
		this.fixtures.join(person, clinic, MembershipStatus.ACTIVE);
		TestSession session = signIn(person);

		EntityExchangeResult<String> withoutToken = this.http.post()
			.uri(CONTEXT)
			.header(HttpHeaders.COOKIE, session.cookies())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("clinicId", clinic.clinicId()))
			.exchange()
			.returnResult(String.class);
		problem(withoutToken, 403, ErrorCodes.CSRF);

		String xsrf = csrfToken();
		problem(this.http.post()
			.uri(CONTEXT)
			.header(HttpHeaders.COOKIE, XSRF_COOKIE + "=" + xsrf)
			.header(XSRF_HEADER, xsrf)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("clinicId", clinic.clinicId()))
			.exchange()
			.returnResult(String.class), 401, ErrorCodes.UNAUTHORIZED);
	}

	private String createNote(TestSession session, String title) {
		return read(this.http.post()
			.uri(NOTES)
			.header(HttpHeaders.COOKIE, session.sessionCookie())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("title", title))
			.exchange()
			.returnResult(String.class)).path("id").asString();
	}

	private List<String> notes(TestSession session) {
		return read(this.http.get().uri(NOTES).header(HttpHeaders.COOKIE, session.sessionCookie()).exchange()
			.returnResult(String.class)).valueStream().map((note) -> note.path("id").asString()).toList();
	}

}
