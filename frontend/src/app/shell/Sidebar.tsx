import { Link, useRouter } from '@tanstack/react-router'
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'

import type { CurrentUser } from '@/shared/api/me'
import { MEDIA } from '@/shared/lib/breakpoints'
import { useMediaQuery } from '@/shared/lib/useMediaQuery'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/shared/ui/tooltip'

import { homePathFor, type Section, visibleSections } from './sections'

/**
 * Плавающий сайдбар «лагуна» по макету Raspisanie-Den: 6 разделов, активный — «лампа»,
 * внизу пользователь → «Мой профиль». На 768–1023 px сворачивается до иконок с подсказками
 * (Base UI Tooltip открывается и с клавиатуры, по фокусу). Ниже 768 px скрыт — там BottomNav.
 *
 * Окно ниже сайдбара (телефон боком — это вид планшета, масштаб 200 %: PLT-21) — карточка не
 * вылезает за окно, а прокручивается сама (колесо, палец, Tab), страница при этом не двигается;
 * полосы прокрутки нет, как у вкладок: в карточке 72 px она отняла бы место у иконок и аватара.
 * Активный пункт подъезжает в видимую часть карточки (`keepActiveInView`).
 */
export function Sidebar({ user }: { user: CurrentUser }) {
  const { t } = useTranslation()
  const router = useRouter()
  const collapsed = useMediaQuery(MEDIA.belowDesktop)
  const sections = visibleSections(user)
  // После перехода роутер переносит прокрутку карточки со старого адреса — доводим после него.
  const cardRef = useCallback(
    (card: HTMLElement | null) => keepActiveInView(card, (reveal) => router.subscribe('onRendered', reveal)),
    [router],
  )

  return (
    <aside className="sticky top-0 hidden h-dvh shrink-0 p-4 pl-[max(1rem,env(safe-area-inset-left))] md:block" data-sidebar={collapsed ? 'collapsed' : 'expanded'}>
      <div
        ref={cardRef}
        data-sidebar-card
        className="surface-dark flex h-full w-18 flex-col overflow-y-auto overscroll-contain rounded-card bg-lagoon px-2.5 py-5 text-white [scrollbar-width:none] lg:w-54 lg:px-3.5 lg:py-6 [&::-webkit-scrollbar]:hidden"
      >
        <Link
          to={homePathFor(user)}
          aria-label={t('common:app.name')}
          className="self-center font-heading text-logo font-bold lg:self-start lg:px-2.5"
        >
          <span className="hidden lg:inline">{t('common:app.logo')}</span>
          <span className="lg:hidden">{t('common:app.logoShort')}</span>
          <span className="text-lamp">.</span>
        </Link>

        {/* mb-3 — просвет до карточки профиля, когда карточка сайдбара прокручивается (mt-auto = 0). */}
        <nav aria-label={t('shell:nav.label')} className="mt-7 mb-3 flex flex-col gap-1">
          {sections.map((section) => (
            <SidebarItem key={section.id} section={section} collapsed={collapsed} />
          ))}
        </nav>

        <Tooltip disabled={!collapsed}>
          <TooltipTrigger
            render={<Link to="/profile" activeOptions={{ includeSearch: false }} />}
            aria-label={t('shell:nav.profileOf', { name: user.fullName })}
            data-sidebar-item
            className="mt-auto flex items-center gap-2.5 rounded-row bg-white/8 p-2 text-left hover:bg-white/15 data-[status=active]:bg-white/20 lg:p-3"
          >
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
              {user.role ? (
                <span className="block truncate text-caption text-lagoon-muted">{user.role.name}</span>
              ) : null}
            </span>
          </TooltipTrigger>
          <TooltipContent side="right">{t('shell:nav.profileOf', { name: user.fullName })}</TooltipContent>
        </Tooltip>
      </div>
    </aside>
  )
}

function SidebarItem({ section, collapsed }: { section: Section; collapsed: boolean }) {
  const { t } = useTranslation()
  const label = t(section.labelKey)

  return (
    <Tooltip disabled={!collapsed}>
      <TooltipTrigger
        render={<Link to={section.to} activeOptions={{ includeSearch: false }} />}
        aria-label={label}
        data-section={section.id}
        data-sidebar-item
        className="flex h-12 items-center justify-center gap-3 rounded-item text-body font-semibold text-lagoon-muted transition-colors hover:bg-white/10 hover:text-white data-[status=active]:bg-lamp data-[status=active]:font-bold data-[status=active]:text-ink data-[status=active]:hover:bg-lamp lg:justify-start lg:px-3.5"
      >
        <section.icon className="size-5 shrink-0" strokeWidth={1.9} aria-hidden="true" />
        <span className="hidden lg:inline">{label}</span>
      </TooltipTrigger>
      <TooltipContent side="right">{label}</TooltipContent>
    </Tooltip>
  )
}

/** Активный пункт: раздел или карточка профиля (логотип — ссылка на домашний раздел — не в счёт). */
const ACTIVE_ITEM = '[data-sidebar-item][aria-current="page"]'

/**
 * Показать активный пункт целиком в прокручиваемой карточке сайдбара — только внутри неё, от
 * текущего места ровно настолько, насколько нужно (как `scrollIntoView({ block: 'nearest' })`,
 * но страница не двигается). Запас у края — внутренний отступ карточки: у крайних пунктов виден
 * её край, а не обрез.
 */
function revealActive(card: HTMLElement) {
  const active = card.querySelector<HTMLElement>(ACTIVE_ITEM)
  if (!active) return
  const style = getComputedStyle(card)
  const outer = card.getBoundingClientRect()
  const inner = active.getBoundingClientRect()
  const top = outer.top + parseFloat(style.paddingTop)
  const bottom = outer.bottom - parseFloat(style.paddingBottom)
  if (inner.top < top) card.scrollTop -= top - inner.top
  else if (inner.bottom > bottom) card.scrollTop += inner.bottom - bottom
}

/**
 * Ref-колбэк карточки сайдбара: активный пункт подъезжает в видимую часть при появлении карточки,
 * после каждого перехода (`onNavigate` — подписка на `onRendered` роутера) и при смене размеров
 * (окно стало ниже, повернули телефон, догрузился шрифт подписи профиля). Прокрутку руками не
 * перебивает: пока размеры и адрес те же, карточка не двигается. Высокое окно — ничего не делает.
 */
function keepActiveInView(
  card: HTMLElement | null,
  onNavigate: (reveal: () => void) => () => void,
): (() => void) | undefined {
  if (!card) return undefined
  const reveal = () => revealActive(card)
  const sizes = new ResizeObserver(reveal)
  sizes.observe(card)
  for (const child of Array.from(card.children)) sizes.observe(child)
  const unsubscribe = onNavigate(reveal)
  reveal()
  return () => {
    unsubscribe()
    sizes.disconnect()
  }
}
