import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { startOfWeek } from 'date-fns/startOfWeek'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { getMe } from '@/shared/api/generated/endpoints/account/account'
import { owner } from '@/shared/api/mocks/fixtures'
import { server } from '@/shared/api/mocks/node'
import { describeApiError } from '@/shared/api/problem'
import { ApiError } from '@/shared/api/http'
import { formatDayLabel } from '@/shared/lib/format'
import { addKazakhFixture, removeKazakhFixture } from '@/test/kkFixture'
import { renderApp } from '@/test/renderApp'

import { dateLocale, weekStartsOn } from './dates'
import { changeUiLanguage, i18n, uiLanguages } from './index'
import { pickLocalized, useLocalized } from './localized'

// i18n (PLT-11, dentify R003): только русский, но казахский включается словарём и флагом i18n.kk
// без правок компонентов. Словарь kk в тестах — фикстура (src/test/kkFixture.ts).

const t = i18n.t.bind(i18n)
/**
 * Первая отрисовка приложения и смена языка (догружает локаль дат отдельным чанком) под нагрузкой
 * полного прогона бывают дольше секунды — ожидание по умолчанию в Testing Library.
 */
const SWITCH = { timeout: 5000 }

afterEach(async () => {
  vi.unstubAllEnvs()
  vi.restoreAllMocks()
  await removeKazakhFixture()
})

describe('ICU-склонения (i18next-icu)', () => {
  it.each([
    [1, '1 год'],
    [2, '2 года'],
    [5, '5 лет'],
    [11, '11 лет'],
    [21, '21 год'],
    [29, '29 лет'],
    [41, '41 год'],
    [52, '52 года'],
    [0, '0 лет'],
    [1.5, '1,5 года'],
  ])('возраст %s — «%s»', (age, text) => {
    expect(t('common:age', { age })).toBe(text)
  })

  it('подстановки — {name}, а не {{name}}', () => {
    expect(t('common:ui.steps.progress', { current: 2, total: 3 })).toBe('Шаг 2 из 3')
    expect(t('shell:nav.userMenu', { name: 'Тестова Алия' })).toBe('Меню пользователя: Тестова Алия')
  })

  it('нет ключа — ошибка в консоли dev-сборки и тестов, на экране — сам ключ', () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    const translate = i18n.t.bind(i18n) as (key: string) => string
    // Ключ собран из частей: намеренно несуществующий, проверка словаря (dictionary.test.ts) его не ищет.
    expect(translate(['shell', 'nav.nope'].join(':'))).toBe('nav.nope')
    expect(error).toHaveBeenCalledWith(expect.stringContaining('нет ключа shell:nav.nope'))
  })
})

describe('даты — date-fns с локалью языка', () => {
  it('русская локаль, неделя с понедельника (настройка клиники, пока константа)', () => {
    expect(dateLocale().code).toBe('ru')
    expect(weekStartsOn()).toBe(1)
    // Четверг 24 сентября 2026 → неделя с понедельника 21-го.
    expect(startOfWeek(new Date(2026, 8, 24)).getDate()).toBe(21)
    expect(formatDayLabel(new Date(2026, 8, 24))).toBe('Четверг, 24 сентября')
  })
})

describe('LocalizedText { ru, kk }', () => {
  it('нет перевода (null, пусто) — русское название', () => {
    expect(pickLocalized({ ru: 'Кариес', kk: null }, 'kk')).toBe('Кариес')
    expect(pickLocalized({ ru: 'Кариес', kk: '  ' }, 'kk')).toBe('Кариес')
    expect(pickLocalized({ ru: 'Кариес' }, 'kk')).toBe('Кариес')
    expect(pickLocalized({ ru: 'Кариес', kk: 'Тісжегі' }, 'kk')).toBe('Тісжегі')
    expect(pickLocalized({ ru: 'Кариес', kk: 'Тісжегі' }, 'ru')).toBe('Кариес')
  })

  it('useLocalized — язык интерфейса, перерисовка при смене', async () => {
    vi.stubEnv('VITE_I18N_KK', 'true')
    addKazakhFixture()
    function Diagnosis() {
      const localized = useLocalized()
      return (
        <p>
          {localized({ ru: 'Кариес', kk: 'Тісжегі' })} / {localized({ ru: 'Пульпит', kk: null })}
        </p>
      )
    }
    render(<Diagnosis />)
    expect(screen.getByText('Кариес / Пульпит')).toBeInTheDocument()
    await changeUiLanguage('kk')
    expect(await screen.findByText('Тісжегі / Пульпит', {}, SWITCH)).toBeInTheDocument()
  })
})

describe('флаг i18n.kk: «Рус | Қаз» на входе, в профиле и «Ещё»', () => {
  it.each(['/login', '/profile', '/more'])('выключен (по умолчанию) — на %s переключателя нет, lang=ru', async (path) => {
    addKazakhFixture() // даже при словаре kk
    renderApp(path, path === '/login' ? 'none' : 'owner')
    await screen.findByRole('heading', { level: 1 }, SWITCH)
    expect(document.querySelector('[data-language-switch]')).toBeNull()
    expect(screen.queryByRole('button', { name: t('common:languages.kk.short') })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: t('common:languages.kk.full') })).not.toBeInTheDocument()
    expect(document.documentElement.lang).toBe('ru')
    expect(uiLanguages()).toEqual(['ru'])
  })

  it('выключен — смена языка в обход интерфейса тоже оставляет русский', async () => {
    addKazakhFixture()
    await changeUiLanguage('kk')
    expect(i18n.language).toBe('ru')
  })

  it.each(['/login', '/profile', '/more'])('включён и есть словарь kk — на %s переключатель есть', async (path) => {
    vi.stubEnv('VITE_I18N_KK', 'true')
    addKazakhFixture()
    renderApp(path, path === '/login' ? 'none' : 'owner')
    const kk = await screen.findByRole(
      'button',
      { name: path === '/profile' ? t('common:languages.kk.full') : t('common:languages.kk.short') },
      SWITCH,
    )
    expect(kk).toHaveAttribute('lang', 'kk')
    expect(kk).toHaveAttribute('aria-pressed', 'false')
  })

  it('включён: «Қаз» на входе — тексты, заголовок вкладки, lang, даты и Accept-Language без правок компонентов', async () => {
    vi.stubEnv('VITE_I18N_KK', 'true')
    addKazakhFixture()
    renderApp('/login', 'none')
    const group = await screen.findByRole('group', { name: t('common:languages.label') }, SWITCH)
    expect(await screen.findByRole('heading', { level: 1, name: 'Вход' })).toBeInTheDocument()
    await waitFor(() => expect(document.title).toBe('Вход — Dentify'))

    await userEvent.click(within(group).getByRole('button', { name: t('common:languages.kk.short') }))

    // Экран — из словаря kk; чего в нём нет — по-русски (фолбэк на ru).
    expect(await screen.findByRole('heading', { level: 1, name: 'Кіру' }, SWITCH)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Кіру' })).toBeInTheDocument()
    expect(screen.getByLabelText(t('auth:login.password'))).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Интерфейс тілі' })).toBeInTheDocument()
    // То, что посчитано вне React, тоже на казахском: заголовок вкладки, lang, даты.
    await waitFor(() => expect(document.title).toBe('Кіру — Dentify'))
    expect(document.documentElement.lang).toBe('kk')
    expect(dateLocale().code).toBe('kk')
    expect(formatDayLabel(new Date(2026, 8, 24))).toMatch(/^24 қыркүйек, /)
    // ICU на казахском: своё склонение и свои правила plural.
    expect(t('common:age', { age: 41 })).toBe('41 жас')
    expect(describeApiError(new ApiError({ status: 429, problem: null, retryAfter: 900 })).message).toBe(
      'Әрекет тым көп. 15 минуттан кейін қайталаңыз',
    )
    // Сервер отвечает на языке интерфейса; выбор запомнен на устройстве.
    let language: string | null = null
    server.use(
      http.get('/api/v1/me', ({ request }) => {
        language = request.headers.get('Accept-Language')
        return HttpResponse.json(owner)
      }),
    )
    await getMe()
    expect(language).toBe('kk')
    expect(localStorage.getItem('dentify-lang')).toBe('kk')
  })

  it('включён: «Ещё» — сайдбар и нижняя панель переходят на казахский сами', async () => {
    vi.stubEnv('VITE_I18N_KK', 'true')
    addKazakhFixture()
    renderApp('/more', 'owner')
    const group = await screen.findByRole('group', { name: t('shell:more.language') }, SWITCH)
    await userEvent.click(within(group).getByRole('button', { name: t('common:languages.kk.short') }))

    const bottom = await screen.findByRole('navigation', { name: t('shell:nav.mobileLabel') }, SWITCH)
    await waitFor(() => expect(within(bottom).getByRole('link', { name: 'Кесте' })).toBeInTheDocument(), SWITCH)
    expect(within(bottom).getByRole('link', { name: 'Тағы' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('group', { name: 'Тіл' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Шығу' })).toBeInTheDocument()
  })

  it('включён: ошибки сервера в форме входа переводятся вместе с экраном, снятые — не возвращаются', async () => {
    vi.stubEnv('VITE_I18N_KK', 'true')
    addKazakhFixture()
    renderApp('/login', 'none')
    const group = await screen.findByRole('group', { name: t('common:languages.label') }, SWITCH)
    const loginError = () => document.getElementById('login-login-error')
    const formAlert = () => document.querySelector('[data-slot="form-alert"]')

    // Пустая форма → 400 с errors[] под полями (мок входа); смена языка переводит текст под полем.
    await userEvent.click(screen.getByRole('button', { name: t('auth:login.submit') }))
    await waitFor(() => expect(loginError()).toHaveTextContent(t('errors:validation.not-blank')))
    await userEvent.click(within(group).getByRole('button', { name: t('common:languages.kk.short') }))
    await waitFor(() => expect(loginError()).toHaveTextContent('Өрісті толтырыңыз'), SWITCH)

    // Правка полей снимает их ошибки; лимит попыток (429, Retry-After 15 мин) — плашка на казахском.
    await userEvent.type(screen.getByLabelText(t('auth:login.login')), 'limit@example.kz')
    await userEvent.type(screen.getByLabelText(t('auth:login.password')), 'secret')
    await userEvent.click(screen.getByRole('button', { name: 'Кіру' }))
    await waitFor(() => expect(formAlert()).toHaveTextContent('Әрекет тым көп. 15 минуттан кейін қайталаңыз'))

    // Обратно на русский: и экран, и плашка по-русски; ошибки полей, снятые правкой, не вернулись.
    await userEvent.click(within(group).getByRole('button', { name: t('common:languages.ru.short') }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Вход' }, SWITCH)).toBeInTheDocument()
    await waitFor(() => expect(formAlert()).toHaveTextContent(t('errors:ui.tooManyWait', { unit: 'minute', value: 15 })))
    expect(formAlert()).not.toHaveTextContent('Әрекет')
    expect(loginError()).toBeNull()
    expect(screen.getByLabelText(t('auth:login.login'))).toHaveAttribute('aria-invalid', 'false')
  })
})
