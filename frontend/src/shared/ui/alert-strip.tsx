import { TriangleAlert } from 'lucide-react'
import type * as React from 'react'
import { useTranslation } from 'react-i18next'

import { Alert, AlertDescription, AlertTitle } from './alert'

interface AlertStripProps {
  /** Главное — жирным («Аллергия на лидокаин.»). */
  title: React.ReactNode
  /** Пояснение после заголовка. */
  children?: React.ReactNode
  /** `compact` — строкой под именем пациента (макет Telefon-Zapis). */
  size?: 'default' | 'compact'
  className?: string
}

/**
 * Красная плашка аллергии (UX-принцип 7: видна везде, где есть пациент). Постоянная
 * информация, а не событие: `role="note"`, диктор читает её вместе с карточкой, а не
 * объявляет при каждом открытии. Слово «Внимание» для диктора — в скрытом префиксе.
 */
function AlertStrip({ title, children, size = 'default', className }: AlertStripProps) {
  const { t } = useTranslation()

  if (size === 'compact') {
    return (
      <p role="note" data-slot="alert-strip" className="flex items-center gap-1.5 text-caption font-extrabold text-danger">
        <TriangleAlert className="size-3.5 shrink-0" aria-hidden="true" />
        <span className="sr-only">{t('common:ui.alert.prefix')}</span>
        {title}
      </p>
    )
  }

  return (
    <Alert variant="danger" role="note" data-slot="alert-strip" className={className}>
      <TriangleAlert aria-hidden="true" />
      <span>
        <span className="sr-only">{t('common:ui.alert.prefix')}</span>
        <AlertTitle>{title}</AlertTitle> {children ? <AlertDescription>{children}</AlertDescription> : null}
      </span>
    </Alert>
  )
}

export { AlertStrip, type AlertStripProps }
