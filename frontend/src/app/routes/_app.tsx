import { createFileRoute, redirect } from '@tanstack/react-router'
import * as z from 'zod/mini'

import { requireSession } from '@/app/guards'
import { AppError } from '@/app/shell/AppError'
import { AppPending } from '@/app/shell/AppPending'
import { AppShell } from '@/app/shell/AppShell'
import { PANELS } from '@/app/shell/panels'

// Общие search-параметры каркаса: открытая боковая панель живёт в URL (usePanel).
// Неизвестная панель (?panel=bogus) — «панель закрыта», параметр уходит из адреса.
// Схемы search-параметров маршрутов — на zod/mini (Standard Schema): они в начальной загрузке,
// а полный zod весит ~20 kB gzip (PLT-22). Полный zod — только в ленивых формах и моках.
const appSearch = z.object({
  panel: z.catch(z.optional(z.enum(PANELS)), undefined),
})

/**
 * Pathless-лейаут всех экранов за входом. Гард сессии: /api/v1/me через TanStack Query;
 * 401 → /login?returnTo=<текущий адрес>; клиника сессии не выбрана (context = null) →
 * /select-clinic?returnTo=<текущий адрес>. Пользователь кладётся в контекст маршрутов (`me`).
 */
export const Route = createFileRoute('/_app')({
  validateSearch: appSearch,
  beforeLoad: async ({ context, location }) => {
    const me = await requireSession({ context, location })
    if (!me.clinic) {
      throw redirect({ to: '/select-clinic', search: { returnTo: location.href } })
    }
    return { me }
  },
  pendingComponent: AppPending,
  errorComponent: AppError,
  component: AppShell,
})
