import { StateScreen } from '@/shared/ui/StateScreen'

import { useHomeLabel } from './useHomeLabel'

/** 404 внутри каркаса: сайдбар и шапка остаются, в контенте — «Такой страницы нет». */
export function ShellNotFound() {
  return <StateScreen kind="notFound" homeLabel={useHomeLabel()} />
}
