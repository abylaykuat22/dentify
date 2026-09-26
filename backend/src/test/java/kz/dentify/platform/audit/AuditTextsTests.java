package kz.dentify.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;

import kz.dentify.platform.api.Languages;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import org.springframework.context.support.StaticMessageSource;

/**
 * Тексты журнала: текст события по ключу типа объекта или общему, названия полей с запасными ключами, значения по
 * формату языка, маска контактов.
 */
class AuditTextsTests {

	static final Locale RU = Languages.RUSSIAN;

	final JsonNodeFactory nodes = JsonMapper.builder().build().getNodeFactory();

	final AuditTexts texts = new AuditTexts(messages());

	@Test
	void eventTextFromEntityKeyOrGenericAction() {
		assertThat(this.texts.eventText("schedule.appointment", "rescheduled", "Айгерим", "Динара", List.of(), RU))
			.isEqualTo("Динара перенесла запись Айгерим");
		assertThat(this.texts.eventText("patient.patient", "exported", null, "Система", List.of(1200L), RU))
			.as("общий текст, числа — по формату языка (разделитель разрядов — неразрывный пробел), нет имени — тире")
			.matches("Выгрузка: —, строк: 1[\s\u00a0\u202f]200");
		assertThat(this.texts.eventText("patient.patient", "merged", "Айгерим", "Динара", List.of(), RU))
			.as("нет ни одного ключа — код действия")
			.isEqualTo("merged");
	}

	@Test
	void fieldLabelsFallBackToCommonKeysAndPathParts() {
		assertThat(this.texts.fieldLabel("schedule.appointment", "date", RU)).isEqualTo("Дата приёма");
		assertThat(this.texts.fieldLabel("patient.patient", "date", RU)).isEqualTo("Дата");
		assertThat(this.texts.fieldLabel("patient.patient", "name.kk", RU)).isEqualTo("Название · каз.");
		assertThat(this.texts.fieldLabel("patient.patient", "workplace", RU)).isEqualTo("workplace");
	}

	@Test
	void valuesAreFormattedForTheLanguage() {
		assertThat(value("date", "2026-09-24", false)).isEqualTo("24.09.2026");
		assertThat(value("time", "11:30:00", false)).isEqualTo("11:30");
		assertThat(value("datetime", "2026-09-24T11:30:00", false)).startsWith("24.09.2026").contains("11:30");
		assertThat(value("date", "не дата", false)).isEqualTo("не дата");
		assertThat(this.texts.value("x.y", "vip", "boolean", this.nodes.booleanNode(true), RU, true)).isEqualTo("да");
		assertThat(value("enum", "ARRIVED", false)).isEqualTo("Пришёл");
		assertThat(this.texts.value("x.y", "status", "enum", this.nodes.stringNode("EXPECTED"), RU, false))
			.isEqualTo("Придёт");
		assertThat(this.texts.value("x.y", "tags", "list",
				this.nodes.arrayNode().add("vip").add(this.nodes.objectNode().put("a", 1)), RU, false))
			.isEqualTo("vip, {\"a\":1}");
		assertThat(this.texts.value("x.y", "price", "number", this.nodes.stringNode("12500.50"), RU, true))
			.isEqualTo("12500.50");
		assertThat(this.texts.value("x.y", "note", "text", this.nodes.nullNode(), RU, true)).isNull();
	}

	/** Без права на контакты: ИИН, телефон, e-mail — маской; поле-контакт в непривычном виде — целиком. */
	@Test
	void contactsAreMasked() {
		assertThat(this.texts.value("x.y", "phone", "text", this.nodes.stringNode("8 (701) 123-45-67"), RU, true))
			.isEqualTo("+7••••4567");
		assertThat(this.texts.value("x.y", "iin", "text", this.nodes.stringNode("900101300123"), RU, true))
			.isEqualTo("••••0123");
		assertThat(this.texts.value("x.y", "email", "text", this.nodes.stringNode("aigerim@example.kz"), RU, true))
			.isEqualTo("a•••@example.kz");
		assertThat(this.texts.value("x.y", "mobilePhone", "text", this.nodes.stringNode("спросить у мамы"), RU, true))
			.isEqualTo("••••");
		assertThat(this.texts.value("x.y", "phone", "text", this.nodes.stringNode("8 (701) 123-45-67"), RU, false))
			.as("с правом — как есть")
			.isEqualTo("8 (701) 123-45-67");
		assertThat(this.texts.value("x.y", "visitDate", "date", this.nodes.stringNode("2026-09-24"), RU, true))
			.as("дата — не контакт")
			.isEqualTo("24.09.2026");
		assertThat(AuditTexts.isContactField("contacts.phone2")).isTrue();
		assertThat(AuditTexts.isContactField("telegramChat")).isFalse();
	}

	/**
	 * Проверка текстов снимка для тестов модулей: у пробной записи названия есть у всех полей и значений статуса, у
	 * пробного снимка пациента нет названий заметки, флага и списка — журнал показал бы их сырым путём.
	 */
	@Test
	void snapshotLabelsCheckFindsFieldsWithoutText() {
		assertThat(AuditFieldLabels.missing("schedule.appointment", AuditProbeAppointments.Snapshot.class,
				"messages.properties", "test-messages.properties"))
			.isEmpty();
		assertThat(AuditFieldLabels.missing("patient.patient", AuditLogTests.PatientSnapshot.class, "messages.properties",
				"test-messages.properties"))
			.containsExactly("note", "vip", "tags");
		assertThat(AuditFieldLabels.missing("schedule.appointment", AuditProbeAppointments.Snapshot.class))
			.as("без тестового словаря: кресла и статусов нет в основном")
			.containsExactly("chair", "status.EXPECTED", "status.ARRIVED");
	}

	private String value(String type, String text, boolean masked) {
		return this.texts.value("schedule.appointment", "status", type, this.nodes.stringNode(text), RU, masked);
	}

	private static StaticMessageSource messages() {
		StaticMessageSource messages = new StaticMessageSource();
		messages.addMessage("audit.schedule.appointment.rescheduled", RU, "{1} перенесла запись {0}");
		messages.addMessage("audit.action.exported", RU, "Выгрузка: {0}, строк: {2}");
		messages.addMessage("audit.field.schedule.appointment.date", RU, "Дата приёма");
		messages.addMessage("audit.field.date", RU, "Дата");
		messages.addMessage("audit.field.name", RU, "Название");
		messages.addMessage("audit.field.kk", RU, "каз.");
		messages.addMessage("audit.value.true", RU, "да");
		messages.addMessage("audit.value.false", RU, "нет");
		messages.addMessage("audit.value.schedule.appointment.status.ARRIVED", RU, "Пришёл");
		messages.addMessage("audit.value.status.EXPECTED", RU, "Придёт");
		return messages;
	}

}
