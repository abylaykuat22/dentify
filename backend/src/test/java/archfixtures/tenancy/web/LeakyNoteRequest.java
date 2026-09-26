package archfixtures.tenancy.web;

import java.util.UUID;

/** Фикстура ArchUnit: DTO принимает клинику — нарушение. */
public record LeakyNoteRequest(UUID clinicId, String title) {
}
