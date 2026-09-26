package kz.dentify.iam.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Аккаунты. Телефон ищется в E.164 ({@code PhoneNumber.value()}), e-mail — по {@code lower(email)}: так поиск идёт по
 * уникальному индексу {@code ux_account_email} и не зависит от регистра строки в БД.
 */
interface AccountRepository extends JpaRepository<Account, UUID> {

	Optional<Account> findByPhone(String phone);

	boolean existsByPhone(String phone);

	/** Аккаунт по e-mail в нижнем регистре ({@code EmailAddress.value()}). */
	@Query("select a from Account a where lower(a.email) = :email")
	Optional<Account> findByEmail(String email);

	@Query("select count(a) > 0 from Account a where lower(a.email) = :email")
	boolean existsByEmail(String email);

}
