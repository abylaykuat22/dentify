import { type RefObject, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Panel, PANEL_INLINE_QUERY } from '@/shared/ui/panel'
import { SearchField } from '@/shared/ui/search-field'

import { SEARCH_PANEL, usePanel } from './usePanel'

/** Ширина, с которой панель встаёт колонкой рядом с контентом (как в макете Raspisanie-Den). */
export const INLINE_PANEL_QUERY = PANEL_INLINE_QUERY

interface SidePanelProps {
  /** Ячейка сетки каркаса справа от контента: сюда портал кладёт панель на ≥ 1024 px. */
  inlineSlot: RefObject<HTMLDivElement | null>
}

/**
 * Боковая панель каркаса на общем `Panel` дизайн-системы: ≥ 1024 px — колонка справа от
 * контента, 768–1023 px — модальная панель справа, < 768 px — нижний лист. Открыта или
 * нет — решает `?panel=` в URL (usePanel). Содержимое пока одно: пустой поиск пациента.
 */
export function SidePanel({ inlineSlot }: SidePanelProps) {
  const { t } = useTranslation()
  const { panel, close } = usePanel()
  const inputRef = useRef<HTMLInputElement>(null)
  const [query, setQuery] = useState('')

  return (
    <Panel
      name={panel}
      open={panel === SEARCH_PANEL}
      onOpenChange={(next) => {
        if (!next) close()
      }}
      inlineContainer={inlineSlot}
      initialFocus={inputRef}
      title={t('shell:search.title')}
      description={t('shell:search.hint')}
    >
      <SearchField
        ref={inputRef}
        label={t('shell:search.title')}
        placeholder={t('shell:search.placeholder')}
        value={query}
        onValueChange={setQuery}
      />
      <p className="mt-auto rounded-block bg-mist p-4 text-body-sm text-slate">{t('shell:search.soon')}</p>
    </Panel>
  )
}
