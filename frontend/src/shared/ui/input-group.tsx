import { cva, type VariantProps } from 'class-variance-authority'
import type * as React from 'react'

import { cn } from '@/shared/lib/utils'

import { Button } from './button'
import { Input } from './input'
import { Kbd } from './kbd'
import { Textarea } from './textarea'

/**
 * Поле-пилюля с иконкой, кнопкой или подсказкой клавиши (shadcn InputGroup, «Кресло»).
 * Контур и фокус рисует контейнер: внутреннее поле без своей рамки, а контейнер при фокусе
 * поля получает контур «чернила» и общий контур фокуса 3 px «лагуна».
 *
 * `surface`: `card` — на белой карточке (контур `line`), `page` — на фоне «туман» (белая
 * пилюля без контура, как поиск в шапке списков).
 */
const inputGroupVariants = cva(
  'group/input-group relative flex w-full min-w-0 items-center gap-2.5 rounded-pill border-2 bg-white px-4 text-ink transition-colors has-disabled:bg-mist has-disabled:text-graphite has-[>textarea]:h-auto has-[>textarea]:rounded-block has-[[data-slot=input-group-control]:focus-visible]:border-ink has-[[data-slot=input-group-control]:focus-visible]:outline-3 has-[[data-slot=input-group-control]:focus-visible]:outline-offset-2 has-[[data-slot=input-group-control]:focus-visible]:outline-[color:var(--focus-color)] has-[[data-slot][aria-invalid=true]]:border-danger',
  {
    variants: {
      density: {
        // На телефоне шрифт поля 16 px (PLT-18): мельче — iOS увеличивает страницу при фокусе.
        default: 'h-12 text-body-lg md:text-body',
        // Компактное на телефоне — 44 px: цель касания (PLT-21), с 768 px — 40 px по макетам.
        compact: 'h-11 gap-2 px-3.5 text-body-lg md:h-10 md:text-body-sm',
      },
      surface: {
        card: 'border-line',
        page: 'border-transparent',
      },
    },
    defaultVariants: { density: 'default', surface: 'card' },
  },
)

type InputGroupProps = React.ComponentProps<'div'> & VariantProps<typeof inputGroupVariants>

function InputGroup({ className, density, surface, ...props }: InputGroupProps) {
  return (
    <div
      data-slot="input-group"
      data-density={density ?? 'default'}
      role="group"
      className={cn(inputGroupVariants({ density, surface }), className)}
      {...props}
    />
  )
}

const inputGroupAddonVariants = cva(
  "flex h-auto shrink-0 cursor-text items-center justify-center gap-2 text-graphite select-none [&>svg:not([class*='size-'])]:size-[18px] group-data-[density=compact]/input-group:[&>svg:not([class*='size-'])]:size-4",
  {
    variants: {
      align: {
        'inline-start': 'order-first',
        'inline-end': 'order-last has-[>button]:-mr-2',
      },
    },
    defaultVariants: { align: 'inline-start' },
  },
)

function InputGroupAddon({
  className,
  align = 'inline-start',
  ...props
}: React.ComponentProps<'div'> & VariantProps<typeof inputGroupAddonVariants>) {
  return (
    <div
      role="group"
      data-slot="input-group-addon"
      data-align={align}
      className={cn(inputGroupAddonVariants({ align }), className)}
      onClick={(e) => {
        if ((e.target as HTMLElement).closest('button')) {
          return
        }
        e.currentTarget.parentElement?.querySelector('input')?.focus()
      }}
      {...props}
    />
  )
}

function InputGroupButton({
  className,
  type = 'button',
  variant = 'ghost',
  size = 'icon-xs',
  ...props
}: Omit<React.ComponentProps<typeof Button>, 'type'> & {
  type?: 'button' | 'submit' | 'reset'
}) {
  return <Button type={type} variant={variant} size={size} className={cn('text-ink', className)} {...props} />
}

function InputGroupText({ className, ...props }: React.ComponentProps<'span'>) {
  return (
    <span
      className={cn(
        "flex items-center gap-2 text-caption font-bold text-graphite [&_svg]:pointer-events-none [&_svg:not([class*='size-'])]:size-4",
        className,
      )}
      {...props}
    />
  )
}

/** Подсказка клавиши внутри поля («Ctrl K») — общий `Kbd`. */
function InputGroupKbd(props: React.ComponentProps<'kbd'>) {
  return <Kbd data-slot="input-group-kbd" {...props} />
}

function InputGroupInput({ className, ...props }: React.ComponentProps<'input'>) {
  return (
    <Input
      data-slot="input-group-control"
      className={cn(
        'h-full flex-1 rounded-none border-0 bg-transparent px-0 text-[length:inherit] outline-none md:text-[length:inherit] focus-visible:outline-none disabled:bg-transparent [&::-webkit-search-cancel-button]:appearance-none',
        className,
      )}
      {...props}
    />
  )
}

function InputGroupTextarea({ className, ...props }: React.ComponentProps<'textarea'>) {
  return (
    <Textarea
      data-slot="input-group-control"
      className={cn(
        'flex-1 resize-none rounded-none border-0 bg-transparent px-0 outline-none focus-visible:outline-none disabled:bg-transparent',
        className,
      )}
      {...props}
    />
  )
}

export {
  InputGroup,
  InputGroupAddon,
  InputGroupButton,
  InputGroupInput,
  InputGroupKbd,
  InputGroupText,
  InputGroupTextarea,
  inputGroupVariants,
}
