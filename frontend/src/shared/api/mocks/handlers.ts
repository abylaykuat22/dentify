import { http, HttpResponse, type HttpHandler } from 'msw'

import type { ProblemFieldError, SelectContextRequest } from '../generated/model'
import { LoginRequest } from '../generated/zod/schemas'
import type { MeWithRights } from '../me'
import { MOCK_CLINIC_KEY, MOCK_USER_COOKIE, type MockPersona, personas, withContext } from './fixtures'
import { problems } from './problems'

// Моки повторяют контракт api/openapi.json: пути, коды ответов и схемы (MeResponse, LoginRequest,
// SelectContextRequest, ProblemDetail — mocks/problems.ts). Вымышленная «логика» здесь — только
// чтобы вести себя как сервер в демо и тестах; во фронт она не попадает (R002).

const noSession = () => problems.unauthorized('error.unauthorized')

function personaFrom(cookies: Record<string, string>): MockPersona {
  const value = cookies[MOCK_USER_COOKIE]
  return value === 'assistant' || value === 'multi' || value === 'plain' || value === 'none' ? value : 'owner'
}

function selectedClinic(): string | undefined {
  try {
    return sessionStorage.getItem(MOCK_CLINIC_KEY) ?? undefined
  } catch {
    return undefined
  }
}

function rememberClinic(clinicId: string | null) {
  try {
    if (clinicId) sessionStorage.setItem(MOCK_CLINIC_KEY, clinicId)
    else sessionStorage.removeItem(MOCK_CLINIC_KEY)
  } catch {
    // Хранилище недоступно — мок просто не помнит выбор клиники.
  }
}

/** /me персоны: без клиники в фикстуре (`multi`) — клиника, выбранная через POST /me/context. */
function meOf(persona: Exclude<MockPersona, 'none'>): MeWithRights {
  const me = personas[persona]
  if (me.context) return me
  return withContext(me, selectedClinic()) ?? me
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

    http.post('/api/v1/me/context', async ({ request, cookies }) => {
      const who = resolve(cookies)
      if (who === 'none') return noSession()
      const body = (await request.json().catch(() => ({}))) as Partial<SelectContextRequest>
      const me = personas[who]
      if (!me.memberships.some((item) => item.clinic.id === body.clinicId)) {
        return problems.notFound()
      }
      const selected = withContext(me, body.clinicId)
      if (!selected) {
        return problems.forbidden(
          'iam.membership.disabled',
          'Доступ к этой клинике отключён. Обратитесь к владельцу клиники.',
        )
      }
      rememberClinic(body.clinicId ?? null)
      return HttpResponse.json(selected)
    }),
  ]
}

export const handlers: HttpHandler[] = [
  ...personaHandlers(),

  http.get('/api/v1/auth/csrf', () => {
    return new HttpResponse(null, {
      status: 204,
      headers: { 'Set-Cookie': 'XSRF-TOKEN=mock-xsrf-token; Path=/; SameSite=Lax' },
    })
  }),

  // Вход как у сервера (LoginRequest: @NotBlank login, @NotEmpty password, @NotNull rememberMe →
  // 400 с errors[]): любой пароль, кроме `wrong` (401); логин со словом `assistant` — ассистент,
  // `multi` — врач двух клиник (context: null → выбор клиники), `limit` — лимит попыток (429 с
  // Retry-After), `boom` — сбой сервера (500 с traceId). Ответ 200 — MeResponse.
  http.post('/api/v1/auth/login', async ({ request }) => {
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
    const blank: ProblemFieldError[] = [
      ...(body.login.trim() ? [] : [{ field: 'login', code: 'validation.not-blank', message: 'Заполните поле.' }]),
      ...(body.password ? [] : [{ field: 'password', code: 'validation.not-empty', message: 'Заполните поле.' }]),
    ]
    if (blank.length > 0) return problems.validation(blank)
    if (body.login.includes('limit')) {
      return problems.tooManyRequests(900, 'error.too-many-login-attempts', 'Слишком много попыток, попробуйте через 15 мин.')
    }
    if (body.login.includes('boom')) return problems.internal()
    if (body.password === 'wrong') return problems.unauthorized('iam.login.invalid')
    const persona = body.login.includes('assistant') ? 'assistant' : body.login.includes('multi') ? 'multi' : 'owner'
    rememberClinic(null)
    return HttpResponse.json(personas[persona], {
      headers: { 'Set-Cookie': `${MOCK_USER_COOKIE}=${persona}; Path=/; SameSite=Lax` },
    })
  }),

  http.post('/api/v1/auth/logout', () => {
    rememberClinic(null)
    return new HttpResponse(null, {
      status: 204,
      headers: { 'Set-Cookie': `${MOCK_USER_COOKIE}=none; Path=/; SameSite=Lax` },
    })
  }),
]
