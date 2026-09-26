package kz.dentify.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Откуда вошли в сессию (PLT-04): IP маской 95.56.•.•, браузер и ОС по User-Agent, город по IP из локальной GeoIP-базы
 * без внешних вызовов. База для теста — тестовая MaxMind формата GeoIP2 City с вымышленными сетями; нет базы — город
 * пустой.
 */
class SessionClientsTests {

	static final Path TEST_DATABASE = Path.of("src", "test", "resources", "geoip", "GeoIP2-City-Test.mmdb");

	static final GeoIpCities CITIES = GeoIpCities.open(TEST_DATABASE);

	static final UserAgents USER_AGENTS = new UserAgents();

	@AfterAll
	static void closeDatabase() throws Exception {
		CITIES.destroy();
	}

	@ParameterizedTest
	@CsvSource({ "95.56.12.34, 95.56.•.•", "127.0.0.1, 127.0.•.•", "::ffff:95.56.12.34, 95.56.•.•",
			"2a02:8109:1234:5678::1, 2a02:8109:•:•", "::1, 0:0:•:•" })
	void ipIsMaskedToFirstTwoParts(String ip, String masked) {
		assertThat(SessionClients.mask(ip)).isEqualTo(masked);
	}

	@Test
	void notAnIpIsNotMasked() {
		assertThat(SessionClients.mask("dentify.kz")).isNull();
		assertThat(SessionClients.mask("")).isNull();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36|Chrome 140|Windows 10",
			"Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1|Mobile Safari 18|iOS 18",
			"Mozilla/5.0 (X11; Linux x86_64; rv:142.0) Gecko/20100101 Firefox/142.0|Firefox 142|Linux",
			"Mozilla/5.0 (Linux; Android 14; SM-A546B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36|Chrome Mobile 139|Android 14" })
	void browserAndOsFromUserAgent(String userAgent, String browser, String os) {
		UserAgents.Client client = USER_AGENTS.parse(userAgent);

		assertThat(client.browser()).isEqualTo(browser);
		assertThat(client.os()).isEqualTo(os);
	}

	@Test
	void unknownUserAgentHasNoBrowser() {
		assertThat(USER_AGENTS.parse(null)).isEqualTo(new UserAgents.Client(null, null));
		assertThat(USER_AGENTS.parse("curl/8.9.1").os()).isNull();
	}

	/** Город — на языке запроса, если он есть в базе, иначе русский, иначе английский. */
	@Test
	void cityFromLocalDatabase() {
		assertThat(CITIES.available()).isTrue();
		assertThat(CITIES.city("81.2.69.142", Locale.forLanguageTag("ru"))).isEqualTo("Лондон");
		assertThat(CITIES.city("81.2.69.142", Locale.forLanguageTag("kk"))).as("казахского нет — русский")
			.isEqualTo("Лондон");
		assertThat(CITIES.city("81.2.69.142", Locale.ENGLISH)).isEqualTo("London");
		assertThat(CITIES.city("2.125.160.216", Locale.forLanguageTag("ru"))).as("русского нет — английский")
			.isEqualTo("Boxford");
	}

	@Test
	void unknownAddressHasNoCity() {
		assertThat(CITIES.city("10.0.0.1", Locale.ROOT)).isNull();
		assertThat(CITIES.city("not-an-ip", Locale.ROOT)).isNull();
	}

	/** Базы нет — город пустой, а не ошибка: база не коммитится и может быть не скачана. */
	@Test
	void withoutDatabaseCityIsEmpty() {
		GeoIpCities missing = GeoIpCities.open(Path.of("target", "no-such-geoip.mmdb"));
		GeoIpCities unset = GeoIpCities.open(null);

		assertThat(missing.available()).isFalse();
		assertThat(missing.city("81.2.69.142", Locale.ROOT)).isNull();
		assertThat(unset.city("81.2.69.142", Locale.ROOT)).isNull();
	}

	/** Путь к базе из настроек — путь файла, в том числе с ../ в начале, как в application-dev.yml. */
	@Test
	void databasePathBindsRelativeToWorkingDirectory() {
		Binder binder = new Binder(new MapConfigurationPropertySource(
				Map.of("dentify.security.geoip.database", "../deploy/geoip/dbip-city-lite.mmdb")));

		SecurityProperties properties = binder.bind("dentify.security", SecurityProperties.class).get();

		assertThat(properties.getGeoip().databasePath()).isEqualTo(Path.of("../deploy/geoip/dbip-city-lite.mmdb"));
		assertThat(new SecurityProperties().getGeoip().databasePath()).isNull();
	}

}
