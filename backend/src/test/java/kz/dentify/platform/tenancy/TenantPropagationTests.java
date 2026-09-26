package kz.dentify.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import kz.dentify.platform.api.MissingTenantContextException;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import kz.dentify.platform.tenancy.TenantProbeListeners.AfterCommitClinicProbeEvent;
import kz.dentify.platform.tenancy.TenantProbeListeners.ClinicProbeEvent;
import kz.dentify.platform.tenancy.TenantProbeListeners.ModuleReadingProbeEvent;
import kz.dentify.platform.tenancy.TenantProbeListeners.ProbeEvent;
import kz.dentify.platform.tenancy.TenantProbeListeners.ReadingProbeEvent;
import kz.dentify.platform.tenancy.TenantProbeListeners.Seen;
import kz.dentify.platform.tenancy.TenantProbeListeners.SyncClinicProbeEvent;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.task.SimpleAsyncTaskSchedulerBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Контекст клиники вне HTTP-запроса (PLT-01): переносится в {@code @Async} и обработчики событий, в том числе
 * {@code @ApplicationModuleListener}, событие клиники ставит свой контекст, а без контекста обращение к данным клиники
 * падает, а не читает всё.
 */
class TenantPropagationTests extends TenantIntegrationTest {

	static final Duration WAIT = Duration.ofSeconds(10);

	@Autowired
	TenantProbeTasks tasks;

	@Autowired
	TenantProbeListeners listeners;

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	TestNoteRepository notes;

	@Autowired
	SimpleAsyncTaskSchedulerBuilder schedulers;

	@Test
	void asyncTaskRunsInClinicOfCaller() {
		Tenant tenant = new Tenant(newClinic(), UUID.randomUUID());

		Optional<Tenant> inTask = TenantContext.call(tenant, () -> this.tasks.tenant()).join();

		assertThat(inTask).contains(tenant);
		assertThat(this.tasks.tenant().join()).as("задача без контекста — без контекста, прежний не остался").isEmpty();
	}

	/** Приёмка PLT-01: фоновая задача без контекста клиники завершается ошибкой и не читает данные всех клиник. */
	@Test
	void asyncTaskWithoutClinicFailsOnClinicData() {
		createNote(enter(newClinic()), "Есть данные");

		assertThat(this.tasks.countNotes()).failsWithin(WAIT)
			.withThrowableThat()
			.havingRootCause()
			.isInstanceOf(MissingTenantContextException.class);
		UUID clinic = newClinic();
		createNote(enter(clinic), "Своя");
		assertThat(TenantContext.call(Tenant.of(clinic), () -> this.tasks.countNotes()).join()).isEqualTo(1L);
	}

	@Test
	void repositoryWithoutClinicFails() {
		assertThatThrownBy(() -> this.notes.count()).isInstanceOf(MissingTenantContextException.class);
		assertThatThrownBy(() -> this.notes.findByTitle("x")).isInstanceOf(MissingTenantContextException.class);
		assertThatThrownBy(() -> this.notes.save(new TestNote("x", null)))
			.isInstanceOf(MissingTenantContextException.class);
	}

	@Test
	void eventListenersRunInClinicOfPublisher() {
		Tenant tenant = Tenant.of(newClinic());
		String marker = UUID.randomUUID().toString();

		TenantContext.run(tenant, () -> this.transactions.executeWithoutResult(
				(status) -> this.events.publishEvent(new ProbeEvent(marker))));

		assertThat(seen("sync:" + marker).tenant()).contains(tenant);
		Seen async = seen("async:" + marker);
		assertThat(async.tenant()).contains(tenant);
		assertThat(async.thread()).isNotEqualTo(Thread.currentThread().getName());
		assertThat(seen("module:" + marker).tenant()).as("@ApplicationModuleListener").contains(tenant);
	}

	/**
	 * Событие клиники, опубликованное без контекста (так его переотправит журнал публикаций после рестарта):
	 * обработчик модуля получает клинику из события ещё до своей транзакции и видит данные этой клиники.
	 */
	@Test
	void clinicEventSetsClinicForModuleListener() {
		UUID clinic = newClinic();
		String session = enter(clinic);
		createNote(session, "Первая");
		createNote(session, "Вторая");
		String marker = UUID.randomUUID().toString();

		this.transactions.executeWithoutResult(
				(status) -> this.events.publishEvent(new ClinicProbeEvent(clinic, marker)));

		Seen seen = seen("clinic:" + marker);
		assertThat(seen.tenant()).map(Tenant::clinicId).contains(clinic);
		assertThat(seen.notes()).as("данные клиники события в транзакции обработчика").isEqualTo(2L);
	}

	/**
	 * Событие клиники из транзакции без клиники, синхронные обработчики после коммита. Ресурсы завершённой транзакции
	 * без клиники ещё привязаны к потоку, поэтому клиника события ставится только обработчику со своей транзакцией
	 * ({@code REQUIRES_NEW}); обработчик без неё не выполняется, а не читает через чужие сессию и соединение.
	 */
	@Test
	void clinicEventAfterCommitSetsClinicOnlyForListenerWithOwnTransaction() {
		UUID clinic = newClinic();
		createNote(enter(clinic), "После коммита");
		String marker = UUID.randomUUID().toString();

		this.transactions.executeWithoutResult(
				(status) -> this.events.publishEvent(new AfterCommitClinicProbeEvent(clinic, marker)));

		Seen own = seen("after-commit-own:" + marker);
		assertThat(own.tenant()).map(Tenant::clinicId).contains(clinic);
		assertThat(own.notes()).as("данные клиники события в своей транзакции").isEqualTo(1L);
		assertThat(own.thread()).as("синхронно").isEqualTo(Thread.currentThread().getName());
		assertThat(this.listeners.seen("after-commit-finished:" + marker))
			.as("обработчик без своей транзакции не выполнился")
			.isNotDone();
	}

	/**
	 * Приёмка PLT-01 для обработчиков: событие без клиники, обработчик без контекста обращается к данным клиники —
	 * ошибка до запроса, данные всех клиник не читаются. Синхронный {@code @EventListener} — ошибка у публикующего,
	 * {@code @ApplicationModuleListener} — в своём потоке, публикация остаётся незавершённой.
	 */
	@Test
	void listenerWithoutClinicFailsOnClinicData() {
		createNote(enter(newClinic()), "Есть данные");
		String marker = UUID.randomUUID().toString();

		assertThatThrownBy(() -> this.events.publishEvent(new ReadingProbeEvent(marker)))
			.isInstanceOf(MissingTenantContextException.class);
		assertThat(this.listeners.seen("sync-reads:" + marker)).isNotDone();

		this.transactions.executeWithoutResult(
				(status) -> this.events.publishEvent(new ModuleReadingProbeEvent(marker)));
		Seen module = seen("module-reads:" + marker);
		assertThat(module.tenant()).isEmpty();
		assertThat(module.failure()).isInstanceOf(MissingTenantContextException.class);
		assertThat(module.notes()).as("ничего не прочитано").isNull();
	}

	@Test
	void clinicEventOfOtherClinicIsRejected() {
		UUID clinic = newClinic();
		UUID other = newClinic();
		String marker = UUID.randomUUID().toString();

		assertThatThrownBy(() -> TenantContext.run(Tenant.of(clinic),
				() -> this.events.publishEvent(new SyncClinicProbeEvent(other, marker))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(other.toString());

		TenantContext.run(Tenant.of(clinic), () -> this.events.publishEvent(new SyncClinicProbeEvent(clinic, marker)));
		assertThat(seen("sync-clinic:" + marker).tenant()).map(Tenant::clinicId).contains(clinic);
	}

	/**
	 * Задача по расписанию (планировщик Spring Boot на виртуальных потоках, как у {@code @Scheduled}) контекста не
	 * получает: без выбора клиники — ошибка, с выбором — данные этой клиники.
	 */
	@Test
	void scheduledTaskMustChooseClinic() {
		UUID clinic = newClinic();
		createNote(enter(clinic), "По расписанию");
		SimpleAsyncTaskScheduler scheduler = this.schedulers.build();
		try {
			CompletableFuture<Long> withoutClinic = new CompletableFuture<>();
			scheduler.schedule(() -> countInto(withoutClinic), Instant.now());
			assertThat(withoutClinic).failsWithin(WAIT)
				.withThrowableThat()
				.havingRootCause()
				.isInstanceOf(MissingTenantContextException.class);

			CompletableFuture<Long> withClinic = new CompletableFuture<>();
			scheduler.schedule(() -> TenantContext.run(Tenant.of(clinic), () -> countInto(withClinic)), Instant.now());
			assertThat(withClinic).succeedsWithin(WAIT).isEqualTo(1L);
		}
		finally {
			scheduler.close();
		}
	}

	private void countInto(CompletableFuture<Long> result) {
		try {
			result.complete(this.notes.count());
		}
		catch (RuntimeException ex) {
			result.completeExceptionally(ex);
		}
	}

	private Seen seen(String key) {
		return this.listeners.seen(key).orTimeout(WAIT.toSeconds(), TimeUnit.SECONDS).join();
	}

}
