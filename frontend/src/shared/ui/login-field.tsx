import type * as React from 'react'
import { useCallback, useLayoutEffect, useRef, useState } from 'react'

import { applyLoginEdit, loginInputMode, loginState, type LoginState } from '@/shared/lib/loginInput'

import { Input } from './input'

type LoginFieldProps = Omit<
  React.ComponentProps<'input'>,
  'type' | 'inputMode' | 'value' | 'defaultValue' | 'onChange'
> & {
  /** Значение — как набрал человек (без пробелов маски): его и отправляет форма. */
  value: string
  onChange: (value: string) => void
  density?: 'default' | 'compact'
}

/**
 * Поле «Телефон или e-mail» (IAM-02; вход, сброс пароля). Тип — по вводу: цифры и «+» — телефон под
 * маской «+7 707 123 45 67» и цифровая клавиатура (`inputmode="tel"`), буква или «@» — e-mail без
 * маски и клавиатура с «@». Правила маски и возврат к набранному — `shared/lib/loginInput.ts`.
 * Поле только показывает: какой это логин и есть ли аккаунт, решает сервер (R002).
 *
 * Контролируемое (Controller react-hook-form): `value`/`onChange` — значение без маски, `ref` — на
 * `<input>` (фокус на ошибке поля). `type="text"`: у `type="email"` нельзя ставить курсор.
 */
function LoginField({ value, onChange, ref, density, ...props }: LoginFieldProps) {
  const [state, setState] = useState<LoginState>(() => loginState(value))
  const input = useRef<HTMLInputElement | null>(null)
  const caret = useRef<number | null>(null)

  // Значение сменили снаружи (сброс формы) — вид с нуля.
  if (value !== state.raw) setState(loginState(value))

  // После правки маска переставила символы — курсор туда, где человек его ожидает.
  useLayoutEffect(() => {
    const element = input.current
    const position = caret.current
    caret.current = null
    if (element && position !== null && document.activeElement === element) {
      element.setSelectionRange(position, position)
    }
  }, [state])

  const setRefs = useCallback(
    (element: HTMLInputElement | null) => {
      input.current = element
      if (typeof ref === 'function') return ref(element)
      if (ref) ref.current = element
      return undefined
    },
    [ref],
  )

  return (
    <Input
      {...props}
      ref={setRefs}
      type="text"
      inputMode={loginInputMode(state.raw)}
      autoComplete={props.autoComplete ?? 'username'}
      autoCapitalize="none"
      spellCheck={false}
      density={density}
      value={state.display}
      onChange={(event) => {
        const target = event.currentTarget
        const edit = applyLoginEdit(state, target.value, target.selectionStart ?? target.value.length)
        caret.current = edit.caret
        setState({ raw: edit.raw, display: edit.display, trunk: edit.trunk })
        if (edit.raw !== state.raw) onChange(edit.raw)
      }}
    />
  )
}

export { LoginField, type LoginFieldProps }
