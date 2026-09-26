import type { QueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'

import { i18n } from '@/shared/i18n'

import type { AppRouter } from './router'

/**
 * Смена языка без правок экранов (PLT-11): компоненты с `useTranslation` перерисуются сами, а здесь —
 * то, что посчитано вне React: заголовки вкладки (`head` маршрутов) пересчитываются, а тексты с
 * сервера (он отвечает на языке из `Accept-Language`) перезапрашиваются.
 */
export function useLanguageRefresh(router: AppRouter, queryClient: QueryClient): void {
  useEffect(() => {
    const refresh = () => {
      void router.invalidate()
      void queryClient.invalidateQueries()
    }
    i18n.on('languageChanged', refresh)
    return () => i18n.off('languageChanged', refresh)
  }, [router, queryClient])
}
