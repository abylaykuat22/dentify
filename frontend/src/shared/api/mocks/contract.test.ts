import { describe, expect, it } from 'vitest'

import { login } from '../generated/endpoints/auth/auth'
import { MeResponse, ProblemDetail } from '../generated/zod/schemas'
import { ApiError } from '../http'
import { personas } from './fixtures'
import { problems } from './problems'

// Моки держат контракт api/openapi.json: фикстуры и ответы-ошибки сверяются со сгенерированными
// zod-схемами (orval, generated/zod). Спека изменилась → `pnpm api:generate` → этот тест покажет, какой
// мок отстал.

describe('контракт моков', () => {
  it.each(Object.entries(personas))('персона %s — MeResponse по спеке', (_, me) => {
    expect(MeResponse.safeParse(me).error).toBeUndefined()
  })

  it.each(
    Object.entries({
      validation: problems.validation([{ field: 'iin', code: 'patient.iin.checksum', message: 'Неверный ИИН.' }], 422),
      unauthorized: problems.unauthorized(),
      forbidden: problems.forbidden(),
      notFound: problems.notFound(),
      versionConflict: problems.versionConflict({ modifiedAt: '2026-09-26T09:05:00Z', modifiedByName: 'Алиева Динара' }),
      businessRule: problems.businessRule(),
      tooManyRequests: problems.tooManyRequests(60),
      unavailable: problems.unavailable(30),
      internal: problems.internal(),
    }),
  )('problems.%s — ProblemDetail по спеке, application/problem+json', async (_, response) => {
    expect(response.headers.get('Content-Type')).toBe('application/problem+json')
    const body: unknown = await response.json()
    expect(ProblemDetail.safeParse(body).error).toBeUndefined()
    expect(body).toMatchObject({ status: response.status })
  })

  it('429 и 503 несут Retry-After', () => {
    expect(problems.tooManyRequests(900).headers.get('Retry-After')).toBe('900')
    expect(problems.unavailable(30).headers.get('Retry-After')).toBe('30')
  })

  it('мок входа отвечает как сервер: пустые поля — 400 с errors[] (@NotBlank, @NotEmpty)', async () => {
    const error = (await login({ login: ' ', password: '', rememberMe: true }).catch((caught: unknown) => caught)) as ApiError
    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(400)
    expect(error.fieldErrors.map(({ field, code }) => [field, code])).toEqual([
      ['login', 'validation.not-blank'],
      ['password', 'validation.not-empty'],
    ])
  })

  it('мок входа: тело не по схеме LoginRequest — 400 с полем', async () => {
    const error = (await login({ login: 'a', password: 'b' } as never).catch((caught: unknown) => caught)) as ApiError
    expect(error.status).toBe(400)
    expect(error.fieldErrors).toEqual([expect.objectContaining({ field: 'rememberMe', code: 'validation.not-null' })])
  })
})
