import { createFileRoute } from '@tanstack/react-router'

import { InventoryStubPage } from '@/modules/inventory/InventoryStubPage'
import { tabTitle } from '@/shared/lib/title'

// Опции — литерал объекта: только так autoCodeSplitting выносит `component` в свой чанк.
export const Route = createFileRoute('/_app/inventory/documents')({
  head: () => ({ meta: [{ title: tabTitle('shell:tabs.inventory.documents', 'shell:nav.inventory') }] }),
  component: () => <InventoryStubPage soon />,
})
