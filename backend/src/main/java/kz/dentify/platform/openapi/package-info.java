/**
 * Спецификация OpenAPI 3.1 для фронта (PLT-23): springdoc строит её по контроллерам {@code /api/**}, а этот пакет
 * дописывает общие соглашения — ответы 4xx и 5xx со схемой ProblemDetail и параметры страницы списка. Тест
 * {@code OpenApiContractTests} выгружает спецификацию в {@code api/openapi.json}, по ней orval генерирует клиент.
 * Внутренний пакет модуля platform.
 */
@NullMarked
package kz.dentify.platform.openapi;

import org.jspecify.annotations.NullMarked;
