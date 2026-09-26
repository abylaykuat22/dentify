import { BASE_LANGUAGE, i18n } from '@/shared/i18n'
import { formatDate } from '@/shared/lib/format'

import type { ProblemFieldError } from './generated/model'
import { ApiError, codeForStatus, NetworkError } from './http'

/**
 * Текст ошибки API для человека (PLT-07). Порядок:
 * 1. словарь фронта по коду — пространство `errors` (`iam.login.invalid` → `errors:iam.login.invalid`,
 *    файл `src/shared/i18n/locales/ru/errors.json`). Там только коды без параметров: у `validation.size`
 *    («от 2 до 50») числа знает лишь сервер;
 * 2. текст сервера — `detail` / `message` (он уже на языке пользователя: `Accept-Language`);
 * 3. общий текст по статусу (`errors:error.*`), в крайнем случае `errors:ui.unknown`.
 * Особые случаи с данными ответа: конфликт версии — кто и когда изменил запись; 429 с `Retry-After` —
 * через сколько повторить; нет сети — `errors:ui.network`.
 */

// Ключ по коду сервера строится во время работы — типизированный `t` его не примет.
type Translate = (key: string, options?: Record<string, unknown>) => unknown
const translate = i18n.t.bind(i18n) as unknown as Translate

/** Текст словаря по коду ошибки или null, если фронт этот код не переводит. */
export function dictionaryText(code: string, params?: Record<string, unknown>): string | null {
  // Код — префикс других кодов (узел словаря, а не строка) — не перевод. Структура словарей всех
  // языков одна (dictionary.test.ts), поэтому смотрим в базовый.
  if (typeof i18n.getResource(BASE_LANGUAGE, 'errors', code) !== 'string') return null
  return String(translate(`errors:${code}`, params))
}

/**
 * Сколько ждать до повтора — параметры строки `errors:ui.tooManyWait`: до минуты — секунды, дальше —
 * минуты с округлением вверх. Склонение («через 1 минуту», «через 15 минут») — ICU в словаре.
 */
export function retryWait(seconds: number): { unit: 'minute' | 'second'; value: number } {
  return seconds >= 60
    ? { unit: 'minute', value: Math.ceil(seconds / 60) }
    : { unit: 'second', value: Math.max(1, Math.ceil(seconds)) }
}

/** «26 сентября, 14:05» — когда запись изменили (конфликт версии). */
function formatMoment(iso: string): string | null {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? null : formatDate(date, 'moment')
}

export interface ApiErrorDescription {
  /** Текст для плашки формы или уведомления. */
  message: string
  /** Код ошибки (`error.version-conflict`); `network` — ответа не было. */
  code: string
  /**
   * Код для поддержки (traceId) — только у сбоев сервера (5xx): «сообщите код ошибки». У ответов по
   * существу (неверный пароль, лимит, конфликт) он человеку не нужен и не показывается, хотя в
   * `ApiError.traceId` есть всегда, когда сервер его прислал.
   */
  traceId: string | null
}

function conflictText(error: ApiError): string | null {
  const name = error.problem?.modifiedByName
  const at = error.problem?.modifiedAt ? formatMoment(error.problem.modifiedAt) : null
  if (!name || !at) return null
  return i18n.t('errors:ui.versionConflictBy', { name, time: at })
}

/** Общая (не полевая) ошибка: что показать пользователю. Любое `unknown` — на случай чужих исключений. */
export function describeApiError(error: unknown): ApiErrorDescription {
  if (error instanceof NetworkError) {
    return { message: i18n.t('errors:ui.network'), code: 'network', traceId: null }
  }
  if (!(error instanceof ApiError)) {
    return { message: i18n.t('errors:ui.unknown'), code: 'unknown', traceId: null }
  }

  const { code } = error
  const traceId = error.status >= 500 ? error.traceId : null
  const special =
    code === 'error.version-conflict'
      ? conflictText(error)
      : error.status === 429 && error.retryAfter !== null
        ? i18n.t('errors:ui.tooManyWait', retryWait(error.retryAfter))
        : null

  const message =
    special ??
    dictionaryText(code) ??
    (error.problem?.detail || null) ??
    dictionaryText(codeForStatus(error.status)) ??
    i18n.t('errors:ui.unknown')

  return { message, code, traceId }
}

/** Текст ошибки поля: словарь по коду → текст сервера → «Проверьте значение». */
export function fieldErrorMessage(fieldError: ProblemFieldError): string {
  return dictionaryText(fieldError.code) ?? (fieldError.message || i18n.t('errors:ui.fieldInvalid'))
}
