package kz.dentify.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import kz.dentify.AbstractIntegrationTest;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.catalina.valves.ValveBase;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

/**
 * За Caddy TLS снимается на прокси: приложение должно верить X-Forwarded-Proto и X-Forwarded-For от адреса прокси
 * ({@code server.forward-headers-strategy: native}), иначе cookie сессии уйдёт без Secure, редиректы — на http, а все
 * клиенты будут одним IP. И только от него: клиент из частной сети с подделанным X-Forwarded-For остаётся своим адресом
 * — лимиты по IP и журналы его не теряют ({@code server.tomcat.remoteip.internal-proxies}, в тестах и dev — loopback).
 */
class ForwardedHeadersTests extends AbstractIntegrationTest {

	@Autowired
	WebServerApplicationContext context;

	@Test
	void httpsFromProxyIsSecure() {
		this.http.get()
			.uri(SchemeProbeController.PATH)
			.header("X-Forwarded-Proto", "https")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("https secure=true");
	}

	@Test
	void plainRequestStaysHttp() {
		this.http.get()
			.uri(SchemeProbeController.PATH)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("http secure=false");
	}

	/** Запрос с адреса прокси (в тестах — 127.0.0.1): адрес клиента — из X-Forwarded-For. */
	@Test
	void forwardedForFromProxyIsClientAddress() {
		this.http.get()
			.uri(SchemeProbeController.CLIENT)
			.header(FORWARDED_FOR, "203.0.113.7")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.isEqualTo("203.0.113.7");
	}

	/**
	 * X-Forwarded-For с адреса не прокси — из частных сетей, link-local, публичного — не принимается: адрес клиента
	 * остаётся настоящим. Проверяется копия настроек {@code RemoteIpValve} запущенного Tomcat: запрос с чужого адреса
	 * через сокет теста не отправить, тесты ходят с 127.0.0.1.
	 */
	@Test
	void forwardedForFromUntrustedAddressIsIgnored() throws Exception {
		RemoteIpValve valve = copyOfRunningValve();

		assertThat(clientAddress(valve, "127.0.0.1", "203.0.113.7")).as("прокси").isEqualTo("203.0.113.7");
		for (String untrusted : List.of("10.1.2.3", "172.16.5.4", "172.31.0.9", "192.168.1.10", "169.254.1.1",
				"100.64.0.1", "203.0.113.50", "fd00:0:0:0:0:0:0:5")) {
			assertThat(clientAddress(valve, untrusted, "203.0.113.7")).as(untrusted).isEqualTo(untrusted);
		}
	}

	private RemoteIpValve copyOfRunningValve() {
		TomcatWebServer server = (TomcatWebServer) this.context.getWebServer();
		RemoteIpValve running = null;
		for (Valve valve : server.getTomcat().getEngine().getPipeline().getValves()) {
			if (valve instanceof RemoteIpValve remoteIp) {
				running = remoteIp;
			}
		}
		assertThat(running).as("RemoteIpValve в Tomcat приложения").isNotNull();
		RemoteIpValve copy = new RemoteIpValve();
		copy.setInternalProxies(running.getInternalProxies());
		if (running.getTrustedProxies() != null) {
			copy.setTrustedProxies(running.getTrustedProxies());
		}
		copy.setRemoteIpHeader(running.getRemoteIpHeader());
		copy.setProtocolHeader(running.getProtocolHeader());
		return copy;
	}

	/** Адрес клиента, который увидит приложение после valve. */
	private static String clientAddress(RemoteIpValve valve, String remoteAddr, String forwardedFor) throws Exception {
		AtomicReference<String> seen = new AtomicReference<>();
		valve.setNext(new ValveBase() {
			@Override
			public void invoke(Request request, Response response) {
				seen.set(request.getRemoteAddr());
			}
		});
		org.apache.coyote.Request coyote = new org.apache.coyote.Request();
		coyote.getMimeHeaders().addValue(FORWARDED_FOR).setString(forwardedFor);
		Request request = new Request(new Connector(), coyote);
		request.setRemoteAddr(remoteAddr);
		request.setRemoteHost(remoteAddr);
		valve.invoke(request, new Response(new org.apache.coyote.Response()));
		return seen.get();
	}

}
