import { createFileRoute } from '@tanstack/react-router'

import { ShellNotFound } from '@/app/shell/ShellNotFound'
import { pageTitle } from '@/shared/lib/title'

/** 404 внутри каркаса: сайдбар и шапка остаются, в контенте — «Такой страницы нет». */
export const Route = createFileRoute('/_app/$')({
  head: () => ({ meta: [{ title: pageTitle('common:titles.notFound') }] }),
  component: ShellNotFound,
})
