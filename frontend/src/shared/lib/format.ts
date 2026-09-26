import { format } from 'date-fns/format'

import { i18n } from '@/shared/i18n'
import { dateLocale, weekStartsOn } from '@/shared/i18n/dates'

/**
 * Шаблоны дат — в словаре (`common:dates.*`, токены date-fns): порядок частей — дело перевода.
 * Язык названий дней и месяцев — локаль date-fns текущего языка (`@/shared/i18n/dates`).
 */
export type DatePattern = 'dayLabel' | 'moment'

/** Дата по шаблону словаря на языке интерфейса: `moment` — «26 сентября, 14:05». */
export function formatDate(date: Date, pattern: DatePattern): string {
  return format(date, i18n.t(`common:dates.${pattern}`), { locale: dateLocale(), weekStartsOn: weekStartsOn() })
}

/** «Четверг, 24 сентября» — контекст шапки. */
export function formatDayLabel(date: Date): string {
  const text = formatDate(date, 'dayLabel')
  return text.charAt(0).toLocaleUpperCase(i18n.language) + text.slice(1)
}
