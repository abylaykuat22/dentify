import { createFileRoute } from '@tanstack/react-router'

import { AuthStubPage } from '@/modules/access/AuthStubPage'
import { pageTitle } from '@/shared/lib/title'

/** «Зарегистрировать» с экрана входа — без входа. TODO(T-CLN-05): мастер регистрации клиники. */
export const Route = createFileRoute('/register')({
  head: () => ({ meta: [{ title: pageTitle('auth:register.title') }] }),
  component: () => <AuthStubPage kind="register" />,
})
