import { Menu as MenuPrimitive } from '@base-ui/react/menu'
import type * as React from 'react'

import { cn } from '@/shared/lib/utils'

/**
 * Меню по кнопке (shadcn DropdownMenu на Base UI Menu, «Кресло»): меню пользователя в сайдбаре,
 * действия строки. Всплывает в портале (R022), у края окна переворачивается; стрелки, Home/End,
 * набор первой буквы и Escape — Base UI, после закрытия фокус возвращается на кнопку.
 * Пункты — 44 px (цель касания), активный — «туман»; `danger` — красный текст (выход, удаление).
 * Из набора shadcn оставлено то, что нужно экранам; подменю и галочки — добавить из shadcn по мере нужды.
 */
function DropdownMenu(props: MenuPrimitive.Root.Props) {
  return <MenuPrimitive.Root data-slot="dropdown-menu" {...props} />
}

function DropdownMenuTrigger(props: MenuPrimitive.Trigger.Props) {
  return <MenuPrimitive.Trigger data-slot="dropdown-menu-trigger" {...props} />
}

function DropdownMenuContent({
  align = 'start',
  alignOffset = 0,
  side = 'bottom',
  sideOffset = 6,
  className,
  ...props
}: MenuPrimitive.Popup.Props &
  Pick<MenuPrimitive.Positioner.Props, 'align' | 'alignOffset' | 'side' | 'sideOffset'>) {
  return (
    <MenuPrimitive.Portal>
      <MenuPrimitive.Positioner
        className="isolate z-50 outline-none"
        align={align}
        alignOffset={alignOffset}
        side={side}
        sideOffset={sideOffset}
      >
        <MenuPrimitive.Popup
          data-slot="dropdown-menu-content"
          className={cn(
            'z-50 flex max-h-(--available-height) min-w-48 origin-(--transform-origin) flex-col gap-0.5 overflow-x-hidden overflow-y-auto rounded-block bg-white p-1.5 text-ink shadow-popover duration-100 outline-none data-open:animate-in data-open:fade-in-0 data-open:zoom-in-95 data-closed:animate-out data-closed:overflow-hidden data-closed:fade-out-0 data-closed:zoom-out-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2',
            className,
          )}
          {...props}
        />
      </MenuPrimitive.Positioner>
    </MenuPrimitive.Portal>
  )
}

const itemClass = (className: Parameters<typeof cn>[number]) =>
  cn(
    "relative flex min-h-11 cursor-default items-center gap-2.5 rounded-item px-3.5 py-2 text-body font-bold text-ink no-underline outline-hidden select-none data-disabled:pointer-events-none data-disabled:opacity-50 data-highlighted:bg-fog data-[variant=danger]:text-danger data-[variant=danger]:data-highlighted:bg-danger-bg [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-[18px]",
    className,
  )

function DropdownMenuItem({
  className,
  variant = 'default',
  ...props
}: MenuPrimitive.Item.Props & { variant?: 'default' | 'danger' }) {
  return <MenuPrimitive.Item data-slot="dropdown-menu-item" data-variant={variant} className={itemClass(className)} {...props} />
}

/** Пункт-ссылка: `render={<Link to="…" />}` — переход роутером; меню закрывается после нажатия. */
function DropdownMenuLinkItem({ className, closeOnClick = true, ...props }: MenuPrimitive.LinkItem.Props) {
  return (
    <MenuPrimitive.LinkItem
      data-slot="dropdown-menu-item"
      data-variant="default"
      closeOnClick={closeOnClick}
      className={itemClass(className)}
      {...props}
    />
  )
}

function DropdownMenuSeparator({ className, ...props }: MenuPrimitive.Separator.Props) {
  return <MenuPrimitive.Separator data-slot="dropdown-menu-separator" className={cn('-mx-1.5 my-1 h-0.5 bg-line', className)} {...props} />
}

function DropdownMenuGroup(props: React.ComponentProps<typeof MenuPrimitive.Group>) {
  return <MenuPrimitive.Group data-slot="dropdown-menu-group" {...props} />
}

export {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLinkItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
}
