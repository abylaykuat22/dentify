import { createFileRoute, notFound } from '@tanstack/react-router'

import { DevUiPage } from '@/dev/ui/DevUiPage'

/**
 * Витрина дизайн-системы «Кресло» (макет Sistema) — только в dev-сборке (`pnpm dev`, e2e).
 * В production адрес отвечает 404, а код витрины вырезает сборка: `import.meta.env.DEV`
 * там `false`, ветка с `DevUiPage` мёртвая (проверяет scripts/check-build.mjs).
 */
export const Route = createFileRoute('/dev/ui')({
  beforeLoad: () => {
    if (!import.meta.env.DEV) throw notFound()
  },
  component: DevUiRoute,
})

function DevUiRoute() {
  return import.meta.env.DEV ? <DevUiPage /> : null
}
