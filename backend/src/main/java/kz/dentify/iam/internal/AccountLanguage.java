package kz.dentify.iam.internal;

import java.util.Locale;
import java.util.Optional;

import jakarta.servlet.http.HttpSession;
import kz.dentify.platform.api.AccountLocaleProvider;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Язык вошедшего аккаунта — первое звено цепочки языка запроса (PLT-11, {@code DentifyLocaleResolver}), до cookie и
 * {@code Accept-Language}. Язык берётся из атрибута сессии {@value #ATTRIBUTE}: его пишет вход ({@link SignIns}) из
 * {@code account.language}, поэтому запросы не читают аккаунт из БД. В атрибуте — строка (код языка), а не класс
 * приложения: сессия в Valkey переживает деплой. Смена языка в профиле (T-IAM-24) пишет его в текущую сессию через
 * {@link #remember}; другие сессии аккаунта получат новый язык при следующем входе.
 * <p>
 * Язык, который выключен флагами {@code dentify.i18n}, цепочка пропускает: при выключенном казахском ответ русский.
 */
@Component
class AccountLanguage implements AccountLocaleProvider {

	/** Атрибут сессии: код языка вошедшего аккаунта, {@code ru} или {@code kk}. */
	static final String ATTRIBUTE = "dentify.account.language";

	/** Запомнить язык аккаунта в сессии: вход и смена языка в профиле. */
	static void remember(HttpSession session, Locale language) {
		session.setAttribute(ATTRIBUTE, language.getLanguage());
	}

	@Override
	public Optional<Locale> currentAccountLocale() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return Optional.empty();
		}
		HttpSession session = attributes.getRequest().getSession(false);
		if (session == null || !(session.getAttribute(ATTRIBUTE) instanceof String language) || language.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(Locale.of(language));
	}

}
