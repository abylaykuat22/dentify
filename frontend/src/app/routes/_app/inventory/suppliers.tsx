import { createFileRoute } from '@tanstack/react-router'

import { InventoryStubPage } from '@/modules/inventory/InventoryStubPage'
import { tabTitle } from '@/shared/lib/title'

// Опции — литерал объекта: только так autoCodeSplitting выносит `component` в свой чанк.
export const Route = createFileRoute('/_app/inventory/suppliers')({
  head: () => ({ meta: [{ title: tabTitle('shell:tabs.inventory.suppliers', 'shell:nav.inventory') }] }),
  component: () => <InventoryStubPage soon />,
})
