import { createFileRoute } from '@tanstack/react-router'

import { ClinicStubPage } from '@/modules/clinic/ClinicStubPage'
import { tabTitle } from '@/shared/lib/title'

// Опции — литерал объекта: только так autoCodeSplitting выносит `component` в свой чанк.
export const Route = createFileRoute('/_app/clinic/dictionaries')({
  head: () => ({ meta: [{ title: tabTitle('shell:tabs.clinic.dictionaries', 'shell:nav.clinic') }] }),
  component: () => <ClinicStubPage soon />,
})
