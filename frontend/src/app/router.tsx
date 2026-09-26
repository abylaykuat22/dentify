import { QueryClient } from '@tanstack/react-query'
import { createRouter, type RouterHistory } from '@tanstack/react-router'

import { retryDelay, shouldRetry } from '@/shared/api/query'

import { routeTree } from './routeTree.gen'

/**
 * Кэш запросов приложения (PLT-23, политика — shared/api/query.ts): повторяется только чтение и только
 * сбой сети или 502–504; изменения — никогда. При возврате во вкладку обновляется устаревшее (старше
 * 30 с); «живые» экраны добавляют опрос раз в 30 с опцией `live`.
 */
export function createAppQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: shouldRetry,
        retryDelay,
        staleTime: 30_000,
        refetchOnWindowFocus: true,
      },
      mutations: {
        retry: false,
      },
    },
  })
}

export interface CreateAppRouterOptions {
  queryClient: QueryClient
  history?: RouterHistory
}

export function createAppRouter({ queryClient, history }: CreateAppRouterOptions) {
  return createRouter({
    routeTree,
    history,
    context: { queryClient },
    defaultPreload: 'intent',
    defaultPreloadStaleTime: 0,
    defaultPendingMs: 200,
    defaultPendingMinMs: 300,
    scrollRestoration: true,
  })
}

export type AppRouter = ReturnType<typeof createAppRouter>

declare module '@tanstack/react-router' {
  interface Register {
    router: AppRouter
  }
}
