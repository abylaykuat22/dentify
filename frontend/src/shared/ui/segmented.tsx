import { ToggleGroup, ToggleGroupItem } from './toggle-group'

interface SegmentedOption<Value extends string> {
  value: Value
  label: string
}

interface SegmentedProps<Value extends string> {
  /** Что переключаем — доступное имя группы («Колонки расписания»). */
  'aria-label': string
  value: Value
  onValueChange: (value: Value) => void
  options: readonly SegmentedOption<Value>[]
  surface?: 'card' | 'page'
  className?: string
}

/**
 * Переключатель «Кресла» (макет Sistema: «Кресла | Врачи»): пилюли в контейнере «туман»,
 * выбранная — белая. Всегда выбран ровно один вариант: повторное нажатие его не снимает.
 */
function Segmented<Value extends string>({
  value,
  onValueChange,
  options,
  surface = 'card',
  className,
  'aria-label': ariaLabel,
}: SegmentedProps<Value>) {
  return (
    <ToggleGroup<Value>
      variant="segment"
      surface={surface}
      value={[value]}
      onValueChange={(next) => {
        const picked = next[0]
        if (picked !== undefined) onValueChange(picked)
      }}
      className={className}
      aria-label={ariaLabel}
    >
      {options.map((option) => (
        <ToggleGroupItem<Value> key={option.value} value={option.value}>
          {option.label}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  )
}

export { Segmented, type SegmentedOption, type SegmentedProps }
