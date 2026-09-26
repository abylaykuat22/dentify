package kz.dentify.platform.api;

import java.util.Locale;
import java.util.Optional;

/**
 * Точка расширения для языка аккаунта: первое звено цепочки выбора языка запроса, до cookie и
 * {@code Accept-Language}. Реализует модуль iam бином Spring, когда появятся аккаунты: язык вошедшего
 * пользователя из профиля. Пока реализаций нет, и язык берётся из cookie или заголовка.
 * <p>
 * Язык, который не поддержан флагами {@code dentify.i18n}, пропускается, так что при выключенном казахском ответ
 * всё равно будет на русском.
 */
@FunctionalInterface
public interface AccountLocaleProvider {

	/** Язык вошедшего пользователя или пусто, если входа нет или язык не выбран. */
	Optional<Locale> currentAccountLocale();

}
