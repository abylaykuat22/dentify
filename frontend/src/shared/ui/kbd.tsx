import type * as React from 'react'

import { cn } from '@/shared/lib/utils'

/**
 * Подсказка клавиши («Ctrl K») — shadcn Kbd. Отдельный лёгкий модуль без Base UI: шапка каркаса
 * показывает её в кнопке поиска на первом экране, а тянуть ради неё поле ввода незачем (PLT-22).
 * Внутри поля — `InputGroupKbd` (input-group.tsx), та же подсказка.
 */
function Kbd({ className, ...props }: React.ComponentProps<'kbd'>) {
  return (
    <kbd
      data-slot="kbd"
      className={cn('rounded-pill bg-fog px-2.5 py-1 font-sans text-caption font-bold text-slate', className)}
      {...props}
    />
  )
}

export { Kbd }
