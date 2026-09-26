import { createFileRoute } from '@tanstack/react-router'

import { ScheduleStubPage } from '@/modules/schedule/ScheduleStubPage'
import { tabTitle } from '@/shared/lib/title'

// Опции — литерал объекта: только так autoCodeSplitting выносит `component` в свой чанк.
export const Route = createFileRoute('/_app/schedule/week')({
  head: () => ({ meta: [{ title: tabTitle('shell:tabs.schedule.week', 'shell:nav.schedule') }] }),
  component: () => <ScheduleStubPage soon />,
})
