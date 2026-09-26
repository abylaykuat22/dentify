import { Button as ButtonPrimitive } from '@base-ui/react/button'
import { cva, type VariantProps } from 'class-variance-authority'

import { cn } from '@/shared/lib/utils'

/**
 * Кнопка «Кресла» (shadcn Button на Base UI). Все кнопки — пилюли; фокус — общий контур
 * 3 px «лагуна» из index.css (ring-классы shadcn убраны намеренно).
 *
 * - `default` — жёлтая «лампа»: главное действие, **одна на область** (страница; открытая
 *   панель или окно — своя). e2e считает их по `data-variant="default"`.
 * - `outline` — белая с контуром 2 px: второстепенное действие.
 * - `selected` — заливка «чернила»: выбранное состояние («Сегодня», выбранный слот).
 * - `soft` — «туман»: круглые служебные (закрыть, «ещё»).
 * - `ghost` — без фона: «← Назад» в шагах (R023).
 * - `danger` — красный текст без фона: отмена, удаление.
 * - `destructive` — красная заливка: только финальное подтверждение опасного действия.
 * - `link` — текстовая ссылка («Карточка пациента»).
 *
 * Размеры: 48 px (`default`, `icon`) и компактные 40 px (`compact`, `icon-compact`); у
 * кнопок меньше 44 px цель касания расширена псевдоэлементом до ≥ 44 px (PLT-21). Вне системы
 * 48/40 — только внутри составных компонентов: `sm` (38 px: «Скопировать код» в FormAlert,
 * действие уведомления) и `icon-xs` (28 px: кнопка внутри поля, InputGroupButton).
 */
const buttonVariants = cva(
  "group/button relative inline-flex shrink-0 items-center justify-center gap-2 rounded-pill border-2 border-transparent font-extrabold whitespace-nowrap transition-colors select-none disabled:pointer-events-none disabled:opacity-50 aria-disabled:pointer-events-none aria-disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-[18px]",
  {
    variants: {
      variant: {
        default: 'bg-lamp text-ink hover:bg-lamp-deep active:bg-lamp-deep',
        outline: 'border-line bg-white text-ink hover:bg-mist aria-expanded:bg-mist',
        selected: 'bg-ink text-white hover:bg-ink/90',
        soft: 'bg-fog text-ink hover:bg-line aria-expanded:bg-line',
        ghost: 'text-ink hover:bg-fog aria-expanded:bg-fog',
        danger: 'text-danger hover:bg-danger-bg',
        destructive: 'bg-danger text-white hover:bg-danger/90',
        link: 'text-ink underline decoration-2 underline-offset-4 hover:text-lagoon',
      },
      size: {
        default: 'h-12 px-5.5 text-body has-[>svg:first-child]:pl-4',
        compact:
          "h-10 gap-1.5 px-4 text-body-sm has-[>svg:first-child]:pl-3.5 after:absolute after:-inset-0.5 after:content-['']",
        sm: "h-9.5 gap-1.5 px-4 text-body-sm after:absolute after:-inset-1 after:content-['']",
        icon: "size-12 [&_svg:not([class*='size-'])]:size-5",
        'icon-compact': "size-10 after:absolute after:-inset-0.5 after:content-['']",
        'icon-xs': "size-7 [&_svg:not([class*='size-'])]:size-4 after:absolute after:-inset-2 after:content-['']",
      },
    },
    compoundVariants: [
      // Текстовые кнопки — почти без внутренних отступов, как в макете («Отменить»).
      { variant: ['danger', 'link'], size: ['default', 'compact', 'sm'], className: 'px-2' },
    ],
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
)

type ButtonProps = ButtonPrimitive.Props & VariantProps<typeof buttonVariants>

function Button({ className, variant = 'default', size = 'default', ...props }: ButtonProps) {
  return (
    <ButtonPrimitive
      data-slot="button"
      data-variant={variant}
      data-size={size}
      className={cn(buttonVariants({ variant, size }), className)}
      {...props}
    />
  )
}

export { Button, buttonVariants, type ButtonProps }
