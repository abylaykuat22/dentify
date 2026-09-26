package kz.dentify.platform;

import static org.assertj.core.api.Assertions.assertThat;

import kz.dentify.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisKeyValueAdapter;
import org.springframework.modulith.moments.support.Moments;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Отключённая лишняя автоконфигурация (application.yml): Moments Modulith, репозитории Spring Data Redis и пользователь
 * Spring Security в памяти.
 */
class InfrastructureConfigTests extends AbstractIntegrationTest {

	@Autowired
	ApplicationContext context;

	/**
	 * {@code spring.modulith.moments.enabled: false}: событий DayHasPassed и т.п. нет, а с ними и
	 * {@code @EnableScheduling} автоконфигурации Moments без TaskScheduler.
	 */
	@Test
	void modulithMomentsAreDisabled() {
		assertThat(this.context.getBeanNamesForType(Moments.class)).isEmpty();
	}

	/** {@code spring.data.redis.repositories.enabled: false}: Valkey — только сессии, лимиты и кэш. */
	@Test
	void redisRepositoriesAreDisabled() {
		assertThat(this.context.getBeanNamesForType(RedisKeyValueAdapter.class)).isEmpty();
	}

	/**
	 * {@code spring.autoconfigure.exclude: UserDetailsServiceAutoConfiguration}: пользователя в памяти со сгенерированным
	 * паролем нет, и пароль не пишется в лог. Аккаунты — в БД, вход — через AccountSignIn.
	 */
	@Test
	void noInMemoryUser() {
		assertThat(this.context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
	}

}
