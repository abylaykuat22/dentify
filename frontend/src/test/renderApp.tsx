import { type QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { render } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import { onCaughtError } from '@/app/errors'
import { type AppRouter, createAppQueryClient, createAppRouter } from '@/app/router'
import { useLanguageRefresh } from '@/app/useLanguageRefresh'
import type { MeResponse } from '@/shared/api/generated/model'
import type { MeWithRights } from '@/shared/api/me'
import type { MockPersona } from '@/shared/api/mocks/fixtures'
import { personaHandlers } from '@/shared/api/mocks/handlers'
import { server } from '@/shared/api/mocks/node'
import { Toaster } from '@/shared/ui/toast'
import { TooltipProvider } from '@/shared/ui/tooltip'

/**
 * Поднимает приложение в памяти по адресу `path` от имени пользователя: персона мока
 * (`owner`, `assistant`, `multi`, `none` — 401) или готовый ответ /api/v1/me — по схеме спеки
 * (`MeResponse`, без прав) или с расширением прав для особой роли.
 */
export function renderApp(path: string, user: MockPersona | MeResponse | MeWithRights = 'owner') {
  server.use(
    ...(typeof user === 'string' ? personaHandlers(user) : [http.get('/api/v1/me', () => HttpResponse.json(user))]),
  )
  const queryClient = createAppQueryClient()
  const history = createMemoryHistory({ initialEntries: [path] })
  const router = createAppRouter({ queryClient, history })

  const view = render(
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <LanguageRefresh router={router} queryClient={queryClient} />
        <RouterProvider router={router} />
        <Toaster />
      </TooltipProvider>
    </QueryClientProvider>,
    { onCaughtError },
  )

  return { ...view, router, history, queryClient }
}

/** Как в App: смена языка пересчитывает заголовки вкладки и перезапрашивает ответы сервера. */
function LanguageRefresh({ router, queryClient }: { router: AppRouter; queryClient: QueryClient }) {
  useLanguageRefresh(router, queryClient)
  return null
}
