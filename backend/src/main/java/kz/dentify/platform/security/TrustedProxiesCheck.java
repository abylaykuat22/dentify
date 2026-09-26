package kz.dentify.platform.security;

import java.net.UnknownHostException;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.apache.catalina.util.NetMaskSet;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.server.autoconfigure.ServerProperties.ForwardHeadersStrategy;
import org.springframework.util.StringUtils;

/**
 * Проверка при старте: заголовкам {@code X-Forwarded-For} и {@code X-Forwarded-Proto} Tomcat верит только от адреса
 * прокси. С {@code server.forward-headers-strategy: native} {@code RemoteIpValve} подставляет адрес из
 * {@code X-Forwarded-For} в {@code request.getRemoteAddr()}, если запрос пришёл с адреса из
 * {@code server.tomcat.remoteip.internal-proxies}. По умолчанию это все частные сети (10/8, 172.16/12, 192.168/16,
 * 169.254/16, 100.64/10, fc00::/7, fe80::/10) и loopback, а пустое значение — любой адрес. Тогда любой источник из
 * частной сети ротацией {@code X-Forwarded-For} обходил бы лимиты по IP (вход — 30 попыток в минуту, общий лимит API
 * без сессии) и подделывал IP в журнале входов и журнале действий.
 * <p>
 * Старт падает, если список доверенных прокси пуст или шире адреса прокси: совпадает с адресом из глубины частных
 * сетей ({@link #WIDE_SAMPLES}). В dev и test доверенный — только loopback, в prod — точный адрес Caddy
 * ({@code DENTIFY_TRUSTED_PROXIES}, T-PLT-25).
 */
class TrustedProxiesCheck implements InitializingBean {

	/**
	 * Адреса, которым доверять нельзя: из глубины частных сетей и link-local. Адрес Caddy на них не приходится, а широкое
	 * выражение (весь 10/8, вся частная сеть) их пропускает.
	 */
	static final List<String> WIDE_SAMPLES = List.of("10.254.253.252", "172.31.254.253", "192.168.254.253",
			"169.254.254.253", "100.127.254.253", "203.0.113.254", "fdfe:dcba:9876::5", "fe80::fedc:ba98");

	private final @Nullable ForwardHeadersStrategy strategy;

	private final @Nullable String internalProxies;

	private final @Nullable String trustedProxies;

	TrustedProxiesCheck(ServerProperties server, @Nullable String internalProxies, @Nullable String trustedProxies) {
		this.strategy = server.getForwardHeadersStrategy();
		this.internalProxies = internalProxies;
		this.trustedProxies = trustedProxies;
	}

	@Override
	public void afterPropertiesSet() {
		verify(this.strategy, this.internalProxies, this.trustedProxies);
	}

	static void verify(@Nullable ForwardHeadersStrategy strategy, @Nullable String internalProxies,
			@Nullable String trustedProxies) {
		if (strategy != ForwardHeadersStrategy.NATIVE) {
			return;
		}
		if (!StringUtils.hasText(internalProxies)) {
			throw new IllegalStateException("server.tomcat.remoteip.internal-proxies пуст: Tomcat поверил бы "
					+ "X-Forwarded-For с любого адреса. Задайте точный адрес прокси (docs/architecture.md, Лимиты частоты)");
		}
		requireNarrow("server.tomcat.remoteip.internal-proxies", internalProxies);
		if (StringUtils.hasText(trustedProxies)) {
			requireNarrow("server.tomcat.remoteip.trusted-proxies", trustedProxies);
		}
	}

	/**
	 * Значение читается, как его читает {@code RemoteIpValve} Tomcat 11: с косой чертой — список сетей CIDR через
	 * запятую ({@code 10.128.0.5/32}), без неё — регулярное выражение.
	 */
	private static void requireNarrow(String property, String expression) {
		Predicate<String> trusted;
		if (expression.indexOf('/') > 0) {
			NetMaskSet networks = NetMaskSet.parse(expression);
			trusted = (address) -> contains(networks, address);
		}
		else {
			Pattern pattern = Pattern.compile(expression);
			trusted = (address) -> pattern.matcher(address).matches();
		}
		for (String sample : WIDE_SAMPLES) {
			if (trusted.test(sample)) {
				throw new IllegalStateException(property + " шире адреса прокси: пропускает " + sample
						+ ". Задайте точный адрес прокси (docs/architecture.md, Лимиты частоты)");
			}
		}
	}

	private static boolean contains(NetMaskSet networks, String address) {
		try {
			return networks.contains(address);
		}
		catch (UnknownHostException ex) {
			throw new IllegalStateException("Не адрес: " + address, ex);
		}
	}

}
