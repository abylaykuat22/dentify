package kz.dentify.platform.security;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import ua_parser.OS;
import ua_parser.Parser;
import ua_parser.UserAgent;

import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;

/**
 * Браузер и ОС по User-Agent — uap-java, Java-порт общих правил ua-parser (uap-core). Правила загружаются при первом
 * разборе, а не при старте: список сессий открывают редко. Названия — как в uap-core, это данные, а не тексты
 * интерфейса: Chrome, Firefox, Yandex Browser, Windows, Android, iOS.
 */
class UserAgents {

	private static final String OTHER = "Other";

	private final Supplier<Parser> parser = SingletonSupplier.of(Parser::new);

	/**
	 * Браузер и ОС с основной версией.
	 *
	 * @param userAgent заголовок User-Agent; null — не было
	 */
	Client parse(@Nullable String userAgent) {
		if (!StringUtils.hasText(userAgent)) {
			return new Client(null, null);
		}
		ua_parser.Client parsed = this.parser.get().parse(userAgent);
		return new Client(browser(parsed.userAgent), os(parsed.os));
	}

	private static @Nullable String browser(@Nullable UserAgent agent) {
		return (agent != null) ? name(agent.family, agent.major) : null;
	}

	private static @Nullable String os(@Nullable OS os) {
		return (os != null) ? name(os.family, os.major) : null;
	}

	private static @Nullable String name(@Nullable String family, @Nullable String major) {
		if (!StringUtils.hasText(family) || OTHER.equals(family)) {
			return null;
		}
		return StringUtils.hasText(major) ? family + " " + major : family;
	}

	/**
	 * Браузер и ОС сессии.
	 *
	 * @param browser браузер с основной версией, null — не распознан
	 * @param os ОС с основной версией, null — не распознана
	 */
	record Client(@Nullable String browser, @Nullable String os) {
	}

}
