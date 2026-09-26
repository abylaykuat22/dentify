import { useTranslation } from 'react-i18next'

/**
 * Где в тексте совпадение с запросом: сравнение как у Base UI `useFilter` (Intl.Collator,
 * `sensitivity: 'base'` — без учёта регистра и диакритики), чтобы подсвечивалось ровно то,
 * по чему Base UI оставил пункт в списке. Нет совпадения — `null`.
 */
export function findMatch(text: string, query: string, locale: string): [number, number] | null {
  const q = query.trim()
  if (!q || q.length > text.length) return null
  const collator = new Intl.Collator(locale, { usage: 'search', sensitivity: 'base' })
  for (let start = 0; start + q.length <= text.length; start++) {
    if (collator.compare(text.slice(start, start + q.length), q) === 0) return [start, start + q.length]
  }
  return null
}

/**
 * Текст с подсвеченным совпадением (макет Sistema: `match`, у активного пункта — `lamp`).
 * Только отображение: что попало в список, решает фильтр Base UI или сервер (R001/R002).
 */
export function Highlight({ text, query }: { text: string; query: string }) {
  const { i18n } = useTranslation()
  const match = findMatch(text, query, i18n.language)
  if (!match) return <>{text}</>
  const [start, end] = match
  return (
    <>
      {text.slice(0, start)}
      <mark className="rounded-mark bg-match text-inherit group-data-highlighted/combobox-item:bg-lamp">
        {text.slice(start, end)}
      </mark>
      {text.slice(end)}
    </>
  )
}
