import type { RootOptions } from 'react-dom/client'

import { ForbiddenError } from './guards'

/**
 * `onCaughtError` корня React. «Нет доступа» — не сбой: ForbiddenError бросает beforeLoad раздела,
 * его ловит errorComponent (SectionError) и рисует экран. Такую ошибку в консоль не пишем, чтобы
 * шум не прятал настоящие; остальные — как у React по умолчанию: ошибка и стек компонентов.
 */
export const onCaughtError: NonNullable<RootOptions['onCaughtError']> = (error, errorInfo) => {
  if (error instanceof ForbiddenError) return
  console.error(error, errorInfo.componentStack)
}
