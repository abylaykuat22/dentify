import { Plus } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/shared/lib/utils'
import { StatusBadge } from '@/shared/ui/status-badge'
import type { AppointmentStatus } from '@/shared/ui/tokens'

/**
 * Картинка на панели «О сервисе» (макеты Vhod, Parol, Priglashenie): три кресла, записи с 10:30 до
 * 13:30 и линия «сейчас». Украшение — скрыто от диктора, данные вымышленные и без имён.
 * Геометрия — как в макете: 348 px на три часа, записи — от своего времени, 8 px между ними.
 */

/** Начало картинки — 10:30 в минутах. */
const START = 10 * 60 + 30
/** Пикселей на минуту: 348 px на 180 минут, с отступом 4 px сверху. */
const PX_PER_MINUTE = 29 / 15
const TOP_GAP = 4
const BETWEEN = 8

type Tint = 'past' | 'teal' | 'coral' | 'lilac' | 'sand' | 'free'

interface Slot {
  from: string
  to: string
  tint: Tint
  /** Сколько строк-заглушек текста (1 или 2). */
  lines?: 1 | 2
  status?: AppointmentStatus
}

const CHAIRS: Slot[][] = [
  [
    { from: '10:30', to: '11:15', tint: 'past' },
    { from: '11:45', to: '12:45', tint: 'teal', status: 'arrived' },
  ],
  [
    { from: '11:00', to: '12:15', tint: 'coral', status: 'inChair' },
    { from: '12:30', to: '13:30', tint: 'free' },
  ],
  [
    { from: '10:30', to: '11:00', tint: 'past', lines: 1 },
    { from: '11:15', to: '12:00', tint: 'lilac' },
    { from: '12:30', to: '13:30', tint: 'sand', status: 'planned' },
  ],
]

const NOW = '11:40'

const TINT: Record<Exclude<Tint, 'free'>, string> = {
  past: 'bg-white/9 text-lagoon-muted',
  teal: 'bg-doctor-teal text-doctor-teal-fg',
  coral: 'bg-doctor-coral text-doctor-coral-fg',
  lilac: 'bg-doctor-lilac text-doctor-lilac-fg',
  sand: 'bg-doctor-sand text-doctor-sand-fg',
}

function minutes(time: string): number {
  const [hours = 0, mins = 0] = time.split(':').map(Number)
  return hours * 60 + mins - START
}

const offset = (time: string) => TOP_GAP + minutes(time) * PX_PER_MINUTE

export function AuthIllustration() {
  const { t } = useTranslation()
  return (
    <div aria-hidden="true" className="flex min-h-0 shrink flex-col gap-2.5 overflow-y-clip" data-auth-illustration>
      <div className="grid grid-cols-3 gap-3 text-label font-extrabold text-lagoon-muted">
        {CHAIRS.map((_, index) => (
          <span key={index} className="pl-3">
            {t('auth:about.chair', { number: index + 1 })}
          </span>
        ))}
      </div>
      <div className="relative grid h-87 shrink-0 grid-cols-3 gap-3">
        <div className="absolute -inset-x-2.5 h-0.5 rounded-pill bg-lamp" style={{ top: offset(NOW) - 1 }} />
        {CHAIRS.map((slots, index) => (
          <div key={index} className="relative rounded-block bg-white/5">
            {slots.map((slot) => (
              <SlotCard key={slot.from} slot={slot} />
            ))}
          </div>
        ))}
        <span
          className="absolute -left-5 z-10 flex h-6 items-center rounded-pill bg-lamp px-2 font-heading text-caption font-semibold text-ink"
          style={{ top: offset(NOW) - 12 }}
        >
          {NOW}
        </span>
      </div>
    </div>
  )
}

function SlotCard({ slot }: { slot: Slot }) {
  const { t } = useTranslation()
  const style = { top: offset(slot.from), height: (minutes(slot.to) - minutes(slot.from)) * PX_PER_MINUTE - BETWEEN }

  if (slot.tint === 'free') {
    return (
      <div
        className="absolute inset-x-1.5 flex items-center justify-center gap-2 rounded-item border-2 border-dashed border-white/28 text-label font-bold text-lagoon-muted"
        style={style}
      >
        <span className="flex size-6.5 items-center justify-center rounded-pill bg-lamp text-ink">
          <Plus className="size-3.5" strokeWidth={3} />
        </span>
        {t('auth:about.free')}
      </div>
    )
  }

  return (
    <div
      className={cn('absolute inset-x-1.5 flex flex-col gap-1.5 overflow-hidden rounded-item px-3 py-2.5', TINT[slot.tint])}
      style={style}
    >
      <span className="font-heading text-label font-semibold">{`${slot.from}–${slot.to}`}</span>
      <span className="h-2 w-[78%] shrink-0 rounded-pill bg-current opacity-50" />
      {slot.lines === 1 ? null : <span className="h-1.5 w-1/2 shrink-0 rounded-pill bg-current opacity-25" />}
      {slot.status ? <StatusBadge status={slot.status} surface="color" size="compact" className="mt-auto h-6 self-start" /> : null}
    </div>
  )
}
