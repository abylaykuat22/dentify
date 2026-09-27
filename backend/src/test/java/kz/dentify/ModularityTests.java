package kz.dentify;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import kz.dentify.platform.identity.AccountSessions;
import kz.dentify.platform.identity.AccountSignIn;
import kz.dentify.platform.identity.AuditJournal;
import kz.dentify.platform.identity.RateLimits;
import kz.dentify.platform.identity.TenantSession;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.NamedInterface;

/**
 * Границы модулей (PLT-24): {@code ApplicationModules.verify()} — нет циклов и обращений во внутренние
 * пакеты чужих модулей; плюс фиксация разрешённых направлений из docs/architecture.md, Модули и события (кратко — в
 * CLAUDE.md).
 */
class ModularityTests {

	static final ApplicationModules MODULES = ApplicationModules.of(DentifyApplication.class);

	/**
	 * Разрешённые зависимости модулей — те же направления, что в docs/architecture.md (Модули и события, Направления
	 * зависимостей) и кратко в CLAUDE.md: clinic ← iam, staff, schedule, inventory; staff, patient ← schedule; medical →
	 * patient, schedule, clinic; все → {@code platform :: api}, и только iam → {@code platform :: identity} (клиника в
	 * сессии, вход, сессии, лимиты входа, чтение журнала).
	 * Подписки на события — тоже зависимости (слушатель ссылается на тип события издателя): iam ←
	 * {@code staff :: events}, inventory ← {@code schedule :: events}. Поменяли направления — сначала решение и
	 * таблица в docs/architecture.md и строка в CLAUDE.md, потом package-info и этот тест.
	 */
	static final Map<String, Set<String>> ALLOWED = new TreeMap<>(Map.of(
			"platform", Set.of(),
			"clinic", Set.of("platform :: api"),
			"iam", Set.of("platform :: api", "platform :: identity", "clinic :: api", "staff :: events"),
			"staff", Set.of("platform :: api", "clinic :: api"),
			"patient", Set.of("platform :: api"),
			"schedule", Set.of("platform :: api", "clinic :: api", "staff :: api", "patient :: api"),
			"medical", Set.of("platform :: api", "patient :: api", "schedule :: api", "clinic :: api"),
			"inventory", Set.of("platform :: api", "clinic :: api", "schedule :: events")));

	/** Модули, которым открыт {@code platform :: identity}: клиника в сессии и вход — инварианты изоляции клиник. */
	static final Set<String> IDENTITY_USERS = Set.of("iam");

	@Test
	void verifiesModularStructure() {
		MODULES.verify();
	}

	@Test
	void detectsAllModules() {
		assertThat(MODULES.stream().map((module) -> module.getIdentifier().toString()))
			.containsExactlyInAnyOrderElementsOf(ALLOWED.keySet());
	}

	@Test
	void declaresDependencyDirectionsFromClaudeMd() throws ClassNotFoundException {
		Map<String, Set<String>> declared = new TreeMap<>();
		for (String module : ALLOWED.keySet()) {
			ApplicationModule annotation = Class.forName("kz.dentify." + module + ".package-info")
				.getAnnotation(ApplicationModule.class);
			assertThat(annotation).as("@ApplicationModule в kz.dentify.%s", module).isNotNull();
			declared.put(module, new TreeSet<>(Arrays.stream(annotation.allowedDependencies())
				.map((dependency) -> dependency.replaceAll("\\s*::\\s*", " :: "))
				.toList()));
		}
		ALLOWED.forEach((module, allowed) -> assertThat(declared.get(module)).as(module)
			.containsExactlyInAnyOrderElementsOf(allowed));
	}

	/**
	 * Клинику в сессию кладёт только iam после проверки членства: {@code TenantSession}, вход, сессии, лимиты входа и
	 * чтение журнала — в named interface {@code platform :: identity}, и он разрешён только iam. Звёздочка
	 * ({@code platform :: *}) открыла бы его всем, поэтому её нет ни у кого.
	 */
	@Test
	void identityPrimitivesAreOpenOnlyToIam() {
		NamedInterface identity = MODULES.getModuleByName("platform")
			.orElseThrow()
			.getNamedInterfaces()
			.getByName("identity")
			.orElseThrow();
		assertThat(List.of(TenantSession.class, AccountSignIn.class, AccountSessions.class, RateLimits.class,
				AuditJournal.class))
			.allSatisfy((type) -> assertThat(identity.contains(type)).as(type.getSimpleName()).isTrue());
		assertThat(MODULES.getModuleByName("platform")
			.orElseThrow()
			.getNamedInterfaces()
			.getByName("api")
			.orElseThrow()
			.contains(TenantSession.class)).as("TenantSession не в platform :: api").isFalse();
		ALLOWED.forEach((module, allowed) -> {
			assertThat(allowed).as(module).doesNotContain("platform :: *");
			assertThat(allowed.contains("platform :: identity")).as(module).isEqualTo(IDENTITY_USERS.contains(module));
		});
	}

	/** Каждый named interface из {@link #ALLOWED} ({@code api}, {@code events}) объявлен в модуле-издателе. */
	@Test
	void everyDependedOnNamedInterfaceExists() {
		ALLOWED.values()
			.stream()
			.flatMap(Set::stream)
			.filter((dependency) -> !dependency.endsWith(":: *"))
			.distinct()
			.forEach((dependency) -> {
				String[] parts = dependency.split(" :: ");
				assertThat(MODULES.getModuleByName(parts[0])
					.orElseThrow()
					.getNamedInterfaces()
					.getByName(parts[1])).as(dependency).isPresent();
			});
	}

}
