package kz.dentify.platform;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * Пробный сервис только для тестов: проверка методов сервиса ({@code @Validated}) бросает
 * {@code ConstraintViolationException}, и ответ — 400 с путём поля в {@code errors[]}. Контроллер
 * {@link ProblemProbeController} сам ничего не проверяет. Top-level класс — контекст остаётся один.
 */
@Service
@Validated
class ValidatedProbeService {

	/** Ограничение на параметре метода: полем ошибки становится имя параметра. */
	public String rename(@NotBlank String name) {
		return name;
	}

	/** Ограничения внутри объекта-параметра: поле ошибки — путь внутри объекта, без имени метода и параметра. */
	public ProbeCommand create(@Valid ProbeCommand command) {
		return command;
	}

	record ProbeCommand(@NotBlank String name, List<@NotBlank String> tags) {
	}

}
