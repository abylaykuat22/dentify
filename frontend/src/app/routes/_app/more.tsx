import { createFileRoute } from '@tanstack/react-router'

import { MorePage } from '@/app/shell/MorePage'
import { pageTitle } from '@/shared/lib/title'

/**
 * Меню «Ещё» (макет Telefon-Eshche): пункт нижней панели телефона. Доступно каждому, без права:
 * в нём только то, что разрешил /api/v1/me. Своя шапка — карточка пользователя.
 */
export const Route = createFileRoute('/_app/more')({
  staticData: { ownHeader: true },
  head: () => ({ meta: [{ title: pageTitle('shell:nav.more') }] }),
  component: MorePage,
})
