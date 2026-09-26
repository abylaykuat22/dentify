import { createMemoryHistory } from '@tanstack/react-router'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'

import { owner } from '@/shared/api/mocks/fixtures'
import { server } from '@/shared/api/mocks/node'
import { problems } from '@/shared/api/mocks/problems'

import { createAppQueryClient, createAppRouter } from './router'
import { takeStartupSession, warmUpStartRoute } from './startup'

// PLT-22: /api/v1/me уходит при запуске, параллельно с остальным, и один раз отдаётся гарду.

function start(path: string) {
  let calls = 0
  server.use(
    http.get('/api/v1/me', () => {
      calls += 1
      return HttpResponse.json(owner)
    }),
  )
  const queryClient = createAppQueryClient()
  const router = createAppRouter({ queryClient, history: createMemoryHistory({ initialEntries: [path] }) })
  warmUpStartRoute(router, queryClient, { apiReady: Promise.resolve() })
  return { queryClient, calls: () => calls }
}

afterEach(() => {
  takeStartupSession()
})

describe('запуск: /me заранее', () => {
  it('экран за входом — /me запрошен сразу и отдаётся гарду один раз, дальше — кэш', async () => {
    const { queryClient, calls } = start('/patients?filter=children')

    const session = takeStartupSession()
    expect(session).not.toBeNull()
    await expect(session).resolves.toMatchObject({ fullName: owner.account.fullName })
    expect(takeStartupSession()).toBeNull()
    expect(calls()).toBe(1)
    expect(queryClient.getQueryData(['me'])).toMatchObject({ id: owner.account.id })
  })

  it('вход и 404 вне каркаса — /me при запуске не нужен', () => {
    start('/login')
    expect(takeStartupSession()).toBeNull()
  })

  it('401 при запуске — ошибку получает гард (уведёт на вход), без второго запроса', async () => {
    let calls = 0
    server.use(
      http.get('/api/v1/me', () => {
        calls += 1
        return problems.unauthorized('error.unauthorized')
      }),
    )
    const queryClient = createAppQueryClient()
    const router = createAppRouter({ queryClient, history: createMemoryHistory({ initialEntries: ['/schedule'] }) })
    warmUpStartRoute(router, queryClient, { apiReady: Promise.resolve() })

    await expect(takeStartupSession()).rejects.toMatchObject({ status: 401 })
    expect(calls).toBe(1)
  })
})
