import { useTranslation } from 'react-i18next'

import { useCurrentUser } from '@/shared/api/useCurrentUser'

import { homeLabelKeyFor } from './sections'

/** Подпись ссылки «домой» для текущего пользователя каркаса («К расписанию», «На склад»…). */
export function useHomeLabel(): string {
  const { t } = useTranslation()
  return t(homeLabelKeyFor(useCurrentUser()))
}
