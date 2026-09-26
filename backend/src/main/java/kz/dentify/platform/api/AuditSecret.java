package kz.dentify.platform.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Секрет в снимке аудита: значение поля в журнал не попадает никогда, в разнице — только отметка, что поле изменилось.
 * Пароли, токены, хэши, соли, PIN и одноразовые коды распознаются и без аннотации — по имени поля
 * ({@code password}, {@code passwordHash}, {@code token}, {@code secret}, {@code pin}, {@code otp}) и по виду значения
 * (хэш пароля {@code {argon2}…}, {@code $2a$…}); {@code byte[]} и {@code char[]} — тоже секрет. Аннотация — для
 * секрета с другим именем.
 */
@Target({ ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditSecret {

}
