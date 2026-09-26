package kz.dentify.platform.openapi;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;

/**
 * Точный контракт record-DTO для orval. Пакеты помечены {@code @NullMarked}, и компонент record в схеме OpenAPI 3.1
 * описывается по его {@code @Nullable}:
 * <ul>
 * <li>без {@code @Nullable} — свойство обязательное и не null. Без этого springdoc считает обязательными только поля
 * с {@code @NotNull} и {@code @NotBlank}, и orval сделал бы необязательными все поля ответов, например items и
 * total у PageResponse;</li>
 * <li>с {@code @Nullable} — свойство необязательное, и null — допустимое значение: Jackson пишет null в JSON. К типу
 * добавляется null, в OpenAPI 3.1 это {@code type: [string, null]}; у ссылки на схему, где соседний тип не
 * поставить, — {@code oneOf: [ссылка, type null]}. Тогда orval даёт {@code kk?: string | null}, а zod не
 * отвергает null в настоящем ответе.</li>
 * </ul>
 * Бин {@code ModelConverter} springdoc подключает сам.
 */
@Component
class RecordRequiredPropertiesConverter implements ModelConverter {

	private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

	/** Тип null в JSON Schema 2020-12, на которой стоит OpenAPI 3.1. */
	private static final String NULL = "null";

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public @Nullable Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
		Schema schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
		Class<?> raw = Json.mapper().constructType(type.getType()).getRawClass();
		if (schema == null || !raw.isRecord()) {
			return schema;
		}
		String ref = schema.get$ref();
		Schema model = (ref != null && ref.startsWith(SCHEMA_REF_PREFIX))
				? context.getDefinedModels().get(ref.substring(SCHEMA_REF_PREFIX.length())) : schema;
		if (model == null || model.getProperties() == null) {
			return schema;
		}
		Map<String, Schema> properties = model.getProperties();
		for (RecordComponent component : raw.getRecordComponents()) {
			String name = component.getName();
			Schema property = properties.get(name);
			if (property == null) {
				continue;
			}
			if (nullable(component)) {
				properties.put(name, allowNull(property));
			}
			else if (model.getRequired() == null || !model.getRequired().contains(name)) {
				model.addRequiredItem(name);
			}
		}
		return schema;
	}

	/**
	 * Схема свойства, которая принимает и null. Повторный вызов ничего не меняет: модель record разбирается при каждом
	 * упоминании типа.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	static Schema allowNull(Schema property) {
		if (property.get$ref() != null) {
			Schema wrapper = new Schema<>();
			wrapper.setDescription(property.getDescription());
			wrapper.setOneOf(new ArrayList<>(List.of(new Schema<>().$ref(property.get$ref()), nullSchema())));
			return wrapper;
		}
		if (property.getOneOf() != null) {
			property.setOneOf(withNull(property.getOneOf()));
			return property;
		}
		if (property.getAnyOf() != null) {
			property.setAnyOf(withNull(property.getAnyOf()));
			return property;
		}
		Set<String> types = new LinkedHashSet<>();
		if (property.getTypes() != null) {
			types.addAll(property.getTypes());
		}
		else if (property.getType() != null) {
			types.add(property.getType());
		}
		if (!types.isEmpty()) {
			types.add(NULL);
			property.setTypes(types);
		}
		return property;
	}

	@SuppressWarnings("rawtypes")
	private static List<Schema> withNull(List<Schema> alternatives) {
		List<Schema> result = new ArrayList<>(alternatives);
		if (result.stream().noneMatch(RecordRequiredPropertiesConverter::isNullSchema)) {
			result.add(nullSchema());
		}
		return result;
	}

	private static Schema<?> nullSchema() {
		return new Schema<>().types(new LinkedHashSet<>(Set.of(NULL)));
	}

	@SuppressWarnings("rawtypes")
	private static boolean isNullSchema(Schema schema) {
		return schema.getTypes() != null && schema.getTypes().equals(Set.of(NULL));
	}

	private static boolean nullable(RecordComponent component) {
		return component.getAnnotatedType().isAnnotationPresent(Nullable.class);
	}

}
