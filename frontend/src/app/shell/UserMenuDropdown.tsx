import { Link } from '@tanstack/react-router'
import { LogOut, UserRound } from 'lucide-react'
import { type ReactNode, useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'

import { useSignOut } from '@/shared/api/useSignOut'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLinkItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/shared/ui/tooltip'

/**
 * Карточка пользователя: вид, подпись и метки — общие у простой карточки каркаса и у кнопки меню. Какая
 * из двух сейчас на месте — `data-user-menu`: `placeholder` (меню ещё грузится) или `menu`.
 */
export interface UserCardProps {
  'aria-label': string
  'data-sidebar-item': true
  'data-current': true | undefined
  className: string
  children: ReactNode
}

/**
 * Что человек успел сделать с простой карточкой, пока грузилось меню: `open` — нажал (меню открыть),
 * `focus` — фокус на карточке (оставить на кнопке меню), `none` — ничего.
 */
export type UserMenuHandoff = 'open' | 'focus' | 'none'

/**
 * Меню пользователя — отдельный чанк (см. `UserMenu`). Меню — Base UI (портал R022, стрелки, Escape, фокус
 * возвращается на карточку). Выход — `useSignOut` (тот же, что в «Ещё» и на выборе клиники); не вышло —
 * уведомление, пользователь остаётся на месте.
 */
export function UserMenuDropdown({
  card,
  label,
  collapsed,
  onProfile,
  handoff,
}: {
  card: UserCardProps
  label: string
  collapsed: boolean
  onProfile: boolean
  handoff: UserMenuHandoff
}) {
  const { t } = useTranslation()
  const { signOut, pending } = useSignOut()
  const trigger = useRef<HTMLButtonElement>(null)
  const replayed = useRef(false)

  // Доиграть нажатие или фокус простой карточки — один раз (StrictMode повторяет эффекты). Нажатие
  // повторяется кликом по кнопке меню: Base UI откроет меню так же, как от Enter, — фокус на первом пункте.
  useEffect(() => {
    if (replayed.current) return
    replayed.current = true
    if (handoff === 'open') trigger.current?.click()
    else if (handoff === 'focus') trigger.current?.focus()
  }, [handoff])

  async function leave() {
    if (await signOut()) return
    // Модуль уведомлений — по нажатию: в начальной загрузке его нет (PLT-22).
    const { toast } = await import('@/shared/ui/toast')
    toast.add({ title: t('shell:more.logoutFailed') })
  }

  return (
    <DropdownMenu>
      <Tooltip disabled={!collapsed}>
        <TooltipTrigger ref={trigger} render={<DropdownMenuTrigger />} data-user-menu="menu" {...card} />
        <TooltipContent side="right">{label}</TooltipContent>
      </Tooltip>
      {/* Свёрнутый сайдбар: меню справа, за краем карточки (кнопка стоит в ней с отступом 10 px, 10 + 8);
          развёрнутый — над карточкой пользователя и шириной с неё. */}
      <DropdownMenuContent
        side={collapsed ? 'right' : 'top'}
        align={collapsed ? 'end' : 'start'}
        sideOffset={collapsed ? 18 : 8}
        className={collapsed ? undefined : 'w-(--anchor-width) min-w-0'}
      >
        <DropdownMenuLinkItem render={<Link to="/profile" />} aria-current={onProfile ? 'page' : undefined}>
          <UserRound aria-hidden="true" />
          {t('shell:nav.profile')}
        </DropdownMenuLinkItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="danger" disabled={pending} onClick={() => void leave()}>
          <LogOut aria-hidden="true" />
          {t('shell:more.logout')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
