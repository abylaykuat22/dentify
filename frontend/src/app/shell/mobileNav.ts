import { type LucideIcon, UserRound } from 'lucide-react'

import type { CurrentUser } from '@/shared/api/me'
import type { TKey } from '@/shared/i18n/keys'
import type { PaletteColor } from '@/shared/ui/tokens'

import { type AppPath, sectionById, sectionByPath, type SectionId, visibleSections } from './sections'

// Навигация телефона (< 768 px) по макетам Telefon и Telefon-Eshche: нижняя панель
// «Расписание · Пациенты · + · Склад · Ещё» и меню «Ещё» с остальными разделами.

/** Разделы нижней панели: слева и справа от «+». Остальные — в меню «Ещё». */
export const BOTTOM_NAV = {
  start: ['schedule', 'patients'],
  end: ['inventory'],
} as const satisfies Record<string, readonly SectionId[]>

const BOTTOM_SECTIONS: readonly SectionId[] = [...BOTTOM_NAV.start, ...BOTTOM_NAV.end]

/** Меню «Ещё». */
export const MORE_PATH = '/more' satisfies AppPath
/** «+» нижней панели — мастер новой записи (T-SCH-16; пока заглушка). */
export const NEW_APPOINTMENT_PATH = '/schedule/new' satisfies AppPath

export interface MoreItem {
  id: SectionId | 'profile'
  to: AppPath
  icon: LucideIcon
  labelKey: TKey
  /** Подпись под названием: что внутри раздела. */
  captionKey: TKey
  /** Цвет кружка с иконкой (макет Telefon-Eshche). */
  color: PaletteColor
}

/** Разделы меню «Ещё» по макету: подпись и цвет кружка. Порядок — как в сайдбаре. */
const MORE_SECTIONS: readonly { id: SectionId; captionKey: TKey; color: PaletteColor }[] = [
  { id: 'staff', captionKey: 'shell:more.captions.staff', color: 'teal' },
  { id: 'inventory', captionKey: 'shell:more.captions.inventory', color: 'sand' },
  { id: 'clinic', captionKey: 'shell:more.captions.clinic', color: 'sky' },
  { id: 'access', captionKey: 'shell:more.captions.access', color: 'lilac' },
]

/**
 * Пункты меню «Ещё»: только разделы, на которые есть право (его сообщает /api/v1/me, R002),
 * и «Мой профиль» — он есть у каждого.
 */
export function moreItems(user: CurrentUser): MoreItem[] {
  const visible = new Set(visibleSections(user).map((section) => section.id))
  const sections = MORE_SECTIONS.filter((item) => visible.has(item.id)).map((item) => {
    const section = sectionById(item.id)
    return { ...item, to: section.to, icon: section.icon, labelKey: section.labelKey }
  })
  return [
    ...sections,
    {
      id: 'profile',
      to: '/profile',
      icon: UserRound,
      labelKey: 'shell:nav.profile',
      captionKey: 'shell:more.captions.profile',
      color: 'neutral',
    },
  ]
}

/**
 * Экран открыт из меню «Ещё» — тогда «Ещё» на нижней панели подсвечен: само меню, профиль и
 * разделы, которых нет на панели (Врачи, Клиника, Доступы).
 */
export function isMoreArea(pathname: string): boolean {
  if (pathname === MORE_PATH || pathname === '/profile' || pathname.startsWith('/profile/')) return true
  const section = sectionByPath(pathname)
  return section !== undefined && !BOTTOM_SECTIONS.includes(section.id)
}
