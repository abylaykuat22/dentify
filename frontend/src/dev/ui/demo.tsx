import type { ReactNode } from 'react'

import { cn } from '@/shared/lib/utils'
import { Label } from '@/shared/ui/label'

/** Карточка витрины: белая, скругление 24, отступ 24 (макет Sistema). */
export function DemoCard({
  title,
  children,
  className,
  grow = false,
}: {
  title: string
  children: ReactNode
  className?: string
  grow?: boolean
}) {
  return (
    <section
      aria-label={title}
      data-demo-card
      className={cn('flex min-w-0 flex-col gap-3.5 rounded-card bg-white p-6', grow && 'grow', className)}
    >
      <h2 className="font-heading text-h3 font-semibold">{title}</h2>
      {children}
    </section>
  )
}

export function DemoLabel({ children, htmlFor }: { children: ReactNode; htmlFor?: string }) {
  return htmlFor ? (
    <Label htmlFor={htmlFor}>{children}</Label>
  ) : (
    <span className="text-label font-extrabold text-slate">{children}</span>
  )
}
