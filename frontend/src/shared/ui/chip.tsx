import { Check } from 'lucide-react'

import { cn } from '@/shared/lib/utils'

import { Toggle, type ToggleProps, toggleVariants } from './toggle'
import { ToggleGroup, ToggleGroupItem } from './toggle-group'

interface ChipVariantsOptions {
  /**
   * `card` — белый с контуром 2 px на белой карточке (макет Sistema, 38 px, 13);
   * `page` — белый без контура на фоне «туман» (фильтры списка, макет Pacienty, 40 px, 14).
   */
  surface?: 'card' | 'page'
}

/**
 * Вид чипа для ссылки-фильтра — как `pillTabVariants` у вкладок-маршрутов: тот же чип, что
 * `Chip`, но выбранный отмечается `aria-current="page"` (фильтр живёт в URL).
 */
function chipVariants({ surface = 'card' }: ChipVariantsOptions = {}) {
  return cn(toggleVariants({ variant: 'chip' }), surface === 'page' && 'h-10 border-transparent px-4 text-body-sm')
}

/** Галочка выбранного чипа (макет Zapis: «✓ Терапия»): видна только у нажатого. */
function ChipCheck() {
  return <Check className="hidden group-aria-pressed/toggle:block" strokeWidth={3} aria-hidden="true" />
}

/**
 * Чип выбора «Кресла»: белый с контуром 2 px; выбранный — «чернила» + белый текст и, если
 * `showCheck`, галочка. Одиночный чип — `Chip`, набор — `ChipGroup`.
 */
function Chip<Value extends string = string>({
  children,
  showCheck = false,
  ...props
}: ToggleProps<Value> & { showCheck?: boolean }) {
  return (
    <Toggle<Value> variant="chip" {...props}>
      {showCheck ? <ChipCheck /> : null}
      {children}
    </Toggle>
  )
}

interface ChipOption<Value extends string> {
  value: Value
  label: string
}

interface ChipGroupProps<Value extends string> {
  'aria-label': string
  options: readonly ChipOption<Value>[]
  value: readonly Value[]
  onValueChange: (value: Value[]) => void
  /** Несколько чипов сразу (фильтры); по умолчанию — один. */
  multiple?: boolean
  showCheck?: boolean
  className?: string
}

/** Набор чипов (макет Sistema: «Выбор из вариантов»). */
function ChipGroup<Value extends string>({
  options,
  value,
  onValueChange,
  multiple = false,
  showCheck = false,
  className,
  'aria-label': ariaLabel,
}: ChipGroupProps<Value>) {
  return (
    <ToggleGroup<Value>
      variant="chip"
      multiple={multiple}
      value={value}
      onValueChange={onValueChange}
      className={className}
      aria-label={ariaLabel}
    >
      {options.map((option) => (
        <ToggleGroupItem<Value> key={option.value} value={option.value}>
          {showCheck ? <ChipCheck /> : null}
          {option.label}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  )
}

export { Chip, ChipGroup, type ChipGroupProps, type ChipOption, chipVariants }
