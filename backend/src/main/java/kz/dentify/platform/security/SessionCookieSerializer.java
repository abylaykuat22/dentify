package kz.dentify.platform.security;

import java.time.Duration;

import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.util.StringUtils;

/**
 * Cookie сессии Spring Session с постоянным сроком для входа с галочкой Запомнить.
 * <p>
 * Стандартный {@link DefaultCookieSerializer} при входе с Запомнить пишет cookie навсегда и полагается на срок сессии
 * на сервере. По PLT-04 срок Запомнить — 30 дней от входа, продлевается только новым входом: поэтому при входе с
 * Запомнить у cookie {@code Max-Age} — срок {@code dentify.security.remember-me}. Cookie пишется только при входе и
 * смене id сессии, так что срок от активности не растёт. Тот же срок от входа держит и сервер
 * ({@link SessionLifetimeFilter}): украденная cookie после него не действует. Без Запомнить cookie сессионная — до
 * закрытия браузера.
 * <p>
 * Остальное — имя, HttpOnly, Secure, SameSite, путь — из {@code server.servlet.session.cookie.*}, как у автоконфигурации
 * Spring Session в Boot.
 */
class SessionCookieSerializer extends DefaultCookieSerializer {

	private final int rememberMeSeconds;

	SessionCookieSerializer(Duration rememberMe) {
		this.rememberMeSeconds = Math.toIntExact(rememberMe.toSeconds());
	}

	@Override
	public void writeCookieValue(CookieValue cookieValue) {
		boolean rememberMe = cookieValue.getRequest()
			.getAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR) != null;
		if (rememberMe && StringUtils.hasText(cookieValue.getCookieValue()) && cookieValue.getCookieMaxAge() < 0) {
			cookieValue.setCookieMaxAge(this.rememberMeSeconds);
		}
		super.writeCookieValue(cookieValue);
	}

}
