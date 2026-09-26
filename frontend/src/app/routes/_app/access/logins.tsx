import { createFileRoute } from '@tanstack/react-router'

import { AccessStubPage } from '@/modules/access/AccessStubPage'
import { tabTitle } from '@/shared/lib/title'

// Опции — литерал объекта: только так autoCodeSplitting выносит `component` в свой чанк.
export const Route = createFileRoute('/_app/access/logins')({
  head: () => ({ meta: [{ title: tabTitle('shell:tabs.access.logins', 'shell:nav.access') }] }),
  component: () => <AccessStubPage soon />,
})
