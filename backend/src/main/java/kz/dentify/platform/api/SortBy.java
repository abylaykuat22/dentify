package kz.dentify.platform.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Разрешённая сортировка списка: ставится на параметр {@link PageQuery} контроллера. Сортировать можно только по
 * перечисленным полям — у них должен быть индекс (PLT-22). Без аннотации параметр {@code sort} не принимается.
 * Поля попадают в спецификацию OpenAPI как допустимые значения {@code sort}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SortBy {

	/** Поля сущности, по которым можно сортировать, например lastName, createdAt. */
	String[] value();

	/** Сортировка, если {@code sort} не передан: поля в порядке приоритета, минус впереди — по убыванию. */
	String[] defaultSort() default {};

}
