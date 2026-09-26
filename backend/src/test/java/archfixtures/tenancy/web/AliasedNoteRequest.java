package archfixtures.tenancy.web;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Фикстура ArchUnit: клиника под другим именем поля, но clinic_id в JSON — нарушение. */
public record AliasedNoteRequest(@JsonProperty("clinic_id") UUID owner, String title) {
}
