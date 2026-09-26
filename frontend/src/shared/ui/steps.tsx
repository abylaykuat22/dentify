import { ArrowLeft } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/shared/lib/utils'

import { Button } from './button'

/** Причины закрытия, которые в шаге означают «шаг назад», а не «закрыть всё» (R023). */
const BACK_REASONS = new Set(['escape-key', 'outside-press', 'close-watcher'])

/**
 * Chrome с CloseWatcher (телефоны) на одно нажатие Escape присылает два события: `escape-key`
 * и следом `close-watcher`. Первое уже вернуло шаг — второе, пришедшее сразу за ним, гасим,
 * иначе оно закрыло бы окно с только что открытого предыдущего шага.
 */
const ECHO_MS = 300
let lastStepBackAt = Number.NEGATIVE_INFINITY

interface CancelableChange {
  reason: string
  cancel: () => void
}

/**
 * R023: в шаге мастера Escape, клик по затемнению и системная «Назад» (Android) возвращают на
 * предыдущий шаг, а не закрывают окно; крестик и смахивание закрывают всё. На первом шаге
 * (`onBack` не передан) — обычное закрытие.
 *
 * ```tsx
 * <Dialog open={open} onOpenChange={stepAwareOpenChange(setOpen, step > 1 ? back : undefined)}>
 * ```
 */
export function stepAwareOpenChange<Details extends CancelableChange>(
  onOpenChange: (open: boolean, details: Details) => void,
  onBack?: () => void,
) {
  return (open: boolean, details: Details) => {
    if (!open && details.reason === 'close-watcher' && performance.now() - lastStepBackAt < ECHO_MS) {
      details.cancel()
      return
    }
    if (!open && onBack && BACK_REASONS.has(details.reason)) {
      details.cancel()
      lastStepBackAt = performance.now()
      onBack()
      return
    }
    onOpenChange(open, details)
  }
}

/** «← Назад» в подвале шага (R023): один вид во всём приложении, подсказка — что произойдёт. */
export function StepBackButton({ onClick, className }: { onClick: () => void; className?: string }) {
  const { t } = useTranslation()
  return (
    <Button
      variant="ghost"
      size="compact"
      onClick={onClick}
      title={t('common:ui.steps.backHint')}
      data-step-back
      className={cn('-ml-2', className)}
    >
      <ArrowLeft aria-hidden="true" />
      {t('common:ui.back')}
    </Button>
  )
}

export interface StepProgressProps {
  /** Текущий шаг, с 1. */
  current: number
  total: number
  /** Название текущего шага: «Врач и время». */
  label?: string
  className?: string
}

/** «● ▬ ● Шаг 2 из 3 · Врач и время» (макет Telefon-Zapis). */
export function StepProgress({ current, total, label, className }: StepProgressProps) {
  const { t } = useTranslation()
  const text = label
    ? t('common:ui.steps.progressWithLabel', { current, total, label })
    : t('common:ui.steps.progress', { current, total })

  return (
    <div data-slot="step-progress" className={cn('flex items-center gap-2', className)}>
      <span aria-hidden="true" className="flex items-center gap-1">
        {Array.from({ length: total }, (_, index) => {
          const step = index + 1
          return (
            <span
              key={step}
              className={cn(
                'h-2 rounded-full',
                step === current ? 'w-6 bg-ink' : 'w-2',
                step < current && 'bg-ink',
                step > current && 'bg-line',
              )}
            />
          )
        })}
      </span>
      <span className="text-caption font-bold text-graphite">{text}</span>
    </div>
  )
}
