import '@/styles/fonts'
import '@/styles/index.css'

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { App } from '@/app/App'
import { onCaughtError } from '@/app/errors'
import { createAppQueryClient, createAppRouter } from '@/app/router'
import { warmUpStartRoute } from '@/app/startup'
import { i18nReady } from '@/shared/i18n'

async function enableMocks() {
  if (import.meta.env.VITE_API_MOCK !== 'true') return
  const { worker } = await import('@/shared/api/mocks/browser')
  await worker.start({ onUnhandledRequest: 'bypass', quiet: true })
}

const queryClient = createAppQueryClient()
const router = createAppRouter({ queryClient })
const mocksReady = enableMocks()
// /api/v1/me и чанки стартового маршрута — сразу, параллельно с моками и языком (PLT-22, app/startup.ts).
warmUpStartRoute(router, queryClient, { apiReady: mocksReady })

// Первый экран — после моков и языка интерфейса (сохранённый выбор догружает словарь до показа).
void Promise.all([mocksReady, i18nReady]).then(() => {
  createRoot(document.getElementById('root')!, { onCaughtError }).render(
    <StrictMode>
      <App router={router} queryClient={queryClient} />
    </StrictMode>,
  )
})
