package archfixtures.web;

import java.util.List;

import kz.dentify.platform.api.PageResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Фикстура ArchUnit: тонкий контроллер — ответы record, список record, страница, ResponseEntity и void. */
@RestController
@PreAuthorize("isAuthenticated()")
public class ThinController {

	@GetMapping("/thin/one")
	public NoteView one() {
		return new NoteView("Осмотр");
	}

	@GetMapping("/thin/list")
	public List<NoteView> list() {
		return List.of();
	}

	@GetMapping("/thin/page")
	public PageResponse<NoteView> page() {
		return new PageResponse<>(List.of(), 0, 25, 0);
	}

	@GetMapping("/thin/entity")
	public ResponseEntity<NoteView> response() {
		return ResponseEntity.ok(new NoteView("Осмотр"));
	}

	@DeleteMapping("/thin")
	public void delete() {
	}

	/** DTO веб-слоя — record. */
	public record NoteView(String title) {
	}

}
