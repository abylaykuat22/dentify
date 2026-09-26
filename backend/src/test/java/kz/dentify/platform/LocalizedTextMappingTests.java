package kz.dentify.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import kz.dentify.AbstractIntegrationTest;
import kz.dentify.platform.api.Languages;
import kz.dentify.platform.api.LocalizedText;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link LocalizedText} в сущности — JSON-агрегат Hibernate 7 в колонке {@code jsonb}: пишется объектом
 * {@code {ru, kk}}, читается обратно, по полю агрегата можно искать в JPQL. Пробная сущность —
 * {@link LocalizedTextProbe}.
 */
class LocalizedTextMappingTests extends AbstractIntegrationTest {

	@Autowired
	EntityManager entityManager;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void storedAsJsonbObject() {
		UUID id = persist(new LocalizedText("Профессиональная чистка", "Кәсіби тазалау"));

		JsonNode stored = JsonMapper.builder()
			.build()
			.readTree(this.jdbc.queryForObject("select name::text from test_localized_probe where id = ?", String.class,
					id));
		assertThat(this.jdbc.queryForObject("select jsonb_typeof(name) from test_localized_probe where id = ?",
				String.class, id))
			.isEqualTo("object");
		assertThat(stored.path("ru").asString()).isEqualTo("Профессиональная чистка");
		assertThat(stored.path("kk").asString()).isEqualTo("Кәсіби тазалау");
	}

	@Test
	void readsBackWithRussianFallback() {
		UUID id = persist(LocalizedText.of("Рентген-снимок"));

		LocalizedText name = this.transactions
			.execute((status) -> this.entityManager.find(LocalizedTextProbe.class, id).getName());

		assertThat(name).isEqualTo(LocalizedText.of("Рентген-снимок"));
		assertThat(name.kk()).isNull();
		assertThat(name.text(Languages.KAZAKH)).isEqualTo("Рентген-снимок");
	}

	@Test
	void queryableByAggregateField() {
		UUID id = persist(new LocalizedText("Отбеливание ZOOM", "ZOOM ағарту"));

		UUID found = this.transactions.execute((status) -> this.entityManager
			.createQuery("select p.id from LocalizedTextProbe p where p.name.kk = :kk", UUID.class)
			.setParameter("kk", "ZOOM ағарту")
			.getSingleResult());

		assertThat(found).isEqualTo(id);
	}

	private UUID persist(LocalizedText name) {
		UUID id = UUID.randomUUID();
		this.transactions.executeWithoutResult((status) -> this.entityManager.persist(new LocalizedTextProbe(id, name)));
		return id;
	}

}
