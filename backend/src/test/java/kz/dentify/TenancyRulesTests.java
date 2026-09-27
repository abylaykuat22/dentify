package kz.dentify;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import archfixtures.tenancy.DictionaryTerm;
import archfixtures.tenancy.GoodNote;
import archfixtures.tenancy.NoteRepository;
import archfixtures.tenancy.NoteRepositoryBase;
import archfixtures.tenancy.PlainEntity;
import archfixtures.tenancy.PoolUnwrapper;
import archfixtures.tenancy.PoolUser;
import archfixtures.tenancy.ScopedNoteRepository;
import archfixtures.tenancy.SessionReader;
import archfixtures.tenancy.SessionWriter;
import archfixtures.tenancy.TermRepository;
import archfixtures.tenancy.web.AliasedNoteRequest;
import archfixtures.tenancy.web.CleanNoteRequest;
import archfixtures.tenancy.web.LeakyNoteRequest;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.servlet.http.HttpSession;
import kz.dentify.platform.api.GlobalEntity;
import kz.dentify.platform.api.TenantEntity;
import org.junit.jupiter.api.Test;

import org.springframework.data.repository.Repository;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.session.Session;

/**
 * ArchUnit-правила изоляции клиник (PLT-01) для рабочих классов {@code kz.dentify}:
 * <ul>
 * <li>каждая сущность JPA наследует {@link TenantEntity} (с {@code @TenantId}) или явно помечена глобальной —
 * {@link GlobalEntity}; глобальные сущности — только в модулях из {@link #GLOBAL_ENTITY_MODULES};</li>
 * <li>DTO-records веб-слоя не принимают и не отдают клинику: поля {@code clinicId}/{@code clinic_id} нет, в том числе
 * под другим именем через {@code @JsonProperty}. Клиника — только из сессии;</li>
 * <li>нативный SQL в репозитории сущности клиники фильтрует по {@code clinic_id}: к нему фильтр Hibernate не
 * применяется;</li>
 * <li>соединения — только через бин {@code DataSource} приложения: пул Hikari в обход обёртки, которая ставит клинику,
 * отдал бы соединение с {@code app.clinic_id} его прошлого владельца (PLT-02);</li>
 * <li>HTTP-сессию меняют только платформа и iam: клиника в сессии и её сроки — инварианты изоляции клиник.</li>
 * </ul>
 * Что правила действительно ловят нарушения, проверяется на фикстурах {@code archfixtures.tenancy} (вне пакета
 * {@code kz.dentify}: их не видят JPA и Spring).
 */
class TenancyRulesTests {

	/** Модули, где разрешены глобальные таблицы, и что в них глобально. */
	static final Map<String, String> GLOBAL_ENTITY_MODULES = Map.of(
			"platform", "инфраструктура платформы",
			"iam", "аккаунты и журнал входов: аккаунт существует до выбора клиники",
			"clinic", "реестр клиник: сама клиника, а не её данные",
			"medical", "справочник МКБ-10, общий для всех клиник");

	static final Pattern CLINIC_ID = Pattern.compile("(?i)clinic_?id");

	static final ArchRule ENTITIES_ARE_TENANT_OR_GLOBAL = classes().that()
		.areAnnotatedWith(Entity.class)
		.should()
		.beAssignableTo(TenantEntity.class)
		.orShould()
		.beAssignableTo(GlobalEntity.class)
		.because("данные клиники изолирует @TenantId из TenantEntity, глобальная таблица помечается явно (PLT-01)")
		.allowEmptyShould(true);

	static final ArchRule GLOBAL_ENTITIES_ONLY_IN_ALLOWED_MODULES = classes().that()
		.areAssignableTo(GlobalEntity.class)
		.should()
		.resideInAnyPackage(GLOBAL_ENTITY_MODULES.keySet()
			.stream()
			.map((module) -> "kz.dentify." + module + "..")
			.toArray(String[]::new))
		.because("данные пациентов, записей, склада принадлежат клинике — только TenantEntity")
		.allowEmptyShould(true);

	/**
	 * DTO веб-слоя, которыми клиника приходит от клиента, — только выбор клиники сессии, с причиной. Клиника становится
	 * контекстом лишь после проверки, что у аккаунта в ней активное членство; данных клиники такой DTO не несёт.
	 */
	static final Map<String, String> CLINIC_SELECTION_DTOS = Map.of("kz.dentify.iam.web.SelectContextRequest",
			"iam: выбор клиники и филиала сессии (POST /api/v1/me/context) из своих членств, T-IAM-02");

	static final ArchRule WEB_DTOS_HAVE_NO_CLINIC_ID = fields().that()
		.areDeclaredInClassesThat(DescribedPredicate.describe("records веб-слоя, кроме выбора клиники",
				(JavaClass type) -> type.isRecord() && type.getPackageName().matches(".*\\.web(\\..*)?")
						&& !CLINIC_SELECTION_DTOS.containsKey(type.getName())))
		.should(notCarryClinicId())
		.because("клиника берётся только из сессии: передать или изменить clinic_id через API нельзя (PLT-01)")
		.allowEmptyShould(true);

	static final ArchRule TENANT_NATIVE_QUERIES_FILTER_BY_CLINIC = classes().that()
		.areInterfaces()
		.and()
		.areAssignableTo(Repository.class)
		.should(filterNativeQueriesByClinic())
		.because("к нативному SQL фильтр Hibernate по клинике не применяется, остаётся только RLS")
		.allowEmptyShould(true);

	/** Пакет обёртки пула: единственное место, где пул Hikari виден напрямую. */
	static final String CLINIC_CONNECTIONS = "kz.dentify.platform.persistence..";

	static final ArchRule CONNECTIONS_ONLY_THROUGH_CLINIC_WRAPPER = noClasses().that()
		.resideOutsideOfPackage(CLINIC_CONNECTIONS)
		.should()
		.dependOnClassesThat()
		.resideInAPackage("com.zaxxer.hikari..")
		.orShould()
		.dependOnClassesThat()
		.haveFullyQualifiedName("org.springframework.boot.jdbc.DataSourceUnwrapper")
		.orShould()
		.callMethodWhere(DescribedPredicate.describe("DataSource.unwrap or DelegatingDataSource.getTargetDataSource",
				(JavaMethodCall call) -> (call.getTargetOwner().isAssignableTo(DataSource.class)
						&& call.getName().equals("unwrap"))
						|| (call.getTargetOwner().isAssignableTo(DelegatingDataSource.class)
								&& call.getName().equals("getTargetDataSource"))))
		.because("соединение пула в обход ClinicConnectionDataSource несёт клинику прошлого владельца (PLT-02)")
		.allowEmptyShould(true);

	/**
	 * Пакеты, которым можно менять HTTP-сессию: платформа (вход и выход, клиника в сессии, сроки и отзыв сессий) и iam
	 * (язык аккаунта; клинику — через {@code TenantSession} после проверки членства).
	 */
	static final String[] SESSION_WRITERS = { "kz.dentify.platform..", "kz.dentify.iam.." };

	/** Методы, которые меняют сессию. */
	static final Set<String> SESSION_WRITES = Set.of("setAttribute", "removeAttribute", "invalidate");

	/**
	 * Клинику в сессию кладёт только iam через {@code platform :: identity}, сессии гасит только платформа — с отметкой
	 * отзыва. Modulith обход через саму сессию не видит: имя атрибута ({@code TenantSession.CLINIC_ID}) — строковая
	 * константа, компилятор встраивает её в вызывающий класс, и зависимости на {@code platform :: identity} в байткоде нет.
	 */
	static final ArchRule SESSION_WRITTEN_ONLY_BY_PLATFORM_AND_IAM = noClasses().that()
		.resideOutsideOfPackages(SESSION_WRITERS)
		.should()
		.callMethodWhere(DescribedPredicate.describe("HttpSession or Session setAttribute, removeAttribute, invalidate",
				(JavaMethodCall call) -> (call.getTargetOwner().isAssignableTo(HttpSession.class)
						|| call.getTargetOwner().isAssignableTo(Session.class))
						&& SESSION_WRITES.contains(call.getName())))
		.because("клиника в сессии и сроки сессии — инварианты изоляции клиник: их меняют только платформа и iam "
				+ "(docs/architecture.md, Модули и события)")
		.allowEmptyShould(true);

	@Test
	void connectionsOnlyThroughClinicWrapper() {
		CONNECTIONS_ONLY_THROUGH_CLINIC_WRAPPER.check(CodingRulesTests.CLASSES);
	}

	@Test
	void sessionIsWrittenOnlyByPlatformAndIam() {
		SESSION_WRITTEN_ONLY_BY_PLATFORM_AND_IAM.check(CodingRulesTests.CLASSES);
	}

	@Test
	void entitiesAreTenantOrExplicitlyGlobal() {
		ENTITIES_ARE_TENANT_OR_GLOBAL.check(CodingRulesTests.CLASSES);
		GLOBAL_ENTITIES_ONLY_IN_ALLOWED_MODULES.check(CodingRulesTests.CLASSES);
	}

	@Test
	void webDtosHaveNoClinicId() {
		WEB_DTOS_HAVE_NO_CLINIC_ID.check(CodingRulesTests.CLASSES);
	}

	/** Реестр выбора клиники — только настоящие records веб-слоя, и в каждом клиника действительно приходит. */
	@Test
	void clinicSelectionDtosAreWebRecordsWithClinicId() {
		for (String name : CLINIC_SELECTION_DTOS.keySet()) {
			JavaClass type = CodingRulesTests.CLASSES.get(name);
			assertThat(type.isRecord()).as(name).isTrue();
			assertThat(type.getPackageName()).as(name).matches(".*\\.web(\\..*)?");
			assertThat(type.getFields()).as(name).anyMatch((field) -> CLINIC_ID.matcher(field.getName()).matches());
		}
	}

	@Test
	void tenantNativeQueriesFilterByClinic() {
		TENANT_NATIVE_QUERIES_FILTER_BY_CLINIC.check(CodingRulesTests.CLASSES);
	}

	/** Приёмка PLT-01: правило падает на сущности без @TenantId, то есть без TenantEntity и без пометки глобальной. */
	@Test
	void rulesCatchViolations() {
		assertViolation(ENTITIES_ARE_TENANT_OR_GLOBAL, imported(PlainEntity.class), PlainEntity.class.getName());
		ENTITIES_ARE_TENANT_OR_GLOBAL.check(imported(GoodNote.class, DictionaryTerm.class));
		assertViolation(GLOBAL_ENTITIES_ONLY_IN_ALLOWED_MODULES, imported(DictionaryTerm.class),
				DictionaryTerm.class.getName());

		assertViolation(WEB_DTOS_HAVE_NO_CLINIC_ID, imported(LeakyNoteRequest.class), "clinicId");
		assertViolation(WEB_DTOS_HAVE_NO_CLINIC_ID, imported(AliasedNoteRequest.class), "owner");
		WEB_DTOS_HAVE_NO_CLINIC_ID.check(imported(CleanNoteRequest.class));

		assertViolation(TENANT_NATIVE_QUERIES_FILTER_BY_CLINIC, imported(NoteRepository.class, GoodNote.class),
				"everything");
		TENANT_NATIVE_QUERIES_FILTER_BY_CLINIC.check(imported(ScopedNoteRepository.class, NoteRepositoryBase.class,
				TermRepository.class));

		assertViolation(CONNECTIONS_ONLY_THROUGH_CLINIC_WRAPPER, imported(PoolUnwrapper.class), "unwrap");
		assertViolation(CONNECTIONS_ONLY_THROUGH_CLINIC_WRAPPER, imported(PoolUnwrapper.class), "com.zaxxer.hikari");
		CONNECTIONS_ONLY_THROUGH_CLINIC_WRAPPER.check(imported(PoolUser.class));

		assertViolation(SESSION_WRITTEN_ONLY_BY_PLATFORM_AND_IAM, imported(SessionWriter.class), "setAttribute");
		assertViolation(SESSION_WRITTEN_ONLY_BY_PLATFORM_AND_IAM, imported(SessionWriter.class), "invalidate");
		SESSION_WRITTEN_ONLY_BY_PLATFORM_AND_IAM.check(imported(SessionReader.class));
	}

	private static void assertViolation(ArchRule rule, JavaClasses classes, String expected) {
		assertThatThrownBy(() -> rule.check(classes)).isInstanceOf(AssertionError.class).hasMessageContaining(expected);
	}

	private static JavaClasses imported(Class<?>... types) {
		return new ClassFileImporter().importClasses(types);
	}

	private static ArchCondition<JavaField> notCarryClinicId() {
		return new ArchCondition<>("not carry clinicId or clinic_id") {
			@Override
			public void check(JavaField field, ConditionEvents events) {
				boolean byName = CLINIC_ID.matcher(field.getName()).matches();
				boolean byJson = field.getAnnotations()
					.stream()
					.filter((annotation) -> annotation.getRawType().getName().startsWith("com.fasterxml.jackson.annotation.Json"))
					.flatMap((annotation) -> jsonNames(annotation).stream())
					.anyMatch((name) -> CLINIC_ID.matcher(name).matches());
				if (byName || byJson) {
					events.add(SimpleConditionEvent.violated(field,
							field.getFullName() + " — клиника в DTO веб-слоя"));
				}
			}
		};
	}

	/** Имена JSON из {@code @JsonProperty} и {@code @JsonAlias}. */
	private static List<String> jsonNames(JavaAnnotation<?> annotation) {
		Optional<Object> value = annotation.get("value");
		if (value.isEmpty()) {
			return List.of();
		}
		if (value.get() instanceof Object[] names) {
			return Arrays.stream(names).map(String::valueOf).toList();
		}
		return List.of(String.valueOf(value.get()));
	}

	private static ArchCondition<JavaClass> filterNativeQueriesByClinic() {
		return new ArchCondition<>("filter native queries by clinic_id when the domain type is a TenantEntity") {
			@Override
			public void check(JavaClass repository, ConditionEvents events) {
				Optional<JavaClass> domain = domainType(repository);
				if (domain.isEmpty() || !domain.get().isAssignableTo(TenantEntity.class)) {
					return;
				}
				for (JavaMethod method : repository.getMethods()) {
					nativeSql(method).filter((sql) -> !sql.toLowerCase(Locale.ROOT).contains("clinic_id"))
						.ifPresent((sql) -> events.add(SimpleConditionEvent.violated(method,
								method.getFullName() + ": нативный запрос без clinic_id")));
				}
			}
		};
	}

	/** SQL из {@code @NativeQuery} или {@code @Query(nativeQuery = true)}. */
	private static Optional<String> nativeSql(JavaMethod method) {
		for (JavaAnnotation<JavaMethod> annotation : method.getAnnotations()) {
			String type = annotation.getRawType().getName();
			boolean nativeQuery = type.equals("org.springframework.data.jpa.repository.NativeQuery")
					|| (type.equals("org.springframework.data.jpa.repository.Query")
							&& Boolean.TRUE.equals(annotation.get("nativeQuery").orElse(false)));
			if (nativeQuery) {
				return annotation.get("value").map(String::valueOf);
			}
		}
		return Optional.empty();
	}

	/** Тип сущности репозитория — первый аргумент типа у {@code Repository<T, ID>} в иерархии интерфейсов. */
	static Optional<JavaClass> domainType(JavaClass repository) {
		for (JavaType type : repository.getInterfaces()) {
			if (type instanceof JavaParameterizedType parameterized
					&& parameterized.toErasure().isAssignableTo(Repository.class)
					&& !parameterized.getActualTypeArguments().isEmpty()
					&& parameterized.getActualTypeArguments().getFirst() instanceof JavaClass domain) {
				return Optional.of(domain);
			}
			Optional<JavaClass> inherited = domainType(type.toErasure());
			if (inherited.isPresent()) {
				return inherited;
			}
		}
		return Optional.empty();
	}

	@Test
	void domainTypeIsFoundThroughIntermediateInterfaces() {
		JavaClasses classes = imported(ScopedNoteRepository.class, NoteRepositoryBase.class, NoteRepository.class,
				TermRepository.class, GoodNote.class, DictionaryTerm.class);

		assertThat(domainType(classes.get(ScopedNoteRepository.class))).map(JavaClass::getName)
			.contains(GoodNote.class.getName());
		assertThat(domainType(classes.get(NoteRepository.class))).map(JavaClass::getName)
			.contains(GoodNote.class.getName());
		assertThat(domainType(classes.get(TermRepository.class))).map(JavaClass::getName)
			.contains(DictionaryTerm.class.getName());
	}

}
