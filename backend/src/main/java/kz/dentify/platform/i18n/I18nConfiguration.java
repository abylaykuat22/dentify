package kz.dentify.platform.i18n;

import kz.dentify.platform.api.AccountLocaleProvider;
import kz.dentify.platform.api.Languages;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Язык запроса: {@link DentifyLocaleResolver} под именем {@code localeResolver} — его ищет DispatcherServlet, и
 * автоконфигурация Spring MVC свой резолвер тогда не создаёт. Язык запроса доступен через
 * {@code LocaleContextHolder}: по нему {@code MessageSource} выбирает словарь для ProblemDetail и сообщений Bean
 * Validation.
 * <p>
 * Вне HTTP-запроса — фоновые задачи, письма, обработчики событий — {@code LocaleContextHolder} отдаёт язык по
 * умолчанию. Без настройки это язык ОС сервера, и на машине с казахской локалью {@code LocalizedText.text()} отдал
 * бы казахский при выключенном {@code dentify.i18n.kk}. Поэтому при старте язык по умолчанию — русский.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(I18nProperties.class)
class I18nConfiguration {

	@Bean(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
	LocaleResolver localeResolver(I18nProperties properties, ObjectProvider<AccountLocaleProvider> accountLocales) {
		return new DentifyLocaleResolver(properties.supportedLocales(), accountLocales::orderedStream);
	}

	/** Язык вне запроса — русский, а не язык ОС сервера. */
	@Bean
	SmartInitializingSingleton russianOutsideRequests() {
		return () -> LocaleContextHolder.setDefaultLocale(Languages.RUSSIAN);
	}

}
