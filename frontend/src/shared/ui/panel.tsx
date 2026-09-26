import { Dialog as DialogPrimitive } from '@base-ui/react/dialog'
import { Drawer as DrawerPrimitive } from '@base-ui/react/drawer'
import { X } from 'lucide-react'
import type * as React from 'react'
import { useTranslation } from 'react-i18next'

import { MEDIA } from '@/shared/lib/breakpoints'
import { useMediaQuery } from '@/shared/lib/useMediaQuery'
import { cn } from '@/shared/lib/utils'

import { Button } from './button'
import { Drawer, DrawerContent } from './drawer'
import { StepBackButton, StepProgress, type StepProgressProps, stepAwareOpenChange } from './steps'

/** С этой ширины (десктоп) панель встаёт колонкой рядом с контентом (макет Raspisanie-Den). */
export const PANEL_INLINE_QUERY = MEDIA.desktop
/** На телефоне панель — нижний лист (макет Telefon-Zapis). */
export const PANEL_SHEET_QUERY = MEDIA.phone

export type PanelMode = 'inline' | 'side' | 'sheet'

export interface PanelProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: React.ReactNode
  description?: React.ReactNode
  /** Слева от заголовка: аватар пациента и т. п. */
  media?: React.ReactNode
  /** Мастер: «Шаг 2 из 3 · Врач и время». */
  step?: Omit<StepProgressProps, 'className'>
  /**
   * Шаг назад (R023): в подвале появляется «← Назад», Escape и клик мимо панели ведут на
   * предыдущий шаг, крестик закрывает панель целиком. На первом шаге не передавайте.
   */
  onBack?: () => void
  /** Действия панели; жёлтая кнопка — одна на панель. */
  footer?: React.ReactNode
  children?: React.ReactNode
  /**
   * Ячейка каркаса справа от контента: с 1024 px панель встаёт туда колонкой и не
   * перекрывает сетку (UX-принцип 2). Без неё — всплывает справа поверх контента.
   */
  inlineContainer?: React.RefObject<HTMLElement | null>
  /** Куда поставить фокус при открытии (поле поиска); по умолчанию — первый элемент. */
  initialFocus?: React.RefObject<HTMLElement | null>
  /** Подпись крестика; по умолчанию «Закрыть панель». */
  closeLabel?: string
  className?: string
  /** Имя панели для e2e и стилей (`data-panel`). */
  name?: string
}

/**
 * Боковая панель «Кресла» вместо окна поверх окна (UX-принцип 2), три вида по ширине:
 * - ≥ 1024 px с `inlineContainer` — немодальная колонка рядом с контентом: сетка видна и
 *   доступна, клик мимо панель не закрывает;
 * - 768–1023 px (или без `inlineContainer`) — модальная панель справа поверх контента;
 * - < 768 px — нижний лист (Base UI Drawer): смахивание вниз закрывает.
 * В модальных видах фокус заперт внутри и после закрытия возвращается туда, откуда
 * открыли. Жёлтая кнопка панели (в `footer`) — своя, отдельная от кнопки страницы.
 */
export function Panel({
  open,
  onOpenChange,
  title,
  description,
  media,
  step,
  onBack,
  footer,
  children,
  inlineContainer,
  initialFocus,
  closeLabel,
  className,
  name,
}: PanelProps) {
  const { t } = useTranslation()
  const wide = useMediaQuery(PANEL_INLINE_QUERY)
  const phone = useMediaQuery(PANEL_SHEET_QUERY)
  const mode: PanelMode = inlineContainer && wide ? 'inline' : phone ? 'sheet' : 'side'
  const handleOpenChange = stepAwareOpenChange((next: boolean) => onOpenChange(next), onBack)
  const close = closeLabel ?? t('common:ui.closePanel')

  const sheet = mode === 'sheet'
  const footerRow =
    footer || onBack ? (
      <div
        data-slot="panel-footer"
        className="flex shrink-0 items-center gap-3 border-t-2 border-line px-5 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]"
      >
        {onBack ? <StepBackButton onClick={onBack} /> : null}
        {/* В нижнем листе кнопки тянутся на всю ширину — под большой палец (макет Telefon-Zapis). */}
        <div
          className={cn(
            'ml-auto flex min-w-0 flex-1 items-center justify-end gap-3',
            sheet && '[&>[data-slot=button]]:flex-1',
          )}
        >
          {footer}
        </div>
      </div>
    ) : null

  const body = (
    <div
      data-slot="panel-body"
      className={cn(
        'flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto overscroll-contain px-5 py-4',
        // Лист без подвала: низ содержимого — над полоской «домой» iOS (safe-area).
        sheet && !footerRow && 'pb-[max(1rem,env(safe-area-inset-bottom))]',
      )}
    >
      {children}
    </div>
  )

  if (sheet) {
    return (
      <Drawer open={open} onOpenChange={handleOpenChange} swipeDirection="down" showSwipeHandle>
        <DrawerContent
          initialFocus={initialFocus}
          data-panel={name}
          data-panel-mode={mode}
          className={cn('max-h-[calc(100dvh-3rem)]', className)}
        >
          <PanelHeader
            className="pt-2"
            media={media}
            step={step}
            title={<DrawerPrimitive.Title className={titleClass}>{title}</DrawerPrimitive.Title>}
            description={
              description ? (
                <DrawerPrimitive.Description className={descriptionClass}>{description}</DrawerPrimitive.Description>
              ) : null
            }
            close={
              <DrawerPrimitive.Close
                aria-label={close}
                render={<Button variant="soft" size="icon-compact" className="shrink-0" />}
              >
                <X aria-hidden="true" />
              </DrawerPrimitive.Close>
            }
          />
          {body}
          {footerRow}
        </DrawerContent>
      </Drawer>
    )
  }

  const inline = mode === 'inline'
  return (
    <DialogPrimitive.Root
      open={open}
      onOpenChange={handleOpenChange}
      modal={!inline}
      disablePointerDismissal={inline}
    >
      <DialogPrimitive.Portal container={inline ? inlineContainer : undefined} className={inline ? 'h-full' : undefined}>
        {inline ? null : (
          <DialogPrimitive.Backdrop className="fixed inset-0 z-40 bg-ink/30 transition-opacity duration-200 data-ending-style:opacity-0 data-starting-style:opacity-0" />
        )}
        <DialogPrimitive.Popup
          initialFocus={initialFocus}
          data-panel={name}
          data-panel-mode={mode}
          className={cn(
            'flex flex-col overflow-hidden rounded-card bg-white text-ink outline-none',
            inline
              ? 'sticky top-4 h-full max-h-[calc(100dvh-2rem)]'
              : 'fixed inset-y-3 right-3 z-50 w-[min(24rem,calc(100vw-1.5rem))] shadow-window transition-[translate,opacity] duration-200 ease-out data-ending-style:translate-x-6 data-ending-style:opacity-0 data-starting-style:translate-x-6 data-starting-style:opacity-0',
            className,
          )}
        >
          <PanelHeader
            media={media}
            step={step}
            title={<DialogPrimitive.Title className={titleClass}>{title}</DialogPrimitive.Title>}
            description={
              description ? (
                <DialogPrimitive.Description className={descriptionClass}>{description}</DialogPrimitive.Description>
              ) : null
            }
            close={
              <DialogPrimitive.Close
                aria-label={close}
                render={<Button variant="soft" size="icon-compact" className="shrink-0" />}
              >
                <X aria-hidden="true" />
              </DialogPrimitive.Close>
            }
          />
          {body}
          {footerRow}
        </DialogPrimitive.Popup>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}

const titleClass = 'font-heading text-h3 font-semibold text-ink'
const descriptionClass = 'text-caption font-bold text-graphite'

function PanelHeader({
  className,
  media,
  step,
  title,
  description,
  close,
}: {
  className?: string
  media: React.ReactNode
  step: PanelProps['step']
  title: React.ReactNode
  description: React.ReactNode
  close: React.ReactNode
}) {
  return (
    <div data-slot="panel-header" className={cn('flex shrink-0 items-start gap-3 px-5 pt-5', className)}>
      {media}
      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        {title}
        {step ? <StepProgress {...step} /> : null}
        {description}
      </div>
      {close}
    </div>
  )
}
