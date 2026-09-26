package kz.dentify.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiFunction;

import kz.dentify.platform.api.Permissions;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.UriTemplate;

/**
 * Общий тест изоляции клиник (PLT-01): перебирает все эндпоинты приложения из {@link RequestMappingHandlerMapping} и
 * проверяет, что пользователь клиники A не достаёт ресурсы клиники B.
 * <ul>
 * <li>каждый эндпоинт — либо в явном реестре {@link #NOT_CLINIC_DATA} с причиной, либо под ресурсом клиники из
 * {@link #RESOURCES} и проверен им: путь коллекции — список (GET) и создание (POST), путь с переменными — по id
 * объектов клиники B, остальное под ресурсом (массовые действия с id в теле или параметрах) — только если описано в
 * {@link TenantResource#notById()} с тем, где проверена изоляция. Эндпоинт вне этих правил роняет тест;</li>
 * <li>любой метод по пути с переменными из-под клиники A, когда каждая переменная — id своего объекта клиники B
 * (родителя, потомка, связанного объекта: их создаёт {@link TenantResource#create()}), — 404 {@code error.not-found},
 * как будто объекта нет, хотя сама клиника B по этому пути GET получает 2xx. Тело запроса — представление объекта, которое видит его владелец, то есть заведомо
 * правильное: 404 не маскируется ошибкой валидации. После всех попыток объект у владельца не изменился;</li>
 * <li>список ресурса не показывает объекты другой клиники.</li>
 * </ul>
 * Новый ресурс модуля: строка в {@link #RESOURCES} — путь, как создать объекты в клинике через API и какие id они дают
 * переменным путей. Чужой id в теле запроса (запись с пациентом клиники B) харнесс не подставляет: это проверяет
 * интеграционный тест модуля, чей DTO ссылается на объекты клиники.
 */
class TenantIsolationTests extends TenantIntegrationTest {

	/** Журнал действий клиники (iam.web.AuditEventController). */
	static final String AUDIT_EVENTS = "/api/v1/audit-events";

	/** Причина для {@code /api/v1/me} и {@code /api/v1/me/context}. */
	private static final String OWN_ACCOUNT = "свой аккаунт и свои членства во всех клиниках (own_memberships); выбор "
			+ "клиники — только из своих активных членств, чужая клиника и её филиал — 404 (iam.MeTests)";

	/** Причина для сессий своего аккаунта. */
	private static final String OWN_SESSIONS = "сессии своего аккаунта: глобальные данные аккаунта в Valkey, не клиники";

	/**
	 * Эндпоинты без данных клиники — путь и причина. Путь — шаблон эндпоинта целиком ({@code /api/v1/me}); путь с
	 * {@code /**} на конце — всё под ним: только для путей, под которыми данных клиники не бывает (вход, пробы тестов).
	 * Путь аккаунта ({@code /api/v1/me}, {@code /api/v1/account}) — только целиком: новый эндпоинт под ним (профиль,
	 * настройки уведомлений) может задеть данные клиники и должен сам попасть в тест. Сюда не попадает ничего, что читает
	 * или меняет данные клиники.
	 */
	static final Map<String, String> NOT_CLINIC_DATA = Map.ofEntries(
			Map.entry("/error", "ответ об ошибке, данных нет (ProblemErrorController)"),
			Map.entry("/v3/api-docs/**", "спецификация OpenAPI, только dev и test"),
			Map.entry("/test/problems/**", "пробы формата ошибок платформы, глобальные тестовые таблицы без clinic_id"),
			Map.entry("/test/log-probe/**", "проба маскирования ПД в логах"),
			Map.entry("/test/scheme/**", "проба заголовков прокси"),
			Map.entry(TenantSessionProbeController.PATH, "пробный вход в клинику: пишет только HTTP-сессию"),
			Map.entry("/api/v1/auth/**", "вход, выход и CSRF-токен: данных клиники нет, клиники до входа тоже нет"),
			Map.entry("/api/v1/account/sessions", OWN_SESSIONS),
			Map.entry("/api/v1/account/sessions/{id}", OWN_SESSIONS),
			Map.entry("/api/v1/me", OWN_ACCOUNT),
			Map.entry("/api/v1/me/context", OWN_ACCOUNT),
			Map.entry("/secured-probe/**", "пробы цепочки безопасности: вход, CSRF, отказ в праве и запрос в полёте, данных нет"),
			Map.entry("/test/audit/**", "пробы записи журнала действий в клинике сессии; изоляцию журнала проверяет ресурс "
					+ AUDIT_EVENTS));

	/** Окончание пути реестра {@link #NOT_CLINIC_DATA}, которое покрывает всё под путём. */
	private static final String SUBTREE = "/**";

	/**
	 * Ресурсы клиники: путь коллекции, переменная id объекта и как создать объекты в клинике сессии. Событие журнала
	 * создаёт правка пробной записи ({@code @Audited} в {@link TestNoteService}).
	 */
	static final List<TenantResource> RESOURCES = List.of(new TenantResource(NOTES, "id", (test, session) -> Map.of(
			"id", test.createNote(session, "Изоляция").path("id").asString(),
			"relatedId", test.createNote(session, "Связанная").path("id").asString()), Map.of()),
			new TenantResource(AUDIT_EVENTS, "id", (test, session) -> Map.of("id",
					test.auditEventOf(session, test.createNote(session, "Журнал").path("id").asString())), Map.of()));

	@Autowired
	ApplicationContext context;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void everyEndpointIsCheckedOrExplicitlyNotClinicData() {
		assertThat(unchecked(endpoints(), RESOURCES))
			.as("эндпоинты вне теста изоляции: добавьте ресурс в RESOURCES, описание в TenantResource.notById или "
					+ "причину в NOT_CLINIC_DATA")
			.isEmpty();
		assertThat(endpoints().keySet()).as("харнесс видит пробный ресурс клиники")
			.contains(NOTES + "/{id}", NOTES + "/{id}/related/{relatedId}");
	}

	/**
	 * Харнесс не засчитывает то, чего не проверяет (массовое действие без id в пути, лишний метод коллекции), и не
	 * подставляет один id во все переменные пути: у каждой переменной — свой объект, нет значения — тест падает.
	 */
	@Test
	void harnessRejectsWhatItCannotCheck() {
		TenantResource notes = new TenantResource(NOTES, "id", (test, session) -> Map.of(), Map.of());
		Map<String, Set<RequestMethod>> endpoints = Map.of(
				NOTES, Set.of(RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE),
				NOTES + "/{id}", Set.of(RequestMethod.GET),
				NOTES + "/bulk-archive", Set.of(RequestMethod.POST),
				"/test/unknown", Set.of(RequestMethod.GET));

		assertThat(unchecked(endpoints, List.of(notes))).containsExactlyInAnyOrder(
				"[DELETE] " + NOTES + " — у коллекции проверяются только список и создание",
				"[POST] " + NOTES + "/bulk-archive — под ресурсом без переменных пути, не описан в notById",
				"[GET] /test/unknown — ни ресурс, ни NOT_CLINIC_DATA");

		TenantResource described = new TenantResource(NOTES, "id", (test, session) -> Map.of(),
				Map.of(NOTES + "/bulk-archive", "id в теле: проверка в тесте модуля"));
		assertThat(unchecked(Map.of(NOTES + "/bulk-archive", Set.of(RequestMethod.POST)), List.of(described)))
			.isEmpty();

		assertThat(unchecked(Map.of("/api/v1/me", Set.of(RequestMethod.GET), "/api/v1/me/context",
				Set.of(RequestMethod.POST), "/api/v1/me/profile", Set.of(RequestMethod.PUT), "/api/v1/auth/password-reset",
				Set.of(RequestMethod.POST)), List.of()))
			.as("путь аккаунта — только целиком, вход — всё под /api/v1/auth")
			.containsExactly("[PUT] /api/v1/me/profile — ни ресурс, ни NOT_CLINIC_DATA");

		assertThat(expand(NOTES + "/{id}/related/{relatedId}", Map.of("id", "a", "relatedId", "b")))
			.isEqualTo(NOTES + "/a/related/b");
		assertThatThrownBy(() -> expand(NOTES + "/{id}/related/{relatedId}", Map.of("id", "a")))
			.hasMessageContaining("relatedId");
	}

	@Test
	void foreignClinicObjectsAreNotFoundByAnyMethod() {
		String sessionA = member(newClinic());
		String sessionB = member(newClinic());
		Map<String, Set<RequestMethod>> endpoints = endpoints();
		List<String> leaks = new ArrayList<>();
		int checked = 0;

		for (TenantResource resource : RESOURCES) {
			Map<String, String> foreign = resource.create().apply(this, sessionB);
			String item = expand(resource.item(), foreign);
			String ownerView = get(item, sessionB);
			for (Map.Entry<String, Set<RequestMethod>> endpoint : endpoints.entrySet()) {
				String pattern = endpoint.getKey();
				if (resource(pattern, RESOURCES) != resource || !hasVariables(pattern)) {
					continue;
				}
				String uri = expand(pattern, foreign);
				if (endpoint.getValue().contains(RequestMethod.GET)) {
					// владелец видит объекты по этому пути: 404 клинике A — изоляция, а не неверные id или сломанный путь
					this.http.get().uri(uri).header(HttpHeaders.COOKIE, sessionB).exchange()
						.expectStatus()
						.is2xxSuccessful();
				}
				for (RequestMethod method : endpoint.getValue()) {
					checked++;
					EntityExchangeResult<String> result = request(method.asHttpMethod(), uri, sessionA, ownerView)
						.expectBody(String.class)
						.returnResult();
					String code = (result.getResponseBody() != null)
							? read(result.getResponseBody()).path("code").asString("") : "";
					if (result.getStatus().value() != 404 || !code.equals("error.not-found")) {
						leaks.add(method + " " + pattern + " -> " + result.getStatus().value() + " " + code);
					}
				}
			}
			assertThat(get(item, sessionB)).as("объект клиники B не изменился").isEqualTo(ownerView);
		}

		assertThat(leaks).as("эндпоинты, которые отдали или изменили объект другой клиники").isEmpty();
		assertThat(checked).as("проверены все методы пробного ресурса по id").isGreaterThanOrEqualTo(7);
	}

	@Test
	void listsShowOnlyOwnClinic() {
		String sessionA = member(newClinic());
		String sessionB = member(newClinic());

		for (TenantResource resource : RESOURCES) {
			Map<String, String> own = resource.create().apply(this, sessionA);
			Map<String, String> foreign = resource.create().apply(this, sessionB);

			String list = get(expand(resource.path(), own), sessionA);

			assertThat(list).contains(own.get(resource.idVariable()))
				.doesNotContain(foreign.get(resource.idVariable()));
		}
	}

	/** Приёмка PLT-01: клиника берётся из сессии, clinicId в теле запроса ничего не меняет. */
	@Test
	void createdObjectBelongsToSessionClinicWhateverTheBodySays() {
		UUID clinicA = newClinic();
		UUID clinicB = newClinic();
		String sessionA = enter(clinicA);
		String sessionB = enter(clinicB);

		JsonNode created = ok(this.http.post()
			.uri(NOTES)
			.header(HttpHeaders.COOKIE, sessionA)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("title", "Чужая клиника в теле", "clinicId", clinicB, "clinic_id", clinicB))
			.exchange());
		String id = created.path("id").asString();

		assertThat(created.has("clinicId")).as("DTO без clinicId").isFalse();
		assertThat(get(NOTES + "/" + id, sessionA)).contains(id);
		problem(this.http.get().uri(NOTES + "/" + id).header(HttpHeaders.COOKIE, sessionB).exchange(), 404,
				"error.not-found");
		UUID stored = TenantContext.call(Tenant.of(clinicA),
				() -> this.jdbc.queryForObject("select clinic_id from test_note where id = ?::uuid", UUID.class, id));
		assertThat(stored).isEqualTo(clinicA);
	}

	/** Без выбранной клиники в сессии данные клиники недоступны: 401, интерфейс ведёт на выбор клиники. */
	@Test
	void requestWithoutClinicInSessionIsUnauthorized() {
		problem(this.http.get().uri(NOTES).exchange(), 401, "error.clinic-not-selected");

		String session = enter(newClinic());
		this.http.delete().uri(TenantSessionProbeController.PATH).header(HttpHeaders.COOKIE, session).exchange()
			.expectStatus()
			.isNoContent();
		problem(this.http.get().uri(NOTES).header(HttpHeaders.COOKIE, session).exchange(), 401,
				"error.clinic-not-selected");
	}

	/**
	 * Сессия вымышленного сотрудника клиники с правами на все ресурсы из {@link #RESOURCES}: харнесс проверяет изоляцию
	 * клиник, а не права.
	 */
	String member(UUID clinicId) {
		return enterClinic(clinicId, UUID.randomUUID(), Permissions.AUDIT_READ, Permissions.PATIENT_PHONES);
	}

	/** Id события журнала об объекте клиники сессии. */
	String auditEventOf(String session, String entityId) {
		JsonNode page = ok(this.http.get()
			.uri(AUDIT_EVENTS + "?entityId={id}", entityId)
			.header(HttpHeaders.COOKIE, session)
			.exchange());
		assertThat(page.path("items")).as("событие журнала об объекте %s", entityId).isNotEmpty();
		return page.path("items").get(0).path("id").asString();
	}

	/** Все эндпоинты приложения: шаблон пути → HTTP-методы. */
	Map<String, Set<RequestMethod>> endpoints() {
		Map<String, Set<RequestMethod>> endpoints = new TreeMap<>();
		for (RequestMappingHandlerMapping mapping : this.context.getBeansOfType(RequestMappingHandlerMapping.class)
			.values()) {
			for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
				Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
				for (String pattern : info.getPatternValues()) {
					endpoints.computeIfAbsent(pattern, (key) -> new TreeSet<>())
						.addAll(methods.isEmpty() ? Set.of(RequestMethod.GET, RequestMethod.POST) : methods);
				}
			}
		}
		return endpoints;
	}

	/**
	 * Эндпоинты, изоляцию которых харнесс не проверяет, с причиной. Пусто — каждый эндпоинт либо без данных клиники,
	 * либо проверен ресурсом.
	 */
	static List<String> unchecked(Map<String, Set<RequestMethod>> endpoints, List<TenantResource> resources) {
		List<String> unchecked = new ArrayList<>();
		new TreeMap<>(endpoints).forEach((pattern, methods) -> {
			if (notClinicData(pattern) != null) {
				return;
			}
			TenantResource resource = resource(pattern, resources);
			if (resource == null) {
				unchecked.add(new TreeSet<>(methods) + " " + pattern + " — ни ресурс, ни NOT_CLINIC_DATA");
			}
			else if (pattern.equals(resource.path()) && !hasVariables(pattern)) {
				Set<RequestMethod> other = new TreeSet<>(methods);
				other.removeAll(Set.of(RequestMethod.GET, RequestMethod.POST));
				if (!other.isEmpty()) {
					unchecked.add(other + " " + pattern + " — у коллекции проверяются только список и создание");
				}
			}
			else if (!hasVariables(pattern) && !resource.notById().containsKey(pattern)) {
				unchecked.add(new TreeSet<>(methods) + " " + pattern
						+ " — под ресурсом без переменных пути, не описан в notById");
			}
		});
		return unchecked;
	}

	/** Путь с подставленными значениями переменных; переменная без значения роняет тест. */
	static String expand(String pattern, Map<String, String> variables) {
		UriTemplate template = new UriTemplate(pattern);
		List<String> missing = template.getVariableNames()
			.stream()
			.filter((name) -> !variables.containsKey(name))
			.toList();
		assertThat(missing).as("TenantResource.create не даёт значений переменным пути " + pattern).isEmpty();
		return template.expand(variables).toString();
	}

	private static boolean hasVariables(String pattern) {
		return !new UriTemplate(pattern).getVariableNames().isEmpty();
	}

	/** Причина из {@link #NOT_CLINIC_DATA}: путь целиком или под путём с {@code /**}; нет — null. */
	private static String notClinicData(String pattern) {
		return NOT_CLINIC_DATA.entrySet()
			.stream()
			.filter((entry) -> covers(entry.getKey(), pattern))
			.map(Map.Entry::getValue)
			.findFirst()
			.orElse(null);
	}

	private static boolean covers(String registered, String pattern) {
		if (!registered.endsWith(SUBTREE)) {
			return pattern.equals(registered);
		}
		String base = registered.substring(0, registered.length() - SUBTREE.length());
		return pattern.equals(base) || pattern.startsWith(base + "/") || pattern.startsWith(base + ".");
	}

	/** Ресурс эндпоинта: самый длинный путь ресурса, под которым он лежит, — для вложенных ресурсов. */
	private static TenantResource resource(String pattern, List<TenantResource> resources) {
		return resources.stream()
			.filter((resource) -> pattern.equals(resource.path()) || pattern.startsWith(resource.path() + "/"))
			.max(Comparator.comparingInt((TenantResource resource) -> resource.path().length()))
			.orElse(null);
	}

	private String get(String uri, String session) {
		return this.http.get()
			.uri(uri)
			.header(HttpHeaders.COOKIE, session)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
	}

	private ResponseSpec request(HttpMethod method, String uri, String session, String body) {
		if (method == HttpMethod.GET || method == HttpMethod.DELETE || method == HttpMethod.HEAD) {
			return this.http.method(method).uri(uri).header(HttpHeaders.COOKIE, session).exchange();
		}
		return this.http.method(method)
			.uri(uri)
			.header(HttpHeaders.COOKIE, session)
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.exchange();
	}

	/**
	 * Ресурс клиники в тесте изоляции.
	 *
	 * @param path путь коллекции; может содержать переменные родителя: {@code /patients/{patientId}/visits}. Все
	 * эндпоинты под ним проверяются этим ресурсом, если под ним нет более длинного пути другого ресурса
	 * @param idVariable переменная пути с id объекта; объект читается по {@code path/{idVariable}}
	 * @param create создаёт в клинике сессии объект ресурса и всё, что нужно его путям (родителя, связанные объекты), и
	 * возвращает значение каждой переменной путей под ресурсом
	 * @param notById эндпоинты под ресурсом без переменных пути, кроме коллекции (массовые действия с id в теле или
	 * параметрах): шаблон пути и где проверена их изоляция
	 */
	record TenantResource(String path, String idVariable,
			BiFunction<TenantIsolationTests, String, Map<String, String>> create, Map<String, String> notById) {

		String item() {
			return this.path + "/{" + this.idVariable + "}";
		}

	}

}
