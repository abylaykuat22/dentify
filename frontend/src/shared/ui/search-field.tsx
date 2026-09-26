import { Search, X } from 'lucide-react'
import { type Ref, useRef } from 'react'
import type * as React from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/shared/lib/utils'

import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupInput, InputGroupKbd } from './input-group'

type SearchFieldProps = Omit<React.ComponentProps<'input'>, 'value' | 'onChange' | 'type'> & {
  /** Что ищем — доступное имя поля («Поиск пациента»). */
  label: string
  /**
   * Подсказка перечисляет поля, по которым ищет сервер (dentify R001): «ФИО, телефон или ИИН».
   */
  placeholder: string
  value: string
  onValueChange: (value: string) => void
  /** Подсказка горячей клавиши справа («Ctrl K»), пока поле пустое. */
  shortcut?: string
  density?: 'default' | 'compact'
  /** `page` — на фоне «туман» (белая пилюля без контура), `card` — на белой карточке. */
  surface?: 'page' | 'card'
  className?: string
}

/**
 * Поле поиска для растущих списков (dentify R001): пилюля с лупой, плейсхолдер перечисляет
 * поля поиска, крестик очищает. Само поле ничего не фильтрует: значение уходит в запрос к
 * серверу (R002) — дебаунс и параметры запроса делает экран списка.
 */
function SearchField({
  label,
  placeholder,
  value,
  onValueChange,
  shortcut,
  density = 'default',
  surface = 'card',
  className,
  ref,
  ...props
}: SearchFieldProps) {
  const { t } = useTranslation()
  const inputRef = useRef<HTMLInputElement | null>(null)
  const setRef = (node: HTMLInputElement | null) => {
    inputRef.current = node
    assignRef(ref, node)
  }

  return (
    <InputGroup density={density} surface={surface} className={cn('pr-2', className)} data-slot="search-field">
      <InputGroupAddon>
        <Search aria-hidden="true" />
      </InputGroupAddon>
      <InputGroupInput
        ref={setRef}
        type="search"
        aria-label={label}
        placeholder={placeholder}
        title={placeholder}
        value={value}
        onChange={(event) => onValueChange(event.target.value)}
        autoComplete="off"
        {...props}
      />
      {value ? (
        <InputGroupAddon align="inline-end">
          <InputGroupButton
            aria-label={t('common:ui.search.clear')}
            onClick={() => {
              onValueChange('')
              // Кнопка очистки исчезает — фокус возвращается в поле, а не теряется.
              inputRef.current?.focus()
            }}
          >
            <X aria-hidden="true" />
          </InputGroupButton>
        </InputGroupAddon>
      ) : shortcut ? (
        // Подсказка клавиши — только там, где есть клавиатура (с 768 px).
        <InputGroupAddon align="inline-end" aria-hidden="true" className="max-md:hidden">
          <InputGroupKbd>{shortcut}</InputGroupKbd>
        </InputGroupAddon>
      ) : null}
    </InputGroup>
  )
}

function assignRef<T>(ref: Ref<T> | undefined, value: T) {
  if (typeof ref === 'function') ref(value)
  else if (ref) ref.current = value
}

export { SearchField, type SearchFieldProps }
