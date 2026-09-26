package kz.dentify;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import archfixtures.tenancy.GoodNote;
import archfixtures.tenancy.NoteRepository;
import archfixtures.web.FatController;
import archfixtures.web.GuardedByClassController;
import archfixtures.web.GuardedController;
import archfixtures.web.ThinController;
import archfixtures.web.UnguardedController;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import kz.dentify.platform.api.PageResponse;
import org.junit.jupiter.api.Test;

import org.springframework.data.repository.Repository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Общие правила кода (ArchUnit) для рабочих классов {@code kz.dentify}.
 */
class CodingRulesTests {

	static final JavaClasses CLASSES = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("kz.dentify");

	/** Контроллеры, эндпоинтам которых {@code @PreAuthorize} не нужен, с причиной. */
	static final Map<String, String> CONTROLLERS_WITHOUT_PRE_AUTHORIZE = Map.of(
			"kz.dentify.platform.web.ProblemErrorController",
			"пересылка ошибок на /error: только отдаёт ProblemDetail отклонённого запроса, ничего не читает и не меняет");

	/**
	 * Каждый эндпоинт — метод с {@code @RequestMapping} или {@code @GetMapping} и прочими в контроллере — закрыт
	 * {@code @PreAuthorize} на методе или на классе (у открытых — {@code permitAll()}). Правило на URL в цепочке
	 * Spring Security — только {@code authenticated()}, права по клинике держит аннотация.
	 */
	static final ArchRule ENDPOINTS_HAVE_PRE_AUTHORIZE = methods().that()
		.areMetaAnnotatedWith(RequestMapping.class)
		.and()
		.areDeclaredInClassesThat()
		.areMetaAnnotatedWith(Controller.class)
		.and()
		.areDeclaredInClassesThat(DescribedPredicate.describe("are not in CONTROLLERS_WITHOUT_PRE_AUTHORIZE",
				(JavaClass controller) -> !CONTROLLERS_WITHOUT_PRE_AUTHORIZE.containsKey(controller.getName())))
		.should(bePreAuthorized())
		.because("у каждого эндпоинта @PreAuthorize: права проверяет метод, а не только URL (docs/architecture.md, "
				+ "раздел Безопасность и сессии)")
		.allowEmptyShould(true);

	/**
	 * Контроллер тонкий: данные — только через сервис модуля, наружу — DTO-records. Веб-слой не видит репозиториев Spring
	 * Data и сущностей JPA (docs/architecture.md, «Соглашения API и OpenAPI»).
	 */
	static final ArchRule WEB_USES_NO_REPOSITORIES_OR_ENTITIES = noClasses().that()
		.resideInAPackage("..web..")
		.should()
		.dependOnClassesThat(DescribedPredicate.describe("are Spring Data repositories or JPA entities",
				(JavaClass type) -> type.isAssignableTo(Repository.class) || type.isAnnotatedWith(Entity.class)))
		.because("контроллер тонкий: правила и данные — в сервисе модуля, ответ — DTO-record, а не сущность")
		.allowEmptyShould(true);

	/** Транзакция — граница сервиса, а не контроллера: в веб-слое {@code @Transactional} нет. */
	static final ArchRule WEB_HAS_NO_TRANSACTIONS = CompositeArchRule
		.of(noClasses().that().resideInAPackage("..web..").should().beAnnotatedWith(Transactional.class))
		.and(noMethods().that()
			.areDeclaredInClassesThat()
			.resideInAPackage("..web..")
			.should()
			.beAnnotatedWith(Transactional.class))
		.because("транзакции открывает сервис модуля (docs/architecture.md), контроллер только передаёт параметры")
		.allowEmptyShould(true);

	/**
	 * Эндпоинт отдаёт DTO-record, {@code PageResponse}, список records, {@code ResponseEntity} или ничего: не сущность,
	 * не {@code Map} и не {@code JsonNode} — у них нет схемы в OpenAPI и списка полей.
	 */
	static final ArchRule ENDPOINTS_RETURN_RECORDS = methods().that()
		.areMetaAnnotatedWith(RequestMapping.class)
		.and()
		.areDeclaredInClassesThat()
		.areMetaAnnotatedWith(Controller.class)
		.should(returnDtoRecords())
		.because("ответ API — DTO-record веб-слоя (docs/architecture.md, «Соглашения API и OpenAPI»)")
		.allowEmptyShould(true);

	/**
	 * PLT-10: ПД маскируются только в структурированном логе. {@code System.out}, {@code System.err} и
	 * {@code printStackTrace()} идут мимо маски — только логгер SLF4J.
	 */
	@Test
	void logsOnlyThroughLogger() {
		NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
			.because("маскирование ПД (PLT-10) работает только в JSON-логе; пишите через SLF4J")
			.check(CLASSES);
	}

	@Test
	void endpointsHavePreAuthorize() {
		ENDPOINTS_HAVE_PRE_AUTHORIZE.check(CLASSES);
	}

	/** Правило ловит эндпоинты без {@code @PreAuthorize} — и с {@code @GetMapping}, и с голым {@code @RequestMapping}. */
	@Test
	void preAuthorizeRuleCatchesUnguardedEndpoints() {
		JavaClasses unguarded = new ClassFileImporter().importClasses(UnguardedController.class);
		assertThatThrownBy(() -> ENDPOINTS_HAVE_PRE_AUTHORIZE.check(unguarded)).isInstanceOf(AssertionError.class)
			.hasMessageContaining("forgotten()")
			.hasMessageContaining("forgottenToo()")
			.hasMessageNotContaining("guarded()");

		ENDPOINTS_HAVE_PRE_AUTHORIZE
			.check(new ClassFileImporter().importClasses(GuardedController.class, GuardedByClassController.class));
	}

	@Test
	void webLayerIsThin() {
		WEB_USES_NO_REPOSITORIES_OR_ENTITIES.check(CLASSES);
		WEB_HAS_NO_TRANSACTIONS.check(CLASSES);
		ENDPOINTS_RETURN_RECORDS.check(CLASSES);
	}

	/** Правила веб-слоя ловят репозиторий, сущность, Map и транзакцию в контроллере и пропускают тонкий контроллер. */
	@Test
	void webRulesCatchFatControllers() {
		JavaClasses fat = new ClassFileImporter().importClasses(FatController.class, GoodNote.class, NoteRepository.class);
		assertThatThrownBy(() -> WEB_USES_NO_REPOSITORIES_OR_ENTITIES.check(fat)).isInstanceOf(AssertionError.class)
			.hasMessageContaining("NoteRepository")
			.hasMessageContaining("GoodNote");
		assertThatThrownBy(() -> WEB_HAS_NO_TRANSACTIONS.check(fat)).isInstanceOf(AssertionError.class)
			.hasMessageContaining("write()");
		assertThatThrownBy(() -> ENDPOINTS_RETURN_RECORDS.check(fat)).isInstanceOf(AssertionError.class)
			.hasMessageContaining("entity(")
			.hasMessageContaining("entities()")
			.hasMessageContaining("map()")
			.hasMessageNotContaining("write()");

		JavaClasses thin = new ClassFileImporter().importClasses(ThinController.class, ThinController.NoteView.class);
		WEB_USES_NO_REPOSITORIES_OR_ENTITIES.check(thin);
		WEB_HAS_NO_TRANSACTIONS.check(thin);
		ENDPOINTS_RETURN_RECORDS.check(thin);
	}

	/** void, record, {@code ResponseEntity}, {@code PageResponse} и {@code List} с record внутри. */
	private static ArchCondition<JavaMethod> returnDtoRecords() {
		return new ArchCondition<>("return a DTO record, PageResponse, List of records, ResponseEntity or void") {
			@Override
			public void check(JavaMethod method, ConditionEvents events) {
				if (!isDtoResponse(method.getReturnType())) {
					events.add(SimpleConditionEvent.violated(method, method.getFullName()
							+ " — эндпоинт отдаёт " + method.getReturnType().getName() + ", а не DTO-record"));
				}
			}
		};
	}

	private static boolean isDtoResponse(JavaType type) {
		JavaClass raw = type.toErasure();
		if (raw.getName().equals("void") || raw.isRecord() && !raw.isEquivalentTo(PageResponse.class)
				|| raw.isEquivalentTo(ResponseEntity.class)) {
			return true;
		}
		if (raw.isEquivalentTo(PageResponse.class) || raw.isEquivalentTo(List.class)) {
			return type instanceof JavaParameterizedType parameterized
					&& parameterized.getActualTypeArguments().size() == 1
					&& parameterized.getActualTypeArguments().getFirst().toErasure().isRecord();
		}
		return false;
	}

	private static ArchCondition<JavaMethod> bePreAuthorized() {
		return new ArchCondition<>("be annotated with @PreAuthorize or declared in a class annotated with it") {
			@Override
			public void check(JavaMethod method, ConditionEvents events) {
				boolean guarded = method.isMetaAnnotatedWith(PreAuthorize.class)
						|| method.getOwner().isMetaAnnotatedWith(PreAuthorize.class);
				if (!guarded) {
					events.add(SimpleConditionEvent.violated(method,
							method.getFullName() + " — эндпоинт без @PreAuthorize"));
				}
			}
		};
	}

}
