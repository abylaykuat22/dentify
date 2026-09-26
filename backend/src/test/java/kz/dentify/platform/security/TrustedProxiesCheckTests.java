package kz.dentify.platform.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.catalina.valves.RemoteIpValve;
import org.junit.jupiter.api.Test;

import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties;
import org.springframework.boot.web.server.autoconfigure.ServerProperties.ForwardHeadersStrategy;

/**
 * Проверка доверенных прокси при старте без приложения: пустой список и широкий дефолт Tomcat и Spring Boot (все частные
 * сети) роняют старт, loopback и точный адрес прокси проходят — сетями CIDR (формат Tomcat 11) и регулярным выражением.
 */
class TrustedProxiesCheckTests {

	/** Как в application.yml. */
	static final String LOOPBACK = "127.0.0.0/8, ::1/128";

	static final String LOOPBACK_REGEX = "127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|0:0:0:0:0:0:0:1|::1";

	@Test
	void emptyOrWideProxiesFailStart() {
		for (String wide : new String[] { "", " ", new RemoteIpValve().getInternalProxies(),
				new TomcatServerProperties().getRemoteip().getInternalProxies(), "10.0.0.0/8", "10.128.0.5/32, fc00::/7",
				"10\\..*", ".*" }) {
			assertThatThrownBy(() -> TrustedProxiesCheck.verify(ForwardHeadersStrategy.NATIVE, wide, null))
				.as(wide)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("internal-proxies");
		}
		assertThatThrownBy(() -> TrustedProxiesCheck.verify(ForwardHeadersStrategy.NATIVE, null, null))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> TrustedProxiesCheck.verify(ForwardHeadersStrategy.NATIVE, LOOPBACK, "192.168.0.0/16"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("trusted-proxies");
	}

	@Test
	void loopbackOrExactProxyPass() {
		for (String narrow : new String[] { LOOPBACK, LOOPBACK_REGEX, "10.128.0.5/32", "10\\.128\\.0\\.5" }) {
			assertThatCode(() -> TrustedProxiesCheck.verify(ForwardHeadersStrategy.NATIVE, narrow, null)).as(narrow)
				.doesNotThrowAnyException();
		}
		assertThatCode(() -> TrustedProxiesCheck.verify(ForwardHeadersStrategy.NONE, "", null))
			.as("заголовки прокси не читаются — проверять нечего")
			.doesNotThrowAnyException();
	}

}
