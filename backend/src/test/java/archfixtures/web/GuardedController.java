package archfixtures.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Фикстура ArchUnit: у каждого эндпоинта свой {@code @PreAuthorize}; служебный метод без маппинга правилу не нужен. */
@RestController
public class GuardedController {

	@GetMapping("/guarded")
	@PreAuthorize("isAuthenticated()")
	public String read() {
		return helper();
	}

	@PostMapping("/guarded")
	@PreAuthorize("permitAll()")
	public void write() {
	}

	String helper() {
		return "";
	}

}
