/**
 * Логирование платформы: маскирование персональных данных в структурированных JSON-логах.
 * Внутренний пакет модуля platform — другим модулям не нужен: подключается через
 * {@code META-INF/spring.factories} к форматтеру Spring Boot.
 */
@NullMarked
package kz.dentify.platform.logging;

import org.jspecify.annotations.NullMarked;
