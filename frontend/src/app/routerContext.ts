import type { QueryClient } from '@tanstack/react-query'

/** Контекст роутера: доступен в beforeLoad/loader каждого маршрута. */
export interface RouterContext {
  queryClient: QueryClient
}
