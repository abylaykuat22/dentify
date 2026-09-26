import { ChevronRight } from 'lucide-react'
import type * as React from 'react'

import { MEDIA } from '@/shared/lib/breakpoints'
import { useMediaQuery } from '@/shared/lib/useMediaQuery'
import { cn } from '@/shared/lib/utils'

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from './table'

export interface ResponsiveColumn<Row> {
  id: string
  /** Подпись колонки — уже переведённая строка (`t(...)`). */
  header: string
  cell: (row: Row) => React.ReactNode
  /**
   * Главная ячейка (имя): на телефоне — заголовок карточки. Ссылку или кнопку строки кладите
   * сюда с классом `rowActionClass` — нажимается вся строка / карточка.
   */
  primary?: boolean
  /** На телефоне не показывать: второстепенное, чтобы карточка оставалась короткой. */
  hideOnPhone?: boolean
  /** Классы колонки в таблице (ширина, выравнивание). */
  className?: string
}

interface ResponsiveTableProps<Row> {
  /** Доступное имя таблицы и списка карточек. */
  label: string
  columns: readonly ResponsiveColumn<Row>[]
  rows: readonly Row[]
  rowKey: (row: Row) => string
  /** Выбранная строка — «подсветка» (открыта в боковой панели). */
  isSelected?: (row: Row) => boolean
  /** Строка нажимается (в главной ячейке — ссылка с `rowActionClass`): справа шеврон. */
  interactive?: boolean
  /** Что показать вместо пустой таблицы (StateScreen). */
  empty?: React.ReactNode
  /** Фон под карточками телефона: `page` — «туман» (карточки белые), `card` — белая карточка. */
  surface?: 'page' | 'card'
  className?: string
}

/**
 * Ссылка или кнопка строки: растягивается на всю строку/карточку (псевдоэлемент поверх), контур
 * фокуса рисуется вокруг всей строки. Кладётся в главную ячейку — у строки одно действие и одно
 * доступное имя (ФИО), а не пять ссылок.
 */
export const rowActionClass =
  "text-left after:absolute after:inset-0 after:rounded-(--row-radius) after:content-[''] focus-visible:outline-none focus-visible:after:outline-3 focus-visible:after:outline-offset-2 focus-visible:after:outline-[color:var(--focus-color)]"

/**
 * Таблица, которая на телефоне (< 768 px) становится карточками (PLT-18: таблицы → карточки).
 * С 768 px — `Table` «Кресла» (shadcn Table, макет Pacienty): шапка с подписями, строка целиком
 * нажимается. На телефоне — список карточек (макет Telefon): главная ячейка — заголовок,
 * остальные — «подпись: значение», `hideOnPhone` пропускаются. Горизонтальной прокрутки нет.
 * Данные, порядок и фильтры — с сервера (R002, R001): компонент только рисует `rows`.
 */
export function ResponsiveTable<Row>({
  label,
  columns,
  rows,
  rowKey,
  isSelected,
  interactive = false,
  empty,
  surface = 'page',
  className,
}: ResponsiveTableProps<Row>) {
  const phone = useMediaQuery(MEDIA.phone)

  if (rows.length === 0 && empty) return <>{empty}</>

  if (phone) {
    const primary = columns.find((column) => column.primary)
    const details = columns.filter((column) => !column.primary && !column.hideOnPhone)
    return (
      <ul aria-label={label} data-slot="responsive-table" data-view="cards" className={cn('flex flex-col gap-2', className)}>
        {rows.map((row) => (
          <li
            key={rowKey(row)}
            data-state={isSelected?.(row) ? 'selected' : undefined}
            className={cn(
              'relative flex items-center gap-3 rounded-block p-4 [--row-radius:var(--radius-block)] data-[state=selected]:bg-highlight',
              surface === 'page' ? 'bg-white' : 'bg-mist',
            )}
          >
            <div className="flex min-w-0 flex-1 flex-col gap-2">
              {primary ? <div className="min-w-0 text-ink">{primary.cell(row)}</div> : null}
              {details.length > 0 ? (
                <dl className="grid grid-cols-[auto_minmax(0,1fr)] items-baseline gap-x-3 gap-y-1">
                  {details.map((column) => (
                    <div key={column.id} className="contents">
                      <dt className="text-caption font-bold text-graphite">{column.header}</dt>
                      <dd className="min-w-0 text-body-sm font-semibold break-words text-ink">{column.cell(row)}</dd>
                    </div>
                  ))}
                </dl>
              ) : null}
            </div>
            {interactive ? <ChevronRight className="size-4.5 shrink-0 text-graphite" aria-hidden="true" /> : null}
          </li>
        ))}
      </ul>
    )
  }

  return (
    <Table aria-label={label} data-slot="responsive-table" data-view="table" className={className}>
      <TableHeader>
        <TableRow>
          {columns.map((column) => (
            <TableHead key={column.id} scope="col" className={column.className}>
              {column.header}
            </TableHead>
          ))}
          {interactive ? <td aria-hidden="true" className="w-10" /> : null}
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow
            key={rowKey(row)}
            data-state={isSelected?.(row) ? 'selected' : undefined}
            className="relative [--row-radius:var(--radius-row)]"
          >
            {columns.map((column) => (
              <TableCell key={column.id} className={column.className}>
                {column.cell(row)}
              </TableCell>
            ))}
            {interactive ? (
              <TableCell className="w-10">
                <ChevronRight className="size-4.5 text-graphite" aria-hidden="true" />
              </TableCell>
            ) : null}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
