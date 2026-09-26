import { createFileRoute, redirect } from '@tanstack/react-router'

import { homePathFor } from '@/app/shell/sections'

/** Корень → первый доступный раздел (обычно «Расписание»). */
export const Route = createFileRoute('/_app/')({
  beforeLoad: ({ context }) => {
    throw redirect({ to: homePathFor(context.me), replace: true })
  },
})
