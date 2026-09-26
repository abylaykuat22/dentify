import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { describe, expect, it, vi } from 'vitest'

import { i18n } from '@/shared/i18n'
import { passwordRepeatMismatch } from '@/shared/lib/passwordStrength'

import { Field, FieldLabel } from './field'
import { LoginField } from './login-field'
import { PasswordField } from './password-field'

// Общие поля экранов без входа (T-IAM-03; IAM-02, IAM-05): «Телефон или e-mail» с маской +7 и
// клавиатурой по типу ввода; пароль с «глазом», а в режиме «новый пароль» — индикатор zxcvbn-ts
// и чек-лист. Что верно, решает сервер: поля только показывают.

const t = i18n.t.bind(i18n)

/** Форма входа в миниатюре: значение поля — в react-hook-form, отправка отдаёт его наружу. */
function LoginForm({ onSubmit }: { onSubmit: (value: string) => void }) {
  const form = useForm({ defaultValues: { login: '' } })
  return (
    <form onSubmit={(event) => void form.handleSubmit((data) => onSubmit(data.login))(event)}>
      <Controller
        name="login"
        control={form.control}
        render={({ field }) => (
          <Field>
            <FieldLabel htmlFor="login">{t('auth:login.login')}</FieldLabel>
            <LoginField {...field} id="login" />
          </Field>
        )}
      />
      <button type="submit">{t('auth:login.submit')}</button>
      <button type="button" onClick={() => form.reset({ login: 'dinara@example.kz' })}>
        reset
      </button>
    </form>
  )
}

describe('LoginField — телефон или e-mail', () => {
  it('цифры — маска «+7 707 123 45 67», цифровая клавиатура; форма получает набранное', async () => {
    const onSubmit = vi.fn()
    render(<LoginForm onSubmit={onSubmit} />)
    const input = screen.getByLabelText(t('auth:login.login'))
    expect(input).toHaveAttribute('inputmode', 'email')
    expect(input).toHaveAttribute('type', 'text')
    expect(input).toHaveAttribute('autocomplete', 'username')

    await userEvent.type(input, '87071234567')

    expect(input).toHaveValue('+7 707 123 45 67')
    expect(input).toHaveAttribute('inputmode', 'tel')
    await userEvent.click(screen.getByRole('button', { name: t('auth:login.submit') }))
    // Серверу — как набрали: «8» и цифры, без пробелов маски (номер нормализует сервер, R002).
    expect(onSubmit).toHaveBeenCalledWith('87071234567')
  })

  it('буква или «@» — e-mail без маски; цифры, набранные до «@», — как были', async () => {
    const onSubmit = vi.fn()
    render(<LoginForm onSubmit={onSubmit} />)
    const input = screen.getByLabelText(t('auth:login.login'))

    await userEvent.type(input, '87071234567@mail.ru')

    expect(input).toHaveValue('87071234567@mail.ru')
    expect(input).toHaveAttribute('inputmode', 'email')
    await userEvent.click(screen.getByRole('button', { name: t('auth:login.submit') }))
    expect(onSubmit).toHaveBeenCalledWith('87071234567@mail.ru')
  })

  it('вставка номера в любой записи и правка в середине — курсор на месте', async () => {
    render(<LoginForm onSubmit={() => {}} />)
    const input = screen.getByLabelText<HTMLInputElement>(t('auth:login.login'))
    await userEvent.click(input)
    await userEvent.paste('+7 (707) 123-45-67')
    expect(input).toHaveValue('+7 707 123 45 67')

    // Курсор после «707», стёрли «7» — номер перестроился, курсор остался у места правки.
    input.setSelectionRange(6, 6)
    await userEvent.keyboard('{Backspace}')
    expect(input).toHaveValue('+7 701 234 56 7')
    expect(input.selectionStart).toBe(5)
  })

  it('сброс формы снаружи — поле показывает новое значение', async () => {
    render(<LoginForm onSubmit={() => {}} />)
    const input = screen.getByLabelText(t('auth:login.login'))
    await userEvent.type(input, '8707')
    await userEvent.click(screen.getByRole('button', { name: 'reset' }))
    expect(input).toHaveValue('dinara@example.kz')
  })
})

function NewPassword({ userInputs }: { userInputs?: string[] }) {
  const [value, setValue] = useState('')
  return (
    <Field>
      <FieldLabel htmlFor="password">{t('auth:login.password')}</FieldLabel>
      <PasswordField
        id="password"
        mode="new"
        value={value}
        onChange={(event) => setValue(event.currentTarget.value)}
        userInputs={userInputs}
      />
    </Field>
  )
}

describe('PasswordField — общий компонент пароля', () => {
  it('вход: «глаз» показывает и прячет пароль, индикатора и чек-листа нет', async () => {
    render(
      <Field>
        <FieldLabel htmlFor="password">{t('auth:login.password')}</FieldLabel>
        <PasswordField id="password" defaultValue="secret-1" />
      </Field>,
    )
    const input = screen.getByLabelText(t('auth:login.password'))
    expect(input).toHaveAttribute('type', 'password')
    expect(input).toHaveAttribute('autocomplete', 'current-password')

    const eye = screen.getByRole('button', { name: t('common:password.show') })
    expect(eye).toHaveAttribute('aria-pressed', 'false')
    expect(eye).toHaveAttribute('aria-controls', 'password')
    await userEvent.click(eye)
    expect(input).toHaveAttribute('type', 'text')
    const hide = screen.getByRole('button', { name: t('common:password.hide') })
    expect(hide).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(hide)
    expect(input).toHaveAttribute('type', 'password')

    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: t('common:password.rules.label') })).not.toBeInTheDocument()
  })

  it('новый пароль: пустое поле — «не оценена», чек-лист не выполнен', () => {
    render(<NewPassword />)
    expect(screen.getByLabelText(t('auth:login.password'))).toHaveAttribute('autocomplete', 'new-password')
    expect(screen.getByRole('img')).toHaveAccessibleName('Надёжность пароля: не оценена')
    const rules = within(screen.getByRole('list', { name: t('common:password.rules.label') })).getAllByRole('listitem')
    expect(rules.map((rule) => rule.textContent)).toEqual([
      '8+ символов— не выполнено',
      'есть цифра— не выполнено',
      'есть заглавная— не выполнено',
    ])
  })

  it('чек-лист и индикатор обновляются по мере ввода; индикатор читается диктором (IAM-05)', async () => {
    render(<NewPassword />)
    const input = screen.getByLabelText(t('auth:login.password'))
    const meter = screen.getByRole('img')

    await userEvent.type(input, '12345678')
    // Частый пароль — слабый, 1 из 4 (zxcvbn-ts со словарями common, en и ru).
    await waitFor(() => expect(meter).toHaveAccessibleName('Надёжность пароля: низкая, 1 из 4'), { timeout: 15_000 })
    expect(screen.getByText('Слабый')).toBeInTheDocument()
    const list = screen.getByRole('list', { name: t('common:password.rules.label') })
    expect(within(list).getByText('8+ символов').closest('li')).toHaveAttribute('data-met', 'true')
    expect(within(list).getByText('есть цифра').closest('li')).toHaveAttribute('data-met', 'true')
    expect(within(list).getByText('есть заглавная').closest('li')).not.toHaveAttribute('data-met')

    await userEvent.clear(input)
    await userEvent.type(input, 'Kx9#vLm2$Qw8zT!r')
    await waitFor(() => expect(meter).toHaveAccessibleName('Надёжность пароля: высокая, 4 из 4'), { timeout: 15_000 })
    expect(within(list).getByText('есть заглавная').closest('li')).toHaveAttribute('data-met', 'true')
    expect(screen.getByText('Надёжный')).toBeInTheDocument()
  })

  it('пароль из e-mail или телефона пользователя индикатор не хвалит (без них тот же — 4 из 4)', async () => {
    render(<NewPassword userInputs={['dinara.alieva@example.kz', '+77071234567']} />)
    await userEvent.type(screen.getByLabelText(t('auth:login.password')), 'dinara.alieva@example.kz')
    await waitFor(() => expect(screen.getByRole('img')).toHaveAccessibleName('Надёжность пароля: низкая, 1 из 4'), {
      timeout: 15_000,
    })
  })

  it('английское слово латиницей индикатор не хвалит: словарь language-en (без него «Nightingale» — 4 из 4)', async () => {
    render(<NewPassword />)
    await userEvent.type(screen.getByLabelText(t('auth:login.password')), 'Nightingale')
    await waitFor(() => expect(screen.getByRole('img')).toHaveAccessibleName('Надёжность пароля: низкая, 1 из 4'), {
      timeout: 15_000,
    })
  })

  it('повтор: пока набирают начало пароля — без ошибки; разошёлся или ушли из поля — подсветка и текст (IAM-05)', async () => {
    const submitted = vi.fn()
    render(<NewPasswordWithRepeat onSubmit={submitted} />)
    const password = screen.getByLabelText(t('auth:login.password'))
    const repeat = screen.getByLabelText(REPEAT_LABEL)
    expect(repeat).toHaveAttribute('autocomplete', 'new-password')
    // В режиме повтора индикатора и чек-листа нет — они у первого поля.
    expect(screen.getAllByRole('img')).toHaveLength(1)

    await userEvent.type(password, 'Kx9#vLm2')
    await userEvent.type(repeat, 'Kx9#')
    expect(repeat).not.toHaveAttribute('aria-invalid')
    expect(screen.queryByText(t('common:password.mismatch'))).not.toBeInTheDocument()
    // Отправка заблокирована, пока повтор не совпал.
    await userEvent.click(screen.getByRole('button', { name: 'save' }))
    expect(submitted).not.toHaveBeenCalled()

    // Ушли из поля с половиной пароля — несовпадение видно.
    await userEvent.tab()
    expect(repeat).toHaveAttribute('aria-invalid', 'true')
    const error = screen.getByRole('alert')
    expect(error).toHaveTextContent(t('common:password.mismatch'))
    expect(repeat).toHaveAttribute('aria-describedby', error.id)

    await userEvent.type(repeat, 'vLm2')
    expect(repeat).not.toHaveAttribute('aria-invalid')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'save' }))
    expect(submitted).toHaveBeenCalledOnce()

    // Разошёлся посреди ввода — ошибка сразу, без ухода из поля.
    await userEvent.clear(repeat)
    await userEvent.type(repeat, 'Kx8')
    expect(repeat).toHaveAttribute('aria-invalid', 'true')
  })
})

const REPEAT_LABEL = 'repeat'

/** Задать пароль с повтором, как в приглашении и сбросе: отправка — только при совпадении. */
function NewPasswordWithRepeat({ onSubmit }: { onSubmit: () => void }) {
  const [password, setPassword] = useState('')
  const [repeat, setRepeat] = useState('')
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        if (!passwordRepeatMismatch(password, repeat)) onSubmit()
      }}
    >
      <Field>
        <FieldLabel htmlFor="password">{t('auth:login.password')}</FieldLabel>
        <PasswordField
          id="password"
          mode="new"
          value={password}
          onChange={(event) => setPassword(event.currentTarget.value)}
        />
      </Field>
      <Field>
        <FieldLabel htmlFor="repeat">{REPEAT_LABEL}</FieldLabel>
        <PasswordField
          id="repeat"
          mode="repeat"
          original={password}
          value={repeat}
          onChange={(event) => setRepeat(event.currentTarget.value)}
        />
      </Field>
      <button type="submit">save</button>
    </form>
  )
}

describe('passwordRepeatMismatch', () => {
  it.each([
    ['Kx9#vLm2', 'Kx9#vLm2', false],
    ['Kx9#vLm2', 'Kx9#', true],
    ['Kx9#vLm2', '', true],
    ['', '', false],
    ['Kx9#vLm2', 'kx9#vlm2', true],
  ] as const)('%j и %j → %s', (password, repeat, mismatch) => {
    expect(passwordRepeatMismatch(password, repeat)).toBe(mismatch)
  })
})
