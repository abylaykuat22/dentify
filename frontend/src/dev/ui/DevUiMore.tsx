import { Ellipsis, UserPlus, X } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/shared/lib/utils'
import { AlertStrip } from '@/shared/ui/alert-strip'
import { Avatar } from '@/shared/ui/avatar'
import { Button } from '@/shared/ui/button'
import { ChipGroup } from '@/shared/ui/chip'
import { Combo } from '@/shared/ui/combo'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Field, FieldError, FieldLabel } from '@/shared/ui/field'
import { IinInput, Input, PhoneInput } from '@/shared/ui/input'
import { Panel } from '@/shared/ui/panel'
import { SearchField } from '@/shared/ui/search-field'
import { Segmented } from '@/shared/ui/segmented'
import { StateScreen } from '@/shared/ui/StateScreen'
import { StatusBadge } from '@/shared/ui/status-badge'
import { StepBackButton, StepProgress, stepAwareOpenChange } from '@/shared/ui/steps'
import { Tabs, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { Tag } from '@/shared/ui/tag'
import { Textarea } from '@/shared/ui/textarea'
import { toastUndo } from '@/shared/ui/toast'
import { DOCTOR_COLORS, type PaletteColor, TOOTH_STATE_CLASSES, TOOTH_STATES } from '@/shared/ui/tokens'

import { DemoCard, DemoLabel } from './demo'
import { TableCard } from './DevUiTable'
import { DEMO_CHAIRS } from './lists'
import { readColorToken } from './tokens'

/** «Полный набор» под макетом: всё, что есть в дизайн-системе, но не поместилось на Sistema. */
export function DevUiMore() {
  const { t } = useTranslation('devUi')
  return (
    <section aria-labelledby="dev-ui-more" className="flex flex-col gap-5 pt-4">
      <div className="flex flex-col gap-1.5">
        <h2 id="dev-ui-more" className="font-heading text-h2 font-semibold">
          {t('more.title')}
        </h2>
        <p className="text-body-lg font-semibold text-graphite">{t('more.lead')}</p>
      </div>
      {/* На всю ширину и до колонок: «Список у нижнего края» должен остаться внизу страницы. */}
      <TableCard />
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
        <div className="flex min-w-0 flex-col gap-5">
          <AllColorsCard />
          <ToothCard />
        </div>
        <div className="flex min-w-0 flex-col gap-5">
          <ButtonSizesCard />
          <InputsCard />
          <TabsOverflowCard />
          <PeopleCard />
          <OverlaysCard />
          <EdgeCard />
        </div>
      </div>
    </section>
  )
}

const BASE_TOKENS = [
  ['lagoon', 'lagoon'],
  ['lagoon-deep', 'lagoonDeep'],
  ['lagoon-muted', 'lagoonMuted'],
  ['lamp', 'lamp'],
  ['lamp-deep', 'lampDeep'],
  ['ink', 'ink'],
  ['graphite', 'graphite'],
  ['slate', 'slate'],
  ['fog', 'fog'],
  ['mist', 'mist'],
  ['white', 'white'],
  ['line', 'line'],
  ['danger', 'danger'],
  ['danger-bg', 'dangerBg'],
  ['danger-line', 'dangerLine'],
  ['highlight', 'highlight'],
  ['match', 'match'],
  ['past', 'past'],
] as const

const STATUS_TOKENS = [
  ['status-planned', 'statusPlanned'],
  ['status-arrived', 'statusArrived'],
  ['status-in-chair', 'statusInChair'],
] as const

const TOOTH_TOKENS = [
  ['tooth-caries', 'caries'],
  ['tooth-filling', 'filling'],
  ['tooth-canals', 'canals'],
  ['tooth-crown', 'crown'],
  ['tooth-implant', 'implant'],
  ['tooth-removed-line', 'removed'],
] as const

/** Классы образцов — строками целиком, чтобы Tailwind их сгенерировал. */
const TOKEN_SWATCH: Record<string, string> = {
  lagoon: 'bg-lagoon',
  'lagoon-deep': 'bg-lagoon-deep',
  'lagoon-muted': 'bg-lagoon-muted',
  lamp: 'bg-lamp',
  'lamp-deep': 'bg-lamp-deep',
  ink: 'bg-ink',
  graphite: 'bg-graphite',
  slate: 'bg-slate',
  fog: 'bg-fog',
  mist: 'bg-mist',
  white: 'bg-white',
  line: 'bg-line',
  danger: 'bg-danger',
  'danger-bg': 'bg-danger-bg',
  'danger-line': 'bg-danger-line',
  highlight: 'bg-highlight',
  match: 'bg-match',
  past: 'bg-past',
  'status-planned': 'bg-status-planned',
  'status-arrived': 'bg-status-arrived',
  'status-in-chair': 'bg-status-in-chair',
  'tooth-caries': 'bg-tooth-caries',
  'tooth-filling': 'bg-tooth-filling',
  'tooth-canals': 'bg-tooth-canals',
  'tooth-crown': 'bg-tooth-crown',
  'tooth-implant': 'bg-tooth-implant',
  'tooth-removed-line': 'bg-tooth-removed-line',
}

function AllColorsCard() {
  const { t } = useTranslation('devUi')
  const groups = [
    { title: t('tokens.base'), tokens: BASE_TOKENS },
    { title: t('tokens.status'), tokens: STATUS_TOKENS },
    { title: t('tokens.tooth'), tokens: TOOTH_TOKENS },
  ]
  return (
    <DemoCard title={t('tokens.title')}>
      {groups.map((group) => (
        <div key={group.title} className="flex flex-col gap-2">
          <DemoLabel>{group.title}</DemoLabel>
          <ul className="grid grid-cols-1 gap-x-4 gap-y-2 md:grid-cols-2">
            {group.tokens.map(([token, key]) => (
              <li key={token} className="flex min-w-0 items-center gap-3" data-token={token}>
                <span
                  aria-hidden="true"
                  className={cn('size-9 shrink-0 rounded-item border-2 border-line', TOKEN_SWATCH[token])}
                />
                <span className="flex min-w-0 flex-col">
                  <span className="truncate text-body-sm font-bold" title={t(`tokens.${key}`)}>
                    {t(`tokens.${key}`)}
                  </span>
                  <span className="flex gap-1.5 text-caption font-semibold text-graphite">
                    <code>{token}</code>
                    <span>{readColorToken(token)}</span>
                  </span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </DemoCard>
  )
}

function ToothCard() {
  const { t } = useTranslation('devUi')
  return (
    <DemoCard title={t('tooth.title')}>
      <ul className="flex flex-wrap gap-x-5 gap-y-2.5">
        {TOOTH_STATES.map((state) => (
          <li key={state} className="flex items-center gap-2 text-body-sm font-bold">
            <span aria-hidden="true" className={cn('size-3.5 rounded-full border-2', TOOTH_STATE_CLASSES[state])} />
            {t(`tooth.${state}`)}
          </li>
        ))}
      </ul>
    </DemoCard>
  )
}

function ButtonSizesCard() {
  const { t } = useTranslation(['devUi', 'common'])
  return (
    <DemoCard title={t('sizes.title')}>
      <div className="flex flex-wrap items-center gap-2.5">
        <Button>{t('sizes.default')}</Button>
        <Button variant="outline" size="compact">
          {t('sizes.compact')}
        </Button>
        <Button variant="outline" disabled>
          {t('sizes.disabled')}
        </Button>
        <Button variant="soft" size="icon-compact" aria-label={t('sizes.iconClose')}>
          <X aria-hidden="true" />
        </Button>
      </div>
      <div className="flex flex-wrap items-center gap-2.5">
        <Button variant="destructive">{t('sizes.destructive')}</Button>
        <Button variant="link">{t('sizes.link')}</Button>
        <StepBackButton onClick={() => undefined} />
        <Button variant="ghost" size="icon" aria-label={t('common:ui.moreActions')}>
          <Ellipsis aria-hidden="true" />
        </Button>
      </div>
      <span className="text-caption font-bold text-graphite">{t('sizes.destructiveNote')}</span>
    </DemoCard>
  )
}

function InputsCard() {
  const { t } = useTranslation('devUi')
  const [search, setSearch] = useState('')
  const [pageSearch, setPageSearch] = useState('')
  return (
    <DemoCard title={t('inputs.title')}>
      <span className="text-label font-semibold text-graphite">{t('inputs.keyboardNote')}</span>
      <div className="grid grid-cols-1 gap-3.5 md:grid-cols-2">
        <Field>
          <FieldLabel htmlFor="ds-name">{t('inputs.name')}</FieldLabel>
          <Input id="ds-name" placeholder={t('inputs.namePlaceholder')} autoComplete="off" />
        </Field>
        <Field>
          <FieldLabel htmlFor="ds-phone">{t('inputs.phone')}</FieldLabel>
          <PhoneInput id="ds-phone" placeholder={t('inputs.phonePlaceholder')} autoComplete="off" />
        </Field>
        <Field data-invalid>
          <FieldLabel htmlFor="ds-iin">{t('inputs.iin')}</FieldLabel>
          <IinInput
            id="ds-iin"
            defaultValue="95031"
            placeholder={t('inputs.iinPlaceholder')}
            aria-invalid
            aria-describedby="ds-iin-error"
          />
          <FieldError id="ds-iin-error">{t('inputs.iinError')}</FieldError>
        </Field>
        <Field>
          <FieldLabel htmlFor="ds-compact">{t('inputs.compact')}</FieldLabel>
          <Input id="ds-compact" density="compact" placeholder={t('inputs.namePlaceholder')} autoComplete="off" />
        </Field>
        <Field className="md:col-span-2">
          <FieldLabel htmlFor="ds-note">{t('inputs.note')}</FieldLabel>
          <Textarea id="ds-note" placeholder={t('inputs.notePlaceholder')} />
        </Field>
        <Field className="md:col-span-2">
          <DemoLabel>{t('inputs.search')}</DemoLabel>
          <SearchField
            label={t('inputs.search')}
            placeholder={t('inputs.searchPlaceholder')}
            value={search}
            onValueChange={setSearch}
          />
        </Field>
      </div>
      <div className="flex flex-col gap-2 rounded-block bg-fog p-4">
        <DemoLabel>{t('inputs.searchOnPage')}</DemoLabel>
        <SearchField
          surface="page"
          label={t('inputs.searchOnPage')}
          placeholder={t('inputs.searchPlaceholder')}
          value={pageSearch}
          onValueChange={setPageSearch}
          shortcut={t('inputs.shortcut')}
        />
      </div>
    </DemoCard>
  )
}

const OVERFLOW_TABS = ['main', 'chart', 'visits', 'files', 'documents', 'payments'] as const

/**
 * Шесть вкладок на узком экране (PLT-18): полоса прокручивается вбок, выбранная — пятая, за правым
 * краем, и всё равно видна целиком (`TabsList` доводит её сам). На десктопе влезают все.
 */
function TabsOverflowCard() {
  const { t } = useTranslation('devUi')
  return (
    <DemoCard title={t('tabsOverflow.title')}>
      <span className="text-label font-semibold text-graphite">{t('tabsOverflow.note')}</span>
      <Tabs defaultValue="documents">
        <TabsList aria-label={t('tabsOverflow.label')}>
          {OVERFLOW_TABS.map((tab) => (
            <TabsTrigger key={tab} size="compact" value={tab}>
              {t(`tabsOverflow.tabs.${tab}`)}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>
    </DemoCard>
  )
}

/** Врачи — 6 цветов, не-врачи — нейтральный серый. */
const AVATAR_COLORS: readonly PaletteColor[] = [...DOCTOR_COLORS, 'neutral']

function PeopleCard() {
  const { t } = useTranslation(['devUi', 'common'])
  return (
    <DemoCard title={t('people.title')}>
      <DemoLabel>{t('people.doctors')}</DemoLabel>
      <div className="flex flex-wrap items-center gap-2.5">
        {AVATAR_COLORS.map((color) => (
          <Avatar
            key={color}
            color={color}
            initials={t(`people.doctorInitials.${color}`)}
            label={t(`people.doctorNames.${color}`)}
          />
        ))}
        <Avatar size="lg" color="teal" initials={t('people.doctorInitials.teal')} label={t('people.doctorNames.teal')} />
      </div>
      <DemoLabel>{t('people.patients')}</DemoLabel>
      <div className="flex flex-col gap-2 rounded-row bg-highlight p-3 md:flex-row md:items-center md:gap-3">
        <div className="flex items-center gap-3">
          <Avatar tone="soft" color="teal" initials={t('people.patientInitials')} />
          <span className="flex flex-col">
            <span className="font-heading text-h4 font-semibold">{t('people.patientName')}</span>
            {/* Возраст — ICU-склонение общего словаря («29 лет», «41 год», «52 года»); номер карты —
                строкой, без разрядов. */}
            <span className="text-caption font-semibold text-graphite">
              {t('people.patientMeta', { age: t('common:age', { age: 29 }), card: '1042' })}
            </span>
          </span>
        </div>
        <AlertStrip size="compact" title={t('messages.allergy')} />
      </div>
      <DemoLabel>{t('people.tags')}</DemoLabel>
      <div className="flex flex-wrap gap-2">
        <Tag color="coral">{t('people.tagAllergy')}</Tag>
        <Tag color="teal">{t('people.tagImplant')}</Tag>
        <Tag color="sky">{t('people.tagChild')}</Tag>
        <Tag color="lilac">{t('people.tagBraces')}</Tag>
        <Tag color="sand">{t('people.tagVip')}</Tag>
        <Tag>{t('people.tagFamily')}</Tag>
      </div>
      <DemoLabel>{t('people.onCard')}</DemoLabel>
      <div className="flex flex-col gap-1.5 rounded-item border-2 border-lamp bg-doctor-teal p-3 text-doctor-teal-fg md:w-72">
        <span className="font-heading text-body font-semibold text-ink">{t('people.patientName')}</span>
        <span className="text-caption font-semibold">{t('people.appointmentTime')}</span>
        <StatusBadge status="arrived" surface="color" size="compact" />
      </div>
    </DemoCard>
  )
}

function OverlaysCard() {
  const { t } = useTranslation('devUi')
  const [panelOpen, setPanelOpen] = useState(false)
  const [panelStep, setPanelStep] = useState(1)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [dialogStep, setDialogStep] = useState(1)

  const showUndoToast = () =>
    toastUndo({
      title: t('overlays.toastDone'),
      onUndo: () => undefined,
    })

  return (
    <DemoCard title={t('overlays.title')}>
      <span className="text-label font-semibold text-graphite">{t('overlays.note')}</span>
      <div className="flex flex-wrap gap-2.5">
        <Button
          variant="outline"
          onClick={() => {
            setPanelStep(1)
            setPanelOpen(true)
          }}
        >
          {t('overlays.openPanel')}
        </Button>
        <Button
          variant="outline"
          onClick={() => {
            setDialogStep(1)
            setDialogOpen(true)
          }}
        >
          {t('overlays.openDialog')}
        </Button>
        <Button variant="outline" onClick={showUndoToast}>
          {t('overlays.showToast')}
        </Button>
      </div>

      <DemoPanel open={panelOpen} onOpenChange={setPanelOpen} step={panelStep} onStep={setPanelStep} onDone={showUndoToast} />

      <Dialog
        open={dialogOpen}
        onOpenChange={stepAwareOpenChange(
          (open: boolean) => setDialogOpen(open),
          dialogStep > 1 ? () => setDialogStep(dialogStep - 1) : undefined,
        )}
      >
        <DialogContent data-demo-dialog>
          <DialogHeader>
            <DialogTitle>{t('overlays.dialogTitle')}</DialogTitle>
            <StepProgress
              current={dialogStep}
              total={2}
              label={dialogStep === 1 ? t('overlays.dialogStepDay') : t('overlays.dialogStepSlot')}
            />
            <DialogDescription>{t('overlays.dialogText')}</DialogDescription>
          </DialogHeader>
          {dialogStep === 1 ? <DurationSwitch /> : <ChairChips />}
          <DialogFooter>
            {dialogStep > 1 ? <StepBackButton onClick={() => setDialogStep(1)} className="md:mr-auto" /> : null}
            <Button onClick={() => (dialogStep === 1 ? setDialogStep(2) : setDialogOpen(false))}>
              {dialogStep === 1 ? t('overlays.next') : t('overlays.dialogSave')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <StateScreen
        kind="empty"
        icon={UserPlus}
        title={t('overlays.emptyTitle')}
        text={t('overlays.emptyText')}
        className="bg-mist py-10"
      />
    </DemoCard>
  )
}

function DurationSwitch() {
  const { t } = useTranslation('devUi')
  const [duration, setDuration] = useState<'30' | '45' | '60' | '90'>('60')
  return (
    <div className="flex flex-col gap-2">
      <DemoLabel>{t('overlays.duration')}</DemoLabel>
      <Segmented
        aria-label={t('overlays.durationLabel')}
        value={duration}
        onValueChange={setDuration}
        options={[
          { value: '30', label: t('overlays.min30') },
          { value: '45', label: t('overlays.min45') },
          { value: '60', label: t('overlays.min60') },
          { value: '90', label: t('overlays.min90') },
        ]}
      />
    </div>
  )
}

function ChairChips() {
  const { t } = useTranslation('devUi')
  const chairs = DEMO_CHAIRS.map((id) => t(`overlays.chairs.${id}`))
  const [chair, setChair] = useState<string[]>([chairs[0] ?? ''])
  return (
    <div className="flex flex-col gap-2">
      <DemoLabel>{t('overlays.chair')}</DemoLabel>
      <ChipGroup
        aria-label={t('overlays.chair')}
        value={chair}
        onValueChange={setChair}
        showCheck
        options={chairs.map((label) => ({ value: label, label }))}
      />
    </div>
  )
}

/** Панель-мастер: шаг 1 — пациент, шаг 2 — длинная прокручиваемая форма со списком внизу. */
function DemoPanel({
  open,
  onOpenChange,
  step,
  onStep,
  onDone,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  step: number
  onStep: (step: number) => void
  onDone: () => void
}) {
  const { t } = useTranslation('devUi')
  const [patientQuery, setPatientQuery] = useState('')
  const [chair, setChair] = useState<string | null>(null)
  const chairs = DEMO_CHAIRS.map((id) => t(`overlays.chairs.${id}`))

  return (
    <Panel
      name="demo"
      open={open}
      onOpenChange={onOpenChange}
      title={t('overlays.panelTitle')}
      step={{ current: step, total: 2, label: step === 1 ? t('overlays.stepPatient') : t('overlays.stepTime') }}
      onBack={step > 1 ? () => onStep(step - 1) : undefined}
      footer={
        // Одна и та же кнопка на обоих шагах: фокус остаётся на ней при переходе.
        <Button
          onClick={() => {
            if (step === 1) {
              onStep(2)
              return
            }
            onOpenChange(false)
            onDone()
          }}
        >
          {step === 1 ? t('overlays.next') : t('overlays.save')}
        </Button>
      }
    >
      {step === 1 ? (
        <>
          <SearchField
            label={t('overlays.patientSearch')}
            placeholder={t('overlays.patientPlaceholder')}
            value={patientQuery}
            onValueChange={setPatientQuery}
          />
          <div className="flex items-center gap-3 rounded-row bg-mist p-3">
            <Avatar tone="soft" color="teal" initials={t('people.patientInitials')} />
            <span className="flex min-w-0 flex-col gap-0.5">
              <span className="font-heading text-h4 font-semibold">{t('people.patientName')}</span>
              <AlertStrip size="compact" title={t('messages.allergy')} />
            </span>
          </div>
        </>
      ) : (
        <>
          <DurationSwitch />
          <ChairChips />
          {[1, 2, 3].map((index) => (
            <p key={index} className="rounded-block bg-mist p-4 text-body-sm font-semibold text-slate">
              {t('overlays.slotsNote')}
            </p>
          ))}
          <div className="flex flex-col gap-2" data-demo-edge-combo>
            <DemoLabel htmlFor="ds-panel-chair">{t('overlays.chairCombo')}</DemoLabel>
            <Combo
              id="ds-panel-chair"
              items={chairs}
              value={chair}
              onValueChange={setChair}
              placeholder={t('overlays.chairPlaceholder')}
            />
          </div>
        </>
      )}
    </Panel>
  )
}

function EdgeCard() {
  const { t } = useTranslation('devUi')
  const chairs = DEMO_CHAIRS.map((id) => t(`overlays.chairs.${id}`))
  const [chair, setChair] = useState<string | null>(null)
  return (
    <DemoCard title={t('edge.title')}>
      <span className="text-label font-semibold text-graphite">{t('edge.note')}</span>
      <DemoLabel htmlFor="ds-edge-chair">{t('edge.label')}</DemoLabel>
      <Combo
        id="ds-edge-chair"
        items={chairs}
        value={chair}
        onValueChange={setChair}
        placeholder={t('overlays.chairPlaceholder')}
      />
    </DemoCard>
  )
}
