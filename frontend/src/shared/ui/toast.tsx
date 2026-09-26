import { Toast as ToastPrimitive } from '@base-ui/react/toast'
import { Check, TriangleAlert } from 'lucide-react'
import type * as React from 'react'
import { useTranslation } from 'react-i18next'

import { type ApiErrorDescription, describeApiError } from '@/shared/api/problem'
import { i18n } from '@/shared/i18n'
import { cn } from '@/shared/lib/utils'

import { Button } from './button'

/**
 * Уведомления «Кресла» (shadcn Toast на Base UI): тёмная пилюля «чернила» с жёлтой
 * кнопкой действия. Вместо окон «Вы уверены?» — действие выполняется сразу, а уведомление
 * предлагает «Вернуть» (design-directions, UX-принцип 10): `toastUndo(...)`.
 *
 * Base UI держит список в живой области (PLT-21: уведомления объявляются диктору), ставит
 * таймер на паузу при наведении и фокусе, позволяет смахнуть; `Toaster` подключён в App.
 */

type ToastManager = ReturnType<typeof ToastPrimitive.createToastManager>
type ToastManagerEvent = Parameters<Parameters<ToastManager[' subscribe']>[0]>[0]

/**
 * Менеджер Base UI без очереди: `add` и `close` просто зовут текущих подписчиков, а
 * подписывается `Toaster`, который App грузит лениво. Чтобы уведомление, вызванное раньше
 * (ошибка первого запроса на медленной сети), не терялось, события копятся, пока нет ни
 * одного `Toaster`, и отдаются первому подписавшемуся.
 */
function createBufferedToastManager(): ToastManager {
  const manager = ToastPrimitive.createToastManager()
  const subscribe = manager[' subscribe']
  let pending: ToastManagerEvent[] = []
  let subscribers = 0
  let stopBuffering: (() => void) | null = null
  const startBuffering = () => {
    stopBuffering = subscribe((event) => pending.push(event))
  }
  startBuffering()

  return {
    ...manager,
    ' subscribe'(listener) {
      const unsubscribe = subscribe(listener)
      subscribers += 1
      if (stopBuffering) {
        stopBuffering()
        stopBuffering = null
        const missed = pending
        pending = []
        missed.forEach(listener)
      }
      let active = true
      return () => {
        if (!active) return
        active = false
        unsubscribe()
        subscribers -= 1
        if (subscribers === 0) startBuffering()
      }
    },
  }
}

const toast = createBufferedToastManager()

/** Внешний вид пилюли — общий для живого уведомления и образца на витрине. */
const toastPillClass =
  'surface-dark mx-auto flex min-h-13 w-fit max-w-full items-center gap-3.5 rounded-pill bg-ink py-1.5 pr-5 pl-5 text-body font-bold text-white shadow-window has-data-[slot=toast-action]:pr-1.5 md:mr-0 md:ml-auto'

const TOAST_ICONS: Record<string, React.ReactNode> = {
  success: <Check className="size-[18px] shrink-0 text-lamp" strokeWidth={2.6} aria-hidden="true" />,
  undo: <Check className="size-[18px] shrink-0 text-lamp" strokeWidth={2.6} aria-hidden="true" />,
  error: <TriangleAlert className="size-[18px] shrink-0 text-lamp" aria-hidden="true" />,
}

function ToastViewport({ className, ...props }: ToastPrimitive.Viewport.Props) {
  return (
    <ToastPrimitive.Viewport
      data-slot="toast-viewport"
      className={cn(
        // На телефоне — над нижней панелью навигации, на компьютере — в правом нижнем углу.
        'pointer-events-none fixed inset-x-4 bottom-[calc(6.5rem+env(safe-area-inset-bottom))] z-50 mx-auto w-auto max-w-md outline-none md:right-6 md:bottom-6 md:left-auto md:mx-0 md:w-full',
        className,
      )}
      {...props}
    />
  )
}

function Toast({ className, ...props }: ToastPrimitive.Root.Props) {
  return (
    <ToastPrimitive.Root
      data-slot="toast"
      className={cn(
        'group/toast pointer-events-auto absolute right-0 bottom-0 z-[calc(1000-var(--toast-index))] w-full origin-bottom outline-none select-none',
        '[--gap:0.75rem] [--height:var(--toast-frontmost-height,var(--toast-height))] [--offset-y:calc(var(--toast-offset-y)*-1+calc(var(--toast-index)*var(--gap)*-1)+var(--toast-swipe-movement-y))] [--peek:0.75rem] [--scale:calc(max(0,1-(var(--toast-index)*0.1)))] [--shrink:calc(1-var(--scale))]',
        'h-(--height) [transform:translateX(var(--toast-swipe-movement-x))_translateY(calc(var(--toast-swipe-movement-y)-(var(--toast-index)*var(--peek))-(var(--shrink)*var(--height))))_scale(var(--scale))] [transition:transform_500ms_cubic-bezier(0.22,1,0.36,1),opacity_500ms,height_150ms]',
        "after:absolute after:top-full after:left-0 after:h-[calc(var(--gap)+1px)] after:w-full after:content-['']",
        'data-expanded:h-(--toast-height) data-expanded:[transform:translateX(var(--toast-swipe-movement-x))_translateY(var(--offset-y))]',
        'data-limited:opacity-0 data-starting-style:[transform:translateY(150%)]',
        '[&[data-ending-style]:not([data-limited]):not([data-swipe-direction])]:[transform:translateY(150%)]',
        'data-ending-style:data-[swipe-direction=down]:[transform:translateY(calc(var(--toast-swipe-movement-y)+150%))]',
        'data-ending-style:data-[swipe-direction=right]:[transform:translateX(calc(var(--toast-swipe-movement-x)+150%))_translateY(var(--offset-y))]',
        className,
      )}
      {...props}
    />
  )
}

function ToastContent({ className, ...props }: ToastPrimitive.Content.Props) {
  return (
    <ToastPrimitive.Content
      data-slot="toast-content"
      className={cn(
        toastPillClass,
        'transition-opacity duration-250 ease-[cubic-bezier(0.22,1,0.36,1)] data-behind:opacity-0 data-expanded:opacity-100',
        className,
      )}
      {...props}
    />
  )
}

function ToastTitle({ className, ...props }: ToastPrimitive.Title.Props) {
  return <ToastPrimitive.Title data-slot="toast-title" className={cn('min-w-0', className)} {...props} />
}

function ToastDescription({ className, ...props }: ToastPrimitive.Description.Props) {
  return (
    <ToastPrimitive.Description
      data-slot="toast-description"
      className={cn('min-w-0 text-body-sm font-semibold text-lagoon-muted empty:hidden', className)}
      {...props}
    />
  )
}

function ToastAction({ className, ...props }: ToastPrimitive.Action.Props) {
  return (
    <ToastPrimitive.Action
      data-slot="toast-action"
      render={<Button size="sm" />}
      className={cn('shrink-0', className)}
      {...props}
    />
  )
}

function ToastList() {
  const { toasts } = ToastPrimitive.useToastManager()

  return toasts.map((item) => (
    <Toast key={item.id} toast={item}>
      <ToastContent>
        {item.type ? TOAST_ICONS[item.type] : null}
        <div className="flex min-w-0 flex-col">
          <ToastTitle />
          <ToastDescription />
        </div>
        <ToastAction />
      </ToastContent>
    </Toast>
  ))
}

/** Провайдер и место уведомлений; один на приложение (App.tsx). */
function Toaster({ children, toastManager = toast, ...props }: ToastPrimitive.Provider.Props) {
  const { t } = useTranslation()
  return (
    <ToastPrimitive.Provider toastManager={toastManager} {...props}>
      {children}
      <ToastPrimitive.Portal data-slot="toast-portal">
        <ToastViewport aria-label={t('common:ui.toast.region')}>
          <ToastList />
        </ToastViewport>
      </ToastPrimitive.Portal>
    </ToastPrimitive.Provider>
  )
}

interface UndoToastOptions {
  /** Что уже сделано: «Запись отменена». */
  title: string
  /** Вернуть как было — повторный запрос к API (сервер решает, можно ли). */
  onUndo: () => void
  /** Подпись кнопки; по умолчанию «Вернуть». */
  undoLabel?: string
  /** Сколько держать уведомление, мс; на паузе, пока наведён курсор или фокус. */
  timeout?: number
}

/**
 * Уведомление «Сделано — Вернуть» вместо окна подтверждения. Возвращает id уведомления.
 */
function toastUndo({ title, onUndo, undoLabel, timeout = 8000 }: UndoToastOptions): string {
  const id: string = toast.add({
    title,
    type: 'undo',
    timeout,
    actionProps: {
      children: undoLabel ?? i18n.t('common:ui.toast.undo'),
      onClick: () => {
        toast.close(id)
        onUndo()
      },
    },
  })
  return id
}

/** Скопировать код ошибки для поддержки; буфер недоступен (старый браузер, запрет) — молча. */
function copyText(value: string): Promise<boolean> {
  return (navigator.clipboard?.writeText(value) ?? Promise.reject(new Error('clipboard'))).then(
    () => true,
    () => false,
  )
}

/**
 * Ошибка API уведомлением (PLT-07): текст — `describeApiError` (словарь по коду → текст сервера), под
 * ним код ошибки для поддержки и кнопка «Скопировать код». Для ошибок вне формы (кнопка в списке,
 * фоновое действие); в форме — `applyApiErrorToForm`, он сам решает, что под поле, а что сюда.
 * Возвращает id уведомления.
 */
function toastApiError(error: unknown, description: ApiErrorDescription = describeApiError(error)): string {
  const { message, traceId } = description
  const id: string = toast.add({
    title: message,
    description: traceId ? i18n.t('errors:ui.traceId', { traceId }) : undefined,
    type: 'error',
    timeout: traceId ? 10_000 : 6000,
    actionProps: traceId
      ? {
          children: i18n.t('errors:ui.copyTrace'),
          onClick: () => {
            void copyText(traceId).then((copied) => {
              if (copied) toast.update(id, { actionProps: { children: i18n.t('errors:ui.traceCopied'), disabled: true } })
            })
          },
        }
      : undefined,
  })
  return id
}

/**
 * Образец уведомления для витрины и документации: та же пилюля, но без живой области и
 * таймера. В экранах — только `toast.add` / `toastUndo`.
 */
function ToastPreview({
  title,
  actionLabel,
  onAction,
  className,
}: {
  title: string
  actionLabel?: string
  onAction?: () => void
  className?: string
}) {
  return (
    <div data-slot="toast-preview" className={cn(toastPillClass, 'mx-0 shadow-none md:mx-0', className)}>
      {TOAST_ICONS.undo}
      <span className="min-w-0">{title}</span>
      {actionLabel ? (
        <Button size="sm" data-slot="toast-action" onClick={onAction}>
          {actionLabel}
        </Button>
      ) : null}
    </div>
  )
}

export { copyText, toast, Toast, toastApiError, ToastContent, Toaster, ToastPreview, toastUndo, ToastViewport }
