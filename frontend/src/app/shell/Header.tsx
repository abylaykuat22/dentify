import { Link } from '@tanstack/react-router'
import { Plus, Search } from 'lucide-react'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'

import type { CurrentUser } from '@/shared/api/me'
import { MEDIA } from '@/shared/lib/breakpoints'
import { formatDayLabel } from '@/shared/lib/format'
import { useMediaQuery } from '@/shared/lib/useMediaQuery'
import { cn } from '@/shared/lib/utils'
import { Button, buttonVariants } from '@/shared/ui/button'
import { Kbd } from '@/shared/ui/kbd'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/shared/ui/tooltip'

import { isSearchHotkey } from './hotkeys'
import { NEW_APPOINTMENT_PATH } from './mobileNav'
import { PillTabs } from './PillTabs'
import type { Section, SectionAction } from './sections'
import { SEARCH_PANEL, usePanel } from './usePanel'

interface HeaderProps {
  user: CurrentUser
  /** Раздел, открытый пользователю; для закрытого раздела не передаётся (только заголовок). */
  section: Section | undefined
  title: string
  /** Экран без вкладок раздела и главного действия (мастер, карточка): staticData.bare. */
  bare?: boolean
}

/**
 * Шапка по макету Raspisanie-Den — на всю ширину над контентом и боковой панелью:
 * заголовок, контекст (дата · филиал), «Найти пациента Ctrl K» и место под одну жёлтую
 * кнопку раздела; ниже — вкладки-пилюли. В одну строку на любой ширине ≥ 390 px:
 * уже 1024 px поиск сворачивается в круглую кнопку с подсказкой (как в макете Telefon),
 * уже 768 px и главное действие — белый круг 48 px рядом с поиском (жёлтый там — «+» панели).
 */
export function Header({ user, section, title, bare = false }: HeaderProps) {
  const { t } = useTranslation()
  const { panel, open } = usePanel()
  const wide = useMediaQuery(MEDIA.desktop)

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (!isSearchHotkey(event)) return
      event.preventDefault()
      if (panel !== SEARCH_PANEL) open(SEARCH_PANEL)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, panel])

  const context = t('shell:header.context', {
    // TODO(T-PLT-17): «сегодня» и часовой пояс клиники — с сервера (в MeResponse их пока нет); сейчас
    // дата и пояс устройства, и устройство с неверными часами покажет не тот день.
    date: formatDayLabel(new Date()),
    // Филиал не выбран (у клиники их несколько) — клиника. Без клиники каркас не открывается.
    branch: user.branch?.name ?? user.clinic?.name ?? '',
  })

  return (
    <div className="flex flex-col gap-4">
      <header className="flex items-center gap-3 md:gap-4" data-shell-header>
        <div className="min-w-0 flex-1">
          <h1 className="truncate font-heading text-h2 font-semibold text-ink md:text-h1">
            {title}
          </h1>
          <p className="mt-1 truncate text-body-sm font-semibold text-graphite" title={context}>
            {context}
          </p>
        </div>

        <Tooltip disabled={wide}>
          <TooltipTrigger
            type="button"
            onClick={() => open(SEARCH_PANEL)}
            aria-label={t('shell:header.searchOpen')}
            aria-keyshortcuts="Control+K Meta+K"
            data-search-trigger
            className="flex size-12 shrink-0 items-center justify-center gap-3 rounded-pill bg-card text-body text-graphite shadow-field hover:text-ink lg:w-60 lg:justify-start lg:px-4 lg:text-left xl:w-75"
          >
            <Search className="size-5 shrink-0" aria-hidden="true" />
            <span className="hidden flex-1 truncate lg:inline">{t('shell:header.search')}</span>
            <Kbd className="hidden lg:inline">{t('shell:header.searchKbd')}</Kbd>
          </TooltipTrigger>
          <TooltipContent side="bottom">{t('shell:header.searchOpen')}</TooltipContent>
        </Tooltip>

        {section?.primaryAction && !bare ? <PrimaryAction section={section} action={section.primaryAction} /> : null}
      </header>

      {section && !bare ? <PillTabs tabs={section.tabs} /> : null}
    </div>
  )
}

/**
 * Главное действие раздела. ≥ 768 px — жёлтая пилюля «+ Новый пациент», одна «лампа» области, как в
 * макетах. На телефоне — белый круг 48 px рядом с поиском (место и вид круглой кнопки в шапке макета
 * Telefon) со значком раздела цвета «лагуна», подпись — для диктора: жёлтый круг на экране телефона
 * один — «+» нижней панели; два жёлтых «добавить» рядом читались бы как одно и то же. «Новая запись»
 * на телефоне не дублируется: это и есть «+». Экран действия есть — ссылка в виде кнопки (data-* как
 * у Button: по ним e2e считает «лампы»); нет — кнопка сообщает, что экран в разработке, а не молчит.
 */
function PrimaryAction({ section, action }: { section: Section; action: SectionAction }) {
  const { t } = useTranslation()
  // По умолчанию (и в jsdom, где matchMedia ничего не совпадает) — лампа; телефон — особый случай.
  const phone = useMediaQuery(MEDIA.phone)
  const variant = phone ? 'outline' : 'default'
  const label = t(action.labelKey)
  const className = cn(
    buttonVariants({ variant, size: 'icon' }),
    // Как поиск рядом: белый круг с тенью поля, без контура второстепенной кнопки.
    phone && 'border-transparent bg-card text-lagoon shadow-field hover:bg-card hover:text-ink',
    'md:w-auto md:gap-2 md:pr-5.5 md:pl-4 md:text-body',
    action.to === NEW_APPOINTMENT_PATH && 'hidden md:inline-flex',
  )
  const content = (
    <>
      <Plus className="hidden size-4.5 md:block" aria-hidden="true" />
      <action.icon className="md:hidden" aria-hidden="true" />
      <span className="sr-only md:not-sr-only">{label}</span>
    </>
  )

  if (action.to) {
    return (
      <Link
        to={action.to}
        data-slot="button"
        data-variant={variant}
        className={className}
        data-primary-action={section.id}
      >
        {content}
      </Link>
    )
  }

  return (
    <Button
      variant={variant}
      size="icon"
      className={className}
      data-primary-action={section.id}
      // Модуль уведомлений грузится по нажатию: в цепочку первого экрана он не входит (PLT-22).
      onClick={() =>
        void import('@/shared/ui/toast').then(({ toast }) => toast.add({ title: t('shell:actions.soon', { action: label }) }))
      }
    >
      {content}
    </Button>
  )
}
