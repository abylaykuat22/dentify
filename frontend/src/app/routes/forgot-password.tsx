import { createFileRoute } from '@tanstack/react-router'

import { AuthStubPage } from '@/modules/access/AuthStubPage'
import { pageTitle } from '@/shared/lib/title'

/** «Забыли пароль?» с экрана входа — без входа. TODO(T-IAM-17): сброс пароля по ссылке (макет Parol). */
export const Route = createFileRoute('/forgot-password')({
  head: () => ({ meta: [{ title: pageTitle('auth:forgot.title') }] }),
  component: () => <AuthStubPage kind="forgot" />,
})
