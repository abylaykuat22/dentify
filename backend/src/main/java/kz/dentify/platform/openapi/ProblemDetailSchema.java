package kz.dentify.platform.openapi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import kz.dentify.platform.web.ProblemFieldError;
import org.jspecify.annotations.Nullable;

/**
 * Описание тела ошибки для спецификации OpenAPI: {@code ProblemDetail} Spring плюс расширения Dentify, как их
 * пишет {@code ProblemFactory}. В коде не создаётся, только для схемы.
 */
@Schema(name = OpenApiConfiguration.PROBLEM_DETAIL,
		description = "Ошибка API по RFC 9457 с расширениями Dentify: code, traceId, errors, "
				+ "modifiedAt/modifiedBy/modifiedByName у конфликта версии")
record ProblemDetailSchema(
		@Schema(description = "URI типа ошибки. Нет поля — about:blank по RFC 9457: тип определяется HTTP-статусом",
				example = "about:blank") @Nullable String type,
		@Schema(description = "Заголовок по HTTP-статусу на языке пользователя", example = "Некорректный запрос",
				requiredMode = RequiredMode.REQUIRED) String title,
		@Schema(description = "HTTP-статус", example = "400", requiredMode = RequiredMode.REQUIRED) int status,
		@Schema(description = "Текст ошибки на языке пользователя: его показывает интерфейс",
				example = "Проверьте правильность заполнения полей.", requiredMode = RequiredMode.REQUIRED) String detail,
		@Schema(description = "Путь запроса", example = "/api/v1/patients") @Nullable String instance,
		@Schema(description = "Машинный код ошибки, он же ключ сообщения", example = "error.validation",
				requiredMode = RequiredMode.REQUIRED) String code,
		@Schema(description = "Идентификатор трассировки запроса: тот же, что в логах, его сообщают поддержке",
				example = "68f1c0a2b3d4e5f60718293a4b5c6d7e") @Nullable String traceId,
		@Schema(description = "Ошибки полей; есть, если ошибка касается полей формы или параметров")
		@Nullable List<ProblemFieldError> errors,
		@Schema(description = "Конфликт версии (409 error.version-conflict): когда запись изменили последний раз",
				example = "2026-10-01T06:38:00Z") @Nullable Instant modifiedAt,
		@Schema(description = "Конфликт версии: id аккаунта автора последней правки; нет — правка системная")
		@Nullable UUID modifiedBy,
		@Schema(description = "Конфликт версии: имя автора последней правки в клинике для плашки в интерфейсе",
				example = "Динара Алиева") @Nullable String modifiedByName) {
}
