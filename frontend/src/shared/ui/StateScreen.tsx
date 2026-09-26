import { Link } from '@tanstack/react-router'
import { CircleX, Lock, type LucideIcon, RefreshCw, WifiOff } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/shared/lib/utils'

import { Button, buttonVariants } from './button'

type StateKind = 'forbidden' | 'notFound' | 'error' | 'empty'

interface StateScreenProps {
  kind: StateKind
  /** Заголовок и текст для `empty` (для остальных берутся из словаря). */
  title?: string
  text?: string
  icon?: LucideIcon
  /** Название раздела для «Раздел «…» закрыт для вашей роли». */
  sectionLabel?: string
  /**
   * Подпись ссылки «домой» у «нет доступа» и 404 («К расписанию», «На склад»…). Ссылка
   * ведёт на `/`, а он — в первый доступный роли раздел (homePathFor); без подписи — «На главную».
   */
  homeLabel?: string
  onRetry?: () => void
  /** Без карточки — для состояний вне каркаса (например, 404 без сессии). */
  bare?: boolean
  className?: string
}

const ICONS: Record<Exclude<StateKind, 'empty'>, LucideIcon> = {
  forbidden: Lock,
  notFound: CircleX,
  error: WifiOff,
}

/** Состояния экрана по макету Sostoyaniya: пусто, нет доступа, нет связи, нет страницы. */
export function StateScreen({
  kind,
  title,
  text,
  icon,
  sectionLabel,
  homeLabel,
  onRetry,
  bare,
  className,
}: StateScreenProps) {
  const { t } = useTranslation()
  const Icon = icon ?? (kind === 'empty' ? CircleX : ICONS[kind])

  const heading =
    kind === 'forbidden'
      ? t('common:states.forbidden.title', { section: sectionLabel ?? '' })
      : kind === 'notFound'
        ? t('common:states.notFound.title')
        : kind === 'error'
          ? t('common:states.error.title')
          : (title ?? '')

  const body =
    kind === 'forbidden'
      ? t('common:states.forbidden.text')
      : kind === 'notFound'
        ? t('common:states.notFound.text')
        : kind === 'error'
          ? t('common:states.error.text')
          : text

  return (
    <section
      data-state-screen={kind}
      className={cn(
        'flex flex-col items-center justify-center gap-4 px-6 py-16 text-center',
        !bare && 'rounded-card bg-card',
        className,
      )}
    >
      <span className="flex size-22 items-center justify-center rounded-full bg-fog text-lagoon">
        <Icon className="size-9" strokeWidth={1.8} aria-hidden="true" />
      </span>
      <h2 className="font-heading text-h3 font-semibold text-ink">{heading}</h2>
      {body ? <p className="max-w-md text-body font-semibold text-graphite">{body}</p> : null}
      {kind === 'forbidden' || kind === 'notFound' ? (
        <Link to="/" data-home-link className={cn(buttonVariants({ variant: 'outline' }), 'mt-2')}>
          {homeLabel ?? t('common:states.home')}
        </Link>
      ) : null}
      {kind === 'error' && onRetry ? (
        <Button variant="outline" className="mt-2" onClick={onRetry}>
          <RefreshCw aria-hidden="true" />
          {t('common:states.error.action')}
        </Button>
      ) : null}
    </section>
  )
}
