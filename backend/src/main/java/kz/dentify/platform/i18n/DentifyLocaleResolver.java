package kz.dentify.platform.i18n;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kz.dentify.platform.api.AccountLocaleProvider;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.util.WebUtils;

/**
 * Язык запроса по цепочке, первое поддерживаемое значение:
 * <ol>
 * <li>язык аккаунта — {@link AccountLocaleProvider}, реализует iam;</li>
 * <li>cookie {@value #COOKIE} — выбор на экране входа, до аккаунта;</li>
 * <li>{@code Accept-Language} браузера — {@link AcceptHeaderLocaleResolver} Spring с поддерживаемыми языками, регион
 * не важен: kk-KZ даёт kk;</li>
 * <li>русский.</li>
 * </ol>
 * Поддерживаемые языки — из {@link I18nProperties}: при {@code dentify.i18n.kk=false} только русский, и любое звено
 * с казахским пропускается. Так включение казахского — это словари и флаг, без правок кода.
 * <p>
 * {@link #setLocale} пишет cookie: HttpOnly, SameSite=Lax, Secure за HTTPS, на год; неподдерживаемый язык или null
 * cookie удаляет.
 */
class DentifyLocaleResolver implements LocaleResolver {

	static final String COOKIE = "dentify-lang";

	static final Duration COOKIE_MAX_AGE = Duration.ofDays(365);

	private final List<Locale> supported;

	private final Supplier<Stream<AccountLocaleProvider>> accountLocales;

	private final AcceptHeaderLocaleResolver acceptHeader = new AcceptHeaderLocaleResolver();

	DentifyLocaleResolver(List<Locale> supported, Supplier<Stream<AccountLocaleProvider>> accountLocales) {
		this.supported = List.copyOf(supported);
		this.accountLocales = accountLocales;
		this.acceptHeader.setSupportedLocales(this.supported);
		this.acceptHeader.setDefaultLocale(defaultLocale());
	}

	@Override
	public Locale resolveLocale(HttpServletRequest request) {
		return accountLocale().or(() -> cookieLocale(request)).orElseGet(() -> this.acceptHeader.resolveLocale(request));
	}

	@Override
	public void setLocale(HttpServletRequest request, @Nullable HttpServletResponse response, @Nullable Locale locale) {
		if (response == null) {
			return;
		}
		Locale value = (locale != null) ? supported(locale) : null;
		ResponseCookie cookie = ResponseCookie.from(COOKIE, (value != null) ? value.toLanguageTag() : "")
			.path("/")
			.maxAge((value != null) ? COOKIE_MAX_AGE : Duration.ZERO)
			.httpOnly(true)
			.secure(request.isSecure())
			.sameSite("Lax")
			.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	private Locale defaultLocale() {
		return this.supported.getFirst();
	}

	private Optional<Locale> accountLocale() {
		return this.accountLocales.get()
			.map(AccountLocaleProvider::currentAccountLocale)
			.flatMap(Optional::stream)
			.map(this::supported)
			.flatMap(Stream::ofNullable)
			.findFirst();
	}

	private Optional<Locale> cookieLocale(HttpServletRequest request) {
		Cookie value = WebUtils.getCookie(request, COOKIE);
		if (value == null || !StringUtils.hasText(value.getValue())) {
			return Optional.empty();
		}
		return Optional.ofNullable(supported(Locale.forLanguageTag(value.getValue())));
	}

	/** Поддерживаемый язык с тем же языковым кодом, без региона: kk-KZ даёт kk. Иначе null. */
	private @Nullable Locale supported(Locale locale) {
		return this.supported.stream()
			.filter((candidate) -> candidate.getLanguage().equals(locale.getLanguage()))
			.findFirst()
			.orElse(null);
	}

}
