import { Ellipsis, Plus } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/shared/lib/utils'
import { AlertStrip } from '@/shared/ui/alert-strip'
import { Button } from '@/shared/ui/button'
import { ChipGroup } from '@/shared/ui/chip'
import { Combo } from '@/shared/ui/combo'
import { Segmented } from '@/shared/ui/segmented'
import { StatusBadge } from '@/shared/ui/status-badge'
import { Tabs, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { toast, ToastPreview } from '@/shared/ui/toast'
import { APPOINTMENT_STATUSES, DOCTOR_COLORS, PALETTE_CLASSES } from '@/shared/ui/tokens'

import { DemoCard, DemoLabel } from './demo'
import { DevUiMore } from './DevUiMore'
import { ensureDevUiI18n } from './i18n'
import { DEMO_SPECIALTIES } from './lists'
import { readColorToken, readDoctorColorToken } from './tokens'

/**
 * Витрина «Кресла» (/dev/ui, только dev-сборка). Верх страницы повторяет макет
 * design/mockups/Sistema.dc.html один в один — на настоящих компонентах; ниже — «Полный
 * набор»: остальные токены, размеры, состояния, панели и окна. Скриншот-тест —
 * e2e/dev-ui.spec.ts, эталоны — e2e/__screenshots__.
 */
export function DevUiPage() {
  ensureDevUiI18n()
  const { t } = useTranslation(['devUi', 'common'])

  return (
    <main data-dev-ui className="min-h-dvh bg-fog px-4 py-6 text-ink md:px-12 md:py-11">
      <div className="mx-auto flex max-w-[1344px] flex-col gap-7">
        <header className="flex flex-col gap-3 md:flex-row md:items-end md:gap-6">
          <div className="flex flex-col gap-2">
            <span className="font-heading text-xl font-bold tracking-[-0.02em] text-lagoon">
              {t('common:app.logo')}
              <span className="text-lamp-deep">.</span>
            </span>
            <h1 className="font-heading text-h1 font-semibold md:text-[2.375rem] md:leading-[1.18] md:tracking-[-0.02em]">
              {t('title')}
            </h1>
          </div>
          <p className="max-w-[520px] text-body-lg font-semibold text-graphite md:mb-1.5">{t('lead')}</p>
        </header>

        <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
          <div className="flex min-w-0 flex-col gap-5">
            <ColorsCard />
            <StatusesCard />
            <FontsCard />
          </div>
          <div className="flex min-w-0 flex-col gap-5">
            <ButtonsCard />
            <FieldsCard />
            <ShapesCard />
            <MessagesCard />
          </div>
        </div>

        <DevUiMore />
      </div>
    </main>
  )
}

const MAIN_SWATCHES = ['lagoon', 'lamp', 'ink', 'graphite', 'fog', 'white'] as const

const SWATCH_BG: Record<(typeof MAIN_SWATCHES)[number], string> = {
  lagoon: 'bg-lagoon',
  lamp: 'bg-lamp',
  ink: 'bg-ink',
  graphite: 'bg-graphite',
  fog: 'bg-fog border-2 border-line',
  white: 'bg-white border-2 border-line',
}

function ColorsCard() {
  const { t } = useTranslation('devUi')
  return (
    <DemoCard title={t('colors.title')} className="gap-4.5">
      <div className="grid grid-cols-3 gap-3 md:grid-cols-6">
        {MAIN_SWATCHES.map((name) => (
          <div key={name} className="flex flex-col gap-2" data-swatch={name}>
            <span aria-hidden="true" className={cn('h-21 rounded-block', SWATCH_BG[name])} />
            <span className="text-body-sm font-extrabold">{t(`colors.${name}.name`)}</span>
            <span className="text-xs leading-[1.35] font-semibold text-graphite">
              {readColorToken(name)}
              <br />
              {t(`colors.${name}.hint`)}
            </span>
          </div>
        ))}
      </div>
      <div className="flex flex-col gap-2.5">
        <span className="text-label font-extrabold text-slate">{t('colors.doctorsTitle')}</span>
        <div className="grid grid-cols-2 gap-2 md:grid-cols-3">
          {DOCTOR_COLORS.map((color) => (
            <span
              key={color}
              className={cn(
                'flex h-11 items-center justify-between gap-2 rounded-item px-3.5 text-body-sm font-extrabold',
                PALETTE_CLASSES[color].bg,
                PALETTE_CLASSES[color].fg,
              )}
            >
              {t(`colors.doctors.${color}`)}
              <span className="text-xs font-semibold">{readDoctorColorToken(color)}</span>
            </span>
          ))}
        </div>
      </div>
    </DemoCard>
  )
}

function StatusesCard() {
  const { t } = useTranslation('devUi')
  return (
    <DemoCard title={t('statuses.title')}>
      <div className="flex flex-wrap gap-2">
        {APPOINTMENT_STATUSES.map((status) => (
          <StatusBadge key={status} status={status} />
        ))}
      </div>
      <span className="text-label font-semibold text-graphite">{t('statuses.note')}</span>
    </DemoCard>
  )
}

function FontsCard() {
  const { t } = useTranslation('devUi')
  return (
    <DemoCard title={t('fonts.title')} grow>
      <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
        <div className="flex flex-col gap-2.5">
          <span className="text-caption font-extrabold text-graphite">{t('fonts.rubik')}</span>
          <span className="font-heading text-h1 font-semibold">{t('fonts.display')}</span>
          <span className="font-heading text-h3 font-semibold">{t('fonts.heading')}</span>
          <span className="font-heading text-h4 font-semibold">{t('fonts.name')}</span>
        </div>
        <div className="flex flex-col gap-2.5">
          <span className="text-caption font-extrabold text-graphite">{t('fonts.nunito')}</span>
          <span className="text-body-lg font-semibold">{t('fonts.body')}</span>
          <span className="text-body-sm font-semibold text-graphite">{t('fonts.meta')}</span>
          <span className="text-caption font-extrabold text-slate">{t('fonts.label')}</span>
        </div>
      </div>
      <div className="flex items-center gap-2.5 rounded-row bg-mist px-3.5 py-3 text-body-sm font-semibold">
        <span data-kazakh-sample className="shrink-0 font-heading text-lg font-semibold tracking-[0.02em] whitespace-nowrap">
          {t('fonts.kazakh')}
        </span>
        <span className="text-slate">{t('fonts.kazakhNote')}</span>
      </div>
    </DemoCard>
  )
}

function ButtonsCard() {
  const { t } = useTranslation(['devUi', 'common'])
  const [today, setToday] = useState(true)
  return (
    <DemoCard title={t('buttons.title')} className="gap-4">
      <div className="flex flex-wrap items-center gap-2.5">
        <Button>
          <Plus strokeWidth={2.8} aria-hidden="true" />
          {t('buttons.record')}
        </Button>
        <Button variant="outline">{t('buttons.reschedule')}</Button>
        <Button
          variant={today ? 'selected' : 'outline'}
          aria-pressed={today}
          onClick={() => setToday((value) => !value)}
        >
          {t('buttons.today')}
        </Button>
        <Button variant="danger">{t('buttons.cancel')}</Button>
        <Button variant="soft" size="icon" aria-label={t('common:ui.moreActions')}>
          <Ellipsis aria-hidden="true" />
        </Button>
      </div>
      <div className="grid grid-cols-2 gap-2.5 text-caption leading-[1.35] font-bold text-graphite md:grid-cols-4">
        <span>{t('buttons.primaryNote')}</span>
        <span>{t('buttons.outlineNote')}</span>
        <span>{t('buttons.selectedNote')}</span>
        <span>{t('buttons.dangerNote')}</span>
      </div>
    </DemoCard>
  )
}

function FieldsCard() {
  const { t } = useTranslation('devUi')
  const specialties = DEMO_SPECIALTIES.map((id) => t(`fields.specialties.${id}`))
  const [specialty, setSpecialty] = useState<string | null>(null)
  const [columns, setColumns] = useState<'chairs' | 'doctors'>('chairs')
  const [choice, setChoice] = useState<string[]>(['therapy'])

  return (
    <DemoCard title={t('fields.title')}>
      <div className="grid grid-cols-1 items-start gap-3.5 md:grid-cols-2">
        <div className="flex flex-col gap-2">
          <DemoLabel htmlFor="ds-specialty">{t('fields.specialty')}</DemoLabel>
          <Combo
            id="ds-specialty"
            items={specialties}
            value={specialty}
            onValueChange={setSpecialty}
            placeholder={t('fields.specialtyPlaceholder')}
          />
          <span className="text-caption leading-[1.4] font-semibold text-graphite">{t('fields.hint')}</span>
        </div>
        <div className="flex flex-col gap-3">
          <DemoLabel>{t('fields.switch')}</DemoLabel>
          <Segmented
            aria-label={t('fields.switchLabel')}
            value={columns}
            onValueChange={setColumns}
            options={[
              { value: 'chairs', label: t('fields.chairs') },
              { value: 'doctors', label: t('fields.doctors') },
            ]}
          />
          <DemoLabel>{t('fields.tabs')}</DemoLabel>
          <Tabs defaultValue="chart">
            <TabsList aria-label={t('fields.tabsLabel')}>
              <TabsTrigger size="compact" value="main">
                {t('fields.tabMain')}
              </TabsTrigger>
              <TabsTrigger size="compact" value="chart">
                {t('fields.tabChart')}
              </TabsTrigger>
              <TabsTrigger size="compact" value="visits">
                {t('fields.tabVisits')}
              </TabsTrigger>
            </TabsList>
          </Tabs>
          <DemoLabel>{t('fields.choice')}</DemoLabel>
          <ChipGroup
            aria-label={t('fields.choiceLabel')}
            value={choice}
            onValueChange={setChoice}
            options={[
              { value: 'therapy', label: t('fields.therapy') },
              { value: 'surgery', label: t('fields.surgery') },
              { value: 'hygiene', label: t('fields.hygiene') },
            ]}
          />
        </div>
      </div>
    </DemoCard>
  )
}

const SPACING_STEPS = [
  { size: 'size-1', radius: 'rounded-[2px]' },
  { size: 'size-2', radius: 'rounded-[2px]' },
  { size: 'size-3', radius: 'rounded-[3px]' },
  { size: 'size-4', radius: 'rounded-[4px]' },
  { size: 'size-6', radius: 'rounded-[5px]' },
  { size: 'size-8', radius: 'rounded-[6px]' },
] as const

function ShapesCard() {
  const { t } = useTranslation('devUi')
  const shapes = [
    { key: 'pill', className: 'h-10 rounded-pill border-2 border-line bg-fog' },
    { key: 'card', className: 'h-18 rounded-card border-2 border-line bg-fog' },
    { key: 'block', className: 'h-18 rounded-block border-2 border-line bg-fog' },
    { key: 'item', className: 'h-14 rounded-item bg-doctor-teal' },
  ] as const
  return (
    <DemoCard title={t('shapes.title')}>
      <div className="flex flex-wrap items-end gap-3.5">
        {shapes.map((shape) => (
          <div key={shape.key} className="flex flex-col items-center gap-1.5">
            <span aria-hidden="true" className={cn('w-24', shape.className)} />
            <span className="text-caption font-bold text-graphite">{t(`shapes.${shape.key}`)}</span>
          </div>
        ))}
        <div className="flex grow flex-col gap-1.5 pl-2.5">
          <span className="text-caption font-extrabold text-slate">{t('shapes.spacing')}</span>
          <div aria-hidden="true" className="flex items-end gap-2">
            {SPACING_STEPS.map((step) => (
              <span key={step.size} className={cn('bg-lamp', step.size, step.radius)} />
            ))}
          </div>
          <span className="text-caption font-bold text-graphite">{t('shapes.spacingScale')}</span>
        </div>
      </div>
    </DemoCard>
  )
}

function MessagesCard() {
  const { t } = useTranslation(['devUi', 'common'])
  return (
    <DemoCard title={t('messages.title')} className="gap-3" grow>
      <ToastPreview
        title={t('messages.toast')}
        actionLabel={t('common:ui.toast.undo')}
        onAction={() => toast.add({ title: t('overlays.toastRestored'), type: 'success' })}
        className="self-start"
      />
      <AlertStrip title={t('messages.allergy')}>{t('messages.allergyNote')}</AlertStrip>
      <span className="text-label font-semibold text-graphite">{t('messages.note')}</span>
    </DemoCard>
  )
}
