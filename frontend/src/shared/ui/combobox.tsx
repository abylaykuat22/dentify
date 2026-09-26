import { Combobox as ComboboxPrimitive } from '@base-ui/react/combobox'
import { CheckIcon, ChevronDownIcon, Search, XIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/shared/lib/utils'

import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupInput } from './input-group'

/**
 * Выпадающий список с поиском по вводу (shadcn Combobox на Base UI, «Кресло»). Готовый
 * компонент для экранов — `Combo` (combo.tsx); части ниже — для особых случаев.
 *
 * R022: список рисуется в портале на `<body>` и позиционируется Base UI (Floating UI):
 * у края окна переворачивается вверх, высота ограничена местом во вьюпорте
 * (`--available-height`), прокручиваемые предки его не режут. Якорь — вся пилюля поля
 * (`Combobox.InputGroup`), поэтому ширина списка равна ширине поля.
 */
const Combobox = ComboboxPrimitive.Root

function ComboboxValue({ ...props }: ComboboxPrimitive.Value.Props) {
  return <ComboboxPrimitive.Value data-slot="combobox-value" {...props} />
}

function ComboboxTrigger({ className, children, ...props }: ComboboxPrimitive.Trigger.Props) {
  return (
    <ComboboxPrimitive.Trigger
      data-slot="combobox-trigger"
      className={cn("[&_svg:not([class*='size-'])]:size-[18px] [&[data-popup-open]>svg]:rotate-180", className)}
      {...props}
    >
      {children}
      <ChevronDownIcon className="pointer-events-none text-ink transition-transform" aria-hidden="true" />
    </ComboboxPrimitive.Trigger>
  )
}

function ComboboxClear({ className, ...props }: ComboboxPrimitive.Clear.Props) {
  const { t } = useTranslation()
  return (
    <ComboboxPrimitive.Clear
      data-slot="combobox-clear"
      aria-label={t('common:ui.combo.clear')}
      render={<InputGroupButton />}
      className={cn(className)}
      {...props}
    >
      <XIcon className="pointer-events-none" aria-hidden="true" />
    </ComboboxPrimitive.Clear>
  )
}

type ComboboxInputProps = ComboboxPrimitive.Input.Props & {
  showTrigger?: boolean
  showClear?: boolean
  /** Лупа слева — поле ищет по вводу (макет Sistema). */
  showSearchIcon?: boolean
  density?: 'default' | 'compact'
  surface?: 'card' | 'page'
}

function ComboboxInput({
  className,
  children,
  disabled = false,
  showTrigger = true,
  showClear = false,
  showSearchIcon = true,
  density,
  surface,
  ...props
}: ComboboxInputProps) {
  const { t } = useTranslation()
  return (
    <ComboboxPrimitive.InputGroup
      render={<InputGroup density={density} surface={surface} className={cn('pr-2', className)} />}
    >
      {showSearchIcon ? (
        <InputGroupAddon>
          <Search aria-hidden="true" />
        </InputGroupAddon>
      ) : null}
      <ComboboxPrimitive.Input render={<InputGroupInput disabled={disabled} />} {...props} />
      <InputGroupAddon align="inline-end">
        {showClear && <ComboboxClear disabled={disabled} />}
        {showTrigger && (
          <InputGroupButton
            render={<ComboboxTrigger />}
            aria-label={t('common:ui.combo.toggle')}
            className="group-has-data-[slot=combobox-clear]/input-group:hidden aria-expanded:bg-transparent"
            disabled={disabled}
          />
        )}
      </InputGroupAddon>
      {children}
    </ComboboxPrimitive.InputGroup>
  )
}

function ComboboxContent({
  className,
  side = 'bottom',
  sideOffset = 6,
  align = 'start',
  alignOffset = 0,
  anchor,
  container,
  ...props
}: ComboboxPrimitive.Popup.Props &
  Pick<ComboboxPrimitive.Positioner.Props, 'side' | 'align' | 'sideOffset' | 'alignOffset' | 'anchor'> &
  Pick<ComboboxPrimitive.Portal.Props, 'container'>) {
  return (
    <ComboboxPrimitive.Portal container={container}>
      <ComboboxPrimitive.Positioner
        side={side}
        sideOffset={sideOffset}
        align={align}
        alignOffset={alignOffset}
        anchor={anchor}
        className="isolate z-50"
      >
        <ComboboxPrimitive.Popup
          data-slot="combobox-content"
          className={cn(
            'group/combobox-content relative flex max-h-[min(var(--available-height),20rem)] w-(--anchor-width) max-w-(--available-width) origin-(--transform-origin) flex-col overflow-hidden rounded-block bg-white text-ink shadow-popover duration-100 data-open:animate-in data-open:fade-in-0 data-open:zoom-in-95 data-closed:animate-out data-closed:fade-out-0 data-closed:zoom-out-95 data-[side=bottom]:slide-in-from-top-2 data-[side=top]:slide-in-from-bottom-2',
            className,
          )}
          {...props}
        />
      </ComboboxPrimitive.Positioner>
    </ComboboxPrimitive.Portal>
  )
}

function ComboboxList({ className, ...props }: ComboboxPrimitive.List.Props) {
  return (
    <ComboboxPrimitive.List
      data-slot="combobox-list"
      className={cn(
        'flex min-h-0 flex-1 scroll-py-1.5 flex-col gap-0.5 overflow-y-auto overscroll-contain p-1.5 data-empty:p-0',
        className,
      )}
      {...props}
    />
  )
}

/**
 * Пункт списка: 40 px, строка 14. Активный (наведение, стрелки) — фон `highlight`,
 * выбранный — жирнее и с галочкой. Длинный текст обрезается многоточием — полный текст в
 * `title` (R011), его передаёт `Combo`.
 */
function ComboboxItem({ className, children, ...props }: ComboboxPrimitive.Item.Props) {
  return (
    <ComboboxPrimitive.Item
      data-slot="combobox-item"
      className={cn(
        "group/combobox-item relative flex min-h-10 w-full cursor-default items-center gap-2 rounded-item py-2 pr-9 pl-3 text-body-sm font-semibold select-none data-disabled:pointer-events-none data-disabled:opacity-50 data-highlighted:bg-highlight data-selected:font-extrabold [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
        className,
      )}
      {...props}
    >
      {children}
      <ComboboxPrimitive.ItemIndicator
        render={<span className="pointer-events-none absolute right-3 flex size-4 items-center justify-center" />}
      >
        <CheckIcon className="pointer-events-none" aria-hidden="true" />
      </ComboboxPrimitive.ItemIndicator>
    </ComboboxPrimitive.Item>
  )
}

function ComboboxGroup({ className, ...props }: ComboboxPrimitive.Group.Props) {
  return <ComboboxPrimitive.Group data-slot="combobox-group" className={cn(className)} {...props} />
}

function ComboboxLabel({ className, ...props }: ComboboxPrimitive.GroupLabel.Props) {
  return (
    <ComboboxPrimitive.GroupLabel
      data-slot="combobox-label"
      className={cn('px-3 py-1.5 text-caption font-extrabold text-graphite', className)}
      {...props}
    />
  )
}

function ComboboxCollection({ ...props }: ComboboxPrimitive.Collection.Props) {
  return <ComboboxPrimitive.Collection data-slot="combobox-collection" {...props} />
}

function ComboboxEmpty({ className, ...props }: ComboboxPrimitive.Empty.Props) {
  return (
    <ComboboxPrimitive.Empty
      data-slot="combobox-empty"
      className={cn(
        'hidden w-full px-4 py-3 text-body-sm font-semibold text-graphite group-data-empty/combobox-content:flex',
        className,
      )}
      {...props}
    />
  )
}

function ComboboxSeparator({ className, ...props }: ComboboxPrimitive.Separator.Props) {
  return (
    <ComboboxPrimitive.Separator
      data-slot="combobox-separator"
      className={cn('-mx-1.5 my-1 h-0.5 bg-line', className)}
      {...props}
    />
  )
}

export {
  Combobox,
  ComboboxCollection,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxGroup,
  ComboboxInput,
  ComboboxItem,
  ComboboxLabel,
  ComboboxList,
  ComboboxSeparator,
  ComboboxTrigger,
  ComboboxValue,
}
