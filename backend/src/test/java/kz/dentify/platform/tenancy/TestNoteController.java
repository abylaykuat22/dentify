package kz.dentify.platform.tenancy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kz.dentify.platform.api.NotFoundException;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Пробный ресурс клиники только для тестов изоляции (T-PLT-03): полный набор операций, как у ресурса модуля, — список,
 * создание, чтение, замена, частичное изменение, удаление, действие над объектом, вложенный путь с двумя id и чтение в
 * обход Hibernate. Путь вне {@code /api/**}: в {@code api/openapi.json} не попадает. DTO без {@code clinicId}: клиника
 * — только из сессии.
 */
@RestController
@RequestMapping(TestNoteController.PATH)
class TestNoteController {

	static final String PATH = "/test/notes";

	private final TestNoteService notes;

	TestNoteController(TestNoteService notes) {
		this.notes = notes;
	}

	@GetMapping
	List<NoteView> list() {
		return this.notes.list().stream().map(NoteView::of).toList();
	}

	@PostMapping
	NoteView create(@Valid @RequestBody NoteCreate request) {
		return NoteView.of(this.notes.create(request.title()));
	}

	@GetMapping("/{id}")
	NoteView get(@PathVariable UUID id) {
		return NoteView.of(this.notes.get(id));
	}

	@PutMapping("/{id}")
	NoteView replace(@PathVariable UUID id, @Valid @RequestBody NoteUpdate request) {
		return NoteView.of(this.notes.rename(id, request.title(), request.version()));
	}

	@PatchMapping("/{id}")
	NoteView patch(@PathVariable UUID id, @Valid @RequestBody NoteUpdate request) {
		return NoteView.of(this.notes.rename(id, request.title(), request.version()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		this.notes.delete(id);
	}

	@PostMapping("/{id}/archive")
	NoteView archive(@PathVariable UUID id, @Valid @RequestBody NoteVersion request) {
		return NoteView.of(this.notes.archive(id, request.version()));
	}

	/** Вложенный путь с двумя переменными: харнесс изоляции подставляет в каждую свой объект клиники. */
	@GetMapping("/{id}/related/{relatedId}")
	NoteView related(@PathVariable UUID id, @PathVariable UUID relatedId) {
		return NoteView.of(this.notes.related(id, relatedId));
	}

	@GetMapping("/{id}/sql-title")
	String titleViaSql(@PathVariable UUID id) {
		return this.notes.titleViaSql(id).orElseThrow(NotFoundException::new);
	}

	record NoteCreate(@NotBlank @Size(max = 200) String title) {
	}

	record NoteUpdate(@NotBlank @Size(max = 200) String title, @NotNull Long version) {
	}

	record NoteVersion(@NotNull Long version) {
	}

	record NoteView(UUID id, String title, @Nullable UUID branchId, long version, Instant createdAt,
			@Nullable UUID createdBy, Instant updatedAt, @Nullable UUID updatedBy) {

		static NoteView of(TestNote note) {
			return new NoteView(require(note.getId()), note.getTitle(), note.getBranchId(), require(note.getVersion()),
					require(note.getCreatedAt()), note.getCreatedBy(), require(note.getUpdatedAt()), note.getUpdatedBy());
		}

		private static <T> T require(@Nullable T value) {
			if (value == null) {
				throw new IllegalStateException("сохранённая сущность без служебного поля");
			}
			return value;
		}

	}

}
