import type * as React from 'react'

import { cn } from '@/shared/lib/utils'

/**
 * Таблица «Кресла» (shadcn Table, перекрашен по макету Pacienty): без линий между строками,
 * шапка — подписи 12.5/800 «графит», строка — скругление 16, при наведении — «туман-светлый»,
 * выбранная (`data-state=selected`) — «подсветка». Шире контейнера — прокрутка внутри него,
 * а не страницы. На телефоне таблицы превращаются в карточки — `ResponsiveTable`.
 */
function Table({ className, ...props }: React.ComponentProps<'table'>) {
  return (
    <div data-slot="table-container" className="relative w-full overflow-x-auto">
      <table
        data-slot="table"
        className={cn('w-full border-separate border-spacing-0 caption-bottom text-body-sm text-ink', className)}
        {...props}
      />
    </div>
  )
}

function TableHeader({ className, ...props }: React.ComponentProps<'thead'>) {
  return <thead data-slot="table-header" className={className} {...props} />
}

function TableBody({ className, ...props }: React.ComponentProps<'tbody'>) {
  return <tbody data-slot="table-body" className={className} {...props} />
}

function TableFooter({ className, ...props }: React.ComponentProps<'tfoot'>) {
  return <tfoot data-slot="table-footer" className={cn('font-bold', className)} {...props} />
}

function TableRow({ className, ...props }: React.ComponentProps<'tr'>) {
  return (
    <tr
      data-slot="table-row"
      className={cn(
        'transition-colors [tbody_&]:hover:bg-mist [tbody_&]:has-aria-expanded:bg-mist [tbody_&]:data-[state=selected]:bg-highlight',
        className,
      )}
      {...props}
    />
  )
}

function TableHead({ className, ...props }: React.ComponentProps<'th'>) {
  return (
    <th
      data-slot="table-head"
      className={cn(
        'px-1.5 pt-3 pb-2.5 text-left align-middle text-caption font-extrabold whitespace-nowrap text-graphite first:pl-4 last:pr-4',
        className,
      )}
      {...props}
    />
  )
}

function TableCell({ className, ...props }: React.ComponentProps<'td'>) {
  return (
    <td
      data-slot="table-cell"
      className={cn(
        'px-1.5 py-2.5 align-middle font-semibold first:rounded-l-row first:pl-4 last:rounded-r-row last:pr-4',
        className,
      )}
      {...props}
    />
  )
}

function TableCaption({ className, ...props }: React.ComponentProps<'caption'>) {
  return (
    <caption data-slot="table-caption" className={cn('mt-4 text-caption font-bold text-graphite', className)} {...props} />
  )
}

export { Table, TableBody, TableCaption, TableCell, TableFooter, TableHead, TableHeader, TableRow }
