package kz.dentify.platform.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Id аккаунта, от имени которого идёт работа. Из него аудит сущностей заполняет {@code created_by} и
 * {@code updated_by} ({@link BaseEntity}), им пользуются модули, которым нужен вошедший аккаунт. Реализация — платформа
 * по контексту Spring Security (T-PLT-04): имя вошедшего — id аккаунта, его ставит {@code identity.AccountSignIn}.
 * Бин один: вторая реализация сделала бы аудит пустым.
 */
@FunctionalInterface
public interface CurrentAccountProvider {

	/** Id вошедшего аккаунта или пусто: нет входа, системная задача. */
	Optional<UUID> currentAccountId();

}
