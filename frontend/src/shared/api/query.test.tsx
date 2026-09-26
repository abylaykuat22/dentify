import { focusManager, QueryClientProvider, QueryObserver } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import { http as route, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { createAppQueryClient } from '@/app/router'

import {
  getListAccountSessionsQueryKey,
  getListAccountSessionsQueryOptions,
  useTerminateAccountSession,
} from './generated/endpoints/account/account'
import { ApiError, NetworkError } from './http'
import { server } from './mocks/node'
import { problems } from './mocks/problems'
import { invalidateApi, LIVE_REFETCH_INTERVAL, live, retryDelay, shouldRetry } from './query'

// Слой данных (PLT-23): повторы только для чтения, «живые» экраны, сброс кэша по ключам.

const SESSIONS = '/api/v1/account/sessions'

/** Счётчик запросов к списку сессий: каждый ответ — `respond(номер)`. */
function countSessions(respond: (call: number) => Response = () => HttpResponse.json([])) {
  const calls = { count: 0 }
  server.use(
    route.get(SESSIONS, () => {
      calls.count += 1
      return respond(calls.count)
    }),
  )
  return calls
}

afterEach(() => {
  vi.useRealTimers()
})

describe('повторы: только чтение и только без ответа по существу', () => {
  const api = (status: number, method = 'GET') => new ApiError({ status, problem: null, method })

  it.each([
    ['сеть, GET', new NetworkError(new TypeError('fetch'), 'GET'), true],
    ['502 GET', api(502), true],
    ['503 GET', api(503), true],
    ['504 GET', api(504), true],
    ['500 GET — ошибка в коде', api(500), false],
    ['404 GET', api(404), false],
    ['429 GET', api(429), false],
    ['401 GET', api(401), false],
    ['сеть, POST', new NetworkError(new TypeError('fetch'), 'POST'), false],
    ['503 POST', api(503, 'POST'), false],
    ['чужое исключение', new TypeError('x'), false],
  ])('%s → %s', (_, error, expected) => {
    expect(shouldRetry(0, error)).toBe(expected)
  })

  it('не больше двух повторов; пауза — Retry-After сервера, иначе 1 с, 2 с … ≤ 30 с', () => {
    expect(shouldRetry(2, api(503))).toBe(false)
    expect(retryDelay(0, api(503))).toBe(1000)
    expect(retryDelay(1, api(503))).toBe(2000)
    expect(retryDelay(10, api(503))).toBe(30_000)
    expect(retryDelay(0, new ApiError({ status: 503, problem: null, retryAfter: 5 }))).toBe(5000)
  })

  it('QueryClient приложения: 503 на чтении повторяется, 404 — нет, изменение — никогда', async () => {
    const queryClient = createAppQueryClient()
    queryClient.setDefaultOptions({ queries: { ...queryClient.getDefaultOptions().queries, retryDelay: 0 } })

    const flaky = countSessions((call) => (call < 3 ? problems.unavailable() : HttpResponse.json([])))
    await expect(queryClient.fetchQuery(getListAccountSessionsQueryOptions())).resolves.toEqual([])
    expect(flaky.count).toBe(3)

    queryClient.clear()
    const missing = countSessions(() => problems.notFound())
    await expect(queryClient.fetchQuery(getListAccountSessionsQueryOptions())).rejects.toMatchObject({ status: 404 })
    expect(missing.count).toBe(1)

    let deletes = 0
    server.use(
      route.delete(`${SESSIONS}/:id`, () => {
        deletes += 1
        return problems.unavailable()
      }),
    )
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
    const { result } = renderHook(() => useTerminateAccountSession(), { wrapper })
    await act(async () => {
      await result.current.mutateAsync({ id: 's-1' }).catch(() => undefined)
    })
    expect(deletes).toBe(1)
  })
})

describe('«живой» экран', () => {
  it('опрашивает сервер раз в 30 с', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const queryClient = createAppQueryClient()
    const calls = countSessions()
    const observer = new QueryObserver(queryClient, { ...getListAccountSessionsQueryOptions(), ...live })
    const off = observer.subscribe(() => {})

    await vi.waitFor(() => expect(calls.count).toBe(1))
    await vi.advanceTimersByTimeAsync(LIVE_REFETCH_INTERVAL)
    await vi.waitFor(() => expect(calls.count).toBe(2))
    await vi.advanceTimersByTimeAsync(LIVE_REFETCH_INTERVAL)
    await vi.waitFor(() => expect(calls.count).toBe(3))
    off()
  })

  it('возврат во вкладку: «живой» — всегда, обычный — только если данные устарели', async () => {
    const queryClient = createAppQueryClient()
    // Фокус слушает смонтированный клиент (в приложении это делает QueryClientProvider).
    queryClient.mount()
    const calls = countSessions()
    const plain = new QueryObserver(queryClient, getListAccountSessionsQueryOptions())
    const offPlain = plain.subscribe(() => {})
    await vi.waitFor(() => expect(calls.count).toBe(1))

    // Данные свежие (staleTime 30 с) — обычный экран фокус не трогает.
    act(() => {
      focusManager.setFocused(false)
      focusManager.setFocused(true)
    })
    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(calls.count).toBe(1)
    offPlain()

    const liveObserver = new QueryObserver(queryClient, { ...getListAccountSessionsQueryOptions(), ...live })
    const offLive = liveObserver.subscribe(() => {})
    act(() => {
      focusManager.setFocused(false)
      focusManager.setFocused(true)
    })
    await vi.waitFor(() => expect(calls.count).toBe(2))
    offLive()

    // Данные устарели — обычный экран при возврате во вкладку их обновляет.
    const stale = new QueryObserver(queryClient, getListAccountSessionsQueryOptions())
    const offStale = stale.subscribe(() => {})
    await queryClient.invalidateQueries({ queryKey: getListAccountSessionsQueryKey(), refetchType: 'none' })
    expect(calls.count).toBe(2)
    act(() => {
      focusManager.setFocused(false)
      focusManager.setFocused(true)
    })
    await vi.waitFor(() => expect(calls.count).toBe(3))
    offStale()
    queryClient.unmount()
  })
})

describe('сброс кэша по ключам', () => {
  it('invalidateApi: ресурс — список с любыми параметрами, карточки и вложенное; соседние пути — нет', async () => {
    const queryClient = createAppQueryClient()
    const keys = {
      list: ['/api/v1/patients', { page: 0, q: 'Тест' }],
      card: ['/api/v1/patients/019a'],
      files: ['/api/v1/patients/019a/files'],
      sibling: ['/api/v1/patients-import'],
      other: ['/api/v1/staff'],
      me: ['me'],
    }
    Object.values(keys).forEach((key) => queryClient.setQueryData(key, {}))

    await invalidateApi(queryClient, '/api/v1/patients')

    const invalidated = Object.fromEntries(
      Object.entries(keys).map(([name, key]) => [name, queryClient.getQueryState(key)?.isInvalidated]),
    )
    expect(invalidated).toEqual({ list: true, card: true, files: true, sibling: false, other: false, me: false })
  })

  it('orval mutationInvalidates: завершили сессию — список сессий устарел', async () => {
    const queryClient = createAppQueryClient()
    queryClient.setQueryData(getListAccountSessionsQueryKey(), [])
    server.use(route.delete(`${SESSIONS}/:id`, () => new HttpResponse(null, { status: 204 })))
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
    const { result } = renderHook(() => useTerminateAccountSession(), { wrapper })

    await act(async () => {
      await result.current.mutateAsync({ id: 's-1' })
    })
    expect(queryClient.getQueryState(getListAccountSessionsQueryKey())?.isInvalidated).toBe(true)
  })
})
