import { createFileRoute } from '@tanstack/react-router'

import { StaffStubPage } from '@/modules/staff/StaffStubPage'
import { pageTitle } from '@/shared/lib/title'

// Опции — литерал объекта: только так autoCodeSplitting выносит `component` в свой чанк.
export const Route = createFileRoute('/_app/staff/')({
  head: () => ({ meta: [{ title: pageTitle('shell:nav.staff') }] }),
  component: StaffStubPage,
})
