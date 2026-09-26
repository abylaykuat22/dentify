import { useSuspenseQuery } from '@tanstack/react-query'

import { type CurrentUser, meQueryOptions } from './me'

// Отдельным файлом, а не в me.ts: me.ts нужен гардам в начальном бандле, а хук (и код
// useSuspenseQuery) — только каркасу, который грузится лениво.
/**
 * Текущий пользователь каркаса — живой, из кэша TanStack Query, а не из контекста маршрута:
 * перезапрос /api/v1/me при фокусе вкладки и после 403 сразу меняет меню, профиль и шапку.
 * Только внутри каркаса: данные уже в кэше (beforeLoad лейаута `_app`), рендер не
 * приостанавливается. Гарды разделов перепроверяет useSessionSync (app/shell).
 */
export function useCurrentUser(): CurrentUser {
  return useSuspenseQuery(meQueryOptions()).data
}
