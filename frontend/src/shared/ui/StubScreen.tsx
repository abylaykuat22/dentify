import type { LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import type { TKey } from '@/shared/i18n/keys'

import { StateScreen } from './StateScreen'

interface StubScreenProps {
  icon: LucideIcon
  titleKey: TKey
  textKey: TKey
  /** Вкладка, экран которой ещё не сделан: к тексту добавляется «Экран в разработке». */
  soon?: boolean
  className?: string
}

/** Пустое состояние экрана-заглушки (макет Sostoyaniya «а · Пустой список»). */
export function StubScreen({ icon, titleKey, textKey, soon = false, className }: StubScreenProps) {
  const { t } = useTranslation()
  const text = soon ? t('common:stub.withSoon', { text: t(textKey), soon: t('common:stub.soon') }) : t(textKey)
  return <StateScreen kind="empty" icon={icon} title={t(titleKey)} text={text} className={className} />
}
