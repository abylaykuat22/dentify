package kz.dentify.platform.security;

import java.time.Instant;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import kz.dentify.platform.api.IpAddresses;
import org.jspecify.annotations.Nullable;

import org.springframework.session.Session;

/**
 * Откуда вошли в сессию: при входе в сессию пишутся IP, User-Agent, время входа и признак Запомнить (атрибуты
 * {@code dentify.client.*}, только строки, {@link Instant} и {@link Boolean}); для списка сессий из них получаются
 * браузер и ОС ({@link UserAgents}), IP маской и город ({@link GeoIpCities}).
 * <p>
 * IP — {@code request.getRemoteAddr()}: за Caddy это адрес клиента из {@code X-Forwarded-For}
 * ({@code server.forward-headers-strategy: native}). Полный IP хранится только в сессии в Valkey и в интерфейс не
 * уходит: только маска с первыми двумя октетами, 95.56.•.•.
 */
class SessionClients {

	static final String IP = "dentify.client.ip";

	static final String USER_AGENT = "dentify.client.userAgent";

	static final String SIGNED_IN_AT = "dentify.client.signedInAt";

	static final String REMEMBER_ME = "dentify.client.rememberMe";

	/** User-Agent длиннее этого обрезается: в сессии он нужен только для браузера и ОС. */
	static final int USER_AGENT_LIMIT = 512;

	private final UserAgents userAgents;

	private final GeoIpCities cities;

	SessionClients(UserAgents userAgents, GeoIpCities cities) {
		this.userAgents = userAgents;
		this.cities = cities;
	}

	static void record(HttpSession session, HttpServletRequest request, Instant signedInAt, boolean rememberMe) {
		session.setAttribute(IP, request.getRemoteAddr());
		String userAgent = request.getHeader("User-Agent");
		if (userAgent != null && !userAgent.isBlank()) {
			session.setAttribute(USER_AGENT,
					(userAgent.length() > USER_AGENT_LIMIT) ? userAgent.substring(0, USER_AGENT_LIMIT) : userAgent);
		}
		else {
			session.removeAttribute(USER_AGENT);
		}
		session.setAttribute(SIGNED_IN_AT, signedInAt);
		session.setAttribute(REMEMBER_ME, rememberMe);
	}

	UserAgents.Client client(Session session) {
		return this.userAgents.parse(session.getAttribute(USER_AGENT));
	}

	@Nullable String maskedIp(Session session) {
		String ip = session.getAttribute(IP);
		return (ip != null) ? mask(ip) : null;
	}

	@Nullable String city(Session session, Locale locale) {
		String ip = session.getAttribute(IP);
		return (ip != null) ? this.cities.city(ip, locale) : null;
	}

	static Instant signedInAt(Session session) {
		Instant signedInAt = session.getAttribute(SIGNED_IN_AT);
		return (signedInAt != null) ? signedInAt : session.getCreationTime();
	}

	static boolean rememberMe(Session session) {
		return Boolean.TRUE.equals(session.getAttribute(REMEMBER_ME));
	}

	/** IP маской: 95.56.•.• ({@link IpAddresses#mask}). Не адрес — null. */
	static @Nullable String mask(String ip) {
		return IpAddresses.mask(ip);
	}

}
