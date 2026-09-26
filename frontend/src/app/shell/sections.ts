import {
  Building2,
  CalendarDays,
  CalendarPlus,
  FilePlus2,
  type LucideIcon,
  Package,
  Send,
  ShieldCheck,
  Stethoscope,
  UserPlus,
  UserRoundPlus,
  Users,
} from 'lucide-react'

import type { FileRoutesByTo } from '@/app/routeTree.gen'
import { type CurrentUser, hasPermission, type Permission } from '@/shared/api/me'
import type { TKey } from '@/shared/i18n/keys'

/** Путь, известный роутеру (из сгенерированного дерева). */
export type AppPath = keyof FileRoutesByTo

export type SectionId = 'schedule' | 'patients' | 'staff' | 'inventory' | 'clinic' | 'access'

export interface SectionTab {
  to: AppPath
  labelKey: TKey
}

/**
 * Главное действие раздела — единственная жёлтая кнопка шапки (одна на область).
 * TODO(T-IAM-07): показывать по `allowedActions` из API (R002), а не по конфигу раздела.
 */
export interface SectionAction {
  labelKey: TKey
  /**
   * Значок кнопки на телефоне (< 768 px): там она — круг 48 px рядом с поиском, без подписи. Свой
   * значок раздела, чтобы не путать с «+» нижней панели (новая запись).
   */
  icon: LucideIcon
  /**
   * Экран действия. Нет адреса — экрана ещё нет: кнопка остаётся на месте, а по нажатию сообщает,
   * что экран в разработке (уведомление), — не выглядит рабочей и не молчит.
   */
  to?: AppPath
}

export interface Section {
  id: SectionId
  to: AppPath
  permission: Permission
  icon: LucideIcon
  labelKey: TKey
  /** Вкладки-пилюли внутри раздела; одна вкладка — пилюли не показываем. */
  tabs: SectionTab[]
  /** Главное действие раздела — жёлтая кнопка шапки. */
  primaryAction?: SectionAction
}

export const SECTIONS: readonly Section[] = [
  {
    id: 'schedule',
    to: '/schedule',
    permission: 'schedule.view',
    icon: CalendarDays,
    labelKey: 'shell:nav.schedule',
    primaryAction: { labelKey: 'shell:actions.newAppointment', icon: CalendarPlus, to: '/schedule/new' },
    tabs: [
      { to: '/schedule', labelKey: 'shell:tabs.schedule.day' },
      { to: '/schedule/week', labelKey: 'shell:tabs.schedule.week' },
      { to: '/schedule/archive', labelKey: 'shell:tabs.schedule.archive' },
    ],
  },
  {
    id: 'patients',
    to: '/patients',
    permission: 'patients.view',
    icon: Users,
    labelKey: 'shell:nav.patients',
    // TODO(T-PAT-14): панель «Новый пациент» (макет Pacient-Novyj).
    primaryAction: { labelKey: 'shell:actions.newPatient', icon: UserPlus },
    tabs: [{ to: '/patients', labelKey: 'shell:tabs.patients.list' }],
  },
  {
    id: 'staff',
    to: '/staff',
    permission: 'staff.view',
    icon: Stethoscope,
    labelKey: 'shell:nav.staff',
    // TODO(T-STF-05): панель «Добавить сотрудника».
    primaryAction: { labelKey: 'shell:actions.newStaff', icon: UserRoundPlus },
    tabs: [
      { to: '/staff', labelKey: 'shell:tabs.staff.list' },
      { to: '/staff/schedules', labelKey: 'shell:tabs.staff.schedules' },
    ],
  },
  {
    id: 'inventory',
    to: '/inventory',
    permission: 'inventory.view',
    icon: Package,
    labelKey: 'shell:nav.inventory',
    // TODO(T-INV-20): редактор документа.
    primaryAction: { labelKey: 'shell:actions.newDocument', icon: FilePlus2 },
    tabs: [
      { to: '/inventory', labelKey: 'shell:tabs.inventory.stock' },
      { to: '/inventory/items', labelKey: 'shell:tabs.inventory.items' },
      { to: '/inventory/documents', labelKey: 'shell:tabs.inventory.documents' },
      { to: '/inventory/suppliers', labelKey: 'shell:tabs.inventory.suppliers' },
    ],
  },
  {
    id: 'clinic',
    to: '/clinic',
    permission: 'clinic.view',
    icon: Building2,
    labelKey: 'shell:nav.clinic',
    tabs: [
      { to: '/clinic', labelKey: 'shell:tabs.clinic.profile' },
      { to: '/clinic/branches', labelKey: 'shell:tabs.clinic.branches' },
      { to: '/clinic/chairs', labelKey: 'shell:tabs.clinic.chairs' },
      { to: '/clinic/prices', labelKey: 'shell:tabs.clinic.prices' },
      { to: '/clinic/dictionaries', labelKey: 'shell:tabs.clinic.dictionaries' },
      { to: '/clinic/settings', labelKey: 'shell:tabs.clinic.settings' },
    ],
  },
  {
    id: 'access',
    to: '/access',
    permission: 'access.view',
    icon: ShieldCheck,
    labelKey: 'shell:nav.access',
    // TODO(T-IAM-14): диалог «Пригласить сотрудника» (макет Dostupy-Priglashenie).
    primaryAction: { labelKey: 'shell:actions.invite', icon: Send },
    tabs: [
      { to: '/access', labelKey: 'shell:tabs.access.staff' },
      { to: '/access/roles', labelKey: 'shell:tabs.access.roles' },
      { to: '/access/journal', labelKey: 'shell:tabs.access.journal' },
      { to: '/access/logins', labelKey: 'shell:tabs.access.logins' },
    ],
  },
]

export function sectionById(id: SectionId): Section {
  const section = SECTIONS.find((item) => item.id === id)
  if (!section) throw new Error(`Unknown section: ${id}`)
  return section
}

/** Раздел по пути: `/clinic/prices` → «Клиника». */
export function sectionByPath(pathname: string): Section | undefined {
  return SECTIONS.find((section) => pathname === section.to || pathname.startsWith(`${section.to}/`))
}

/** Пункты меню — только разделы, на которые есть право (правами владеет /api/v1/me). */
export function visibleSections(user: CurrentUser): Section[] {
  return SECTIONS.filter((section) => hasPermission(user, section.permission))
}

/** Куда вести с корня: первый доступный раздел, иначе профиль. */
export function homePathFor(user: CurrentUser): AppPath {
  return visibleSections(user)[0]?.to ?? '/profile'
}

/** Подпись ссылки «домой» на «нет доступа» и 404: туда же, куда ведёт `/` для этой роли. */
export function homeLabelKeyFor(user: CurrentUser): TKey {
  const first = visibleSections(user)[0]
  return first ? `shell:home.${first.id}` : 'shell:home.profile'
}
