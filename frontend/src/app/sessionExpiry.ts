import { useQueryClient } from '@tanstack/react-query'
import { useRouter } from '@tanstack/react-router'
import { useEffect } from 'react'

import { onUnauthorized } from '@/shared/api/http'
import { ME_QUERY_KEY } from '@/shared/api/me'

/**
 * Сессия кончилась посреди работы (PLT-07: 401 — нет сессии): HTTP-клиент сообщает о 401 на любом
 * защищённом запросе — чтении, изменении, ручном вызове, — и корень приложения уводит на
 * `/login?returnTo=<где был>&reason=expired`; экран входа показывает «Сеанс истёк», а после входа
 * возвращает на тот же адрес.
 *
 * «Кончилась» — только если сессия была: в кэше есть пользователь. Первый заход без сессии (401 от
 * /me в гарде `_app`) — обычный вход без сообщения, туда уводит сам гард. Экран входа при открытии
 * чистит кэш, поэтому запоздалые 401 старых запросов повторно не уводят.
 */
export function useSessionExpiry(): void {
  const router = useRouter()
  const queryClient = useQueryClient()

  useEffect(() => {
    let leaving = false
    return onUnauthorized(() => {
      if (leaving || queryClient.getQueryData(ME_QUERY_KEY) === undefined) return
      const { pathname, href } = router.state.location
      if (pathname === '/login') return
      leaving = true
      void router
        .navigate({ to: '/login', search: { returnTo: href, reason: 'expired' }, replace: true })
        .finally(() => {
          leaving = false
        })
    })
  }, [queryClient, router])
}
