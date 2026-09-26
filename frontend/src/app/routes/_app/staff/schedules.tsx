import { createFileRoute } from '@tanstack/react-router'

import { StaffStubPage } from '@/modules/staff/StaffStubPage'
import { tabTitle } from '@/shared/lib/title'

// Опции — литерал объекта: только так autoCodeSplitting выносит `component` в свой чанк.
export const Route = createFileRoute('/_app/staff/schedules')({
  head: () => ({ meta: [{ title: tabTitle('shell:tabs.staff.schedules', 'shell:nav.staff') }] }),
  component: () => <StaffStubPage soon />,
})
