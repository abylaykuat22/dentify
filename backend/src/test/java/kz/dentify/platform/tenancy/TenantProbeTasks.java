package kz.dentify.platform.tenancy;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.api.TenantContext;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Пробные {@code @Async}-задачи только для тестов переноса контекста клиники ({@code TenantPropagationTests}). */
@Component
class TenantProbeTasks {

	private final TestNoteRepository notes;

	TenantProbeTasks(TestNoteRepository notes) {
		this.notes = notes;
	}

	/** Контекст клиники в потоке исполнителя. */
	@Async
	public CompletableFuture<Optional<Tenant>> tenant() {
		return CompletableFuture.completedFuture(TenantContext.current());
	}

	/** Обращение к данным клиники в потоке исполнителя. */
	@Async
	public CompletableFuture<Long> countNotes() {
		return CompletableFuture.completedFuture(this.notes.count());
	}

}
