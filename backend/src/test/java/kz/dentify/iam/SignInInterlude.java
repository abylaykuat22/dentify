package kz.dentify.iam;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kz.dentify.platform.identity.AccountSignIn;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Только для тестов: действие между проверкой пароля и созданием сессии входа. Оборачивает бин {@link AccountSignIn}
 * и перед {@code signIn} аккаунта выполняет действие, заданное тестом, — например, коммитит деактивацию или смену
 * пароля: так воспроизводится вход, у которого пароль и статус прочитаны до коммита, а сессия создана после
 * (docs/architecture.md, Активные сессии). Без заданного действия — просто вызов настоящего входа. Бин — в тестовых
 * исходниках для всех тестов, без {@code @MockitoBean}: контекст один.
 */
@Component
class SignInInterlude implements BeanPostProcessor {

	private static final Map<UUID, Runnable> BEFORE_SIGN_IN = new ConcurrentHashMap<>();

	/** Выполнить действие перед следующим созданием сессии этого аккаунта, один раз. */
	static void beforeSignIn(UUID accountId, Runnable action) {
		BEFORE_SIGN_IN.put(accountId, action);
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof AccountSignIn signIn && !(bean instanceof Interluded)) {
			return new Interluded(signIn);
		}
		return bean;
	}

	private record Interluded(AccountSignIn delegate) implements AccountSignIn {

		@Override
		public void signIn(HttpServletRequest request, HttpServletResponse response, UUID accountId,
				boolean rememberMe) {
			Runnable action = BEFORE_SIGN_IN.remove(accountId);
			if (action != null) {
				action.run();
			}
			this.delegate.signIn(request, response, accountId, rememberMe);
		}

		@Override
		public void signOut(HttpServletRequest request, HttpServletResponse response) {
			this.delegate.signOut(request, response);
		}

	}

}
