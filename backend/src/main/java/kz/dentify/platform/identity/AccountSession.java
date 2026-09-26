package kz.dentify.platform.identity;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Активная сессия аккаунта для экрана сессий.
 *
 * @param id открытый id сессии: хэш настоящего id, по нему сессию завершают
 * @param current сессия текущего запроса
 * @param browser браузер с основной версией, например Chrome 140; null — не распознан
 * @param os операционная система, например Windows 10 или iOS 18; null — не распознана
 * @param ip IP входа маской 95.56.•.•: в интерфейс полный адрес не уходит; null — не записан
 * @param city город по IP из локальной GeoIP-базы на языке запроса; null — базы нет или адрес в ней не найден
 * @param signedInAt время входа
 * @param lastActiveAt время последнего запроса
 * @param rememberMe вход с галочкой Запомнить
 */
public record AccountSession(String id, boolean current, @Nullable String browser, @Nullable String os,
		@Nullable String ip, @Nullable String city, Instant signedInAt, Instant lastActiveAt, boolean rememberMe) {
}
