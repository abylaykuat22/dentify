import { createFileRoute, stripSearchParams } from '@tanstack/react-router'
import * as z from 'zod/mini'

import { PatientsListPage } from '@/modules/patients/PatientsListPage'
import { PATIENT_FILTERS, PATIENTS_SEARCH_DEFAULTS } from '@/modules/patients/patientsSearch'
import { pageTitle } from '@/shared/lib/title'

// Состояние списка — в URL: /patients?filter=children&page=3 открывает тот же вид.
// Неверное значение (filter=abc, page=-1) заменяется значением по умолчанию, а не ломает экран.
const patientsSearch = z.object({
  filter: z.catch(z.enum(PATIENT_FILTERS), PATIENTS_SEARCH_DEFAULTS.filter),
  page: z.catch(z.int().check(z.minimum(1)), PATIENTS_SEARCH_DEFAULTS.page),
})

export const Route = createFileRoute('/_app/patients/')({
  validateSearch: patientsSearch,
  search: { middlewares: [stripSearchParams(PATIENTS_SEARCH_DEFAULTS)] },
  head: () => ({ meta: [{ title: pageTitle('shell:nav.patients') }] }),
  component: PatientsListPage,
})
