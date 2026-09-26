package archfixtures.tenancy.web;

import java.util.UUID;

/** Фикстура ArchUnit: правильный DTO — без клиники. */
public record CleanNoteRequest(UUID branchId, String title) {
}
