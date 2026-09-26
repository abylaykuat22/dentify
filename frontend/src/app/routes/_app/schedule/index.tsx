import { createFileRoute } from '@tanstack/react-router'

import { ScheduleStubPage } from '@/modules/schedule/ScheduleStubPage'
import { pageTitle } from '@/shared/lib/title'

// Опции — литерал объекта: только так autoCodeSplitting выносит `component` в свой чанк.
export const Route = createFileRoute('/_app/schedule/')({
  head: () => ({ meta: [{ title: pageTitle('shell:nav.schedule') }] }),
  component: ScheduleStubPage,
})
