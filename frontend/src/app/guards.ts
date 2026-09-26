import type { QueryClient } from '@tanstack/react-query'
import { type ParsedLocation, redirect } from '@tanstack/react-router'

import { isUnauthorized } from '@/shared/api/http'
import { type CurrentUser, hasPermission, meQueryOptions } from '@/shared/api/me'
import { pageTitle } from '@/shared/lib/title'

import { sectionById, type SectionId } from './shell/sections'
import { takeStartupSession } from './startup'

/**
 * Гард сессии для beforeLoad: текущий пользователь из /api/v1/me (кэш TanStack Query);
 * 401 — сессии нет → /login?returnTo=<текущий адрес>. Первый гард после запуска берёт /me, который
 * точка входа запросила заранее (app/startup.ts), — без второго запроса.
 */
export async function requireSession({
  context,
  location,
}: {
  context: { queryClient: QueryClient }
  location: ParsedLocation
}): Promise<CurrentUser> {
  try {
    return await (takeStartupSession() ?? context.queryClient.ensureQueryData(meQueryOptions()))
  } catch (error) {
    if (isUnauthorized(error)) {
      throw redirect({ to: '/login', search: { returnTo: location.href } })
    }
    throw error
  }
}

/**
 * Раздел закрыт для роли: ловит errorComponent раздела и показывает «Нет доступа». Это ожидаемая
 * ошибка — React о ней в консоль не пишет (`onCaughtError`, app/errors.ts); остаётся только
 * dev-предупреждение роутера «Error in route match» (см. docs/frontend.md, «Гарды и права»).
 */
export class ForbiddenError extends Error {
  readonly section: SectionId

  constructor(section: SectionId) {
    super(`forbidden: ${section}`)
    this.name = 'ForbiddenError'
    this.section = section
  }
}

interface GuardContext {
  context: { me: CurrentUser }
}

/** beforeLoad раздела: право берётся из /api/v1/me, фронт только проверяет флаг. */
export function guardSection(id: SectionId) {
  const section = sectionById(id)
  return ({ context }: GuardContext): void => {
    if (!hasPermission(context.me, section.permission)) throw new ForbiddenError(id)
  }
}

/**
 * head раздела: закрыт для роли — вкладка браузера «Нет доступа — Dentify». Роутер вызывает
 * head и у маршрута с ошибкой (дальше по дереву не идёт), в остальных случаях заголовок
 * задаёт вкладка раздела.
 */
export function sectionHead({ match }: { match: { error: unknown } }) {
  return match.error instanceof ForbiddenError ? { meta: [{ title: pageTitle('common:titles.forbidden') }] } : {}
}
