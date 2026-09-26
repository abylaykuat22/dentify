import { describe, expect, it } from 'vitest'

import { i18n } from '@/shared/i18n'

import type { ProblemDetail } from './generated/model'
import { ApiError, NetworkError } from './http'
import { describeApiError, dictionaryText, fieldErrorMessage, retryWait } from './problem'

const t = i18n.t.bind(i18n)

function apiError(status: number, problem: Partial<ProblemDetail> | null, retryAfter: number | null = null) {
  return new ApiError({
    status,
    problem: problem ? { title: '', status, detail: '', code: 'error.bad-request', ...problem } : null,
    retryAfter,
  })
}

describe('текст ошибки API: словарь → текст сервера → по статусу', () => {
  it('код есть в словаре — текст словаря, а не сервера', () => {
    const error = apiError(401, { code: 'iam.login.invalid', detail: 'Server says no', traceId: 'trace-1' })
    expect(describeApiError(error)).toEqual({
      message: t('errors:iam.login.invalid'),
      code: 'iam.login.invalid',
      // Ответ по существу — код для поддержки не показывается.
      traceId: null,
    })
  })

  it('сбой сервера (5xx) — с кодом для поддержки', () => {
    const error = apiError(500, { code: 'error.internal', detail: 'Внутренняя ошибка.', traceId: 'trace-5' })
    expect(describeApiError(error)).toEqual({ message: t('errors:error.internal'), code: 'error.internal', traceId: 'trace-5' })
  })

  it('кода нет в словаре — текст сервера (он уже на языке пользователя)', () => {
    const error = apiError(422, { code: 'schedule.appointment.past', detail: 'Нельзя записать задним числом.' })
    expect(describeApiError(error).message).toBe('Нельзя записать задним числом.')
  })

  it('ни словаря, ни текста (ответил прокси) — общий текст по статусу', () => {
    expect(describeApiError(apiError(503, null)).message).toBe(t('errors:error.unavailable'))
    expect(describeApiError(apiError(500, null)).message).toBe(t('errors:error.internal'))
    expect(describeApiError(apiError(418, { code: 'teapot', detail: '' })).message).toBe(
      t('errors:error.bad-request'),
    )
  })

  it('409 конфликт версии — кто и когда изменил запись', () => {
    const error = apiError(409, {
      code: 'error.version-conflict',
      detail: 'Запись уже изменил другой пользователь.',
      modifiedAt: '2026-09-26T09:05:00Z',
      modifiedByName: 'Алиева Динара',
    })
    const { message } = describeApiError(error)
    expect(message).toContain('Алиева Динара')
    // Время — в поясе браузера (тесты: как у CI), шаблон словаря `common:dates.moment` (date-fns).
    expect(message).toMatch(/26 сентября, \d{2}:05/)
    // Без имени — обычный текст по коду.
    expect(describeApiError(apiError(409, { code: 'error.version-conflict' })).message).toBe(
      t('errors:error.version-conflict'),
    )
  })

  it('429 с Retry-After — через сколько повторить', () => {
    const minutes = apiError(429, { code: 'error.too-many-login-attempts', detail: 'через 15 мин.' }, 900)
    expect(describeApiError(minutes).message).toBe('Слишком много попыток. Повторите через 15 минут')
    const seconds = apiError(429, { code: 'error.too-many-requests' }, 45)
    expect(describeApiError(seconds).message).toBe('Слишком много попыток. Повторите через 45 секунд')
    // Без заголовка — текст по коду.
    expect(describeApiError(apiError(429, { code: 'error.too-many-requests' })).message).toBe(
      t('errors:error.too-many-requests'),
    )
  })

  it('сеть и чужие исключения', () => {
    expect(describeApiError(new NetworkError(new TypeError('Failed to fetch')))).toEqual({
      message: t('errors:ui.network'),
      code: 'network',
      traceId: null,
    })
    expect(describeApiError(new Error('boom')).message).toBe(t('errors:ui.unknown'))
  })
})

describe('ошибка поля', () => {
  it('словарь по коду, иначе текст сервера, иначе «Проверьте значение»', () => {
    expect(fieldErrorMessage({ field: 'login', code: 'validation.not-blank', message: 'must not be blank' })).toBe(
      t('errors:validation.not-blank'),
    )
    expect(fieldErrorMessage({ field: 'name', code: 'validation.size', message: 'Допустимая длина — от 2 до 50.' })).toBe(
      'Допустимая длина — от 2 до 50.',
    )
    expect(fieldErrorMessage({ field: 'x', code: 'x.unknown', message: '' })).toBe(t('errors:ui.fieldInvalid'))
  })

  it('код — узел словаря, а не строка (`iam.login`) — не перевод', () => {
    expect(dictionaryText('iam.login')).toBeNull()
    expect(dictionaryText('iam.login.invalid')).toBe(t('errors:iam.login.invalid'))
  })

  it('retryWait: секунды до минуты, дальше — минуты с округлением вверх', () => {
    expect(retryWait(0.2)).toEqual({ unit: 'second', value: 1 })
    expect(retryWait(59)).toEqual({ unit: 'second', value: 59 })
    expect(retryWait(60)).toEqual({ unit: 'minute', value: 1 })
    expect(retryWait(61)).toEqual({ unit: 'minute', value: 2 })
    expect(retryWait(900)).toEqual({ unit: 'minute', value: 15 })
  })

  it.each([
    [1, 'через 1 секунду'],
    [3, 'через 3 секунды'],
    [45, 'через 45 секунд'],
    [60, 'через 1 минуту'],
    [120, 'через 2 минуты'],
    [900, 'через 15 минут'],
    [1260, 'через 21 минуту'],
  ])('429 с Retry-After %i с — «%s» (ICU-склонение после «через»)', (retryAfter, wait) => {
    const error = apiError(429, { code: 'error.too-many-requests' }, retryAfter)
    expect(describeApiError(error).message).toBe(`Слишком много попыток. Повторите ${wait}`)
  })
})
