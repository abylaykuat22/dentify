import { queryOptions } from '@tanstack/react-query'

import { getMe } from './generated/endpoints/account/account'
import type { BranchRef, ClinicRef, MeMembership, MeResponse } from './generated/model'

/**
 * Роль и права в ответе /api/v1/me.
 *
 * TODO(T-IAM-06, T-IAM-07 → бэкенд): в `api/openapi.json` их ещё нет — бэкенд добавит поля вместе
 * с каталогом прав, «не ломая ответ» (MeResponse.java). До тех пор расширение заполняют только моки
 * (`shared/api/mocks/fixtures.ts`), живой бэкенд его не присылает. Когда поля появятся в спеке —
 * `pnpm api:generate`, удалить это расширение и поправить только адаптер `toCurrentUser`.
 */
export interface MeRightsExtension {
  role?: { code: string; name: string }
  permissions?: string[]
}

/** Ответ /me, входа и выбора клиники: сгенерированный MeResponse + расширение прав (TODO выше). */
export type MeWithRights = MeResponse & MeRightsExtension

export const PERMISSIONS = [
  'schedule.view',
  'patients.view',
  'staff.view',
  'inventory.view',
  'clinic.view',
  'access.view',
] as const

export type Permission = (typeof PERMISSIONS)[number]

/**
 * Текущий пользователь в терминах фронта. Только простые данные (массивы, а не Set): TanStack
 * Query сравнивает ответы структурно, и неизменившийся /me остаётся тем же объектом — по смене
 * ссылки каркас понимает, что права поменялись (useSessionSync).
 */
export interface CurrentUser {
  id: string
  fullName: string
  /** Инициалы для аватара — от сервера, фронт их не считает. */
  initials: string
  /** Роль в клинике сессии; null — API роль ещё не отдаёт (MeRightsExtension). */
  role: { code: string; name: string } | null
  /** Клиника сессии; null — не выбрана: каркас уводит на выбор клиники. */
  clinic: ClinicRef | null
  /** Филиал сессии; null — не выбран (у клиники несколько филиалов). */
  branch: BranchRef | null
  memberships: MeMembership[]
  /** Известные фронту коды прав: по ним прячутся пункты меню и закрываются разделы. */
  permissions: Permission[]
}

/**
 * Права пользователя. Поля прав в ответе ещё нет (живой бэкенд до T-IAM-07) — фронт разделы не
 * прячет: меню полное, а закрытое всё равно закроет сервер ответом 403 (после него /me
 * перезапрашивается). Поле есть — только известные фронту коды из него.
 */
function permissionsOf(codes: unknown): Permission[] {
  if (!Array.isArray(codes)) return [...PERMISSIONS]
  return PERMISSIONS.filter((permission) => codes.includes(permission))
}

export function toCurrentUser(raw: MeWithRights): CurrentUser {
  const context = raw.context ?? null
  return {
    id: raw.account.id,
    fullName: raw.account.fullName,
    initials: raw.account.initials,
    role: raw.role ?? null,
    clinic: context?.clinic ?? null,
    branch: context?.branch ?? null,
    memberships: raw.memberships,
    permissions: permissionsOf(raw.permissions),
  }
}

export function hasPermission(user: CurrentUser, permission: Permission): boolean {
  return user.permissions.includes(permission)
}

export const ME_QUERY_KEY = ['me'] as const

export const meQueryOptions = () =>
  queryOptions({
    queryKey: ME_QUERY_KEY,
    queryFn: async ({ signal }) => toCurrentUser(await getMe({ signal })),
    staleTime: 5 * 60_000,
    // Спека /api/v1/me: интерфейс перезапрашивает его при фокусе вкладки (и после 403 —
    // useSessionSync). Срабатывает, пока каркас смотрит на запрос (useCurrentUser).
    refetchOnWindowFocus: 'always',
    retry: false,
  })
