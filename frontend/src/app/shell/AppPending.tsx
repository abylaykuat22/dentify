import { useTranslation } from 'react-i18next'

export function AppPending() {
  const { t } = useTranslation()
  return (
    <div role="status" className="flex min-h-dvh items-center justify-center text-graphite">
      {t('common:states.loading')}
    </div>
  )
}
