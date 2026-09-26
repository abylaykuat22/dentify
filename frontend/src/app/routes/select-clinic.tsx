import { createFileRoute, redirect } from '@tanstack/react-router'
import * as z from 'zod/mini'

import { requireSession } from '@/app/guards'
import { SelectClinicPage } from '@/modules/access/SelectClinicPage'
import { isReturnPath } from '@/shared/lib/safeReturnTo'
import { pageTitle } from '@/shared/lib/title'

// returnTo — как у /login: только путь этого же приложения и не сам вход, иначе значение по умолчанию.
const selectClinicSearch = z.object({
  returnTo: z.catch(z.optional(z.string().check(z.refine((value) => isReturnPath(value)))), undefined),
})

/**
 * Выбор клиники сессии: сюда ведёт каркас, когда в /api/v1/me `context = null` (врач в нескольких
 * клиниках, клиника с несколькими филиалами). Клиника уже выбрана — сразу на returnTo.
 * TODO(T-IAM-08, T-PLT-17): записи на сегодня, кресла, последний выбор, смена филиала из каркаса.
 */
export const Route = createFileRoute('/select-clinic')({
  validateSearch: selectClinicSearch,
  beforeLoad: async ({ context, location, search }) => {
    const me = await requireSession({ context, location })
    if (me.clinic) throw redirect({ href: search.returnTo ?? '/', replace: true })
    return { me }
  },
  head: () => ({ meta: [{ title: pageTitle('auth:selectClinic.title') }] }),
  component: SelectClinicPage,
})
