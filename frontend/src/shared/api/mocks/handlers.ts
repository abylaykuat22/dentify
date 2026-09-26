import { http, HttpResponse, type HttpHandler } from 'msw'

import type { ProblemFieldError, SelectContextRequest } from '../generated/model'
import { LoginRequest } from '../generated/zod/schemas'
import type { MeWithRights } from '../me'
import { MOCK_CLINIC_KEY, MOCK_USER_COOKIE, type MockPersona, personas, withContext } from './fixtures'
import { problems } from './problems'

// Моки повторяют контракт api/openapi.json и поведение бэкенда (iam.SignInTests, iam.MeTests,
// platform.security.CsrfTests): пути, коды ответов, схемы (MeResponse, LoginRequest,
// SelectContextRequest, ProblemDetail — mocks/problems.ts) и CSRF. Вымышленная «логика» здесь — только
// чтобы вести себя как сервер в демо и тестах; во фронт она не попадает (R002).

const noSession = () => problems.unauthorized('error.unauthorized')

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const MOCK_CSRF_TOKEN = 'mock-xsrf-token'
/** Сколько раз вход сменил токен: у каждого входа свой (`mock-xsrf-token-<n>`). */
let csrfRotations = 0

/**
 * Cookie CSRF-токена. У сервера это `Set-Cookie`, но cookie из ответов моков MSW держит в своём
 * хранилище, а не в `document.cookie` — мутатор (shared/api/http.ts) её бы не увидел. Поэтому мок
 * пишет её в `document.cookie` сам, как её увидел бы браузер после ответа сервера.
 */
function setCsrfCookie(value: string | null) {
  if (typeof document === 'undefined') return
  document.cookie = value === null ? `${CSRF_COOKIE}=; Max-Age=0; Path=/` : `${CSRF_COOKIE}=${value}; Path=/; SameSite=Lax`
}

/**
 * Вход меняет CSRF-токен, как сервер (`AccountSignIn.signIn` — новая cookie `XSRF-TOKEN` в ответе входа):
 * следующее изменение мутатор шлёт уже с новым, прежний сервер отклонит (403 `error.csrf`).
 */
function rotateCsrfCookie() {
  csrfRotations += 1
  setCsrfCookie(`${MOCK_CSRF_TOKEN}-${csrfRotations}`)
}

/**
 * Изменяющий запрос без заголовка X-XSRF-TOKEN (или с чужим токеном) — 403 `error.csrf`, как у сервера
 * (CSRF проверяется раньше входа). Токен сверяется с cookie, если мок её видит (браузер).
 */
function csrfRejected(request: Request, cookies: Record<string, string>) {
  const header = request.headers.get(CSRF_HEADER)
  const cookie = cookies[CSRF_COOKIE]
  if (header && (cookie === undefined || cookie === header)) return null
  return problems.forbidden('error.csrf', 'Страница устарела. Обновите её и повторите действие.')
}

function personaFrom(cookies: Record<string, string>): MockPersona {
  const value = cookies[MOCK_USER_COOKIE]
  return value === 'assistant' || value === 'multi' || value === 'plain' || value === 'none' ? value : 'owner'
}

interface Selection {
  clinicId: string
  branchId: string | null
}

function selectedContext(): Selection | undefined {
  try {
    const saved = sessionStorage.getItem(MOCK_CLINIC_KEY)
    return saved ? (JSON.parse(saved) as Selection) : undefined
  } catch {
    return undefined
  }
}

function rememberContext(selection: Selection | null) {
  try {
    if (selection) sessionStorage.setItem(MOCK_CLINIC_KEY, JSON.stringify(selection))
    else sessionStorage.removeItem(MOCK_CLINIC_KEY)
  } catch {
    // Хранилище недоступно — мок просто не помнит выбор клиники.
  }
}

/** /me персоны: без клиники в фикстуре (`multi`) — клиника и филиал, выбранные через POST /api/v1/me/context. */
function meOf(persona: Exclude<MockPersona, 'none'>): MeWithRights {
  const me = personas[persona]
  if (me.context) return me
  const selection = selectedContext()
  return (selection && withContext(me, selection.clinicId, selection.branchId)) ?? me
}

/**
 * Обработчики, ответ которых зависит от пользователя: GET /api/v1/me и POST /api/v1/me/context.
 * `cookie` — персона из cookie `dentify_mock_user` (браузер, e2e); тесты передают персону явно.
 */
export function personaHandlers(persona: MockPersona | 'cookie' = 'cookie'): HttpHandler[] {
  const resolve = (cookies: Record<string, string>) => (persona === 'cookie' ? personaFrom(cookies) : persona)

  return [
    http.get('/api/v1/me', ({ cookies }) => {
      const who = resolve(cookies)
      return who === 'none' ? noSession() : HttpResponse.json(meOf(who))
    }),

    // Как AccountContexts.select: нет членства (или только приглашение), чужой или недоступный филиал —
    // 404; членство отключено — 403 iam.membership.disabled.
    http.post('/api/v1/me/context', async ({ request, cookies }) => {
      const rejected = csrfRejected(request, cookies)
      if (rejected) return rejected
      const who = resolve(cookies)
      if (who === 'none') return noSession()
      const body = (await request.json().catch(() => ({}))) as Partial<SelectContextRequest>
      const me = personas[who]
      const membership = me.memberships.find((item) => item.clinic.id === body.clinicId && item.status !== 'INVITED')
      if (!membership) return problems.notFound()
      if (membership.status === 'DISABLED') {
        return problems.forbidden(
          'iam.membership.disabled',
          'Доступ к этой клинике отключён. Обратитесь к владельцу клиники.',
        )
      }
      const selected = withContext(me, membership.clinic.id, body.branchId ?? null)
      if (!selected) return problems.notFound()
      rememberContext({ clinicId: membership.clinic.id, branchId: selected.context?.branch?.id ?? null })
      return HttpResponse.json(selected)
    }),
  ]
}

/** Логин, по которому мок отвечает не входом, а ошибкой сервера (демо и e2e; подсказка — на экране входа). */
function loginFailure(login: string) {
  if (login.includes('limit')) {
    return problems.tooManyRequests(900, 'error.too-many-login-attempts', 'Слишком много попыток, попробуйте через 15 мин.')
  }
  // Предел одновременных проверок пароля на сервере: пароль не проверен, Retry-After — секунда.
  if (login.includes('busy')) return problems.tooManyRequests(1)
  if (login.includes('disabled')) {
    return problems.forbidden('iam.login.access-disabled', 'Доступ отключён. Обратитесь к владельцу клиники.')
  }
  if (login.includes('boom')) return problems.internal()
  // Нет ответа (сеть оборвалась) — fetch падает, клиент показывает «Нет связи с сервером».
  if (login.includes('offline')) return HttpResponse.error()
  return null
}

export const handlers: HttpHandler[] = [
  ...personaHandlers(),

  http.get('/api/v1/auth/csrf', () => {
    setCsrfCookie(MOCK_CSRF_TOKEN)
    return new HttpResponse(null, { status: 204 })
  }),

  // Вход как у сервера (LoginRequest: @NotBlank @Size(max=320) login, @NotEmpty @Size(max=1024)
  // password, @NotNull rememberMe → 400 с errors[]): любой пароль, кроме `wrong` (401
  // iam.login.invalid); логин со словом `assistant` — ассистент, `multi` — врач двух клиник (context:
  // null → выбор клиники); ошибки сервера — loginFailure. Ответ 200 — MeResponse.
  http.post('/api/v1/auth/login', async ({ request, cookies }) => {
    const rejected = csrfRejected(request, cookies)
    if (rejected) return rejected
    const parsed = LoginRequest.safeParse(await request.json().catch(() => null))
    if (!parsed.success) {
      const errors = parsed.error.issues.map(
        (issue): ProblemFieldError => ({
          field: issue.path.join('.') || null,
          code: issue.code === 'invalid_type' && issue.input === undefined ? 'validation.not-null' : 'validation.invalid',
          message: issue.input === undefined ? 'Заполните поле.' : 'Неверное значение.',
        }),
      )
      return problems.validation(errors)
    }
    const body = parsed.data
    const invalid: ProblemFieldError[] = [
      ...(!body.login.trim()
        ? [{ field: 'login', code: 'validation.not-blank', message: 'Заполните поле.' }]
        : body.login.length > 320
          ? [{ field: 'login', code: 'validation.size', message: 'Допустимая длина — от 0 до 320.' }]
          : []),
      ...(!body.password
        ? [{ field: 'password', code: 'validation.not-empty', message: 'Заполните поле.' }]
        : body.password.length > 1024
          ? [{ field: 'password', code: 'validation.size', message: 'Допустимая длина — от 0 до 1024.' }]
          : []),
    ]
    if (invalid.length > 0) return problems.validation(invalid)
    const failure = loginFailure(body.login)
    if (failure) return failure
    if (body.password === 'wrong') return problems.unauthorized('iam.login.invalid')
    const persona = body.login.includes('assistant') ? 'assistant' : body.login.includes('multi') ? 'multi' : 'owner'
    rememberContext(null)
    rotateCsrfCookie()
    return HttpResponse.json(personas[persona], {
      headers: { 'Set-Cookie': `${MOCK_USER_COOKIE}=${persona}; Path=/; SameSite=Lax` },
    })
  }),

  // Выход как у сервера: 204 и без сессии; cookie сессии и CSRF-токен стёрты.
  http.post('/api/v1/auth/logout', ({ request, cookies }) => {
    const rejected = csrfRejected(request, cookies)
    if (rejected) return rejected
    rememberContext(null)
    setCsrfCookie(null)
    return new HttpResponse(null, {
      status: 204,
      headers: { 'Set-Cookie': `${MOCK_USER_COOKIE}=none; Path=/; SameSite=Lax` },
    })
  }),
]
