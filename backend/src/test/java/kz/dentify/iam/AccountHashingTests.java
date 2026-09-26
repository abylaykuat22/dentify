package kz.dentify.iam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import kz.dentify.AbstractIntegrationTest;
import kz.dentify.iam.internal.Account;
import kz.dentify.iam.internal.Accounts;
import kz.dentify.iam.internal.Accounts.PreparedAccount;
import kz.dentify.iam.internal.NewAccount;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Хэш пароля нового аккаунта считается вне транзакции БД (docs/architecture.md, Пароли): Argon2id — десятки
 * миллисекунд и до 3 секунд ожидания места, и открытая транзакция держала бы всё это время соединение пула. Вызывающий
 * со своей транзакцией (регистрация клиники, приглашение) готовит аккаунт до неё и записывает внутри.
 */
class AccountHashingTests extends AbstractIntegrationTest {

	@Autowired
	Accounts accounts;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Test
	void newAccountPasswordIsHashedOutsideTransaction() {
		String password = "Хэш-вне-транзакции-" + UUID.randomUUID();
		PasswordChecks.count(password);

		String phone = "8707" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		Account account = this.accounts.create(new NewAccount(phone, null, "Хэш Демо", password));

		assertThat(account.getId()).isNotNull();
		assertThat(PasswordChecks.encodedInTransaction(password)).isFalse();
	}

	@Test
	void preparedAccountIsWrittenInCallerTransaction() {
		String password = "Хэш-до-транзакции-" + UUID.randomUUID();
		PasswordChecks.count(password);
		PreparedAccount prepared = this.accounts.prepare(new NewAccount(null,
				"prepared." + UUID.randomUUID() + "@example.kz", "Приглашённый Демо", password));

		Account account = new TransactionTemplate(this.transactionManager)
			.execute((status) -> this.accounts.create(prepared));

		assertThat(account).isNotNull();
		assertThat(this.accounts.find(Objects.requireNonNull(account.getId()))).isPresent();
		assertThat(PasswordChecks.encodedInTransaction(password)).isFalse();
		assertThat(prepared.toString()).doesNotContain(prepared.passwordHash(), "example.kz");
	}

}
