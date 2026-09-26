import { createFileRoute } from '@tanstack/react-router'

import { AccessStubPage } from '@/modules/access/AccessStubPage'
import { pageTitle } from '@/shared/lib/title'

// Опции — литерал объекта: только так autoCodeSplitting выносит `component` в свой чанк.
export const Route = createFileRoute('/_app/access/')({
  head: () => ({ meta: [{ title: pageTitle('shell:nav.access') }] }),
  component: AccessStubPage,
})
