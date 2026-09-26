import { Copy, TriangleAlert } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { GlobalError } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { cn } from '@/shared/lib/utils'

import { Alert, AlertTitle } from './alert'
import { Button } from './button'
import { copyText } from './toast'

interface FormAlertProps {
  /**
   * `form.formState.errors.root`: текст — `root.server`, код трассировки — `root.serverTrace` (их ставит
   * `applyApiErrorToForm`).
   */
  errors?: Record<string, GlobalError>
  className?: string
}

/**
 * Общая ошибка формы (PLT-07): красная плашка над кнопкой отправки — «Неверный телефон, e-mail или
 * пароль», конфликт версии, лимит попыток. Появляется в ответ на действие — `role="alert"`, диктор
 * её объявляет. Прислал сервер код трассировки (сбой 5xx) — он виден и копируется для поддержки.
 *
 * Фокус (WCAG 2.4.3): появившись, плашка забирает фокус — клавиатура продолжает с места ошибки, а не
 * с начала страницы. Кроме случая, когда фокус уже в поле: ошибки полей ставят его на первое
 * ошибочное (`applyApiErrorToForm`), его не отнимаем.
 */
function FormAlert({ errors, className }: FormAlertProps) {
  const message = errors?.server?.message
  if (!message) return null
  return <FormAlertBody message={message} traceId={errors.serverTrace?.message || null} className={className} />
}

function FormAlertBody({ message, traceId, className }: { message: string; traceId: string | null; className?: string }) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState<string | null>(null)
  const ref = useRef<HTMLDivElement>(null)

  // Только при появлении: смена языка переводит текст на месте и фокус не трогает.
  useEffect(() => {
    const active = document.activeElement
    const inField = active instanceof HTMLElement && active.matches('input, textarea, select, [contenteditable="true"]')
    if (!inField) ref.current?.focus()
  }, [])

  return (
    <Alert ref={ref} tabIndex={-1} variant="danger" data-slot="form-alert" className={cn('items-start', className)}>
      <TriangleAlert aria-hidden="true" />
      <span className="flex min-w-0 flex-col gap-1">
        <AlertTitle>{message}</AlertTitle>
        {traceId ? (
          <span className="flex flex-wrap items-center gap-x-2 text-caption font-semibold break-all">
            {t('errors:ui.traceId', { traceId })}
            <Button
              type="button"
              variant="danger"
              size="sm"
              className="-ml-2 underline decoration-2 underline-offset-4"
              onClick={() => void copyText(traceId).then((done) => setCopied(done ? traceId : null))}
            >
              <Copy aria-hidden="true" className="size-3.5" />
              {copied === traceId ? t('errors:ui.traceCopied') : t('errors:ui.copyTrace')}
            </Button>
          </span>
        ) : null}
      </span>
    </Alert>
  )
}

export { FormAlert, type FormAlertProps }
