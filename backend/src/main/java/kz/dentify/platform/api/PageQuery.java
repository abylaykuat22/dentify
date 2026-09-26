package kz.dentify.platform.api;

import org.jspecify.annotations.Nullable;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Параметры страницы списка по соглашению {@code /api/v1} (PLT-23): {@code page}, {@code size}, {@code sort},
 * {@code q}. Контроллер принимает его параметром метода, разбирает и проверяет параметры запроса резолвер
 * платформы, а разрешённые поля сортировки задаёт {@link SortBy} на том же параметре:
 *
 * <pre>
 * &#64;GetMapping
 * PageResponse&lt;PatientRow&gt; list(&#64;SortBy(value = {lastName, createdAt}, defaultSort = lastName) PageQuery query)
 * </pre>
 *
 * Правила резолвера:
 * <ul>
 * <li>{@code page} — номер страницы с 0, по умолчанию 0; смещение {@code page * size} не больше
 * {@value #MAX_OFFSET}, предела {@code int} в JPA, дальше — 400;</li>
 * <li>{@code size} — от 1, по умолчанию {@value #DEFAULT_SIZE}; больше {@value #MAX_SIZE} — вернётся
 * {@value #MAX_SIZE}, это не ошибка;</li>
 * <li>{@code sort} — поля через запятую или повтором параметра, минус впереди — по убыванию:
 * {@code sort=lastName,-createdAt}. Поле вне {@link SortBy} — 400;</li>
 * <li>{@code q} — строка поиска без крайних пробелов, пустая — null, длиннее {@value #MAX_QUERY_LENGTH} — 400.</li>
 * </ul>
 * Ошибки — 400 с {@code errors[]} по полю параметра, коды в {@link ErrorCodes}.
 *
 * @param page номер страницы с 0
 * @param size размер страницы, от 1 до {@value #MAX_SIZE}
 * @param sort сортировка: запрошенная или по умолчанию из {@link SortBy}
 * @param q строка поиска или null
 */
public record PageQuery(int page, int size, Sort sort, @Nullable String q) {

	public static final int DEFAULT_SIZE = 25;

	public static final int MAX_SIZE = 100;

	public static final int MAX_QUERY_LENGTH = 100;

	/** Наибольшее смещение {@code page * size}: JPA принимает его как {@code int}. */
	public static final int MAX_OFFSET = Integer.MAX_VALUE;

	/** Страница для Spring Data: номер, размер и сортировка. */
	public Pageable pageable() {
		return PageRequest.of(this.page, this.size, this.sort);
	}

}
