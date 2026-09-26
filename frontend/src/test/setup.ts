import '@testing-library/jest-dom/vitest'
import '@/shared/i18n'

import { focusManager } from '@tanstack/react-query'
import { cleanup, configure } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'

import { server } from '@/shared/api/mocks/node'

// findBy*/waitFor ждут дольше секунды по умолчанию: первая отрисовка приложения в файле — роутер,
// моки, словари, date-fns шапки — под нагрузкой параллельного прогона бывает дольше секунды. Предел
// теста (testTimeout в vite.config.ts) больше этого ожидания, чтобы упал `findBy` с понятным текстом.
configure({ asyncUtilTimeout: 10_000 })

// jsdom не умеет matchMedia и ResizeObserver — нужны сайдбару (medium-query) и Base UI.
if (typeof window.matchMedia !== 'function') {
  window.matchMedia = (query: string): MediaQueryList => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })
}

if (typeof globalThis.ResizeObserver === 'undefined') {
  class ResizeObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  globalThis.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver
}

// jsdom не прокручивает (scrollTo пишет «Not implemented» на каждый переход роутера со
// scrollRestoration) — заглушка без шума.
window.scrollTo = () => {}

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  cleanup()
  document.cookie = 'dentify_mock_user=; Max-Age=0; Path=/'
  sessionStorage.clear()
  focusManager.setFocused(undefined)
})
afterAll(() => server.close())
