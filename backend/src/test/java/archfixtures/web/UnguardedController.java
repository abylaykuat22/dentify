package archfixtures.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Фикстура ArchUnit: эндпоинты без {@code @PreAuthorize} — правило должно поймать оба. */
@RestController
public class UnguardedController {

	@GetMapping("/unguarded")
	public String forgotten() {
		return "";
	}

	@RequestMapping("/unguarded-any")
	public void forgottenToo() {
	}

	@GetMapping("/guarded-here")
	@PreAuthorize("isAuthenticated()")
	public void guarded() {
	}

}
