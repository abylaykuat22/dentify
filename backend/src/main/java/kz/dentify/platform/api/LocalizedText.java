package kz.dentify.platform.api;

import java.util.Locale;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

import org.springframework.context.i18n.LocaleContextHolder;

/**
 * Двуязычное значение справочника: название услуги, специальности, причины отмены. Русский обязателен, казахский —
 * нет; без казахского показывается русский.
 * <p>
 * В сущности — одна колонка {@code jsonb} через JSON-агрегат Hibernate 7:
 *
 * <pre>
 * &#64;Embedded
 * &#64;JdbcTypeCode(SqlTypes.JSON)
 * private LocalizedText name;   // колонка name jsonb NOT NULL, в ней {ru, kk}
 * </pre>
 *
 * В DTO тот же тип: в JSON это объект с полями ru и kk, в запросе проверяется через {@code @Valid}. Пустой kk
 * хранится как null. По агрегату можно фильтровать и сортировать в JPQL: {@code where s.name.ru = :ru}.
 *
 * @param ru название на русском, обязательно
 * @param kk название на казахском или null
 */
@Embeddable
public record LocalizedText(@NotBlank String ru, @Nullable String kk) {

	public LocalizedText {
		kk = (kk == null || kk.isBlank()) ? null : kk;
	}

	/** Значение только на русском. */
	public static LocalizedText of(String ru) {
		return new LocalizedText(ru, null);
	}

	/** Текст на языке локали: казахский, если он есть и локаль казахская, иначе русский. */
	public String text(Locale locale) {
		return (this.kk != null && Languages.isKazakh(locale)) ? this.kk : this.ru;
	}

	/**
	 * Текст на языке текущего запроса: локаль из {@code LocaleResolver} платформы. Вне запроса — русский, язык по
	 * умолчанию платформы, а не язык ОС сервера.
	 */
	public String text() {
		return text(LocaleContextHolder.getLocale());
	}

}
