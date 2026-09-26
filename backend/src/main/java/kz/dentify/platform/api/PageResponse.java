package kz.dentify.platform.api;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Ответ со страницей списка по соглашению {@code /api/v1} (PLT-23): элементы, номер страницы с 0, размер и общее
 * число элементов.
 *
 * @param items элементы страницы
 * @param page номер страницы с 0
 * @param size размер страницы после ограничения
 * @param total всего элементов по запросу
 * @param <T> DTO элемента
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

	public PageResponse {
		items = List.copyOf(items);
	}

	public static <T> PageResponse<T> of(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
	}

	/** Страница сущностей Spring Data, переложенная в DTO. */
	public static <E, T> PageResponse<T> of(Page<E> page, Function<? super E, ? extends T> mapper) {
		List<T> items = page.getContent().stream().<T>map(mapper).toList();
		return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
	}

}
