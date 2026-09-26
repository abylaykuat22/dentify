package kz.dentify.platform.security;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import kz.dentify.platform.api.NotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Запрос в полёте только для тестов: держит запрос вошедшей сессии, пока тест его не отпустит. Так тест завершает
 * сессию, пока её запрос ещё идёт, и проверяет, что конец этого запроса сессию не воскресит
 * ({@code SessionRevocationTests}).
 * <p>
 * Перед ожиданием запрос заново кладёт контекст безопасности в сессию, как будет делать смена клиники с новыми правами:
 * конец запроса запишет его в Valkey и вернёт сессию в индекс аккаунта. Путь — под цепочкой приложения, вне
 * {@code /api}, в спецификацию OpenAPI не попадает.
 */
@RestController
class SessionHoldProbeController {

	static final String PATH = SecuredProbeController.PATH + "/hold";

	/** Сколько запрос ждёт, пока тест его отпустит: дольше — тест завис, запрос завершается ошибкой. */
	static final Duration MAX_HOLD = Duration.ofSeconds(30);

	private final Map<String, Hold> holds = new ConcurrentHashMap<>();

	/** Новая задержка: путь запроса, который будет ждать, — {@link Hold#path()}. */
	Hold prepare() {
		Hold hold = new Hold(UUID.randomUUID().toString());
		this.holds.put(hold.key(), hold);
		return hold;
	}

	@GetMapping(PATH + "/{key}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("isAuthenticated()")
	void hold(@PathVariable String key, HttpServletRequest request) throws InterruptedException {
		Hold hold = this.holds.remove(key);
		if (hold == null) {
			throw new NotFoundException();
		}
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
					SecurityContextHolder.getContext());
		}
		hold.entered.countDown();
		if (!hold.released.await(MAX_HOLD.toMillis(), TimeUnit.MILLISECONDS)) {
			throw new IllegalStateException("Тест не отпустил запрос");
		}
	}

	/** Задержка одного запроса: запрос вошёл в контроллер — {@link #awaitEntered()}, отпустить — {@link #release()}. */
	static final class Hold {

		private final String key;

		private final CountDownLatch entered = new CountDownLatch(1);

		private final CountDownLatch released = new CountDownLatch(1);

		Hold(String key) {
			this.key = key;
		}

		String key() {
			return this.key;
		}

		String path() {
			return PATH + "/" + this.key;
		}

		/** Запрос прошёл фильтры, загрузил сессию и ждёт. */
		boolean awaitEntered() throws InterruptedException {
			return this.entered.await(MAX_HOLD.toMillis(), TimeUnit.MILLISECONDS);
		}

		void release() {
			this.released.countDown();
		}

	}

}
