import { Input as InputPrimitive } from '@base-ui/react/input'
import { cva, type VariantProps } from 'class-variance-authority'
import type * as React from 'react'

import { cn } from '@/shared/lib/utils'

/**
 * Поле-пилюля «Кресла»: 48 px (компактное — 40 px, на телефоне 44), белое, контур 2 px `line`;
 * в фокусе — контур «чернила» плюс общий контур фокуса 3 px «лагуна». Подпись — через `Field`/`Label`
 * (у поля без видимой подписи обязателен `aria-label`). Поле с иконкой, кнопкой или
 * подсказкой — `InputGroup` (search-field, combo).
 */
const inputVariants = cva(
  'w-full min-w-0 rounded-pill border-2 border-line bg-white font-semibold text-ink transition-colors placeholder:font-medium placeholder:text-graphite focus-visible:border-ink disabled:cursor-not-allowed disabled:bg-mist disabled:text-graphite aria-invalid:border-danger',
  {
    variants: {
      density: {
        // На телефоне шрифт поля 16 px (PLT-18): мельче — iOS увеличивает страницу при фокусе.
        default: 'h-12 px-4.5 text-body-lg md:text-body',
        // Компактное на телефоне — 44 px: цель касания (PLT-21), с 768 px — 40 px по макетам.
        compact: 'h-11 px-4 text-body-lg md:h-10 md:text-body-sm',
      },
    },
    defaultVariants: { density: 'default' },
  },
)

type InputProps = React.ComponentProps<'input'> & VariantProps<typeof inputVariants>

function Input({ className, type, density, ...props }: InputProps) {
  return (
    <InputPrimitive
      type={type}
      data-slot="input"
      className={cn(inputVariants({ density }), className)}
      {...props}
    />
  )
}

type PresetInputProps = Omit<InputProps, 'type' | 'inputMode'>

/**
 * Телефон: `type="tel"` — на телефоне цифровая клавиатура с «+» (PLT-18). Маска и формат
 * номера — отдельно (CLN-26), проверку делает сервер (R002). Автозаполнение задаёт экран:
 * свой номер — `autoComplete="tel"`, номер пациента — `"off"`.
 */
function PhoneInput(props: PresetInputProps) {
  return <Input type="tel" inputMode="tel" {...props} />
}

/**
 * ИИН — 12 цифр: цифровая клавиатура (`inputMode="numeric"`, PLT-18), без автозаполнения и
 * проверки орфографии. Контрольную сумму, дату рождения и пол по ИИН проверяет сервер (R002).
 */
function IinInput(props: PresetInputProps) {
  return <Input type="text" inputMode="numeric" autoComplete="off" spellCheck={false} maxLength={12} {...props} />
}

export { IinInput, Input, inputVariants, type InputProps, PhoneInput }
