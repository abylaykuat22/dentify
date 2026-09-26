import type { QueryClient } from '@tanstack/react-query'
import { QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from '@tanstack/react-router'
import { lazy, Suspense, useEffect, useState } from 'react'

import { TooltipProvider } from '@/shared/ui/tooltip'

import type { AppRouter } from './router'
import { whenIdle } from './idle'
import { useLanguageRefresh } from './useLanguageRefresh'

// Место уведомлений грузится отдельным чанком в простое после первого экрана: ни в бюджет
// начальной загрузки, ни в цепочку до первого экрана (PLT-22, LCP) оно не входит. Показывают
// уведомления через общий менеджер `toast` / `toastUndo` из @/shared/ui/toast — оборачивать
// приложение провайдером не нужно; вызванные до загрузки чанка уведомления менеджер копит и
// показывает, как только Toaster появится.
const Toaster = lazy(() => import('@/shared/ui/toast').then((module) => ({ default: module.Toaster })))

interface AppProps {
  router: AppRouter
  queryClient: QueryClient
}

/**
 * Провайдеры приложения. i18n инициализируется импортом `@/shared/i18n`; язык интерфейса точка входа
 * восстанавливает до первого экрана (`i18nReady`), его смену подхватывает `useLanguageRefresh`.
 */
export function App({ router, queryClient }: AppProps) {
  useLanguageRefresh(router, queryClient)
  const [idle, setIdle] = useState(false)
  useEffect(() => whenIdle(() => setIdle(true)), [])
  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider delay={300}>
        <RouterProvider router={router} />
        {idle ? (
          <Suspense fallback={null}>
            <Toaster />
          </Suspense>
        ) : null}
      </TooltipProvider>
    </QueryClientProvider>
  )
}

