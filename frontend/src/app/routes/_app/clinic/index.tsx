import { createFileRoute } from '@tanstack/react-router'

import { ClinicStubPage } from '@/modules/clinic/ClinicStubPage'
import { pageTitle } from '@/shared/lib/title'

// Опции — литерал объекта: только так autoCodeSplitting выносит `component` в свой чанк.
export const Route = createFileRoute('/_app/clinic/')({
  head: () => ({ meta: [{ title: pageTitle('shell:nav.clinic') }] }),
  component: ClinicStubPage,
})
