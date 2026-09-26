import { mergeProps } from '@base-ui/react/merge-props'
import { useRender } from '@base-ui/react/use-render'
import { cva, type VariantProps } from 'class-variance-authority'

import { cn } from '@/shared/lib/utils'

/**
 * Пилюля-метка «Кресла» (shadcn Badge на Base UI). Основа для `StatusBadge` (точка + слово)
 * и `Tag` (метка в цвете палитры). Не интерактивна; кликабельный вариант — `Chip`.
 */
const badgeVariants = cva(
  'group/badge inline-flex w-fit shrink-0 items-center justify-center gap-1.5 overflow-hidden rounded-pill border-2 border-transparent font-extrabold whitespace-nowrap [&>svg]:pointer-events-none [&>svg]:shrink-0',
  {
    variants: {
      variant: {
        default: 'bg-fog text-ink',
        white: 'bg-white text-ink',
        outline: 'border-line bg-white text-ink',
        danger: 'bg-danger-bg text-danger',
      },
      size: {
        default: 'h-8 px-3 text-label [&>svg]:size-3.5',
        compact: 'h-6.5 px-2.5 text-caption [&>svg]:size-3',
      },
    },
    defaultVariants: { variant: 'default', size: 'default' },
  },
)

function Badge({
  className,
  variant = 'default',
  size = 'default',
  render,
  ...props
}: useRender.ComponentProps<'span'> & VariantProps<typeof badgeVariants>) {
  return useRender({
    defaultTagName: 'span',
    props: mergeProps<'span'>({ className: cn(badgeVariants({ variant, size }), className) }, props),
    render,
    state: { slot: 'badge', variant },
  })
}

export { Badge, badgeVariants }
