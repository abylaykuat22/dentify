import { cva, type VariantProps } from 'class-variance-authority'
import type * as React from 'react'

import { cn } from '@/shared/lib/utils'

/**
 * Плашка-предупреждение «Кресла» (shadcn Alert). Готовая плашка аллергии — `AlertStrip`.
 * По умолчанию `role="alert"` (shadcn) — для ошибок, появившихся в ответ на действие;
 * постоянной плашке (аллергия в карточке) передавайте `role="note"`, чтобы экранный диктор
 * не объявлял её при каждом открытии.
 */
const alertVariants = cva(
  "group/alert relative flex w-full items-start gap-2.5 rounded-row px-4 py-3 text-left text-body-sm [&>svg]:mt-px [&>svg]:shrink-0 [&>svg:not([class*='size-'])]:size-[19px]",
  {
    variants: {
      variant: {
        danger: 'bg-danger-bg text-danger',
        warning: 'bg-highlight text-ink [&>svg]:text-status-arrived-fg',
        info: 'bg-mist text-ink [&>svg]:text-lagoon',
      },
    },
    defaultVariants: { variant: 'danger' },
  },
)

function Alert({ className, variant, ...props }: React.ComponentProps<'div'> & VariantProps<typeof alertVariants>) {
  return <div data-slot="alert" role="alert" className={cn(alertVariants({ variant }), className)} {...props} />
}

function AlertTitle({ className, ...props }: React.ComponentProps<'span'>) {
  return <span data-slot="alert-title" className={cn('font-extrabold', className)} {...props} />
}

function AlertDescription({ className, ...props }: React.ComponentProps<'span'>) {
  return <span data-slot="alert-description" className={cn('font-semibold', className)} {...props} />
}

export { Alert, AlertDescription, AlertTitle, alertVariants }
