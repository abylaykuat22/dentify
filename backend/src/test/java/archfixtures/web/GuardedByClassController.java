package archfixtures.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

/** Фикстура ArchUnit: {@code @PreAuthorize} на классе действует на все его эндпоинты. */
@RestController
@PreAuthorize("isAuthenticated()")
public class GuardedByClassController {

	@DeleteMapping("/guarded-by-class")
	public void delete() {
	}

}
