import { createRootRouteWithContext, HeadContent, Outlet } from '@tanstack/react-router'

import type { RouterContext } from '@/app/routerContext'
import { useSessionExpiry } from '@/app/sessionExpiry'
import { i18n } from '@/shared/i18n'
import { StateScreen } from '@/shared/ui/StateScreen'

export const Route = createRootRouteWithContext<RouterContext>()({
  head: () => ({ meta: [{ title: i18n.t('common:app.name') }] }),
  component: RootLayout,
  notFoundComponent: RootNotFound,
})

function RootLayout() {
  useSessionExpiry()
  return (
    <>
      <HeadContent />
      <Outlet />
    </>
  )
}

/** 404 вне каркаса (например, /login/лишнее); внутри каркаса — маршрут _app/$. */
function RootNotFound() {
  return (
    <div className="flex min-h-dvh items-center justify-center p-4">
      <StateScreen kind="notFound" bare />
    </div>
  )
}
