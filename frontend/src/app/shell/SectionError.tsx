import { type ErrorComponentProps, useRouter } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { ForbiddenError } from '@/app/guards'
import { StateScreen } from '@/shared/ui/StateScreen'

import { sectionById } from './sections'
import { useHomeLabel } from './useHomeLabel'

/** errorComponent раздела: «нет доступа» для ForbiddenError, иначе — «нет связи» с повтором. */
export function SectionError({ error, reset }: ErrorComponentProps) {
  const { t } = useTranslation()
  const router = useRouter()
  const homeLabel = useHomeLabel()

  if (error instanceof ForbiddenError) {
    return (
      <StateScreen kind="forbidden" sectionLabel={t(sectionById(error.section).labelKey)} homeLabel={homeLabel} />
    )
  }

  return (
    <StateScreen
      kind="error"
      onRetry={() => {
        reset()
        void router.invalidate()
      }}
    />
  )
}
