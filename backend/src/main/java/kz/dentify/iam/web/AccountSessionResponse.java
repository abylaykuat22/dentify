package kz.dentify.iam.web;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import kz.dentify.platform.identity.AccountSession;
import org.jspecify.annotations.Nullable;

/**
 * Активная сессия аккаунта в списке сессий.
 *
 * @param id открытый id сессии: по нему сессию завершают
 * @param current это сессия, из которой пришёл запрос
 * @param browser браузер с основной версией
 * @param os операционная система с основной версией
 * @param ip IP входа маской, полный адрес не отдаётся
 * @param city город по IP из локальной GeoIP-базы
 * @param signedInAt время входа
 * @param lastActiveAt время последнего запроса
 * @param rememberMe вход с галочкой Запомнить: сессия и cookie действуют 30 дней от входа
 */
@Schema(description = "Активная сессия аккаунта")
record AccountSessionResponse(
		@Schema(description = "Открытый id сессии: по нему сессию завершают", example = "q3Vd0bQ1m9m9uYcX4nZ1wA") String id,
		@Schema(description = "Сессия, из которой пришёл запрос") boolean current,
		@Schema(description = "Браузер с основной версией; null — не распознан", example = "Chrome 140") @Nullable String browser,
		@Schema(description = "Операционная система; null — не распознана", example = "Windows 10") @Nullable String os,
		@Schema(description = "IP входа маской", example = "95.56.•.•") @Nullable String ip,
		@Schema(description = "Город по IP из локальной GeoIP-базы; null — не определён", example = "Almaty") @Nullable String city,
		@Schema(description = "Время входа") Instant signedInAt,
		@Schema(description = "Время последнего запроса") Instant lastActiveAt,
		@Schema(description = "Вход с «Запомнить»: сессия и cookie действуют 30 дней от входа, активность срок не "
				+ "продлевает, продлевает только новый вход") boolean rememberMe) {

	static AccountSessionResponse from(AccountSession session) {
		return new AccountSessionResponse(session.id(), session.current(), session.browser(), session.os(),
				session.ip(), session.city(), session.signedInAt(), session.lastActiveAt(), session.rememberMe());
	}

}
