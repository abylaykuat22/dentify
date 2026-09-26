package kz.dentify.platform.audit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import kz.dentify.platform.api.AuditEvent;
import kz.dentify.platform.api.AuditSnapshots;
import kz.dentify.platform.api.Audited;
import kz.dentify.platform.audit.AuditDiff.Change;
import kz.dentify.platform.audit.AuditDiff.Value;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.VariableReference;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link Audited}: снимок объекта до метода, метод, снимок после, событие с разницей — в одной транзакции.
 * <p>
 * Советник аудита может оказаться и снаружи советника {@code @Transactional}, и внутри: порядок советников с одинаковым
 * приоритетом не определён. Поэтому интерцептор сам открывает транзакцию по атрибуту {@code @Transactional} метода — так
 * же, как {@code TransactionInterceptor}, — а {@code @Transactional} метода к ней присоединяется. Снимок до читается уже
 * в этой транзакции: между ним и изменением никто не вклинится незамеченным. Поддерживается только распространение
 * REQUIRED без readOnly: с REQUIRES_NEW изменение ушло бы в другую транзакцию, чем событие.
 * <p>
 * Неверное применение — {@link IllegalStateException}, а не тихо потерянное событие: нет транзакции или она не та, нет
 * бина снимков, в выражении id переменная, которой нет среди параметров метода (проверяется при первом вызове), id
 * вычислился в null или не в UUID.
 */
class AuditedInterceptor implements MethodInterceptor {

	private static final String RESULT = "result";

	/** Переменные SpEL, которые есть всегда: текущий и корневой объект. */
	private static final Set<String> BUILT_IN_VARIABLES = Set.of("this", "root");

	private final ObjectProvider<JdbcAuditService> audit;

	private final ObjectProvider<AuditDiff> diff;

	private final ObjectProvider<AuditSnapshots<?>> snapshotBeans;

	private final ObjectProvider<TransactionAttributeSource> transactionAttributes;

	private final ObjectProvider<PlatformTransactionManager> transactionManager;

	private final SpelExpressionParser parser = new SpelExpressionParser();

	private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

	/** Разобранное и проверенное выражение id по методу. */
	private final Map<Method, IdExpression> expressions = new ConcurrentHashMap<>();

	private volatile @Nullable Map<String, AuditSnapshots<?>> snapshots;

	/** Бины — через ObjectProvider: советник создаётся раньше них, до обработки бинов постпроцессорами. */
	AuditedInterceptor(ObjectProvider<JdbcAuditService> audit, ObjectProvider<AuditDiff> diff,
			ObjectProvider<AuditSnapshots<?>> snapshotBeans,
			ObjectProvider<TransactionAttributeSource> transactionAttributes,
			ObjectProvider<PlatformTransactionManager> transactionManager) {
		this.audit = audit;
		this.diff = diff;
		this.snapshotBeans = snapshotBeans;
		this.transactionAttributes = transactionAttributes;
		this.transactionManager = transactionManager;
	}

	@Override
	public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
		Object target = invocation.getThis();
		Class<?> targetClass = (target != null) ? AopUtils.getTargetClass(target)
				: invocation.getMethod().getDeclaringClass();
		Method method = AopUtils.getMostSpecificMethod(invocation.getMethod(), targetClass);
		Audited audited = AnnotatedElementUtils.findMergedAnnotation(method, Audited.class);
		if (audited == null) {
			return invocation.proceed();
		}
		TransactionAttribute transaction = transactionAttribute(method, targetClass);
		AuditSnapshots<?> snapshots = snapshots(audited.entity(), method);
		IdExpression idExpression = this.expressions.computeIfAbsent(method, (key) -> idExpression(audited.id(), key));
		@Nullable Object[] arguments = invocation.getArguments();
		// id по аргументам — до транзакции: null или не UUID — ошибка применения, метод не выполняется
		@Nullable UUID id = idExpression.fromResult() ? null
				: idExpression.evaluate(method, arguments, null, this.parameterNames);

		PlatformTransactionManager manager = this.transactionManager.getObject();
		TransactionStatus status = manager.getTransaction(transaction);
		Object result;
		try {
			if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
				throw new IllegalStateException("@Audited " + method + ": внешняя транзакция только для чтения");
			}
			Record before = (id != null) ? load(snapshots, id) : null;
			AuditDiff diff = this.diff.getObject();
			Map<String, Value> beforeValues = diff.flatten(before);

			result = invocation.proceed();

			UUID afterId = (id != null) ? id : idExpression.evaluate(method, arguments, result, this.parameterNames);
			@Nullable Record after = load(snapshots, afterId);
			List<Change> changes = diff.compare(beforeValues, diff.flatten(after));
			Record labelled = (after != null) ? after : before;
			// объекта нет ни до, ни после — писать не о чем; оба снимка есть и равны — изменения не было
			if (labelled != null && (before == null || after == null || !changes.isEmpty())) {
				AuditEvent event = AuditEvent.of(audited.entity(), audited.action())
					.entity(afterId, label(snapshots, labelled));
				this.audit.getObject().record(event, changes);
			}
		}
		catch (Throwable ex) {
			if (transaction.rollbackOn(ex)) {
				manager.rollback(status);
			}
			else {
				manager.commit(status);
			}
			throw ex;
		}
		manager.commit(status);
		return result;
	}

	private TransactionAttribute transactionAttribute(Method method, Class<?> targetClass) {
		TransactionAttributeSource source = this.transactionAttributes
			.getIfAvailable(() -> new AnnotationTransactionAttributeSource(false));
		TransactionAttribute attribute = source.getTransactionAttribute(method, targetClass);
		if (attribute == null || attribute.isReadOnly()
				|| attribute.getPropagationBehavior() != TransactionDefinition.PROPAGATION_REQUIRED) {
			throw new IllegalStateException("@Audited " + method + ": метод должен быть @Transactional с "
					+ "распространением REQUIRED и без readOnly, чтобы событие и изменение сохранились вместе");
		}
		return attribute;
	}

	private AuditSnapshots<?> snapshots(String entityType, Method method) {
		Map<String, AuditSnapshots<?>> byType = this.snapshots;
		if (byType == null) {
			Map<String, AuditSnapshots<?>> collected = new HashMap<>();
			this.snapshotBeans.orderedStream().forEach((bean) -> {
				AuditSnapshots<?> previous = collected.put(bean.entityType(), bean);
				if (previous != null) {
					throw new IllegalStateException("Два бина AuditSnapshots для " + bean.entityType() + ": "
							+ previous.getClass().getName() + ", " + bean.getClass().getName());
				}
			});
			byType = Map.copyOf(collected);
			this.snapshots = byType;
		}
		AuditSnapshots<?> found = byType.get(entityType);
		if (found == null) {
			throw new IllegalStateException(
					"@Audited " + method + ": нет бина AuditSnapshots для типа объекта " + entityType);
		}
		return found;
	}

	/**
	 * Разбирает выражение id и проверяет его переменные: каждая — параметр метода по имени ({@code #id}), по номеру
	 * ({@code #a0}, {@code #p0}) или результат ({@code #result}). Иначе SpEL молча дал бы null, и событие потерялось бы:
	 * например, {@code #id} по умолчанию у метода {@code reschedule(UUID appointmentId)}.
	 */
	private IdExpression idExpression(String text, Method method) {
		Expression expression;
		try {
			expression = this.parser.parseExpression(text);
		}
		catch (ParseException ex) {
			throw new IllegalStateException("@Audited " + method + ": выражение id не разобрать: " + text, ex);
		}
		Set<String> variables = new LinkedHashSet<>();
		if (expression instanceof SpelExpression spel) {
			collectVariables(spel.getAST(), variables);
		}
		Set<String> known = new LinkedHashSet<>(BUILT_IN_VARIABLES);
		known.add(RESULT);
		@Nullable String[] names = this.parameterNames.getParameterNames(method);
		List<String> parameters = new ArrayList<>();
		for (int i = 0; i < method.getParameterCount(); i++) {
			known.add("a" + i);
			known.add("p" + i);
			String name = (names != null) ? names[i] : null;
			if (name != null) {
				known.add(name);
				parameters.add(name);
			}
		}
		for (String variable : variables) {
			if (!known.contains(variable)) {
				throw new IllegalStateException("@Audited " + method + ": в выражении id " + text + " переменная #"
						+ variable + ", а у метода параметры " + parameters
						+ ". Укажите id параметром метода или Audited.RESULT_ID");
			}
		}
		return new IdExpression(text, expression, variables.contains(RESULT));
	}

	private static void collectVariables(SpelNode node, Set<String> variables) {
		if (node instanceof VariableReference) {
			variables.add(node.toStringAST().substring(1));
		}
		for (int i = 0; i < node.getChildCount(); i++) {
			collectVariables(node.getChild(i), variables);
		}
	}

	private static @Nullable Record load(AuditSnapshots<?> snapshots, UUID id) {
		Optional<? extends Record> snapshot = snapshots.load(id);
		return snapshot.orElse(null);
	}

	@SuppressWarnings("unchecked")
	private static <S extends Record> @Nullable String label(AuditSnapshots<S> snapshots, Record snapshot) {
		return snapshots.label((S) snapshot);
	}

	/**
	 * Выражение id {@link Audited}.
	 *
	 * @param text выражение как в аннотации
	 * @param expression разобранное выражение
	 * @param fromResult id по результату метода: снимка до нет
	 */
	private record IdExpression(String text, Expression expression, boolean fromResult) {

		/** id объекта; null, не UUID или ошибка вычисления — {@link IllegalStateException}. */
		UUID evaluate(Method method, @Nullable Object[] arguments, @Nullable Object result,
				ParameterNameDiscoverer parameterNames) {
			MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(null, method, arguments,
					parameterNames);
			if (this.fromResult) {
				context.setVariable(RESULT, result);
			}
			Object value;
			try {
				value = this.expression.getValue(context);
			}
			catch (EvaluationException ex) {
				throw new IllegalStateException("@Audited " + method + ": id не вычислить по " + this.text, ex);
			}
			if (value == null) {
				throw new IllegalStateException("@Audited " + method + ": id объекта по " + this.text
						+ " — null, событие не записать. Создание — id = Audited.RESULT_ID, необязательный id — "
						+ "AuditService.record вручную");
			}
			if (value instanceof UUID uuid) {
				return uuid;
			}
			throw new IllegalStateException("@Audited " + method + ": id объекта не UUID: " + value.getClass().getName());
		}

	}

}
