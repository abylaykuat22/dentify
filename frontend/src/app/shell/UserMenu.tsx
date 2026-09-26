import { useLocation } from '@tanstack/react-router'
import { type PointerEvent, useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { whenIdle } from '@/app/idle'
import type { CurrentUser } from '@/shared/api/me'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/shared/ui/tooltip'

import type { UserCardProps, UserMenuDropdown, UserMenuHandoff } from './UserMenuDropdown'

// Само меню (Base UI Menu: портал, позиционирование, фокус, стрелки) — своим чанком: на первом экране
// оно закрыто, а в чанке каркаса стояло бы в цепочке до первого экрана (PLT-22, LCP). Грузится в простое
// после первого экрана (`whenIdle`, как боковая панель) или сразу, как только к карточке потянулись.
let dropdown: typeof UserMenuDropdown | undefined
const loadDropdown = () =>
  import('./UserMenuDropdown').then((module) => {
    dropdown = module.UserMenuDropdown
  })

/**
 * Меню пользователя внизу сайдбара (IAM-03): карточка с аватаром и ФИО открывает меню «Мой профиль» и
 * «Выйти» (`UserMenuDropdown`). На 768–1023 px карточка — только аватар с подсказкой и меню справа от
 * сайдбара. На «Моём профиле» карточка подсвечена, как активный пункт.
 *
 * Пока чанк меню не пришёл, на месте карточки — та же карточка простой кнопкой (вид, подпись, подсказка
 * те же). Потянулись к ней — чанк грузится сразу; когда он придёт, карточка меняется на кнопку меню и
 * доигрывает то, что человек уже сделал: нажал (мышь, Enter, пробел, стрелка) — меню открывается, фокус
 * был на карточке — остаётся на ней. Под нажатым указателем кнопка не меняется: клик ушёл бы мимо обеих.
 */
export function UserMenu({ user, collapsed }: { user: CurrentUser; collapsed: boolean }) {
  const { t } = useTranslation()
  const onProfile = useLocation({ select: (location) => location.pathname === '/profile' })
  const label = t('shell:nav.userMenu', { name: user.fullName })
  const [handoff, setHandoff] = useState<UserMenuHandoff | null>(() => (dropdown ? 'none' : null))
  const placeholder = useRef<HTMLButtonElement>(null)
  const intent = useRef({ pressed: false, open: false })

  const settle = useCallback(() => {
    if (!dropdown || intent.current.pressed) return
    const focused = placeholder.current !== null && placeholder.current === document.activeElement
    const next: UserMenuHandoff = intent.current.open ? 'open' : focused ? 'focus' : 'none'
    setHandoff((current) => current ?? next)
  }, [])
  // Не загрузился (нет сети) — остаётся простая карточка, без ошибки в консоли.
  const load = useCallback(() => void loadDropdown().then(settle, () => undefined), [settle])
  useEffect(() => whenIdle(load), [load])

  const card: UserCardProps = {
    'aria-label': label,
    'data-sidebar-item': true,
    'data-current': onProfile || undefined,
    className:
      'flex items-center gap-2.5 rounded-row bg-white/8 p-2 text-left hover:bg-white/15 data-current:bg-white/20 aria-expanded:bg-white/20 lg:p-3',
    children: (
      <>
        <span
          aria-hidden="true"
          className="flex size-9.5 shrink-0 items-center justify-center rounded-full bg-lamp font-heading text-body-sm font-bold text-ink"
        >
          {user.initials}
        </span>
        <span aria-hidden="true" className="hidden min-w-0 lg:block">
          {/* ФИО целиком (фамилия первой, как в API), 14 px, как в макете Raspisanie-Den: обычное имя
              встаёт в строку, длинное переносится на вторую, а не обрезается. */}
          <span className="line-clamp-2 text-body-sm font-bold break-words">{user.fullName}</span>
          {/* Роли в /me пока нет (TODO в shared/api/me.ts) — тогда строка не выводится. */}
          {user.role ? <span className="block truncate text-caption text-lagoon-muted">{user.role.name}</span> : null}
        </span>
      </>
    ),
  }

  // Обёртка — постоянный узел в карточке сайдбара: подмена кнопки внутри неё не сбивает наблюдение за
  // размерами (`keepActiveInView` в Sidebar.tsx) и прижимает карточку к низу.
  if (handoff !== null && dropdown) {
    const Menu = dropdown
    return (
      <div className="mt-auto flex flex-col">
        <Menu card={card} label={label} collapsed={collapsed} onProfile={onProfile} handoff={handoff} />
      </div>
    )
  }

  function press(event: PointerEvent) {
    intent.current.pressed = true
    load()
    // Отпустили мимо карточки (клика не будет) — подменить можно. Клик мыши приходит в той же задаче,
    // что и отпускание, тап пальцем — позже, отдельным жестом: ему — запас.
    const released = new AbortController()
    const release = (delay: number) => {
      released.abort()
      window.setTimeout(() => {
        intent.current.pressed = false
        settle()
      }, delay)
    }
    const options = { capture: true, signal: released.signal }
    document.addEventListener('pointerup', () => release(event.pointerType === 'mouse' ? 0 : 500), options)
    document.addEventListener('pointercancel', () => release(0), options)
  }

  function open() {
    intent.current.pressed = false
    intent.current.open = true
    load()
  }

  return (
    <div className="mt-auto flex flex-col">
      <Tooltip disabled={!collapsed}>
        <TooltipTrigger
          ref={placeholder}
          render={<button type="button" />}
          data-user-menu="placeholder"
          aria-haspopup="menu"
          aria-expanded={false}
          onPointerEnter={load}
          onFocus={load}
          onPointerDown={press}
          onClick={open}
          onKeyDown={(event) => {
            if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return
            event.preventDefault()
            open()
          }}
          {...card}
        />
        <TooltipContent side="right">{label}</TooltipContent>
      </Tooltip>
    </div>
  )
}
