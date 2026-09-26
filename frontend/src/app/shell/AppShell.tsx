import { Outlet, useRouterState } from '@tanstack/react-router'
import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { hasPermission } from '@/shared/api/me'
import { useCurrentUser } from '@/shared/api/useCurrentUser'
import { cn } from '@/shared/lib/utils'

import { whenIdle } from '@/app/idle'

import { BottomNav } from './BottomNav'
import { Header } from './Header'
import { useScreenOptions } from './screen'
import { sectionByPath } from './sections'
import { Sidebar } from './Sidebar'
import { usePanel } from './usePanel'
import { useSessionSync } from './useSessionSync'

// Боковая панель (Base UI Drawer, поле поиска) — своим чанком: на первом экране она закрыта, и
// тянуть её до отрисовки незачем (PLT-22, LCP). Чанк догружается в простое после первого экрана,
// так что Ctrl K открывает панель без задержки.
const loadSidePanel = () => import('./SidePanel')
const SidePanel = lazy(() => loadSidePanel().then((module) => ({ default: module.SidePanel })))


/**
 * Каркас по макету Raspisanie-Den: плавающий сайдбар слева; справа от него сверху — шапка
 * и вкладки на всю ширину, под ними две колонки: контент и боковая панель (≥ 1024 px),
 * верх панели выровнен по карточке контента. Открытие панели не двигает шапку и вкладки.
 * Пользователь — живой (useCurrentUser): /me, перезапрошенный при фокусе вкладки или после 403,
 * сразу меняет меню, а гарды перепроверяет useSessionSync.
 */
export function AppShell() {
  const { t } = useTranslation()
  const me = useCurrentUser()
  useSessionSync()
  const pathname = useRouterState({ select: (state) => state.location.pathname })
  const { panel } = usePanel()
  const panelSlot = useRef<HTMLDivElement>(null)
  const screen = useScreenOptions()
  // Панель монтируется при первом открытии и дальше остаётся: закрытие проигрывает анимацию, а
  // фокус возвращается на кнопку, которая её открыла.
  const [panelMounted, setPanelMounted] = useState(panel !== undefined)
  if (panel !== undefined && !panelMounted) setPanelMounted(true)
  useEffect(() => whenIdle(() => void loadSidePanel()), [])

  const section = sectionByPath(pathname)
  // Закрытый для роли раздел: только заголовок — без его вкладок и главного действия (R002:
  // что разрешено, говорит API; гард раздела при этом показывает «нет доступа»).
  const allowed = section && hasPermission(me, section.permission) ? section : undefined
  // Заголовок: экрана (staticData.titleKey: «Новая запись», «Мой профиль»), иначе раздела.
  const title = screen.titleKey ? t(screen.titleKey) : section ? t(section.labelKey) : t('common:titles.notFound')

  return (
    <div className="flex min-h-dvh">
      <Sidebar user={me} />
      {/* Телефон: снизу место под плавающую нижнюю панель, по краям и сверху — safe-area iOS. */}
      <div className="flex min-w-0 flex-1 flex-col pt-[max(1rem,env(safe-area-inset-top))] pr-[max(1rem,env(safe-area-inset-right))] pb-[calc(7rem+env(safe-area-inset-bottom))] pl-[max(1rem,env(safe-area-inset-left))] md:pt-6 md:pr-[max(1.5rem,env(safe-area-inset-right))] md:pb-6 md:pl-2">
        {screen.ownHeader ? null : <Header user={me} section={allowed} title={title} bare={screen.bare} />}
        <div
          data-shell-body
          className={cn(
            'flex-1 lg:grid lg:gap-4',
            !screen.ownHeader && 'mt-4',
            panel && 'lg:grid-cols-[minmax(0,1fr)_340px]',
          )}
        >
          <main className="min-w-0">
            <Outlet />
          </main>
          <div ref={panelSlot} data-panel-slot className={panel ? 'hidden min-h-0 lg:block' : 'hidden'} />
        </div>
      </div>
      {panelMounted ? (
        <Suspense fallback={null}>
          <SidePanel inlineSlot={panelSlot} />
        </Suspense>
      ) : null}
      <BottomNav user={me} />
    </div>
  )
}
