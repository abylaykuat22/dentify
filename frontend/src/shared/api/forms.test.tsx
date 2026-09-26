import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http as route, HttpResponse } from 'msw'
import { Controller, useForm } from 'react-hook-form'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/shared/i18n'
import { Field, FieldError, FieldLabel } from '@/shared/ui/field'
import { FormAlert } from '@/shared/ui/form-alert'
import { Input } from '@/shared/ui/input'
import { Toaster } from '@/shared/ui/toast'

import { type ApplyApiErrorOptions, applyApiErrorToForm, fieldErrorList, toFormPath } from './forms'
import { http } from './http'
import { server } from './mocks/node'
import { MOCK_TRACE_ID, problems } from './mocks/problems'

const t = i18n.t.bind(i18n)

// Хелпер форм (PLT-07): ответы сервера из MSW-моков (422 с errors[], 409 конфликт версии, 429, 500,
// сеть) раскладываются по форме react-hook-form на shadcn Field + Controller.

/** Форма-образец: пациент с позициями — порядок полей в DOM не совпадает с порядком ответа. */
interface PatientForm {
  name: string
  iin: string
  items: { quantity: string }[]
  version: number
}

const ENDPOINT = '/api/v1/patients'

function PatientFormView({ options }: { options?: ApplyApiErrorOptions<PatientForm> }) {
  const form = useForm<PatientForm>({ defaultValues: { name: '', iin: '', items: [{ quantity: '' }], version: 3 } })
  const onSubmit = form.handleSubmit(async (values) => {
    try {
      await http(ENDPOINT, { method: 'POST', body: JSON.stringify(values), headers: { 'Content-Type': 'application/json' } })
    } catch (error) {
      applyApiErrorToForm(error, form, options)
    }
  })
  const fields = [
    ['name', 'ФИО'],
    ['iin', 'ИИН'],
    ['items.0.quantity', 'Количество'],
  ] as const

  return (
    <form onSubmit={(event) => void onSubmit(event)} noValidate>
      {fields.map(([name, label]) => (
        <Controller
          key={name}
          name={name}
          control={form.control}
          render={({ field, fieldState }) => (
            <Field data-invalid={fieldState.invalid}>
              <FieldLabel htmlFor={name}>{label}</FieldLabel>
              <Input {...field} id={name} aria-invalid={fieldState.invalid} />
              <FieldError data-field={name} errors={fieldErrorList(fieldState.error)} />
            </Field>
          )}
        />
      ))}
      <FormAlert errors={form.formState.errors.root} />
      <button type="submit">save</button>
    </form>
  )
}

function respond(resolver: () => Response) {
  server.use(route.post(ENDPOINT, resolver))
}

async function submit() {
  await userEvent.click(screen.getByRole('button', { name: 'save' }))
}

const fieldError = (name: string) => document.querySelector(`[data-field="${name}"]`)

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('applyApiErrorToForm: ошибки полей', () => {
  it('422 errors[]: текст под полями, красный контур, фокус на первом ошибочном поле формы', async () => {
    // Сервер перечисляет ИИН раньше ФИО, но в форме первое — ФИО: фокус туда.
    respond(() =>
      problems.validation(
        [
          { field: 'iin', code: 'patient.iin.checksum', message: 'ИИН не прошёл проверку.' },
          { field: 'name', code: 'validation.not-blank', message: 'must not be blank' },
        ],
        422,
      ),
    )
    render(<PatientFormView />)
    await submit()

    await waitFor(() => expect(screen.getByLabelText('ФИО')).toHaveFocus())
    // Код в словаре — текст словаря; нет в словаре — текст сервера.
    expect(fieldError('name')).toHaveTextContent(t('errors:validation.not-blank'))
    expect(fieldError('iin')).toHaveTextContent('ИИН не прошёл проверку.')
    expect(screen.getByLabelText('ИИН')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Количество')).toHaveAttribute('aria-invalid', 'false')
    // Всё легло на поля — общей плашки нет.
    expect(document.querySelector('[data-slot="form-alert"]')).toBeNull()
  })

  it('вложенный путь сервера items[0].quantity → поле items.0.quantity', async () => {
    expect(toFormPath('items[0].quantity')).toBe('items.0.quantity')
    expect(toFormPath('name.ru')).toBe('name.ru')
    respond(() =>
      problems.validation([{ field: 'items[0].quantity', code: 'inventory.quantity.negative', message: 'Минус на складе.' }], 422),
    )
    render(<PatientFormView />)
    await submit()

    await waitFor(() => expect(screen.getByLabelText('Количество')).toHaveFocus())
    expect(fieldError('items.0.quantity')).toHaveTextContent('Минус на складе.')
  })

  it('несколько ошибок одного поля — все под полем', async () => {
    respond(() =>
      problems.validation([
        { field: 'name', code: 'password.too-short', message: 'Не короче 10 символов.' },
        { field: 'name', code: 'password.common', message: 'Слишком распространён.' },
      ]),
    )
    render(<PatientFormView />)
    await submit()

    const list = await waitFor(() => within(fieldError('name') as HTMLElement).getAllByRole('listitem'))
    expect(list.map((item) => item.textContent)).toEqual(['Не короче 10 символов.', 'Слишком распространён.'])
  })

  it('поле, которого нет в форме, и ошибка без field — в общую плашку; поля формы — под поля', async () => {
    respond(() =>
      problems.validation([
        { field: 'iin', code: 'patient.iin.checksum', message: 'ИИН не прошёл проверку.' },
        { field: 'birthDate', code: 'validation.past', message: 'Дата должна быть в прошлом.' },
        { field: null, code: 'iam.account.contact-required', message: 'Укажите телефон или e-mail.' },
      ]),
    )
    render(<PatientFormView />)
    await submit()

    const formAlert = await waitFor(() => document.querySelector('[data-slot="form-alert"]') as HTMLElement)
    expect(formAlert).toHaveTextContent('Дата должна быть в прошлом. Укажите телефон или e-mail.')
    expect(fieldError('iin')).toHaveTextContent('ИИН не прошёл проверку.')
    expect(screen.getByLabelText('ИИН')).toHaveFocus()
  })

  it('fields и fieldMap: скрытое поле — в общую ошибку, другое имя сервера — на своё поле', async () => {
    respond(() =>
      problems.validation([
        { field: 'fullName', code: 'validation.not-blank', message: 'Заполните поле.' },
        { field: 'version', code: 'validation.not-null', message: 'Заполните поле.' },
      ]),
    )
    render(<PatientFormView options={{ fieldMap: { fullName: 'name' }, fields: ['name', 'iin', 'items.0.quantity'] }} />)
    await submit()

    await waitFor(() => expect(fieldError('name')).toHaveTextContent(t('errors:validation.not-blank')))
    // `version` в значениях формы есть, но не показан — без `fields` ошибка пропала бы молча.
    expect(document.querySelector('[data-slot="form-alert"]')).toHaveTextContent(t('errors:validation.not-null'))
  })

  it('правка поля снимает его ошибку сервера, следующая отправка — общую', async () => {
    respond(() => problems.validation([{ field: 'name', code: 'validation.not-blank', message: 'Заполните поле.' }]))
    render(<PatientFormView />)
    await submit()
    await waitFor(() => expect(fieldError('name')).toHaveTextContent(t('errors:validation.not-blank')))

    await userEvent.type(screen.getByLabelText('ФИО'), 'Тестова Айгерим')
    await waitFor(() => expect(fieldError('name')).toBeNull())
    expect(screen.getByLabelText('ФИО')).toHaveAttribute('aria-invalid', 'false')

    respond(() => problems.businessRule())
    await submit()
    await waitFor(() => expect(document.querySelector('[data-slot="form-alert"]')).toHaveTextContent(t('errors:error.business-rule')))
    respond(() => HttpResponse.json({}))
    await submit()
    await waitFor(() => expect(document.querySelector('[data-slot="form-alert"]')).toBeNull())
  })
})

describe('applyApiErrorToForm: общая ошибка формы', () => {
  it('плашка забирает фокус: клавиатура продолжает с места ошибки (WCAG 2.4.3)', async () => {
    respond(() => problems.businessRule())
    render(<PatientFormView />)
    await submit()

    const formAlert = await waitFor(() => document.querySelector('[data-slot="form-alert"]') as HTMLElement)
    await waitFor(() => expect(formAlert).toHaveFocus())
  })

  it('409 конфликт версии — плашка «Запись уже изменена (кто, когда)»', async () => {
    respond(() => problems.versionConflict({ modifiedAt: '2026-09-26T09:05:00Z', modifiedByName: 'Алиева Динара' }))
    render(<PatientFormView />)
    await submit()

    const formAlert = await waitFor(() => document.querySelector('[data-slot="form-alert"]') as HTMLElement)
    expect(formAlert).toHaveAttribute('role', 'alert')
    expect(formAlert).toHaveTextContent(/Запись уже изменена \(Алиева Динара, 26 сентября/)
  })

  it('429 — через сколько повторить (Retry-After)', async () => {
    respond(() => problems.tooManyRequests(900, 'error.too-many-login-attempts'))
    render(<PatientFormView />)
    await submit()

    await waitFor(() =>
      expect(document.querySelector('[data-slot="form-alert"]')).toHaveTextContent(
        t('errors:ui.tooManyWait', { unit: 'minute', value: 15 }),
      ),
    )
  })

  it('500 — текст и код ошибки для поддержки, «Скопировать код» кладёт его в буфер', async () => {
    const writeText = vi.fn(() => Promise.resolve())
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } })
    respond(() => problems.internal())
    render(<PatientFormView />)
    await submit()

    const formAlert = await waitFor(() => document.querySelector('[data-slot="form-alert"]') as HTMLElement)
    expect(formAlert).toHaveTextContent(t('errors:error.internal'))
    expect(formAlert).toHaveTextContent(t('errors:ui.traceId', { traceId: MOCK_TRACE_ID }))
    await userEvent.click(within(formAlert).getByRole('button', { name: t('errors:ui.copyTrace') }))
    expect(writeText).toHaveBeenCalledWith(MOCK_TRACE_ID)
    expect(await within(formAlert).findByRole('button', { name: t('errors:ui.traceCopied') })).toBeInTheDocument()
  })

  it('нет сети — «Нет связи с сервером»', async () => {
    respond(() => HttpResponse.error())
    render(<PatientFormView />)
    await submit()

    await waitFor(() =>
      expect(document.querySelector('[data-slot="form-alert"]')).toHaveTextContent(t('errors:ui.network')),
    )
  })

  it("general: 'toast' — общая ошибка уведомлением, поля — как обычно", async () => {
    respond(() => problems.internal())
    render(
      <>
        <PatientFormView options={{ general: 'toast' }} />
        <Toaster />
      </>,
    )
    await submit()

    expect(await screen.findByText(t('errors:error.internal'))).toBeInTheDocument()
    expect(screen.getByText(t('errors:ui.traceId', { traceId: MOCK_TRACE_ID }))).toBeInTheDocument()
    expect(document.querySelector('[data-slot="form-alert"]')).toBeNull()
  })
})
