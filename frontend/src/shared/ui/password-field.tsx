import { Check, Eye, EyeOff, X } from 'lucide-react'
import type * as React from 'react'
import { useEffect, useId, useState } from 'react'
import { useTranslation } from 'react-i18next'

import {
  PASSWORD_RULES,
  passwordLevel,
  passwordRepeatMismatch,
  passwordRules,
  type StrengthLevel,
} from '@/shared/lib/passwordStrength'
import { cn } from '@/shared/lib/utils'

import { FieldError } from './field'
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupInput } from './input-group'

type PasswordFieldProps = Omit<React.ComponentProps<'input'>, 'type'> & {
  /**
   * `current` — ввести свой пароль (вход): только поле и «глаз». `new` — задать пароль (приглашение,
   * сброс, смена в профиле, регистрация): ещё индикатор надёжности zxcvbn-ts и чек-лист (IAM-05).
   * `repeat` — «Повторите пароль»: сверка с `original` до отправки (IAM-05).
   */
  mode?: 'current' | 'new' | 'repeat'
  /** Для `new`: телефон, e-mail, имя — пароль из них индикатор не похвалит. */
  userInputs?: readonly string[]
  /** Для `repeat`: пароль из поля нового пароля, с которым сверяется повтор. */
  original?: string
  density?: 'default' | 'compact'
}

/**
 * Общее поле пароля (IAM-05; макеты Vhod, Priglashenie, Parol, Profil): пилюля с кнопкой-«глазом»
 * (показать/скрыть, `aria-pressed`, `aria-controls`). В режиме «новый пароль» под полем — индикатор
 * надёжности в 4 уровня (читается диктором: «Надёжность пароля: хорошая, 3 из 4») и чек-лист
 * «8+ символов · есть цифра · есть заглавная» — подсказки по мере ввода. Режим «повтор»
 * (`mode="repeat"`, `original` — пароль из первого поля) подсвечивает поле и пишет «Пароли не
 * совпадают», форма не отправляет повтор, пока `passwordRepeatMismatch` (IAM-05: совпадение — до
 * отправки). Правила пароля проверяет сервер (`PasswordPolicy`, R002): ошибку поля от него
 * показывает `FieldError` под полем.
 *
 * Словари zxcvbn-ts грузятся лениво, только в режиме `new` и только при первом вводе.
 * `ref`, `value`, `onChange`, `aria-*` — на `<input>` (Controller react-hook-form передаёт их как есть).
 */
function PasswordField({
  mode = 'current',
  userInputs,
  original = '',
  density,
  className,
  id,
  ...props
}: PasswordFieldProps) {
  const { t } = useTranslation()
  const [visible, setVisible] = useState(false)
  const [left, setLeft] = useState(false)
  const generatedId = useId()
  const inputId = id ?? generatedId
  // Значение для индикатора и сверки: из `value` (контролируемое поле) или из ввода (неконтролируемое).
  const [typed, setTyped] = useState(typeof props.defaultValue === 'string' ? props.defaultValue : '')
  const password = typeof props.value === 'string' ? props.value : typed
  // Повтор не совпал: подсветка, когда он уже разошёлся с паролем или человек ушёл из поля (пока он
  // набирает начало пароля, красным не мигаем). Блокировать отправку — `passwordRepeatMismatch`.
  const mismatch =
    mode === 'repeat' &&
    passwordRepeatMismatch(original, password) &&
    password !== '' &&
    (left || !original.startsWith(password))
  const mismatchId = `${inputId}-mismatch`
  const describedBy = [props['aria-describedby'], mismatch ? mismatchId : null].filter(Boolean).join(' ') || undefined

  return (
    <div data-slot="password-field" className={cn('flex w-full flex-col gap-2', className)}>
      <InputGroup density={density}>
        <InputGroupInput
          {...props}
          id={inputId}
          type={visible ? 'text' : 'password'}
          autoComplete={props.autoComplete ?? (mode === 'current' ? 'current-password' : 'new-password')}
          autoCapitalize="none"
          spellCheck={false}
          aria-invalid={mismatch || props['aria-invalid']}
          aria-describedby={describedBy}
          onChange={(event) => {
            setTyped(event.currentTarget.value)
            props.onChange?.(event)
          }}
          onBlur={(event) => {
            setLeft(true)
            props.onBlur?.(event)
          }}
        />
        <InputGroupAddon align="inline-end">
          <InputGroupButton
            aria-label={visible ? t('common:password.hide') : t('common:password.show')}
            aria-pressed={visible}
            aria-controls={inputId}
            onClick={() => setVisible((shown) => !shown)}
            className="text-graphite hover:text-ink"
          >
            {visible ? <EyeOff className="size-5" aria-hidden="true" /> : <Eye className="size-5" aria-hidden="true" />}
          </InputGroupButton>
        </InputGroupAddon>
      </InputGroup>
      {mode === 'new' ? <PasswordHints password={password} userInputs={userInputs} /> : null}
      {mismatch ? <FieldError id={mismatchId}>{t('common:password.mismatch')}</FieldError> : null}
    </div>
  )
}

/** Цвет сегментов и слова по уровню: слабый — красный, средний — «пришёл», хороший и надёжный — «в кресле». */
const LEVEL_STYLE: Record<Exclude<StrengthLevel, 0>, { bar: string; text: string }> = {
  1: { bar: 'bg-danger', text: 'text-danger' },
  2: { bar: 'bg-status-arrived', text: 'text-status-arrived-fg' },
  3: { bar: 'bg-status-in-chair', text: 'text-status-in-chair-fg' },
  4: { bar: 'bg-status-in-chair', text: 'text-status-in-chair-fg' },
}

const SEGMENTS = [1, 2, 3, 4] as const

function PasswordHints({ password, userInputs }: { password: string; userInputs?: readonly string[] }) {
  const { t } = useTranslation()
  const level = usePasswordLevel(password, userInputs)
  const rules = passwordRules(password)
  const style = level === 0 ? null : LEVEL_STYLE[level]

  return (
    <div data-slot="password-hints" className="flex flex-col gap-2">
      <div className="flex items-center gap-2.5 pt-0.5">
        <div
          role="img"
          aria-label={t('common:password.strength', { level: String(level) })}
          data-level={level}
          className="grid flex-1 grid-cols-4 gap-1.5"
        >
          {SEGMENTS.map((segment) => (
            <span
              key={segment}
              className={cn('h-1.5 rounded-pill', style && segment <= level ? style.bar : 'bg-line')}
            />
          ))}
        </div>
        {style ? (
          <span aria-hidden="true" className={cn('text-caption font-extrabold', style.text)}>
            {t('common:password.strengthWord', { level: String(level) })}
          </span>
        ) : null}
      </div>
      <ul aria-label={t('common:password.rules.label')} className="flex flex-wrap gap-x-4 gap-y-1.5">
        {PASSWORD_RULES.map((rule) => (
          <li
            key={rule}
            data-rule={rule}
            data-met={rules[rule] || undefined}
            className={cn(
              'flex items-center gap-1.5 text-body-sm font-bold',
              rules[rule] ? 'text-ink' : 'text-graphite',
            )}
          >
            <span
              aria-hidden="true"
              className={cn(
                'flex size-4.5 shrink-0 items-center justify-center rounded-pill',
                rules[rule] ? 'bg-status-in-chair text-white' : 'bg-fog text-graphite',
              )}
            >
              {rules[rule] ? <Check className="size-3" strokeWidth={3.6} /> : <X className="size-2.5" strokeWidth={3.4} />}
            </span>
            {t(`common:password.rules.${rule}`)}
            <span className="sr-only">
              {rules[rule] ? t('common:password.rules.met') : t('common:password.rules.unmet')}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/**
 * Уровень zxcvbn-ts для пароля: поле пустое или словари ещё грузятся — 0; пока считается новый ввод,
 * виден прошлый уровень (без мигания). Ответ для устаревшего ввода не пишется.
 */
function usePasswordLevel(password: string, userInputs?: readonly string[]): StrengthLevel {
  const [scored, setScored] = useState<StrengthLevel>(0)
  const inputs = (userInputs ?? []).join('\n')

  useEffect(() => {
    if (!password) return undefined
    let current = true
    passwordLevel(password, inputs ? inputs.split('\n') : []).then(
      (level) => {
        if (current) setScored(level)
      },
      // Словари не загрузились (нет сети) — индикатор просто не покажет уровень.
      () => undefined,
    )
    return () => {
      current = false
    }
  }, [password, inputs])

  return password ? scored : 0
}

export { PasswordField, type PasswordFieldProps }
