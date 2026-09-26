package kz.dentify.platform.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import kz.dentify.platform.api.ErrorCodes;
import kz.dentify.platform.api.FieldViolation;
import kz.dentify.platform.api.InvalidRequestException;
import kz.dentify.platform.api.PageQuery;
import kz.dentify.platform.api.SortBy;
import org.jspecify.annotations.Nullable;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Разбирает параметры страницы {@code page}, {@code size}, {@code sort}, {@code q} в {@link PageQuery}. Правила — в
 * Javadoc {@link PageQuery}; все ошибки собираются разом и уходят одним 400 с {@code errors[]}.
 */
class PageQueryArgumentResolver implements HandlerMethodArgumentResolver {

	static final String PAGE = "page";

	static final String SIZE = "size";

	static final String SORT = "sort";

	static final String QUERY = "q";

	static final String DESCENDING = "-";

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return PageQuery.class.equals(parameter.getParameterType());
	}

	@Override
	public PageQuery resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
			NativeWebRequest request, @Nullable WebDataBinderFactory binderFactory) {
		List<FieldViolation> violations = new ArrayList<>();
		int page = page(request.getParameter(PAGE), violations);
		int size = size(request.getParameter(SIZE), violations);
		if ((long) page * size > PageQuery.MAX_OFFSET) {
			// смещение не помещается в int JPA (setFirstResult): иначе Spring Data бросит исключение и будет 500
			violations.add(FieldViolation.of(PAGE, ErrorCodes.PAGE_INVALID));
			page = 0;
		}
		Sort sort = sort(request.getParameterValues(SORT), parameter.getParameterAnnotation(SortBy.class), violations);
		String q = query(request.getParameter(QUERY), violations);
		if (!violations.isEmpty()) {
			throw new InvalidRequestException(violations);
		}
		return new PageQuery(page, size, sort, q);
	}

	private static int page(@Nullable String value, List<FieldViolation> violations) {
		if (value == null || value.isBlank()) {
			return 0;
		}
		Integer page = parse(value);
		if (page == null || page < 0) {
			violations.add(FieldViolation.of(PAGE, ErrorCodes.PAGE_INVALID));
			return 0;
		}
		return page;
	}

	private static int size(@Nullable String value, List<FieldViolation> violations) {
		if (value == null || value.isBlank()) {
			return PageQuery.DEFAULT_SIZE;
		}
		Integer size = parse(value);
		if (size == null || size < 1) {
			violations.add(FieldViolation.of(SIZE, ErrorCodes.PAGE_SIZE_INVALID, PageQuery.MAX_SIZE));
			return PageQuery.DEFAULT_SIZE;
		}
		return Math.min(size, PageQuery.MAX_SIZE);
	}

	private static Sort sort(String @Nullable [] values, @Nullable SortBy sortBy, List<FieldViolation> violations) {
		Set<String> allowed = (sortBy != null) ? Set.of(sortBy.value()) : Set.of();
		List<String> requested = split(values);
		if (requested.isEmpty()) {
			return (sortBy != null) ? toSort(Arrays.asList(sortBy.defaultSort())) : Sort.unsorted();
		}
		for (String item : requested) {
			if (!allowed.contains(property(item))) {
				violations.add(FieldViolation.of(SORT, ErrorCodes.SORT_UNSUPPORTED));
				return Sort.unsorted();
			}
		}
		return toSort(requested);
	}

	private static @Nullable String query(@Nullable String value, List<FieldViolation> violations) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String q = value.strip();
		if (q.length() > PageQuery.MAX_QUERY_LENGTH) {
			violations.add(FieldViolation.of(QUERY, ErrorCodes.SEARCH_TOO_LONG, PageQuery.MAX_QUERY_LENGTH));
			return null;
		}
		return q;
	}

	/** {@code sort=a,-b} и {@code sort=a&sort=-b} — одно и то же; повтор поля не учитывается. */
	private static List<String> split(String @Nullable [] values) {
		Set<String> items = new LinkedHashSet<>();
		if (values != null) {
			for (String value : values) {
				for (String item : value.split(",")) {
					if (!item.isBlank()) {
						items.add(item.strip());
					}
				}
			}
		}
		Set<String> properties = new LinkedHashSet<>();
		return items.stream().filter((item) -> properties.add(property(item))).toList();
	}

	private static Sort toSort(List<String> items) {
		return Sort.by(items.stream()
			.map((item) -> item.startsWith(DESCENDING) ? Sort.Order.desc(property(item)) : Sort.Order.asc(item))
			.toList());
	}

	private static String property(String item) {
		return item.startsWith(DESCENDING) ? item.substring(DESCENDING.length()) : item;
	}

	private static @Nullable Integer parse(String value) {
		try {
			return Integer.valueOf(value.strip());
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

}
