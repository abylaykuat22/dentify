/**
 * Веб-слой платформы: единый формат ошибок ProblemDetail по RFC 9457 для любых ответов 4xx и 5xx и разбор
 * параметров страницы списка. Внутренний пакет модуля platform: другие модули бросают исключения и принимают
 * {@code PageQuery} из {@code platform.api}, а в ответ их превращает этот пакет.
 */
@NullMarked
package kz.dentify.platform.web;

import org.jspecify.annotations.NullMarked;
