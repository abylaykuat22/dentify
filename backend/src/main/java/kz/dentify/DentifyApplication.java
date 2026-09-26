package kz.dentify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Dentify — модульный монолит. Каждый прямой подпакет {@code kz.dentify.*} — модуль Spring Modulith
 * (границы и разрешённые зависимости — в его {@code package-info.java}).
 * <p>
 * platform — общий модуль ({@code sharedModules}): Modulith поднимает его в каждом срезе {@code @ApplicationModuleTest}.
 * Сейчас срезов нет — интеграционные тесты идут на полном приложении с одним кэшированным контекстом
 * ({@code AbstractIntegrationTest}, docs/architecture.md, Одна история миграций). Объявление оставлено, чтобы срез, если
 * он понадобится, получил тенант-контекст, безопасность и ошибки платформы.
 */
@SpringBootApplication
@Modulithic(systemName = "Dentify", sharedModules = "platform")
public class DentifyApplication {

	public static void main(String[] args) {
		SpringApplication.run(DentifyApplication.class, args);
	}

}
