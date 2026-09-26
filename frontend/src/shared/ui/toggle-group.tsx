import { ToggleGroup as ToggleGroupPrimitive } from '@base-ui/react/toggle-group'
import { cva, type VariantProps } from 'class-variance-authority'
import { createContext, useContext } from 'react'

import { cn } from '@/shared/lib/utils'

import { Toggle, type ToggleProps, type toggleVariants } from './toggle'

/**
 * Группа переключателей (shadcn ToggleGroup на Base UI). Готовые обёртки для экранов —
 * `Segmented` (один из нескольких, контейнер-пилюля) и `ChipGroup` (чипы выбора).
 * `surface`: `card` — контейнер «туман» на белой карточке, `page` — белый на фоне «туман».
 */
const toggleGroupVariants = cva('group/toggle-group flex w-fit items-center', {
  variants: {
    variant: {
      default: 'gap-1',
      segment: 'rounded-pill p-1',
      chip: 'flex-wrap gap-1.5',
    },
    surface: {
      card: '',
      page: '',
    },
  },
  compoundVariants: [
    { variant: 'segment', surface: 'card', className: 'bg-fog' },
    { variant: 'segment', surface: 'page', className: 'bg-white' },
  ],
  defaultVariants: { variant: 'default', surface: 'card' },
})

type ToggleVariant = NonNullable<VariantProps<typeof toggleVariants>['variant']>

const ToggleGroupContext = createContext<ToggleVariant>('default')

function ToggleGroup<Value extends string = string>({
  className,
  variant,
  surface,
  ...props
}: ToggleGroupPrimitive.Props<Value> & VariantProps<typeof toggleGroupVariants>) {
  const resolved = variant ?? 'default'
  return (
    <ToggleGroupContext.Provider value={resolved}>
      <ToggleGroupPrimitive<Value>
        data-slot="toggle-group"
        data-variant={resolved}
        className={cn(toggleGroupVariants({ variant: resolved, surface }), className)}
        {...props}
      />
    </ToggleGroupContext.Provider>
  )
}

function ToggleGroupItem<Value extends string = string>({ variant, ...props }: ToggleProps<Value>) {
  const groupVariant = useContext(ToggleGroupContext)
  return <Toggle<Value> data-slot="toggle-group-item" variant={variant ?? groupVariant} {...props} />
}

export { ToggleGroup, ToggleGroupItem }
