package kz.dentify.platform.api;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Точка расширения: имя аккаунта для показа в текущей клинике, например автор последней правки в ответе 409 о
 * конфликте версии (PLT-08, поле {@code modifiedByName}) и автор события журнала действий. Реализаций может быть
 * несколько — цепочка по {@code @Order}: сейчас iam отдаёт ФИО аккаунта с членством в клинике контекста, имя из карточки
 * сотрудника (T-IAM-26) встанет раньше него. Чего не знает первый, спрашивается у следующего ({@link #resolve}). Имя —
 * ПД: в логи его не пишут.
 */
@FunctionalInterface
public interface AccountNameProvider {

	/** Отображаемое имя аккаунта в клинике текущего контекста или пусто. */
	Optional<String> displayName(UUID accountId);

	/**
	 * Имена нескольких аккаунтов одним обращением — страница журнала, список с авторами: без пакетного метода страница из
	 * 100 событий давала бы до 100 запросов. По умолчанию — {@link #displayName} по одному; реализация с запросом к БД
	 * переопределяет его одним запросом.
	 *
	 * @return имена найденных аккаунтов; кого нет — того нет в ответе
	 */
	default Map<UUID, String> displayNames(Collection<UUID> accountIds) {
		Map<UUID, String> names = new HashMap<>();
		for (UUID accountId : accountIds) {
			displayName(accountId).ifPresent((name) -> names.put(accountId, name));
		}
		return names;
	}

	/**
	 * Имена по цепочке реализаций в их порядке: каждый следующий провайдер получает только тех, кого не нашли
	 * предыдущие, одним пакетом.
	 *
	 * @param providers реализации в порядке {@code @Order}, например {@code ObjectProvider.orderedStream()}
	 */
	static Map<UUID, String> resolve(Stream<AccountNameProvider> providers, Collection<UUID> accountIds) {
		Map<UUID, String> names = new HashMap<>();
		Set<UUID> missing = new LinkedHashSet<>(accountIds);
		for (Iterator<AccountNameProvider> iterator = providers.iterator(); iterator.hasNext() && !missing.isEmpty();) {
			iterator.next().displayNames(Set.copyOf(missing)).forEach((id, name) -> {
				if (missing.remove(id)) {
					names.put(id, name);
				}
			});
		}
		return names;
	}

}
