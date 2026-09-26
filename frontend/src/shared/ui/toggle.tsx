import { Toggle as TogglePrimitive } from '@base-ui/react/toggle'
import { cva, type VariantProps } from 'class-variance-authority'

import { cn } from '@/shared/lib/utils'

/**
 * Кнопка-переключатель «Кресла» (shadcn Toggle на Base UI, `aria-pressed`).
 * - `segment` — сегмент переключателя в контейнере «туман»: нажатый — белый, «чернила».
 * - `chip` — чип выбора: белый с контуром 2 px, нажатый — заливка «чернила» + белый текст.
 * - `default` — иконка-переключатель.
 * Высота 38 px, цель касания расширена по вертикали до 44 px.
 */
const toggleVariants = cva(
  "group/toggle relative inline-flex shrink-0 items-center justify-center gap-1.5 rounded-pill whitespace-nowrap transition-colors select-none after:absolute after:inset-x-0 after:-inset-y-[3px] after:content-[''] disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        default: 'size-9.5 text-ink hover:bg-fog aria-pressed:bg-ink aria-pressed:text-white',
        segment:
          'h-9.5 px-4.5 text-body-sm font-bold text-graphite hover:text-ink aria-pressed:bg-white aria-pressed:font-extrabold aria-pressed:text-ink aria-pressed:shadow-raised',
        // Выбранный чип — `aria-pressed` (кнопка) или `aria-current="page"` (ссылка-фильтр, `chipVariants`).
        chip: 'h-9.5 border-2 border-line bg-white px-3.5 text-label font-bold text-ink hover:bg-mist aria-pressed:border-ink aria-pressed:bg-ink aria-pressed:font-extrabold aria-pressed:text-white aria-pressed:hover:bg-ink aria-[current=page]:border-ink aria-[current=page]:bg-ink aria-[current=page]:font-extrabold aria-[current=page]:text-white aria-[current=page]:hover:bg-ink',
      },
    },
    defaultVariants: { variant: 'default' },
  },
)

type ToggleProps<Value extends string = string> = TogglePrimitive.Props<Value> & VariantProps<typeof toggleVariants>

function Toggle<Value extends string = string>({ className, variant = 'default', ...props }: ToggleProps<Value>) {
  return (
    <TogglePrimitive<Value>
      data-slot="toggle"
      data-variant={variant}
      className={cn(toggleVariants({ variant }), className)}
      {...props}
    />
  )
}

export { Toggle, type ToggleProps, toggleVariants }
