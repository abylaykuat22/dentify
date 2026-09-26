import { i18n } from '@/shared/i18n'

import type { ProblemDetail, ProblemFieldError } from './generated/model'

/**
 * Единственный HTTP-клиент фронта — мутатор orval: через него ходят все сгенерированные функции и
 * хуки (`generated/endpoints`). Здесь только транспорт и контракт API (PLT-07, PLT-23), никакой
 * логики экранов:
 * - cookie сессии (`credentials: 'include'`), язык ответа (`Accept-Language` = язык интерфейса);
 * - CSRF: изменяющий запрос несёт `X-XSRF-TOKEN` из cookie `XSRF-TOKEN`; cookie нет — сначала
 *   `GET /api/v1/auth/csrf`; ответ 403 `error.csrf` (токен сменился) — новый токен и один повтор;
 * - не-2xx → `ApiError` с разобранным ProblemDetail (`code`, `traceId`, `errors[]`) и
 *   `Retry-After` у 429; нет ответа → `NetworkError`;
 * - 401 вне `/api/v1/auth/**` и `/api/v1/public/**` → событие `onUnauthorized` (сессия кончилась —
 *   его слушает корень приложения и уводит на вход).
 */

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const CSRF_URL = '/api/v1/auth/csrf'
/** Пути без сессии (контракт /api/v1): их 401 — ответ по существу (неверный пароль), а не конец сессии. */
const PUBLIC_PATHS = ['/api/v1/auth/', '/api/v1/public/']
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

/**
 * Код по статусу, когда ProblemDetail нет (ответил не наш сервер: прокси, балансировщик). Те же коды,
 * что у бэкенда (`ErrorCodes`), поэтому экран и словарь их не различают.
 */
const STATUS_CODES: Record<number, string> = {
  400: 'error.bad-request',
  401: 'error.unauthorized',
  403: 'error.forbidden',
  404: 'error.not-found',
  409: 'error.conflict',
  413: 'error.payload-too-large',
  415: 'error.unsupported-media-type',
  422: 'error.business-rule',
  429: 'error.too-many-requests',
  502: 'error.unavailable',
  503: 'error.unavailable',
  504: 'error.unavailable',
}

export function codeForStatus(status: number): string {
  return STATUS_CODES[status] ?? (status >= 500 ? 'error.internal' : 'error.bad-request')
}

interface ApiErrorInit {
  status: number
  problem: ProblemDetail | null
  method?: string
  url?: string
  retryAfter?: number | null
}

/** Ошибка API: HTTP-статус и ProblemDetail (RFC 9457) с `code`, `traceId`, `errors[]`. */
export class ApiError extends Error {
  readonly status: number
  /** Тело ответа, если это ProblemDetail; иначе null (ответил не наш сервер). */
  readonly problem: ProblemDetail | null
  readonly method: string
  readonly url: string
  /** Через сколько секунд повторить (заголовок `Retry-After` у 429 и 503); null — сервер не сказал. */
  readonly retryAfter: number | null

  constructor({ status, problem, method = 'GET', url = '', retryAfter = null }: ApiErrorInit) {
    super(problem?.detail || `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
    this.method = method.toUpperCase()
    this.url = url
    this.retryAfter = retryAfter
  }

  /** Машинный код ошибки — он же ключ сообщения (`iam.login.invalid`); без ProblemDetail — по статусу. */
  get code(): string {
    return this.problem?.code || codeForStatus(this.status)
  }

  /** Идентификатор запроса в логах сервера: его сообщают поддержке. */
  get traceId(): string | null {
    return this.problem?.traceId ?? null
  }

  /** Ошибки полей (`errors[]`): `field` — путь поля (`iin`, `items[0].quantity`), null — вся форма. */
  get fieldErrors(): ProblemFieldError[] {
    return this.problem?.errors ?? []
  }
}

/** Сеть недоступна: fetch не получил ответа. */
export class NetworkError extends Error {
  readonly method: string
  readonly url: string

  constructor(cause: unknown, method = 'GET', url = '') {
    super('network', { cause })
    this.name = 'NetworkError'
    this.method = method.toUpperCase()
    this.url = url
  }
}

/** Тип ошибки хуков orval (`TError`): любой не-2xx — ApiError с телом ProblemDetail. */
export type ErrorType<E> = ApiError & { problem: E | null }

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

export function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401
}

// --- Разбор ответа --------------------------------------------------------------------------

const isObject = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null
const stringOr = <T>(value: unknown, fallback: T): string | T => (typeof value === 'string' ? value : fallback)

function toFieldErrors(value: unknown): ProblemFieldError[] | null {
  if (!Array.isArray(value)) return null
  return value.filter(isObject).flatMap((item): ProblemFieldError[] =>
    typeof item.code === 'string'
      ? [{ code: item.code, field: stringOr(item.field, null), message: stringOr(item.message, '') }]
      : [],
  )
}

/**
 * Тело ошибки → ProblemDetail. Признак — строковый `code` (есть у каждого ответа нашего сервера);
 * прочие поля берутся, если их тип верный. Разбор мягкий, а не `ProblemDetail.parse` из zod: сервер
 * новее фронта (лишнее поле, другой формат даты) не должен стирать код и ошибки полей.
 */
export function parseProblem(body: unknown, status: number): ProblemDetail | null {
  if (!isObject(body) || typeof body.code !== 'string') return null
  return {
    type: stringOr(body.type, null),
    title: stringOr(body.title, ''),
    status: typeof body.status === 'number' ? body.status : status,
    detail: stringOr(body.detail, ''),
    instance: stringOr(body.instance, null),
    code: body.code,
    traceId: stringOr(body.traceId, null),
    errors: toFieldErrors(body.errors),
    modifiedAt: stringOr(body.modifiedAt, null),
    modifiedBy: stringOr(body.modifiedBy, null),
    modifiedByName: stringOr(body.modifiedByName, null),
  }
}

/** `Retry-After` (RFC 9110): секунды или HTTP-дата → секунды от `now`; нет или мусор — null. */
export function parseRetryAfter(value: string | null, now = Date.now()): number | null {
  if (!value) return null
  const trimmed = value.trim()
  if (/^\d+$/.test(trimmed)) return Number(trimmed)
  const date = Date.parse(trimmed)
  return Number.isNaN(date) ? null : Math.max(0, Math.ceil((date - now) / 1000))
}

function parseBody(text: string): unknown {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

// --- Cookie и CSRF ---------------------------------------------------------------------------

function readCookie(name: string): string | null {
  if (typeof document === 'undefined') return null
  const prefix = `${name}=`
  const part = document.cookie.split('; ').find((item) => item.startsWith(prefix))
  return part ? decodeURIComponent(part.slice(prefix.length)) : null
}

let csrfRequest: Promise<void> | null = null

/**
 * Получить cookie XSRF-TOKEN (`GET /api/v1/auth/csrf`). Параллельные изменения ждут один запрос.
 * Ошибку не бросает: без токена сервер ответит 403 `error.csrf`, и её увидит тот, кто спрашивал.
 */
function fetchCsrfCookie(): Promise<void> {
  csrfRequest ??= fetch(new URL(CSRF_URL, window.location.origin), {
    credentials: 'include',
    headers: { Accept: 'application/json, application/problem+json' },
  })
    .then(
      () => undefined,
      () => undefined,
    )
    .finally(() => {
      csrfRequest = null
    })
  return csrfRequest
}

// --- 401: сессия кончилась ------------------------------------------------------------------

type UnauthorizedListener = (error: ApiError) => void
const unauthorizedListeners = new Set<UnauthorizedListener>()

/**
 * Подписка на 401 защищённого эндпоинта (сессия истекла или завершена). Возвращает отписку.
 * Слушатель — корень приложения (`app/sessionExpiry.ts`): уводит на вход с «Сеанс истёк».
 */
export function onUnauthorized(listener: UnauthorizedListener): () => void {
  unauthorizedListeners.add(listener)
  return () => {
    unauthorizedListeners.delete(listener)
  }
}

const isPublicPath = (pathname: string) => PUBLIC_PATHS.some((prefix) => pathname.startsWith(prefix))

// --- Запрос -----------------------------------------------------------------------------------

function languageHeader(): string {
  return i18n.resolvedLanguage ?? i18n.language ?? 'ru'
}

/**
 * Мутатор orval (сигнатура fetch: путь + RequestInit). Относительный путь резолвится от origin
 * страницы — один код работает в браузере, под Vite-прокси и в jsdom-тестах с MSW.
 */
export async function http<T>(url: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const target = new URL(url, window.location.origin)
  const mutating = !SAFE_METHODS.has(method)

  const send = async (): Promise<Response> => {
    const headers = new Headers(init.headers)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json, application/problem+json')
    if (!headers.has('Accept-Language')) headers.set('Accept-Language', languageHeader())
    if (mutating) {
      if (!readCookie(CSRF_COOKIE)) await fetchCsrfCookie()
      const token = readCookie(CSRF_COOKIE)
      if (token) headers.set(CSRF_HEADER, token)
    }
    try {
      return await fetch(target, { ...init, method, headers, credentials: 'include' })
    } catch (cause) {
      throw new NetworkError(cause, method, target.pathname)
    }
  }

  let response = await send()
  let body = parseBody(await response.text())
  let problem = response.ok ? null : parseProblem(body, response.status)

  // Токен CSRF сменился (новая сессия в другой вкладке, истёк) — один повтор со свежим.
  if (mutating && response.status === 403 && problem?.code === 'error.csrf') {
    await fetchCsrfCookie()
    response = await send()
    body = parseBody(await response.text())
    problem = response.ok ? null : parseProblem(body, response.status)
  }

  if (!response.ok) {
    const error = new ApiError({
      status: response.status,
      problem,
      method,
      url: target.pathname,
      retryAfter: parseRetryAfter(response.headers.get('Retry-After')),
    })
    if (error.status === 401 && !isPublicPath(target.pathname)) {
      unauthorizedListeners.forEach((listener) => listener(error))
    }
    throw error
  }
  return body as T
}
