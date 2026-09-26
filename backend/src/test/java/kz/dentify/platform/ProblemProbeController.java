package kz.dentify.platform;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kz.dentify.platform.api.BusinessRuleException;
import kz.dentify.platform.api.ConflictException;
import kz.dentify.platform.api.FieldViolation;
import kz.dentify.platform.api.ForbiddenException;
import kz.dentify.platform.api.InvalidRequestException;
import kz.dentify.platform.api.LocalizedText;
import kz.dentify.platform.api.NotFoundException;
import kz.dentify.platform.api.PageQuery;
import kz.dentify.platform.api.PageResponse;
import kz.dentify.platform.api.SortBy;
import kz.dentify.platform.api.TooManyRequestsException;
import kz.dentify.platform.api.UnauthorizedException;
import org.jspecify.annotations.Nullable;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Пробные эндпоинты только для тестов ({@code src/test}): бросают исключения и принимают данные, чтобы проверить
 * формат ошибок ProblemDetail, i18n и параметры страницы. Путь вне {@code /api/**} — в выгружаемую спецификацию
 * {@code api/openapi.json} они не попадают; для контрактного теста есть группа springdoc {@code probes}
 * ({@link ProbeOpenApiGroup}). Top-level класс — контекст остаётся один (см. {@code AbstractIntegrationTest}).
 */
@RestController
@RequestMapping(ProblemProbeController.PATH)
class ProblemProbeController {

	static final String PATH = "/test/problems";

	/** Всего элементов в пробном списке: больше предела страницы. */
	static final int ITEMS = 250;

	private final ValidatedProbeService service;

	ProblemProbeController(ValidatedProbeService service) {
		this.service = service;
	}

	@GetMapping("/unauthorized")
	void unauthorized() {
		throw new UnauthorizedException();
	}

	@GetMapping("/forbidden")
	void forbidden() {
		throw new ForbiddenException();
	}

	@GetMapping("/not-found/{id}")
	void notFound(@PathVariable UUID id) {
		throw new NotFoundException();
	}

	@PostMapping("/conflict")
	void conflict() {
		throw new ConflictException();
	}

	@PostMapping("/version-conflict")
	void versionConflict() {
		throw new ObjectOptimisticLockingFailureException(LocalizedTextProbe.class, UUID.randomUUID());
	}

	/** Нарушение уникального индекса, как его отдаёт драйвер: в сообщении SQL и значение поля — вымышленный ИИН. */
	@PostMapping("/duplicate")
	void duplicate() {
		throw new DataIntegrityViolationException("could not execute statement [insert into patient (iin) values (?)]",
				new SQLException("ERROR: duplicate key value violates unique constraint \"ux_patient_iin\"\n"
						+ "  Detail: Key (iin)=(900101300123) already exists.", "23505"));
	}

	@PostMapping("/business-rule")
	void businessRule() {
		throw new BusinessRuleException("probe.rule.backdated", 30);
	}

	/** Нарушение поля, найденное сервисом, а не Bean Validation. */
	@PostMapping("/invalid")
	void invalid() {
		throw new InvalidRequestException(FieldViolation.of("iin", "probe.iin.format"));
	}

	@GetMapping("/too-many-requests")
	void tooManyRequests() {
		throw new TooManyRequestsException(Duration.ofSeconds(30));
	}

	/** Необработанное исключение с SQL и вымышленным ИИН в сообщении: в ответ не должно попасть ничего из этого. */
	@GetMapping("/failure")
	void failure() {
		throw new IllegalStateException("select * from patient where iin = '900101300123' — probe-failure");
	}

	@PostMapping("/validate")
	ProbeRequest validate(@Valid @RequestBody ProbeRequest request) {
		return request;
	}

	/** Ограничение класса: ошибка всей формы, без поля. */
	@PostMapping("/period")
	PeriodRequest period(@Valid @RequestBody PeriodRequest request) {
		return request;
	}

	/** Проверку делает сервис ({@code @Validated}), а не контроллер. */
	@PostMapping("/service-validate")
	ValidatedProbeService.ProbeCommand serviceValidate(@RequestBody ValidatedProbeService.ProbeCommand command) {
		return this.service.create(command);
	}

	@GetMapping("/service-rename")
	String serviceRename(@RequestParam String name) {
		return this.service.rename(name);
	}

	@GetMapping("/params")
	int params(@RequestParam @Max(100) int count) {
		return count;
	}

	@GetMapping("/page")
	PageResponse<String> page(@SortBy(value = { "name", "createdAt" }, defaultSort = "name") PageQuery query) {
		List<String> all = IntStream.rangeClosed(1, ITEMS).mapToObj((i) -> "item-" + i).toList();
		int from = Math.min(query.page() * query.size(), all.size());
		int to = Math.min(from + query.size(), all.size());
		return PageResponse.of(new PageImpl<>(all.subList(from, to), query.pageable(), all.size()));
	}

	@GetMapping("/page-query")
	PageQueryEcho pageQuery(@SortBy(value = { "name", "createdAt" }, defaultSort = "-createdAt") PageQuery query) {
		return new PageQueryEcho(query.page(), query.size(),
				query.sort().stream().map(ProblemProbeController::order).toList(), query.q());
	}

	@GetMapping("/unsorted")
	PageQueryEcho unsorted(PageQuery query) {
		return pageQuery(query);
	}

	private static String order(Sort.Order order) {
		return (order.isDescending() ? "-" : "") + order.getProperty();
	}

	/** Тело пробной формы: стандартные ограничения, своё сообщение-ключ и вложенный LocalizedText. */
	record ProbeRequest(@NotBlank String name, @Size(min = 2, max = 5) @Nullable String code,
			@Min(0) @Max(150) @Nullable Integer age,
			@Pattern(regexp = "\\d{12}", message = "{probe.iin.format}") @Nullable String iin,
			@Valid @Nullable LocalizedText title, @Nullable LocalDate birthDate, @Nullable BigDecimal price) {
	}

	@ProbePeriod
	record PeriodRequest(@Nullable LocalDate from, @Nullable LocalDate to) {
	}

	record PageQueryEcho(int page, int size, List<String> sort, @Nullable String q) {
	}

}
