import type { QueryClient } from '@tanstack/react-query'

import { type CurrentUser, meQueryOptions } from '@/shared/api/me'

import type { AppRouter } from './router'
import { SECTIONS } from './shell/sections'

/**
 * Первый экран без водопада (PLT-22: LCP ≤ 2.5 с на телефоне). Без этого цепочка последовательная:
 * начальный JS → рендер → гард `_app` просит /api/v1/me → только после ответа роутер тянет чанки
 * каркаса и экрана → шрифты. Здесь, сразу после запуска точки входа и параллельно со стартом моков и
 * языка, уходят и /me, и чанки маршрутов стартового адреса; гард потом берёт уже летящий /me.
 */

/** Маршруты, за которыми гард сессии: им нужен /me ещё до первого экрана. */
const SESSION_ROUTES = new Set(['/_app', '/select-clinic'])
/** Корень каркаса: `/` уводит на первый доступный раздел (homePathFor) — обычно «Расписание». */
const APP_INDEX = '/_app/'

let startupSession: Promise<CurrentUser> | null = null

export interface WarmUpOptions {
  /** Когда можно ходить в API: в режиме моков — после старта MSW, иначе сразу. */
  apiReady: Promise<unknown>
}

export function warmUpStartRoute(router: AppRouter, queryClient: QueryClient, { apiReady }: WarmUpOptions): void {
  const { pathname } = router.latestLocation
  const [routes] = router.getMatchedRoutes(pathname)
  const ids = new Set(routes.map((route) => route.id))

  if ([...SESSION_ROUTES].some((id) => ids.has(id))) {
    startupSession = apiReady.then(() => queryClient.fetchQuery(meQueryOptions()))
    // Ошибку (401 → вход) обработает гард, забрав этот запрос; здесь — только не шуметь в консоли.
    startupSession.catch(() => undefined)
  }

  // Корень: куда он уведёт, решит /me (права), но чаще всего это первый раздел меню — его чанки
  // берём заранее. Не угадали — лишний чанк из кэша браузера, не ошибка.
  const home = ids.has(APP_INDEX) ? router.getMatchedRoutes(SECTIONS[0]?.to ?? '/')[0] : []
  for (const route of [...routes, ...home]) {
    void router.loadRouteChunk(route)?.catch(() => undefined)
  }
}

/**
 * /me, запрошенный при запуске, — один раз, первому гарду сессии. Дальше гард читает кэш TanStack
 * Query как обычно. Без передачи из рук в руки гард при 401 повторил бы запрос: ensureQueryData
 * без данных в кэше запрашивает заново.
 */
export function takeStartupSession(): Promise<CurrentUser> | null {
  const session = startupSession
  startupSession = null
  return session
}
