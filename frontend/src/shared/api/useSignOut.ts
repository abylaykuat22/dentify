import { useQueryClient } from '@tanstack/react-query'
import { useRouter } from '@tanstack/react-router'

import { useLogout } from './generated/endpoints/auth/auth'

/**
 * «Выйти» (IAM-03): POST /api/v1/auth/logout (CSRF-токен мутатор берёт сам) → экран входа → кэш
 * TanStack Query очищен. Сессию и cookie завершает сервер (R002); кэш чистится после перехода, когда
 * кабинет уже размонтирован (иначе его экраны перезапросили бы данные без сессии), — и ещё раз самим
 * экраном входа при открытии. Поэтому «Назад» браузера после выхода данных кабинета не покажет: гард
 * заново спросит /me и получит 401. Ошибка — `failed`, пользователь остаётся на месте и может повторить.
 * Общий для меню пользователя в сайдбаре, меню «Ещё» и выбора клиники.
 */
export function useSignOut() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const logout = useLogout({
    mutation: {
      onSuccess: async () => {
        await router.navigate({ to: '/login', replace: true })
        queryClient.clear()
      },
    },
  })

  return {
    /** Выйти; `false` — не получилось (сеть, сбой), см. `failed`. */
    signOut: () => logout.mutateAsync().then(
      () => true,
      () => false,
    ),
    // Пока идёт переход на вход, кнопка остаётся нажатой: success, но экран ещё не сменился.
    pending: logout.isPending || logout.isSuccess,
    failed: logout.isError,
  }
}
