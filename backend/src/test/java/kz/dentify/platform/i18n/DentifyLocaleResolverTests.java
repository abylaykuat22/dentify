package kz.dentify.platform.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.servlet.http.Cookie;
import kz.dentify.platform.api.AccountLocaleProvider;
import kz.dentify.platform.api.Languages;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Цепочка выбора языка: аккаунт, cookie, Accept-Language, русский — и только поддерживаемые языки из
 * {@code dentify.i18n}.
 */
class DentifyLocaleResolverTests {

	static final I18nProperties RUSSIAN_ONLY = new I18nProperties(false);

	static final I18nProperties WITH_KAZAKH = new I18nProperties(true);

	@Test
	void kazakhDisabledAlwaysGivesRussian() {
		DentifyLocaleResolver resolver = resolver(RUSSIAN_ONLY, Languages.KAZAKH);
		MockHttpServletRequest request = request("kk", "kk-KZ,kk");

		assertThat(resolver.resolveLocale(request)).isEqualTo(Languages.RUSSIAN);
	}

	@Test
	void russianWithoutAnyHint() {
		assertThat(resolver(WITH_KAZAKH, null).resolveLocale(new MockHttpServletRequest()))
			.isEqualTo(Languages.RUSSIAN);
	}

	@Test
	void accountLanguageComesFirst() {
		assertThat(resolver(WITH_KAZAKH, Languages.KAZAKH).resolveLocale(request("ru", "ru")))
			.isEqualTo(Languages.KAZAKH);
		assertThat(resolver(WITH_KAZAKH, Languages.RUSSIAN).resolveLocale(request("kk", "kk")))
			.isEqualTo(Languages.RUSSIAN);
	}

	@Test
	void cookieBeforeAcceptLanguage() {
		assertThat(resolver(WITH_KAZAKH, null).resolveLocale(request("kk", "ru"))).isEqualTo(Languages.KAZAKH);
		assertThat(resolver(WITH_KAZAKH, null).resolveLocale(request("ru", "kk"))).isEqualTo(Languages.RUSSIAN);
	}

	@Test
	void unsupportedOrBrokenCookieIsSkipped() {
		assertThat(resolver(WITH_KAZAKH, null).resolveLocale(request("en", "kk"))).isEqualTo(Languages.KAZAKH);
		assertThat(resolver(WITH_KAZAKH, null).resolveLocale(request("%%%", "kk"))).isEqualTo(Languages.KAZAKH);
	}

	@Test
	void acceptLanguageMatchesByLanguageWithoutRegion() {
		DentifyLocaleResolver resolver = resolver(WITH_KAZAKH, null);

		assertThat(resolver.resolveLocale(request(null, "kk-KZ"))).isEqualTo(Languages.KAZAKH);
		assertThat(resolver.resolveLocale(request(null, "ru-RU,ru;q=0.9"))).isEqualTo(Languages.RUSSIAN);
		assertThat(resolver.resolveLocale(request(null, "en-US,de;q=0.8"))).isEqualTo(Languages.RUSSIAN);
	}

	@Test
	void setLocaleWritesCookieOnlyForSupportedLanguage() {
		DentifyLocaleResolver resolver = resolver(WITH_KAZAKH, null);
		MockHttpServletResponse response = new MockHttpServletResponse();

		resolver.setLocale(new MockHttpServletRequest(), response, Locale.forLanguageTag("kk-KZ"));

		assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).startsWith("dentify-lang=kk;")
			.contains("HttpOnly", "SameSite=Lax", "Path=/");

		MockHttpServletResponse cleared = new MockHttpServletResponse();
		resolver(RUSSIAN_ONLY, null).setLocale(new MockHttpServletRequest(), cleared, Languages.KAZAKH);
		assertThat(cleared.getHeader(HttpHeaders.SET_COOKIE)).startsWith("dentify-lang=;").contains("Max-Age=0");
	}

	private static DentifyLocaleResolver resolver(I18nProperties properties, Locale accountLocale) {
		AccountLocaleProvider account = () -> Optional.ofNullable(accountLocale);
		return new DentifyLocaleResolver(properties.supportedLocales(), () -> Stream.of(account));
	}

	private static MockHttpServletRequest request(String cookie, String acceptLanguage) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		if (cookie != null) {
			request.setCookies(new Cookie(DentifyLocaleResolver.COOKIE, cookie));
		}
		if (acceptLanguage != null) {
			request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
		}
		return request;
	}

}
