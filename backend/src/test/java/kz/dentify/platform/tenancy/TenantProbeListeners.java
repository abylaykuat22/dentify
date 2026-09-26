package kz.dentify.platform.tenancy;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;
import kz.dentify.platform.api.TenantEvent;
import org.jspecify.annotations.Nullable;

import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Пробные обработчики событий только для тестов переноса контекста клиники ({@code TenantPropagationTests}):
 * записывают, в каком контексте их вызвали, по метке события.
 */
@Component
class TenantProbeListeners {

	private final Map<String, CompletableFuture<Seen>> seen = new ConcurrentHashMap<>();

	private final TestNoteRepository notes;

	TenantProbeListeners(TestNoteRepository notes) {
		this.notes = notes;
	}

	@EventListener
	public void sync(ProbeEvent event) {
		record("sync:" + event.marker(), TenantContext.current(), null);
	}

	@Async
	@EventListener
	public void async(ProbeEvent event) {
		record("async:" + event.marker(), TenantContext.current(), null);
	}

	/** Асинхронно, после коммита, в своей транзакции — как обработчик модуля. */
	@ApplicationModuleListener
	public void module(ProbeEvent event) {
		record("module:" + event.marker(), TenantContext.current(), null);
	}

	/** Событие клиники: контекст из события, данные клиники доступны в транзакции обработчика. */
	@ApplicationModuleListener
	public void clinicEvent(ClinicProbeEvent event) {
		record("clinic:" + event.marker(), TenantContext.current(), this.notes.count());
	}

	@EventListener
	public void syncClinicEvent(SyncClinicProbeEvent event) {
		record("sync-clinic:" + event.marker(), TenantContext.current(), null);
	}

	/**
	 * Синхронно после коммита транзакции публикации, в своей транзакции: прежняя транзакция отложена, данные — через
	 * новые сессию и соединение с клиникой события.
	 */
	@TransactionalEventListener
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void afterCommitInOwnTransaction(AfterCommitClinicProbeEvent event) {
		record("after-commit-own:" + event.marker(), TenantContext.current(), this.notes.count());
	}

	/**
	 * Синхронно после коммита без своей транзакции: данные пошли бы через сессию и соединение завершённой транзакции
	 * публикации. Если она была без клиники, обработчик не выполняется.
	 */
	@TransactionalEventListener
	public void afterCommitInFinishedTransaction(AfterCommitClinicProbeEvent event) {
		record("after-commit-finished:" + event.marker(), TenantContext.current(), this.notes.count());
	}

	/** Событие без клиники, обработчик читает данные клиники: без контекста — ошибка. */
	@EventListener
	public void syncReads(ReadingProbeEvent event) {
		record("sync-reads:" + event.marker(), TenantContext.current(), this.notes.count());
	}

	/** То же для обработчика модуля; исключение записывается и пробрасывается, как в рабочем обработчике. */
	@ApplicationModuleListener
	public void moduleReads(ModuleReadingProbeEvent event) {
		String key = "module-reads:" + event.marker();
		try {
			record(key, TenantContext.current(), this.notes.count());
		}
		catch (RuntimeException ex) {
			seen(key).complete(new Seen(TenantContext.current(), Thread.currentThread().getName(), null, ex));
			throw ex;
		}
	}

	CompletableFuture<Seen> seen(String key) {
		return this.seen.computeIfAbsent(key, (ignored) -> new CompletableFuture<>());
	}

	private void record(String key, Optional<Tenant> tenant, @Nullable Long notes) {
		seen(key).complete(new Seen(tenant, Thread.currentThread().getName(), notes, null));
	}

	/** Что увидел обработчик: контекст, поток, сколько записей клиники прочитал или чем упал. */
	record Seen(Optional<Tenant> tenant, String thread, @Nullable Long notes, @Nullable Throwable failure) {
	}

	/** Событие без клиники: контекст — из публикующего потока. */
	record ProbeEvent(String marker) {
	}

	/** Событие клиники для асинхронного обработчика модуля. */
	record ClinicProbeEvent(UUID clinicId, String marker) implements TenantEvent {
	}

	/** Событие клиники для синхронного обработчика. */
	record SyncClinicProbeEvent(UUID clinicId, String marker) implements TenantEvent {
	}

	/** Событие клиники для синхронных обработчиков после коммита. */
	record AfterCommitClinicProbeEvent(UUID clinicId, String marker) implements TenantEvent {
	}

	/** Событие без клиники для синхронного обработчика, который читает данные клиники. */
	record ReadingProbeEvent(String marker) {
	}

	/** Событие без клиники для обработчика модуля, который читает данные клиники. */
	record ModuleReadingProbeEvent(String marker) {
	}

}
