package kz.dentify.platform.tenancy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import kz.dentify.platform.api.AuditSnapshots;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/**
 * Снимки пробной записи для аудита только в тестах: {@code @Audited} на {@link TestNoteService} пишет по ним события с
 * разницей, как будет в модулях.
 */
@Component
class TestNoteSnapshots implements AuditSnapshots<TestNoteSnapshots.Snapshot> {

	static final String ENTITY = "platform.test-note";

	private final TestNoteRepository notes;

	TestNoteSnapshots(TestNoteRepository notes) {
		this.notes = notes;
	}

	@Override
	public String entityType() {
		return ENTITY;
	}

	@Override
	public Optional<Snapshot> load(UUID id) {
		return this.notes.findById(id).map((note) -> new Snapshot(note.getTitle(), note.getArchivedAt()));
	}

	@Override
	public String label(Snapshot snapshot) {
		return snapshot.title();
	}

	record Snapshot(String title, @Nullable Instant archivedAt) {
	}

}
