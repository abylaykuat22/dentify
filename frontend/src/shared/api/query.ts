import type { QueryClient } from '@tanstack/react-query'

import { ApiError, NetworkError } from './http'

/**
 * Политика слоя данных TanStack Query (PLT-23): повторы, «живые» экраны, сброс кэша по ключам.
 * Сам QueryClient собирает `createAppQueryClient` (app/router.tsx).
 */

/** Сколько раз повторить неудачное чтение. */
export const MAX_RETRIES = 2
/** «Живой» экран (расписание, склад) опрашивает сервер раз в 30 с. */
export const LIVE_REFETCH_INTERVAL = 30_000

/** Сервер не ответил по существу — прокси, балансировщик, перезапуск: такое проходит само. */
const TRANSIENT_STATUSES = new Set([502, 503, 504])

/**
 * Повторять ли запрос: только чтение (GET) и только если ответа по существу не было — сеть или
 * 502/503/504. Любой 4xx — ответ сервера (нет права, не найдено, неверные данные), повтор его не
 * изменит; 500 — ошибка в коде, повтор только размножит её в логах. Изменения не повторяются никогда
 * (`mutations.retry: false`): повтор POST может создать запись дважды.
 */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (failureCount >= MAX_RETRIES) return false
  if (error instanceof NetworkError) return error.method === 'GET'
  if (error instanceof ApiError) return error.method === 'GET' && TRANSIENT_STATUSES.has(error.status)
  return false
}

/** Пауза перед повтором: `Retry-After` сервера (503), иначе 1 с, 2 с … не больше 30 с. */
export function retryDelay(attempt: number, error: unknown): number {
  if (error instanceof ApiError && error.retryAfter !== null) return Math.min(error.retryAfter * 1000, 30_000)
  return Math.min(1000 * 2 ** attempt, 30_000)
}

/**
 * Опции «живого» экрана: опрос раз в 30 с (пока вкладка видна) и перезапрос при каждом возврате во
 * вкладку, даже если данные свежие. Передаются в запрос экрана:
 * `useListAppointments(params, { query: live })` или `useQuery({ ...getXQueryOptions(), ...live })`.
 * Обычные экраны при возврате во вкладку обновляют только устаревшие данные (старше `staleTime`).
 */
export const live = {
  refetchInterval: LIVE_REFETCH_INTERVAL,
  refetchIntervalInBackground: false,
  refetchOnWindowFocus: 'always',
} as const

/**
 * Сбросить кэш ресурса по ключам. Ключ запроса orval — путь (`['/api/v1/patients', params]`,
 * `['/api/v1/patients/<id>']`), поэтому ресурс `/api/v1/patients` сбрасывает список со всеми
 * фильтрами, карточки и вложенное (`/api/v1/patients/<id>/files`), но не `/api/v1/patients-import`.
 * Для пары «мутация → запросы» есть готовое правило orval — `mutationInvalidates` в orval.config.ts;
 * эта функция — для остального (изменение одного модуля задело другой, событие с сервера).
 */
export function invalidateApi(queryClient: QueryClient, ...resources: string[]): Promise<void> {
  return queryClient.invalidateQueries({
    predicate: ({ queryKey }) => {
      const [head] = queryKey
      return typeof head === 'string' && resources.some((path) => head === path || head.startsWith(`${path}/`))
    },
  })
}
