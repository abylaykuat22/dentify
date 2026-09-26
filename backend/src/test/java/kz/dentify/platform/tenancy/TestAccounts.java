package kz.dentify.platform.tenancy;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import kz.dentify.platform.api.AccountNameProvider;

import org.springframework.stereotype.Component;

/**
 * Вымышленные аккаунты тестов и пробная реализация имён для плашки конфликта версии, пока нет сотрудников
 * (T-STF). Кто вошёл, аудит узнаёт из настоящего контекста Spring Security: вход — {@code enterAs} в тестах изоляции.
 */
@Component
public class TestAccounts implements AccountNameProvider {

	/** Вымышленный сотрудник. */
	public static final UUID DINARA = UUID.fromString("0199a000-0000-7000-8000-000000000001");

	public static final UUID ASKAR = UUID.fromString("0199a000-0000-7000-8000-000000000002");

	static final Map<UUID, String> NAMES = Map.of(DINARA, "Динара Алиева", ASKAR, "Аскар Нурланов");

	/** Сколько раз у провайдера спрашивали имена пачкой: страница журнала — один раз, а не на каждого автора. */
	private static final AtomicInteger BATCHES = new AtomicInteger();

	@Override
	public Optional<String> displayName(UUID accountId) {
		return Optional.ofNullable(NAMES.get(accountId));
	}

	@Override
	public Map<UUID, String> displayNames(Collection<UUID> accountIds) {
		BATCHES.incrementAndGet();
		return AccountNameProvider.super.displayNames(accountIds);
	}

	/** Число обращений за именами пачкой с запуска тестов. */
	public static int batches() {
		return BATCHES.get();
	}

}
