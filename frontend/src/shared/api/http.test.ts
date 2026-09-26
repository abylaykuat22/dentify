import { http as route, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/shared/i18n'

import { getMe, terminateAccountSession } from './generated/endpoints/account/account'
import { login } from './generated/endpoints/auth/auth'
import { ApiError, codeForStatus, http, NetworkError, onUnauthorized, parseProblem, parseRetryAfter } from './http'
import { owner } from './mocks/fixtures'
import { server } from './mocks/node'
import { MOCK_TRACE_ID, problems } from './mocks/problems'

// Мутатор orval (shared/api/http.ts): транспорт и контракт ошибок API (PLT-07, PLT-23). Запросы идут
// через сгенерированные функции — так проверяется и то, что orval вызывает именно этот мутатор.

const SESSION = '/api/v1/account/sessions/:id'

function setCookie(value: string) {
  document.cookie = `${value}; Path=/`
}

afterEach(() => {
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
})

/** Запомнить запросы к пути: заголовки, режим cookie, метод. */
function capture(path: string, method: 'get' | 'post' | 'delete', respond: () => Response = () => new HttpResponse(null, { status: 204 })) {
  const seen: Request[] = []
  server.use(
    route[method](path, ({ request }) => {
      seen.push(request.clone())
      return respond()
    }),
  )
  return seen
}

describe('запрос', () => {
  it('cookie сессии (credentials: include), Accept и язык интерфейса в Accept-Language', async () => {
    const seen = capture('/api/v1/me', 'get', () => HttpResponse.json(owner))
    await expect(getMe()).resolves.toEqual(owner)

    const [request] = seen
    expect(request?.credentials).toBe('include')
    expect(request?.headers.get('Accept')).toBe('application/json, application/problem+json')
    expect(request?.headers.get('Accept-Language')).toBe(i18n.resolvedLanguage)
    expect(request?.headers.get('X-XSRF-TOKEN')).toBeNull()
  })

  it('204 без тела — null', async () => {
    capture(SESSION, 'delete')
    setCookie('XSRF-TOKEN=token-1')
    await expect(terminateAccountSession('s-1')).resolves.toBeNull()
  })
})

describe('CSRF', () => {
  it('изменяющий запрос несёт X-XSRF-TOKEN из cookie XSRF-TOKEN', async () => {
    setCookie('XSRF-TOKEN=token-1')
    const csrf = capture('/api/v1/auth/csrf', 'get')
    const seen = capture(SESSION, 'delete')

    await terminateAccountSession('s-1')

    expect(seen[0]?.headers.get('X-XSRF-TOKEN')).toBe('token-1')
    expect(csrf).toHaveLength(0)
  })

  it('cookie нет — сначала GET /api/v1/auth/csrf, параллельные изменения ждут один запрос', async () => {
    const csrf = capture('/api/v1/auth/csrf', 'get', () => {
      setCookie('XSRF-TOKEN=fresh')
      return new HttpResponse(null, { status: 204 })
    })
    const seen = capture(SESSION, 'delete')

    await Promise.all([terminateAccountSession('s-1'), terminateAccountSession('s-2')])

    expect(csrf).toHaveLength(1)
    expect(seen.map((request) => request.headers.get('X-XSRF-TOKEN'))).toEqual(['fresh', 'fresh'])
  })

  it('403 error.csrf — новый токен и один повтор; снова 403 — ошибка', async () => {
    setCookie('XSRF-TOKEN=stale')
    const csrf = capture('/api/v1/auth/csrf', 'get', () => {
      setCookie('XSRF-TOKEN=renewed')
      return new HttpResponse(null, { status: 204 })
    })
    let calls = 0
    server.use(
      route.delete(SESSION, ({ request }) => {
        calls += 1
        return request.headers.get('X-XSRF-TOKEN') === 'renewed'
          ? new HttpResponse(null, { status: 204 })
          : problems.forbidden('error.csrf', 'Страница устарела.')
      }),
    )
    await expect(terminateAccountSession('s-1')).resolves.toBeNull()
    expect(calls).toBe(2)
    expect(csrf).toHaveLength(1)

    server.use(route.delete(SESSION, () => problems.forbidden('error.csrf', 'Страница устарела.')))
    await expect(terminateAccountSession('s-1')).rejects.toMatchObject({ status: 403, code: 'error.csrf' })
  })
})

describe('ошибки → ApiError', () => {
  it('ProblemDetail: код, traceId, ошибки полей, метод и путь', async () => {
    server.use(
      route.post('/api/v1/auth/login', () =>
        problems.validation([{ field: 'login', code: 'validation.not-blank', message: 'Заполните поле.' }], 422),
      ),
    )
    const error = await login({ login: '', password: '', rememberMe: true }).catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError).toMatchObject({ status: 422, code: 'error.validation', traceId: MOCK_TRACE_ID, method: 'POST' })
    expect(apiError.url).toBe('/api/v1/auth/login')
    expect(apiError.fieldErrors).toEqual([{ field: 'login', code: 'validation.not-blank', message: 'Заполните поле.' }])
    expect(apiError.message).toBe('Проверьте правильность заполнения полей.')
    expect(apiError.retryAfter).toBeNull()
  })

  it('429: Retry-After в секундах — в ошибке', async () => {
    server.use(route.post('/api/v1/auth/login', () => problems.tooManyRequests(900, 'error.too-many-login-attempts')))
    await expect(login({ login: 'a', password: 'b', rememberMe: true })).rejects.toMatchObject({
      status: 429,
      code: 'error.too-many-login-attempts',
      retryAfter: 900,
    })
  })

  it('ответ не нашего сервера (HTML прокси) — без ProblemDetail, код по статусу', async () => {
    server.use(route.get('/api/v1/me', () => new HttpResponse('<html>Bad Gateway</html>', { status: 502 })))
    const error = (await getMe().catch((caught: unknown) => caught)) as ApiError
    expect(error.problem).toBeNull()
    expect(error.code).toBe('error.unavailable')
    expect(error.traceId).toBeNull()
    expect(error.fieldErrors).toEqual([])
  })

  it('нет ответа — NetworkError с методом и путём', async () => {
    server.use(route.get('/api/v1/me', () => HttpResponse.error()))
    const error = await getMe().catch((caught: unknown) => caught)
    expect(error).toBeInstanceOf(NetworkError)
    expect(error).toMatchObject({ method: 'GET', url: '/api/v1/me' })
  })
})

describe('401 — событие для роутера', () => {
  it('защищённый эндпоинт: слушатели получают ошибку; после отписки — нет', async () => {
    const listener = vi.fn()
    const off = onUnauthorized(listener)
    server.use(route.get('/api/v1/me', () => problems.unauthorized('error.session-expired')))

    await expect(getMe()).rejects.toMatchObject({ status: 401, code: 'error.session-expired' })
    expect(listener).toHaveBeenCalledOnce()
    expect(listener.mock.calls[0]?.[0]).toBeInstanceOf(ApiError)

    off()
    await getMe().catch(() => undefined)
    expect(listener).toHaveBeenCalledOnce()
  })

  it('/api/v1/auth/**: 401 — ответ по существу (неверный пароль), не конец сессии', async () => {
    const listener = vi.fn()
    const off = onUnauthorized(listener)
    server.use(route.post('/api/v1/auth/login', () => problems.unauthorized('iam.login.invalid')))

    await expect(login({ login: 'a', password: 'wrong', rememberMe: true })).rejects.toMatchObject({
      code: 'iam.login.invalid',
    })
    expect(listener).not.toHaveBeenCalled()
    off()
  })
})

describe('разбор', () => {
  it('parseProblem: мягкий — поля неверного типа отбрасываются, код и ошибки полей остаются', () => {
    const problem = parseProblem(
      {
        code: 'patient.iin.checksum',
        status: 400,
        detail: 'ИИН не прошёл проверку.',
        traceId: 42,
        modifiedAt: '2026-10-01 06:38',
        errors: [{ field: 'iin', code: 'iin.checksum', message: 'Неверный ИИН.' }, { field: 'x' }, 'мусор'],
        extra: 'новое поле сервера',
      },
      400,
    )
    expect(problem).toMatchObject({ code: 'patient.iin.checksum', traceId: null, modifiedAt: '2026-10-01 06:38' })
    expect(problem?.errors).toEqual([{ field: 'iin', code: 'iin.checksum', message: 'Неверный ИИН.' }])
    expect(parseProblem({ title: 'Bad Gateway' }, 502)).toBeNull()
    expect(parseProblem(null, 500)).toBeNull()
  })

  it('parseRetryAfter: секунды и HTTP-дата', () => {
    const now = Date.parse('2026-09-26T10:00:00Z')
    expect(parseRetryAfter('120', now)).toBe(120)
    expect(parseRetryAfter('Sat, 26 Sep 2026 10:01:30 GMT', now)).toBe(90)
    expect(parseRetryAfter('Sat, 26 Sep 2026 09:00:00 GMT', now)).toBe(0)
    expect(parseRetryAfter('скоро', now)).toBeNull()
    expect(parseRetryAfter(null, now)).toBeNull()
  })

  it('код по статусу — те же коды, что у сервера', () => {
    expect(codeForStatus(404)).toBe('error.not-found')
    expect(codeForStatus(503)).toBe('error.unavailable')
    expect(codeForStatus(500)).toBe('error.internal')
    expect(codeForStatus(418)).toBe('error.bad-request')
  })

  it('http() напрямую: JSON-ответ как есть', async () => {
    server.use(route.get('/api/v1/me', () => HttpResponse.json({ ok: true })))
    await expect(http<{ ok: boolean }>('/api/v1/me')).resolves.toEqual({ ok: true })
  })
})
