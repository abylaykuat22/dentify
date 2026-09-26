package archfixtures.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import archfixtures.tenancy.GoodNote;
import archfixtures.tenancy.NoteRepository;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Фикстура ArchUnit: толстый контроллер — ходит в репозиторий, отдаёт сущность JPA и Map, открывает транзакцию. Правила
 * веб-слоя должны поймать каждое.
 */
@RestController
@PreAuthorize("isAuthenticated()")
public class FatController {

	private final NoteRepository notes;

	public FatController(NoteRepository notes) {
		this.notes = notes;
	}

	@GetMapping("/fat/{id}")
	public GoodNote entity(@PathVariable UUID id) {
		return this.notes.findById(id).orElseThrow();
	}

	@GetMapping("/fat")
	public List<GoodNote> entities() {
		return this.notes.findAll();
	}

	@GetMapping("/fat/map")
	public Map<String, Object> map() {
		return Map.of();
	}

	@Transactional
	@PostMapping("/fat")
	public void write() {
	}

}
