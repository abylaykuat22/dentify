import { Check } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/shared/lib/utils'

import { Badge } from './badge'
import type { AppointmentStatus } from './tokens'

/** Точка и цвет слова для статуса (макет Sistema); `null` — вместо точки своё оформление. */
const STATUS_STYLE: Record<AppointmentStatus, { dot: string | null; text: string }> = {
  planned: { dot: 'bg-status-planned', text: 'text-status-planned-fg' },
  arrived: { dot: 'bg-status-arrived', text: 'text-status-arrived-fg' },
  inChair: { dot: 'bg-status-in-chair', text: 'text-status-in-chair-fg' },
  done: { dot: null, text: 'text-status-done-fg' },
  noShow: { dot: null, text: 'border-danger-line bg-white text-danger' },
  cancelled: { dot: null, text: 'font-bold text-status-cancelled-fg line-through' },
}

interface StatusBadgeProps {
  status: AppointmentStatus
  /** `card` — на белой карточке (фон «туман»), `color` — на цветной записи в сетке (белый). */
  surface?: 'card' | 'color'
  size?: 'default' | 'compact'
  className?: string
}

/**
 * Статус записи — всегда точка **и слово** (design-directions, PLT-21: не только цвет).
 * «Готово» — галочка, «Не пришёл» — белый с красным контуром, «Отменена» — зачёркнуто.
 * Какой статус и куда его можно перевести, решает сервер (R002); компонент только рисует.
 */
function StatusBadge({ status, surface = 'card', size = 'default', className }: StatusBadgeProps) {
  const { t } = useTranslation()
  const style = STATUS_STYLE[status]

  return (
    <Badge
      variant={surface === 'color' ? 'white' : 'default'}
      size={size}
      data-status={status}
      className={cn(style.text, className)}
    >
      {style.dot ? (
        <span aria-hidden="true" className={cn('size-2.25 shrink-0 rounded-full', style.dot)} />
      ) : status === 'done' ? (
        <Check strokeWidth={3} aria-hidden="true" />
      ) : null}
      {t(`common:ui.status.${status}`)}
    </Badge>
  )
}

export { StatusBadge, type StatusBadgeProps }
