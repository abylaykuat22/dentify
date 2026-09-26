import { useLocation, useNavigate, useRouter, useSearch } from '@tanstack/react-router'
import { useCallback } from 'react'

import type { PanelName } from './panels'

export { SEARCH_PANEL } from './panels'

declare module '@tanstack/react-router' {
  interface HistoryState {
    /** Запись истории создана `open()`: закрыть панель = вернуться на шаг назад. */
    panelPushed?: boolean
  }
}

// Открытая панель живёт в URL (`?panel=…`): ссылкой можно поделиться, «Назад» браузера
// закрывает панель, а не уходит со страницы. Открытие — push в историю с пометкой в
// history.state; закрытие — history.back(), если текущая запись создана open(), иначе
// (пришли по ссылке, перезагрузили) — replace без параметра, чтобы не уйти со страницы.
export function usePanel() {
  const router = useRouter()
  const navigate = useNavigate()
  const panel = useSearch({
    strict: false,
    select: (search) => (search as { panel?: PanelName }).panel,
  })
  const pushed = useLocation({ select: (location) => location.state.panelPushed === true })

  const open = useCallback(
    (name: PanelName) => {
      void navigate({
        to: '.',
        search: (prev) => ({ ...prev, panel: name }),
        state: (prev) => ({ ...prev, panelPushed: true }),
      })
    },
    [navigate],
  )

  const close = useCallback(() => {
    if (pushed && router.history.canGoBack()) {
      router.history.back()
      return
    }
    void navigate({
      to: '.',
      search: (prev) => ({ ...prev, panel: undefined }),
      state: (prev) => ({ ...prev, panelPushed: false }),
      replace: true,
    })
  }, [navigate, pushed, router])

  return { panel, open, close }
}
