import { createFileRoute } from '@tanstack/react-router'

import { ProfilePage } from '@/modules/profile/ProfilePage'
import { pageTitle } from '@/shared/lib/title'

/** «Мой профиль» доступен каждому, без права. */
export const Route = createFileRoute('/_app/profile')({
  staticData: { titleKey: 'shell:nav.profile' },
  head: () => ({ meta: [{ title: pageTitle('shell:nav.profile') }] }),
  component: ProfilePage,
})
