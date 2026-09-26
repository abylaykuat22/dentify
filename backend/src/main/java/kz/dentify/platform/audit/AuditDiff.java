package kz.dentify.platform.audit;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

import kz.dentify.platform.api.AuditSecret;
import kz.dentify.platform.api.Hashes;
import kz.dentify.platform.identity.AuditEntry;
import kz.dentify.platform.logging.PiiMasker;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.util.ReflectionUtils;

/**
 * Разница было и стало по двум снимкам — record. Поля сравниваются в порядке объявления, вложенный record — по своим
 * полям через точку ({@code name.ru}). Снимок сразу раскладывается в значения JSON ({@link #flatten}): изменение
 * объекта после снимка на них не влияет.
 * <ul>
 * <li>служебные поля верхнего уровня ({@link #TECHNICAL}) в разницу не идут;</li>
 * <li>секрет — поле с {@link AuditSecret}, с именем из {@link #SECRET_WORDS} ({@code password}, {@code passwordHash},
 * {@code token}), {@code byte[]} и {@code char[]}, а также значение вида хэша пароля. Его значение не хранится и не
 * возвращается: для сравнения — только SHA-256 в памяти, в разнице — {@code type: secret} без было и стало;</li>
 * <li>поле-коллекция, Map или record, объявленный не record-типом, идёт в разницу целиком, но обходится рекурсивно по
 * тем же правилам: компонент record с секретом, значение Map с ключом-секретом, {@code byte[]}, {@code char[]} и строка
 * вида хэша заменяются на {@link #HIDDEN}. Если внутри изменились только секреты, в разнице — {@code type: secret};</li>
 * <li>в снимке только record, коллекции, массивы, Map и простые значения: строки, числа, да и нет, перечисления,
 * UUID, дата и время. Сущность JPA, прокси Hibernate или другой объект — {@link IllegalArgumentException}: у такого
 * объекта нет списка полей, по которому можно найти секреты.</li>
 * </ul>
 */
final class AuditDiff {

	/** Служебные поля снимка: id, версия и аудит сущности меняются при каждой правке и ничего не говорят человеку. */
	static final Set<String> TECHNICAL = Set.of("id", "version", "createdAt", "createdBy", "updatedAt", "updatedBy");

	/** Слова имени поля, по которым оно считается секретом: {@code passwordHash} — слова password и hash. */
	static final Set<String> SECRET_WORDS = Set.of("password", "passwd", "pwd", "secret", "secrets", "token", "tokens",
			"hash", "salt", "otp", "pin", "credential", "credentials");

	/** Секрет внутри коллекции, Map или record из них: в разнице вместо значения. */
	static final String HIDDEN = PiiMasker.MASK;

	/**
	 * Хэш пароля: с префиксом DelegatingPasswordEncoder Spring Security ({@code {argon2}…}, {@code {bcrypt}…}) или
	 * в формате crypt ({@code $argon2id$…}, {@code $2a$…}).
	 */
	private static final Pattern PASSWORD_HASH = Pattern
		.compile("\\{[A-Za-z0-9@._-]+}\\S+|\\$(?:argon2(?:id|i|d)|2[abxy]?|pbkdf2[a-z0-9-]*|scrypt|s0|[156])\\$\\S+");

	private static final Pattern WORD_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|[_\\-.\\s]+");

	private static final Pattern TRAILING_DIGITS = Pattern.compile("\\d+$");

	private final JsonMapper json;

	private final JsonNodeFactory nodes;

	AuditDiff(JsonMapper json) {
		this.json = json;
		this.nodes = json.getNodeFactory();
	}

	/**
	 * Значения полей снимка по путям; null — снимка нет.
	 *
	 * @throws IllegalArgumentException в снимке объект, который не record, не коллекция, не Map и не простое значение
	 */
	Map<String, Value> flatten(@Nullable Record snapshot) {
		Map<String, Value> values = new LinkedHashMap<>();
		if (snapshot != null) {
			flatten(snapshot.getClass(), snapshot, "", values);
		}
		return values;
	}

	/**
	 * Изменившиеся поля: сначала в порядке снимка после, затем поля, которых после нет. Секрет или значение, в котором
	 * изменились только скрытые секреты, — без было и стало.
	 */
	List<Change> compare(Map<String, Value> before, Map<String, Value> after) {
		Set<String> fields = new LinkedHashSet<>(after.keySet());
		fields.addAll(before.keySet());
		List<Change> changes = new ArrayList<>();
		for (String field : fields) {
			Value from = before.getOrDefault(field, Value.ABSENT);
			Value to = after.getOrDefault(field, Value.ABSENT);
			if (from.sameAs(to)) {
				continue;
			}
			String type = to.isAbsent() ? from.type() : to.type();
			if (from.isSecret() || to.isSecret() || from.sameNode(to)) {
				changes.add(new Change(field, AuditEntry.Change.SECRET, null, null));
			}
			else {
				changes.add(new Change(field, type, from.node(), to.node()));
			}
		}
		return changes;
	}

	/** Разница в JSON для колонки {@code diff}: {@code [{field, type, from, to}]}, пустые было и стало не пишутся. */
	ArrayNode toJson(List<Change> changes) {
		ArrayNode array = this.json.createArrayNode();
		for (Change change : changes) {
			ObjectNode node = array.addObject();
			node.put("field", change.field());
			node.put("type", change.type());
			if (change.from() != null) {
				node.set("from", change.from());
			}
			if (change.to() != null) {
				node.set("to", change.to());
			}
		}
		return array;
	}

	private void flatten(Class<?> type, @Nullable Record snapshot, String prefix, Map<String, Value> values) {
		for (RecordComponent component : type.getRecordComponents()) {
			String name = component.getName();
			if (prefix.isEmpty() && TECHNICAL.contains(name)) {
				continue;
			}
			String path = prefix + name;
			Object value = (snapshot != null) ? read(component, snapshot) : null;
			Class<?> declared = component.getType();
			if (isSecret(component, declared, value)) {
				values.put(path, Value.secret(fingerprint(value, path)));
			}
			else if (declared.isRecord()) {
				flatten(declared, (Record) value, path + ".", values);
			}
			else if (value == null) {
				values.put(path, new Value(typeOf(declared), null, null));
			}
			else {
				Hidden hidden = new Hidden();
				JsonNode node = tree(value, path, hidden);
				values.put(path, new Value(typeOf(value.getClass()), node, hidden.fingerprint()));
			}
		}
	}

	/**
	 * Значение в JSON. С {@code hidden} секреты внутри заменяются на {@link #HIDDEN}, а их SHA-256 копится в
	 * {@code hidden}; без него — дерево как есть, только для отпечатка секрета в памяти.
	 */
	private JsonNode tree(@Nullable Object value, String path, @Nullable Hidden hidden) {
		if (value == null) {
			return this.nodes.nullNode();
		}
		if (value instanceof Record record) {
			ObjectNode node = this.nodes.objectNode();
			for (RecordComponent component : record.getClass().getRecordComponents()) {
				Object item = read(component, record);
				String itemPath = path + "." + component.getName();
				node.set(component.getName(), (hidden != null && isSecret(component, component.getType(), item))
						? hide(item, itemPath, hidden) : tree(item, itemPath, hidden));
			}
			return node;
		}
		if (value instanceof byte[] || value instanceof char[]) {
			if (hidden != null) {
				return hide(value, path, hidden);
			}
			return (value instanceof char[] chars) ? this.nodes.stringNode(new String(chars))
					: this.nodes.binaryNode((byte[]) value);
		}
		if (value instanceof Map<?, ?> map) {
			ObjectNode node = this.nodes.objectNode();
			for (Map.Entry<String, @Nullable Object> entry : sortedByKey(map, path).entrySet()) {
				String itemPath = path + "." + entry.getKey();
				node.set(entry.getKey(), (hidden != null && isSecretName(entry.getKey()))
						? hide(entry.getValue(), itemPath, hidden) : tree(entry.getValue(), itemPath, hidden));
			}
			return node;
		}
		if (value instanceof Collection<?> collection) {
			ArrayNode node = this.nodes.arrayNode();
			int index = 0;
			for (Object item : collection) {
				node.add(tree(item, path + "[" + index++ + "]", hidden));
			}
			return node;
		}
		if (value.getClass().isArray()) {
			ArrayNode node = this.nodes.arrayNode();
			for (int index = 0; index < Array.getLength(value); index++) {
				node.add(tree(Array.get(value, index), path + "[" + index + "]", hidden));
			}
			return node;
		}
		if (value instanceof CharSequence text) {
			String string = text.toString();
			if (hidden != null && PASSWORD_HASH.matcher(string).matches()) {
				return hide(string, path, hidden);
			}
			return this.nodes.stringNode(string);
		}
		if (isPlainValue(value)) {
			return this.json.valueToTree(value);
		}
		throw new IllegalArgumentException("Снимок аудита — record из простых значений, record, коллекций и Map, а в "
				+ path + " лежит " + value.getClass().getName() + ": переложите его в record");
	}

	/** Секрет внутри значения: отпечаток — в {@code hidden}, в разнице — {@link #HIDDEN}, пустой — null. */
	private JsonNode hide(@Nullable Object value, String path, Hidden hidden) {
		hidden.add(fingerprint(value, path));
		return (value != null) ? this.nodes.stringNode(HIDDEN) : this.nodes.nullNode();
	}

	/** Записи Map по ключу-строке в алфавитном порядке: порядок обхода HashMap не влияет ни на JSON, ни на отпечаток. */
	private static Map<String, @Nullable Object> sortedByKey(Map<?, ?> map, String path) {
		Map<String, @Nullable Object> sorted = new TreeMap<>();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			Object key = entry.getKey();
			String name = switch (key) {
				case String string -> string;
				case Enum<?> constant -> constant.name();
				case UUID id -> id.toString();
				case Number number -> number.toString();
				case Character character -> character.toString();
				case null -> throw new IllegalArgumentException("Ключ null в Map снимка аудита: " + path);
				default -> throw new IllegalArgumentException("Ключ Map снимка аудита — строка, перечисление, UUID или "
						+ "число, а в " + path + " — " + key.getClass().getName());
			};
			if (sorted.containsKey(name)) {
				throw new IllegalArgumentException("Два ключа " + name + " в Map снимка аудита: " + path);
			}
			sorted.put(name, entry.getValue());
		}
		return sorted;
	}

	/** Простое значение: его JSON — строка, число или да и нет. */
	private static boolean isPlainValue(Object value) {
		return value instanceof Number || value instanceof Boolean || value instanceof Character
				|| value instanceof Enum<?> || value instanceof UUID || value instanceof TemporalAccessor
				|| value instanceof TemporalAmount || value instanceof ZoneId || value instanceof Currency
				|| value instanceof Locale;
	}

	private static @Nullable Object read(RecordComponent component, Record snapshot) {
		Method accessor = component.getAccessor();
		ReflectionUtils.makeAccessible(accessor);
		return ReflectionUtils.invokeMethod(accessor, snapshot);
	}

	static boolean isSecret(RecordComponent component, Class<?> declared, @Nullable Object value) {
		return component.isAnnotationPresent(AuditSecret.class)
				|| component.getAccessor().isAnnotationPresent(AuditSecret.class) || declared == byte[].class
				|| declared == char[].class || value instanceof byte[] || value instanceof char[]
				|| isSecretName(component.getName())
				|| (value instanceof CharSequence text && PASSWORD_HASH.matcher(text).matches());
	}

	/** Имя поля — секрет: одно из его слов в {@link #SECRET_WORDS}. */
	static boolean isSecretName(String name) {
		return words(name).stream().anyMatch(SECRET_WORDS::contains);
	}

	/**
	 * Слова имени поля в нижнем регистре без цифр в конце: {@code phone2 → phone},
	 * {@code passwordHash → password, hash}.
	 */
	static List<String> words(String name) {
		return Arrays.stream(WORD_BOUNDARY.split(name))
			.filter((word) -> !word.isEmpty())
			.map((word) -> TRAILING_DIGITS.matcher(word.toLowerCase(Locale.ROOT)).replaceAll(""))
			.toList();
	}

	static String typeOf(Class<?> type) {
		if (type == LocalDate.class) {
			return "date";
		}
		if (type == LocalTime.class) {
			return "time";
		}
		if (type == LocalDateTime.class) {
			return "datetime";
		}
		if (type == Instant.class || type == OffsetDateTime.class || type == ZonedDateTime.class) {
			return "instant";
		}
		if (type == Boolean.class || type == boolean.class) {
			return "boolean";
		}
		if (Number.class.isAssignableFrom(type) || (type.isPrimitive() && type != char.class && type != void.class)) {
			return "number";
		}
		if (type.isEnum()) {
			return "enum";
		}
		if (type.isArray() || Collection.class.isAssignableFrom(type)) {
			return "list";
		}
		if (Map.class.isAssignableFrom(type) || type.isRecord()) {
			return "json";
		}
		return "text";
	}

	/**
	 * SHA-256 значения секрета, только в памяти: сравнить было и стало, не храня значение. Пустое — SHA-256 пустой
	 * строки байтов. Объект, которого не может быть в снимке, отклоняется и здесь.
	 */
	private byte[] fingerprint(@Nullable Object value, String path) {
		if (value == null) {
			return sha256(new byte[0]);
		}
		return sha256(this.json.writeValueAsBytes(tree(value, path, null)));
	}

	private static byte[] sha256(byte[] bytes) {
		return Hashes.sha256(bytes);
	}

	/** Отпечатки секретов, скрытых внутри одного значения, по порядку обхода. */
	private static final class Hidden {

		private final List<byte[]> fingerprints = new ArrayList<>();

		void add(byte[] fingerprint) {
			this.fingerprints.add(fingerprint);
		}

		/** Общий SHA-256 скрытых секретов или null, если их нет. */
		byte @Nullable [] fingerprint() {
			if (this.fingerprints.isEmpty()) {
				return null;
			}
			MessageDigest digest = Hashes.sha256();
			this.fingerprints.forEach(digest::update);
			return digest.digest();
		}

	}

	/**
	 * Значение поля снимка.
	 *
	 * @param type вид значения для показа: text, number, date и т. д.; secret — всё значение секрет
	 * @param node значение в JSON, секреты внутри заменены на {@link #HIDDEN}; null — пусто или секрет
	 * @param secret SHA-256 только в памяти: у секрета — его значения, у остальных — секретов, скрытых внутри
	 * {@code node}; null — скрытых секретов нет
	 */
	record Value(String type, @Nullable JsonNode node, byte @Nullable [] secret) {

		/** Поля нет в снимке: снимка нет или поле появилось в другой версии record. */
		static final Value ABSENT = new Value("text", null, null);

		/** SHA-256 пустого значения: секрета не было. */
		private static final byte[] SECRET_OF_NULL = sha256(new byte[0]);

		static Value secret(byte[] fingerprint) {
			return new Value(AuditEntry.Change.SECRET, null, fingerprint);
		}

		boolean isSecret() {
			return AuditEntry.Change.SECRET.equals(this.type);
		}

		boolean isAbsent() {
			return this == ABSENT;
		}

		boolean sameAs(Value other) {
			if (isSecret() || other.isSecret()) {
				return Arrays.equals(isSecret() ? this.secret : SECRET_OF_NULL,
						other.isSecret() ? other.secret : SECRET_OF_NULL);
			}
			return sameNode(other) && Arrays.equals(this.secret, other.secret);
		}

		/** Видимая часть значений равна: без секретов, скрытых внутри. */
		boolean sameNode(Value other) {
			JsonNode left = this.node;
			JsonNode right = other.node;
			if (left == null || left.isNull() || right == null || right.isNull()) {
				return (left == null || left.isNull()) && (right == null || right.isNull());
			}
			if ("number".equals(this.type) && "number".equals(other.type)) {
				return decimal(left).compareTo(decimal(right)) == 0;
			}
			return Objects.equals(left, right);
		}

		private static BigDecimal decimal(JsonNode node) {
			return new BigDecimal(node.asString());
		}

		@Override
		public String toString() {
			return isSecret() ? "Value[secret]" : "Value[" + this.type + "=" + this.node + "]";
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return other instanceof Value value && this.type.equals(value.type) && Objects.equals(this.node, value.node)
					&& Arrays.equals(this.secret, value.secret);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.type, this.node, Arrays.hashCode(this.secret));
		}

	}

	/**
	 * Изменившееся поле.
	 *
	 * @param field путь поля
	 * @param type вид значения; secret — без было и стало
	 * @param from было или null
	 * @param to стало или null
	 */
	record Change(String field, String type, @Nullable JsonNode from, @Nullable JsonNode to) {
	}

}
