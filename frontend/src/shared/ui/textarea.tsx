import type * as React from 'react'

import { cn } from '@/shared/lib/utils'

/**
 * Многострочное поле «Кресла»: блок 20, контур 2 px `line`, растёт по содержимому. На телефоне
 * шрифт 16 px (PLT-18), иначе iOS увеличивает страницу при фокусе.
 */
function Textarea({ className, ...props }: React.ComponentProps<'textarea'>) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(
        'flex field-sizing-content min-h-24 w-full rounded-block border-2 border-line bg-white px-4.5 py-3 text-body-lg font-semibold text-ink transition-colors placeholder:font-medium placeholder:text-graphite focus-visible:border-ink disabled:cursor-not-allowed disabled:bg-mist disabled:text-graphite aria-invalid:border-danger md:text-body',
        className,
      )}
      {...props}
    />
  )
}

export { Textarea }
