import { useCallback, useEffect, useRef } from 'react'
import type { FieldError, FieldPath, FieldValues, UseFormReturn } from 'react-hook-form'

import { i18n } from '@/shared/i18n'
import { toastApiError } from '@/shared/ui/toast'

import { ApiError } from './http'
import { type ApiErrorDescription, describeApiError, fieldErrorMessage } from './problem'

/**
 * Ошибки сервера → форма react-hook-form (PLT-07). Проверяет данные сервер (R002): форма шлёт как
 * есть, а ответ 400/422 с `errors[]` раскладывается по полям — текст под полем, фокус на первом
 * ошибочном поле формы. Всё, что к полю не относится (409, 429, 5xx, сеть, ошибка без `field`),
 * — общая ошибка формы: плашка `FormAlert` (`errors.root`) или уведомление.
 *
 * На экране — хук `useApiErrorForm`: он раскладывает ошибку так же и переводит её тексты заново при
 * смене языка (PLT-11).
 *
 * ```tsx
 * const form = useForm<LoginRequest>({ defaultValues: { login: '', password: '', rememberMe: true } })
 * const applyApiError = useApiErrorForm(form)
 * const login = useLogin()
 * const onSubmit = form.handleSubmit(async (data) => {
 *   try { await login.mutateAsync({ data }) } catch (error) { applyApiError(error) }
 * })
 * // …<FormAlert errors={form.formState.errors.root} />
 * ```
 */

/**
 * Общая ошибка формы — `errors.root.server` (текст; `type` — код ошибки) и код трассировки для
 * поддержки — `errors.root.serverTrace`. react-hook-form сам сбрасывает `root.*` при следующей отправке.
 */
export const FORM_ERROR = 'root.server' as const
export const FORM_TRACE = 'root.serverTrace' as const

export interface ApplyApiErrorOptions<T extends FieldValues> {
  /**
   * Поля, которые форма показывает. Ошибка другого поля — в общую ошибку, иначе её не увидят.
   * По умолчанию — поля со значением в форме (`getValues(path) !== undefined`), поэтому задавайте
   * `defaultValues` всем полям; скрытые поля (галочка по умолчанию) перечисляйте явно.
   */
  fields?: readonly FieldPath<T>[]
  /** Путь сервера → поле формы, если они расходятся (`contacts[0].phone` → `phone`). */
  fieldMap?: Readonly<Record<string, FieldPath<T>>>
  /** Куда общая ошибка: `inline` — плашка в форме (по умолчанию), `toast` — уведомление. */
  general?: 'inline' | 'toast'
}

export interface AppliedApiError {
  /** Поля с ошибкой в порядке формы; на первом — фокус. */
  fields: string[]
  /** Общая ошибка формы; null — всё разложено по полям. */
  general: ApiErrorDescription | null
}

type FormLike<T extends FieldValues> = Pick<UseFormReturn<T>, 'setError' | 'setFocus' | 'getValues'>

/** Путь поля сервера → путь react-hook-form: `items[0].quantity` → `items.0.quantity`. */
export function toFormPath(field: string): string {
  return field.replace(/\[(\d+)\]/g, '.$1')
}

/** Поля по порядку в документе (как их видит пользователь), а не в порядке ответа сервера. */
function inFormOrder(names: string[]): string[] {
  if (typeof document === 'undefined') return names
  const elementOf = (name: string) => document.getElementsByName(name)[0]
  return names
    .map((name, index) => ({ name, index, element: elementOf(name) }))
    .sort((a, b) => {
      if (a.element && b.element && a.element !== b.element) {
        return a.element.compareDocumentPosition(b.element) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1
      }
      if (a.element && !b.element) return -1
      if (!a.element && b.element) return 1
      return a.index - b.index
    })
    .map(({ name }) => name)
}

/** Разбор ошибки по форме: тексты — на текущем языке интерфейса. */
interface ErrorLayout {
  /** Ошибки полей в порядке формы: поле → ошибка для `setError`. */
  fields: [name: string, error: FieldError][]
  general: ApiErrorDescription | null
}

function layoutApiError<T extends FieldValues>(
  error: unknown,
  form: Pick<UseFormReturn<T>, 'getValues'>,
  { fields, fieldMap }: ApplyApiErrorOptions<T>,
): ErrorLayout {
  const isShown = (path: string) =>
    fields ? (fields as readonly string[]).includes(path) : form.getValues(path as FieldPath<T>) !== undefined

  const byField = new Map<string, { code: string; message: string }[]>()
  const formLevel: string[] = []
  for (const fieldError of error instanceof ApiError ? error.fieldErrors : []) {
    const message = fieldErrorMessage(fieldError)
    const path = fieldError.field ? (fieldMap?.[fieldError.field] ?? toFormPath(fieldError.field)) : null
    if (path && isShown(path)) byField.set(path, [...(byField.get(path) ?? []), { code: fieldError.code, message }])
    else formLevel.push(message)
  }

  const names = inFormOrder([...byField.keys()])
  const fieldErrors = names.map((name): [string, FieldError] => {
    const messages = byField.get(name) ?? []
    return [
      name,
      {
        type: messages[0]?.code ?? 'server',
        message: messages[0]?.message,
        // Несколько ошибок одного поля (пароль: короткий и распространённый) — все в `types`.
        types: Object.fromEntries(messages.map(({ code, message }) => [code, message])),
      },
    ]
  })

  let general: ApiErrorDescription | null = null
  if (formLevel.length > 0) general = { ...describeApiError(error), message: [...new Set(formLevel)].join(' ') }
  else if (names.length === 0) general = describeApiError(error)

  return { fields: fieldErrors, general }
}

/**
 * Разложить ошибку запроса по форме. Возвращает, что куда попало (для тестов и особых экранов).
 * Любую ошибку можно передать как есть: не ApiError (сеть, сбой) — тоже общая ошибка.
 */
export function applyApiErrorToForm<T extends FieldValues>(
  error: unknown,
  form: FormLike<T>,
  options: ApplyApiErrorOptions<T> = {},
): AppliedApiError {
  const { fields, general } = layoutApiError(error, form, options)
  for (const [name, fieldError] of fields) form.setError(name as FieldPath<T>, fieldError)
  const first = fields[0]?.[0]
  if (first) form.setFocus(first as FieldPath<T>)

  if (general) {
    if ((options.general ?? 'inline') === 'toast') toastApiError(error, general)
    else {
      form.setError(FORM_ERROR, { type: general.code, message: general.message })
      if (general.traceId) form.setError(FORM_TRACE, { type: 'trace', message: general.traceId })
    }
  }
  return { fields: fields.map(([name]) => name), general }
}

type RetranslatableForm<T extends FieldValues> = FormLike<T> & Pick<UseFormReturn<T>, 'getFieldState'>

/**
 * Тексты уже показанной ошибки — заново, на текущем языке. Только то, что ещё на экране: ошибку поля,
 * которую сняла правка, и плашку, которую сбросила новая отправка, смена языка не возвращает.
 */
function retranslateApiError<T extends FieldValues>(
  error: unknown,
  form: RetranslatableForm<T>,
  options: ApplyApiErrorOptions<T>,
): void {
  const shown = (path: string, type: string | undefined) => form.getFieldState(path as FieldPath<T>).error?.type === type
  const { fields, general } = layoutApiError(error, form, options)
  for (const [name, fieldError] of fields) {
    if (shown(name, fieldError.type)) form.setError(name as FieldPath<T>, fieldError)
  }
  if (general && (options.general ?? 'inline') === 'inline' && shown(FORM_ERROR, general.code)) {
    form.setError(FORM_ERROR, { type: general.code, message: general.message })
  }
}

/**
 * `applyApiErrorToForm` для экрана: раскладывает ошибку по форме и помнит её, а при смене языка
 * (PLT-11, «Рус | Қаз») переводит заново тексты под полями и в плашке — экран не правится. Без хука
 * готовые строки остались бы на прежнем языке рядом с уже переведённым экраном. Уведомление
 * (`general: 'toast'`) не переводится: оно живёт секунды.
 *
 * ```tsx
 * const applyApiError = useApiErrorForm(form)
 * // …catch (error) { applyApiError(error, { fields: ['login', 'password'] }) }
 * ```
 */
export function useApiErrorForm<T extends FieldValues>(
  form: RetranslatableForm<T>,
): (error: unknown, options?: ApplyApiErrorOptions<T>) => AppliedApiError {
  const shown = useRef<{ error: unknown; options: ApplyApiErrorOptions<T> } | null>(null)

  useEffect(() => {
    const retranslate = () => {
      if (shown.current) retranslateApiError(shown.current.error, form, shown.current.options)
    }
    i18n.on('languageChanged', retranslate)
    return () => i18n.off('languageChanged', retranslate)
  }, [form])

  return useCallback(
    (error: unknown, options: ApplyApiErrorOptions<T> = {}) => {
      shown.current = { error, options }
      return applyApiErrorToForm(error, form, options)
    },
    [form],
  )
}

/** Все тексты ошибки поля для shadcn `<FieldError errors={…} />` (несколько — списком). */
export function fieldErrorList(error: FieldError | undefined): { message?: string }[] {
  if (!error) return []
  const all = Object.values(error.types ?? {}).filter((value): value is string => typeof value === 'string')
  return all.length > 0 ? all.map((message) => ({ message })) : [{ message: error.message }]
}
