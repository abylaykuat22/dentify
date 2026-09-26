/**
 * Кэш платформы: Spring Cache в Valkey, ключи данных клиники начинаются с её id (PLT-01). Внутренний пакет модуля
 * platform: модули пишут {@code @Cacheable} с именем кэша, глобальный кэш — с приставкой {@code global.}.
 */
@NullMarked
package kz.dentify.platform.cache;

import org.jspecify.annotations.NullMarked;
