import { type ErrorComponentProps, useRouter } from '@tanstack/react-router'

import { StateScreen } from '@/shared/ui/StateScreen'

/** Ошибка загрузки каркаса (не 401): /api/v1/me недоступен — «нет связи» и повтор. */
export function AppError({ reset }: ErrorComponentProps) {
  const router = useRouter()
  return (
    <div className="flex min-h-dvh items-center justify-center p-4">
      <StateScreen
        kind="error"
        className="w-full max-w-xl"
        onRetry={() => {
          reset()
          void router.invalidate()
        }}
      />
    </div>
  )
}
