import { HttpResponse } from 'msw'

import type { ProblemDetail, ProblemFieldError } from '../generated/model'

// Ответы-ошибки моков по контракту api/openapi.json (PLT-07): ProblemDetail (RFC 9457) с `code`,
// `traceId`, `errors[]`, у 409 — `modifiedAt`/`modifiedByName`, у 429 — заголовок Retry-After.
// Тексты — как у бэкенда (messages.properties); сверку со схемой держит mocks/contract.test.ts.
// Ими отвечают обработчики demo-моков (handlers.ts) и тесты: `server.use(http.post(…, () =>
// problems.validation([...])))`. Данные вымышленные.

/** Вымышленный идентификатор трассировки (32 hex, как у сервера). */
export const MOCK_TRACE_ID = '0f1e2d3c4b5a69788796a5b4c3d2e1f0'

const TITLES: Record<number, string> = {
  400: 'Некорректный запрос',
  401: 'Требуется вход',
  403: 'Доступ запрещён',
  404: 'Не найдено',
  409: 'Конфликт данных',
  422: 'Действие недопустимо',
  429: 'Слишком много запросов',
  500: 'Внутренняя ошибка',
  503: 'Сервис недоступен',
}

/** Ответ-ошибка: ProblemDetail с `application/problem+json`. */
export function problem(
  status: number,
  code: string,
  detail: string,
  extra: Partial<ProblemDetail> = {},
  headers: Record<string, string> = {},
) {
  const body: ProblemDetail = {
    type: null,
    title: TITLES[status] ?? 'Ошибка',
    status,
    detail,
    code,
    traceId: MOCK_TRACE_ID,
    ...extra,
  }
  return HttpResponse.json(body, {
    status,
    headers: { 'Content-Type': 'application/problem+json', ...headers },
  })
}

export const problems = {
  /** 400 (Bean Validation) или 422 (правило) с ошибками полей. */
  validation: (errors: ProblemFieldError[], status: 400 | 422 = 400, code = 'error.validation') =>
    problem(status, code, 'Проверьте правильность заполнения полей.', { errors }),

  /** 401: сессии нет (`error.unauthorized`) или она кончилась (`error.session-expired`). */
  unauthorized: (code: 'error.unauthorized' | 'error.session-expired' | 'iam.login.invalid' = 'error.session-expired') =>
    problem(
      401,
      code,
      code === 'iam.login.invalid'
        ? 'Неверный телефон, e-mail или пароль.'
        : code === 'error.unauthorized'
          ? 'Войдите в систему, чтобы продолжить.'
          : 'Сеанс завершён. Войдите снова, чтобы продолжить.',
    ),

  forbidden: (code = 'error.forbidden', detail = 'Недостаточно прав для этого действия.') => problem(403, code, detail),

  notFound: () => problem(404, 'error.not-found', 'Запись не найдена или удалена.'),

  /** 409: запись успели изменить — кто и когда (для плашки «Запись уже изменена»). */
  versionConflict: ({ modifiedAt, modifiedByName }: { modifiedAt: string; modifiedByName: string }) =>
    problem(409, 'error.version-conflict', 'Запись уже изменил другой пользователь. Обновите страницу и внесите правки заново.', {
      modifiedAt,
      modifiedBy: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e01',
      modifiedByName,
    }),

  /** 422: нарушено бизнес-правило. */
  businessRule: (code = 'error.business-rule', detail = 'Действие нарушает правила клиники.') => problem(422, code, detail),

  /** 429 с Retry-After (секунды). */
  tooManyRequests: (retryAfter: number, code = 'error.too-many-requests', detail = 'Слишком много запросов. Подождите немного и повторите.') =>
    problem(429, code, detail, {}, { 'Retry-After': String(retryAfter) }),

  /** 503: сервис недоступен (перезапуск, перегрузка); Retry-After — если сервер знает срок. */
  unavailable: (retryAfter?: number) =>
    problem(
      503,
      'error.unavailable',
      'Сервис временно недоступен. Повторите позже.',
      {},
      retryAfter === undefined ? {} : { 'Retry-After': String(retryAfter) },
    ),

  /** 500: только общий текст и traceId. */
  internal: () =>
    problem(500, 'error.internal', 'Что-то пошло не так. Если ошибка повторится, сообщите в поддержку код ошибки.'),
}
