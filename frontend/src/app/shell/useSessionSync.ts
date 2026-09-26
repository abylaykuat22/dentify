import { type QueryKey, useQueryClient } from '@tanstack/react-query'
import { useRouteContext, useRouter } from '@tanstack/react-router'
import { useEffect } from 'react'

import { ApiError } from '@/shared/api/http'
import { ME_QUERY_KEY } from '@/shared/api/me'
import { useCurrentUser } from '@/shared/api/useCurrentUser'

const isMeKey = (key: QueryKey) => key.length === ME_QUERY_KEY.length && key[0] === ME_QUERY_KEY[0]

/**
 * Держит каркас в согласии с /api/v1/me (контракт спеки: интерфейс перезапрашивает /me при фокусе
 * вкладки и после ответа 403):
 * - 403 на любом запросе или изменении → /me перезапрашивается (права могли отнять);
 * - /me изменился (фокус вкладки, 403) → `router.invalidate()`: гарды разделов и корня
 *   перепроверяются с новыми правами, открытый закрытый раздел сразу показывает «нет доступа».
 *   Неизменившийся ответ остаётся тем же объектом (структурное сравнение Query) — лишних
 *   перезагрузок маршрутов нет, и 403 не зацикливается.
 * 401 (сессия кончилась) здесь не ловится: его видит сам HTTP-клиент на любом запросе, и корень
 * приложения уводит на вход с «Сеанс истёк» (app/sessionExpiry.ts).
 */
export function useSessionSync(): void {
  const queryClient = useQueryClient()
  const router = useRouter()
  const me = useCurrentUser()
  const routeMe = useRouteContext({ from: '/_app', select: (context) => context.me })

  useEffect(() => {
    if (me !== routeMe) void router.invalidate()
  }, [me, routeMe, router])

  useEffect(() => {
    const onApiError = (error: unknown, fromMe: boolean) => {
      if (error instanceof ApiError && error.status === 403 && !fromMe) {
        void queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY })
      }
    }

    const offQueries = queryClient.getQueryCache().subscribe((event) => {
      if (event.type === 'updated' && event.action.type === 'error') {
        onApiError(event.action.error, isMeKey(event.query.queryKey))
      }
    })
    const offMutations = queryClient.getMutationCache().subscribe((event) => {
      if (event.type === 'updated' && event.action.type === 'error') onApiError(event.action.error, false)
    })
    return () => {
      offQueries()
      offMutations()
    }
  }, [queryClient])
}
