import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/shared/lib/utils'
import { Avatar } from '@/shared/ui/avatar'
import { type ResponsiveColumn, ResponsiveTable, rowActionClass } from '@/shared/ui/responsive-table'
import { Tag } from '@/shared/ui/tag'
import { PALETTE_CLASSES, type PaletteColor } from '@/shared/ui/tokens'

import { DemoCard } from './demo'

/**
 * Пример строки — вымышленные пациенты (как в макете Pacienty); текст — из словаря витрины. Возраст и
 * номер карты — данные (как придут с сервера): «29 лет», «41 год» склоняет ICU (`common:age`).
 */
interface DemoPatient {
  id: 'p1' | 'p2' | 'p3' | 'p4'
  age: number
  card: string
  color: PaletteColor
  doctorColor: PaletteColor
  tag: PaletteColor | null
}

const PATIENTS: readonly DemoPatient[] = [
  { id: 'p1', age: 29, card: '1042', color: 'teal', doctorColor: 'teal', tag: 'coral' },
  { id: 'p2', age: 41, card: '877', color: 'sky', doctorColor: 'teal', tag: null },
  { id: 'p3', age: 35, card: '912', color: 'rose', doctorColor: 'coral', tag: 'neutral' },
  { id: 'p4', age: 8, card: '1030', color: 'sand', doctorColor: 'lilac', tag: 'sky' },
]

/** Таблица → карточки (PLT-18): тот же `ResponsiveTable`, что и на экранах списков. */
export function TableCard() {
  const { t } = useTranslation(['devUi', 'common'])
  const [selected, setSelected] = useState<DemoPatient['id']>('p1')

  const columns: ResponsiveColumn<DemoPatient>[] = [
    {
      id: 'patient',
      header: t('table.columns.patient'),
      primary: true,
      className: 'w-[34%]',
      cell: (row) => (
        <span className="flex min-w-0 items-center gap-3">
          <Avatar tone="soft" color={row.color} initials={t(`table.rows.${row.id}.initials`)} />
          <span className="flex min-w-0 flex-col">
            <button
              type="button"
              className={cn(rowActionClass, 'truncate font-heading text-h4 font-semibold text-ink')}
              onClick={() => setSelected(row.id)}
            >
              {t(`table.rows.${row.id}.name`)}
            </button>
            <span className="text-label font-semibold text-graphite">{t('people.patientMeta', { age: t('common:age', { age: row.age }), card: row.card })}</span>
          </span>
        </span>
      ),
    },
    {
      id: 'phone',
      header: t('table.columns.phone'),
      cell: (row) => <span className="whitespace-nowrap tabular-nums">{t(`table.rows.${row.id}.phone`)}</span>,
    },
    {
      id: 'visit',
      header: t('table.columns.visit'),
      cell: (row) => (
        <span className="flex flex-col">
          <span className="font-bold">{t(`table.rows.${row.id}.visit`)}</span>
          <span className="text-label text-graphite">{t(`table.rows.${row.id}.visitNote`)}</span>
        </span>
      ),
    },
    {
      id: 'doctor',
      header: t('table.columns.doctor'),
      hideOnPhone: true,
      cell: (row) => (
        <span className="flex items-center gap-2 font-bold">
          <span aria-hidden="true" className={cn('size-2.5 shrink-0 rounded-full', PALETTE_CLASSES[row.doctorColor].strong)} />
          {t(`table.rows.${row.id}.doctor`)}
        </span>
      ),
    },
    {
      id: 'tags',
      header: t('table.columns.tags'),
      cell: (row) =>
        row.tag ? (
          <Tag color={row.tag}>{t(`table.rows.${row.id}.tag`)}</Tag>
        ) : (
          <span className="text-graphite">{t('table.none')}</span>
        ),
    },
  ]

  return (
    <DemoCard title={t('table.title')}>
      <span className="text-label font-semibold text-graphite">{t('table.note')}</span>
      <ResponsiveTable
        label={t('table.label')}
        columns={columns}
        rows={PATIENTS}
        rowKey={(row) => row.id}
        isSelected={(row) => row.id === selected}
        interactive
        surface="card"
      />
    </DemoCard>
  )
}
