package kz.dentify.platform.tenancy;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import kz.dentify.AbstractIntegrationTest;
import kz.dentify.platform.api.Tenant;
import kz.dentify.platform.identity.AccountSignIn;
import kz.dentify.platform.identity.TenantSession;
import kz.dentify.platform.security.TestAuthorities;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Пробный «вход в клинику» только для тестов: кладёт клинику в HTTP-сессию так, как это сделает вход и выбор клиники
 * (T-IAM-02), через {@link TenantSession}. Дальше запросы с cookie этой сессии идут в контексте клиники — через
 * настоящий фильтр платформы. С {@code accountId} сначала входит этим вымышленным аккаунтом через настоящий
 * {@link AccountSignIn}: тогда аудит пишет его автором правок. С {@code authorities} — ещё и права аккаунта
 * ({@code audit:read}, {@code patient:phones}), пока их не выдаёт iam.
 */
@RestController
class TenantSessionProbeController {

	static final String PATH = AbstractIntegrationTest.TENANT_SESSION_PROBE;

	private final AccountSignIn accountSignIn;

	TenantSessionProbeController(AccountSignIn accountSignIn) {
		this.accountSignIn = accountSignIn;
	}

	@PostMapping(PATH)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void select(HttpServletRequest request, HttpServletResponse response, @Validated @RequestBody SelectClinic body) {
		if (body.accountId() != null) {
			this.accountSignIn.signIn(request, response, body.accountId(), false);
			if (body.authorities() != null && !body.authorities().isEmpty()) {
				TestAuthorities.grant(request, response, body.authorities());
			}
		}
		TenantSession.select(request.getSession(true), new Tenant(body.clinicId(), body.branchId()));
	}

	@DeleteMapping(PATH)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void clear(HttpServletRequest request) {
		TenantSession.clear(request.getSession(true));
	}

	record SelectClinic(@NotNull UUID clinicId, @Nullable UUID branchId, @Nullable UUID accountId,
			@Nullable List<String> authorities) {
	}

}
