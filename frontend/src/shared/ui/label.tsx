import type * as React from 'react'

import { cn } from '@/shared/lib/utils'

/** Метка поля «Кресла»: 13 px, 800, «сланец» (макет Sistema: «Специальность»). */
function Label({ className, ...props }: React.ComponentProps<'label'>) {
  return (
    <label
      data-slot="label"
      className={cn(
        'flex items-center gap-2 text-label font-extrabold text-slate select-none group-data-[disabled=true]:pointer-events-none group-data-[disabled=true]:opacity-50 peer-disabled:cursor-not-allowed peer-disabled:opacity-50',
        className,
      )}
      {...props}
    />
  )
}

export { Label }
