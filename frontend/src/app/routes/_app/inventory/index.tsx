import { createFileRoute } from '@tanstack/react-router'

import { InventoryStubPage } from '@/modules/inventory/InventoryStubPage'
import { pageTitle } from '@/shared/lib/title'

// Опции — литерал объекта: только так autoCodeSplitting выносит `component` в свой чанк.
export const Route = createFileRoute('/_app/inventory/')({
  head: () => ({ meta: [{ title: pageTitle('shell:nav.inventory') }] }),
  component: InventoryStubPage,
})
