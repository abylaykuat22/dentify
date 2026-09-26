import { useRouter } from '@tanstack/react-router'

import { useLogout } from './generated/endpoints/auth/auth'

/**
 * «Выйти»: POST /api/v1/auth/logout (CSRF-токен мутатор берёт сам), затем экран входа. Сессию
 * завершает сервер (R002); кэш прошлой сессии очищает сам экран входа при открытии. Ошибка —
 * `failed`, пользователь остаётся на месте и может повторить.
 * Общий для выбора клиники и меню «Ещё» (TODO(T-IAM-03): и меню пользователя в сайдбаре).
 */
export function useSignOut() {
  const router = useRouter()
  const logout = useLogout({
    mutation: {
      onSuccess: () => router.navigate({ to: '/login', replace: true }),
    },
  })

  return {
    signOut: () => logout.mutateAsync().catch(() => undefined),
    // Пока идёт переход на вход, кнопка остаётся нажатой: success, но экран ещё не сменился.
    pending: logout.isPending || logout.isSuccess,
    failed: logout.isError,
  }
}
