import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import {
  Combobox,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
} from './combobox'
import { Highlight } from './highlight'

type ComboProps<Item> = {
  /** Варианты. Для растущих справочников — уже отфильтрованные сервером (см. `filter`). */
  items: readonly Item[]
  value: Item | null
  onValueChange: (value: Item | null) => void
  /** Подпись варианта; для строк не нужна. */
  itemToLabel?: (item: Item) => string
  /** Ключ React для варианта; по умолчанию — подпись. */
  itemToKey?: (item: Item) => string
  /** Сравнение выбранного значения с вариантом (объекты из разных ответов API). */
  isItemEqualToValue?: (item: Item, value: Item) => boolean
  /**
   * `null` — фильтрует сервер (dentify R001: растущий список): текст ввода приходит в
   * `onInputValueChange`, а `items` — ответ API на него. По умолчанию фильтрует Base UI
   * (короткий справочник, который API отдаёт целиком).
   */
  filter?: null
  onInputValueChange?: (value: string) => void
  id?: string
  placeholder?: string
  /** Имя поля, если рядом нет `<label htmlFor={id}>`. */
  'aria-label'?: string
  'aria-invalid'?: boolean
  emptyText?: string
  disabled?: boolean
  density?: 'default' | 'compact'
  surface?: 'card' | 'page'
  className?: string
}

/**
 * Выбор из списка «Кресла» (R011, R022): пилюля с лупой, поиск по вводу с подсветкой
 * совпадения, список в портале с переворотом у края окна. Обрезанный текст виден целиком
 * по наведению (`title`) — и у пунктов, и у выбранного значения в поле.
 */
function Combo<Item>({
  items,
  value,
  onValueChange,
  itemToLabel = String,
  itemToKey,
  isItemEqualToValue,
  filter,
  onInputValueChange,
  id,
  placeholder,
  'aria-label': ariaLabel,
  'aria-invalid': ariaInvalid,
  emptyText,
  disabled,
  density,
  surface,
  className,
}: ComboProps<Item>) {
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const selectedLabel = value == null ? '' : itemToLabel(value)
  // Поле показывает выбранное значение — это не запрос, подсвечивать нечего.
  const highlightQuery = query === selectedLabel ? '' : query

  return (
    <Combobox<Item>
      items={items}
      value={value}
      onValueChange={(next) => onValueChange(next ?? null)}
      itemToStringLabel={itemToLabel}
      isItemEqualToValue={isItemEqualToValue}
      {...(filter === null ? { filter: null } : {})}
      // Первое совпадение подсвечено сразу: набрал «Тер» — Enter выбирает (макет Sistema).
      autoHighlight
      onInputValueChange={(next) => {
        setQuery(next)
        onInputValueChange?.(next)
      }}
      disabled={disabled}
    >
      <ComboboxInput
        id={id}
        placeholder={placeholder}
        aria-label={ariaLabel}
        aria-invalid={ariaInvalid}
        title={selectedLabel || undefined}
        density={density}
        surface={surface}
        className={className}
        disabled={disabled}
      />
      <ComboboxContent>
        <ComboboxEmpty>{emptyText ?? t('common:ui.combo.empty')}</ComboboxEmpty>
        <ComboboxList>
          {(item: Item) => {
            const label = itemToLabel(item)
            return (
              <ComboboxItem key={itemToKey ? itemToKey(item) : label} value={item} title={label}>
                <span className="min-w-0 truncate">
                  <Highlight text={label} query={highlightQuery} />
                </span>
              </ComboboxItem>
            )
          }}
        </ComboboxList>
      </ComboboxContent>
    </Combobox>
  )
}

export { Combo, type ComboProps }
