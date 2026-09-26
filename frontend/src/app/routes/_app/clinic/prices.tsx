import { createFileRoute } from '@tanstack/react-router'

import { ClinicStubPage } from '@/modules/clinic/ClinicStubPage'
import { tabTitle } from '@/shared/lib/title'

// Опции — литерал объекта: только так autoCodeSplitting выносит `component` в свой чанк.
export const Route = createFileRoute('/_app/clinic/prices')({
  head: () => ({ meta: [{ title: tabTitle('shell:tabs.clinic.prices', 'shell:nav.clinic') }] }),
  component: () => <ClinicStubPage soon />,
})
