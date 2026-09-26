import { createFileRoute } from '@tanstack/react-router'
import * as z from 'zod/mini'

import { LoginPage } from '@/modules/access/LoginPage'
import { isReturnPath } from '@/shared/lib/safeReturnTo'
import { pageTitle } from '@/shared/lib/title'

// returnTo — только путь внутри этого же приложения. //host, http://…, «/\host» (браузер
// читает «\» как «/») и управляющие символы — значение по умолчанию: ссылкой на вход нельзя
// увести на чужой сайт. Сам вход (`/login`) — тоже: после входа — в кабинет, а не снова на форму.
const loginSearch = z.object({
  returnTo: z.catch(z.optional(z.string().check(z.refine((value) => isReturnPath(value)))), undefined),
  // Почему попали на вход: `expired` — сессия кончилась посреди работы (app/sessionExpiry.ts),
  // экран показывает «Сеанс истёк». Другие значения отбрасываются.
  reason: z.catch(z.optional(z.enum(['expired'])), undefined),
})

export const Route = createFileRoute('/login')({
  validateSearch: loginSearch,
  head: () => ({ meta: [{ title: pageTitle('auth:login.title') }] }),
  component: LoginPage,
})
