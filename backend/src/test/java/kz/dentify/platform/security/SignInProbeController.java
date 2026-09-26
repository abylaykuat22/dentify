package kz.dentify.platform.security;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kz.dentify.AbstractIntegrationTest;
import kz.dentify.platform.api.TenantContext;
import kz.dentify.platform.api.UnauthorizedException;
import kz.dentify.platform.identity.AccountSignIn;
import kz.dentify.platform.identity.RateLimits;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Пробный вход только для тестов платформы: вход вымышленным аккаунтом без пароля и без строки в {@code account} через
 * настоящий {@link AccountSignIn} — тестам сессий, CSRF и изоляции не нужны аккаунты iam. Вход по паролю —
 * {@code POST /api/v1/auth/login} (iam, T-IAM-02, {@code iam.SignInTests}). Лежит под {@code /api/v1/auth/**}, как
 * настоящий вход: запрос идёт через цепочку приложения с CSRF и лимитом API, как у SPA. Там же — попытка входа по паролю
 * для лимитов входа. В спецификацию OpenAPI не попадает ({@code @Hidden}).
 */
@Hidden
@RestController
class SignInProbeController {

	static final String SIGN_IN = AbstractIntegrationTest.SIGN_IN_PROBE;

	static final String SIGN_OUT = "/api/v1/auth/test-sign-out";

	static final String LOGIN_ATTEMPT = "/api/v1/auth/test-login-attempt";

	/** Заголовок ответа пробного входа: клиника контекста потока сразу после {@code signIn} или {@value #NO_CLINIC}. */
	static final String CLINIC_AFTER_SIGN_IN = "X-Test-Clinic-After-Sign-In";

	static final String NO_CLINIC = "none";

	private final AccountSignIn accountSignIn;

	private final RateLimits rateLimits;

	SignInProbeController(AccountSignIn accountSignIn, RateLimits rateLimits) {
		this.accountSignIn = accountSignIn;
		this.rateLimits = rateLimits;
	}

	@PostMapping(SIGN_IN)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("permitAll()")
	void signIn(HttpServletRequest request, HttpServletResponse response, @Validated @RequestBody SignIn body) {
		this.accountSignIn.signIn(request, response, body.accountId(), body.rememberMe());
		// клиника потока сразу после входа: клиника прежнего входа в этом браузере снята и с сессии, и с запроса
		response.setHeader(CLINIC_AFTER_SIGN_IN,
				TenantContext.current().map((tenant) -> tenant.clinicId().toString()).orElse(NO_CLINIC));
	}

	@PostMapping(SIGN_OUT)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("permitAll()")
	void signOut(HttpServletRequest request, HttpServletResponse response) {
		this.accountSignIn.signOut(request, response);
	}

	/**
	 * Попытка входа по паролю без аккаунтов, как у входа T-IAM-02: лимиты до проверки пароля, неверный пароль — 401 и
	 * неверная попытка на логин, верный — счётчик логина обнуляется.
	 */
	@PostMapping(LOGIN_ATTEMPT)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("permitAll()")
	void loginAttempt(HttpServletRequest request, @Validated @RequestBody LoginAttempt body) {
		this.rateLimits.checkLogin(body.login(), request.getRemoteAddr());
		if (!body.passwordMatches()) {
			this.rateLimits.recordLoginFailure(body.login());
			throw new UnauthorizedException();
		}
		this.rateLimits.resetLogin(body.login());
	}

	record SignIn(@NotNull UUID accountId, boolean rememberMe) {
	}

	record LoginAttempt(@NotBlank String login, boolean passwordMatches) {
	}

}
