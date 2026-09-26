import { focusManager } from '@tanstack/react-query'
import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'

import { terminateAccountSession } from '@/shared/api/generated/endpoints/account/account'
import type { LoginRequest, MeResponse } from '@/shared/api/generated/model'
import { ApiError } from '@/shared/api/http'
import type { MeWithRights } from '@/shared/api/me'
import { owner } from '@/shared/api/mocks/fixtures'
import { personaHandlers } from '@/shared/api/mocks/handlers'
import { server } from '@/shared/api/mocks/node'
import { problems } from '@/shared/api/mocks/problems'
import { i18n } from '@/shared/i18n'
import { renderApp } from '@/test/renderApp'

const t = i18n.t.bind(i18n)

/** Роль без «Расписания»: вымышленный кладовщик, видит только склад. */
const storekeeper: MeWithRights = {
  ...owner,
  account: { ...owner.account, id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e04', fullName: 'Тестов Ерлан', initials: 'ТЕ' },
  role: { code: 'storekeeper', name: 'Кладовщик' },
  permissions: ['inventory.view'],
}

/** Ответ /api/v1/me строго по схеме api/openapi.json: без роли и прав, как у живого бэкенда сейчас. */
const specOnly: MeResponse = {
  account: {
    id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e05',
    fullName: 'Тестова Жанна',
    initials: 'ТЖ',
    language: 'ru',
    phone: '+77010000005',
    email: null,
  },
  context: {
    clinic: { id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e19', name: 'Тест Тіс', city: 'Алматы' },
    branch: { id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e29', name: 'Желтоксан, 1', city: 'Алматы' },
  },
  memberships: [],
}

/** Вкладка браузера снова в фокусе: так TanStack Query видит возврат пользователя. */
function refocusTab() {
  act(() => {
    focusManager.setFocused(false)
    focusManager.setFocused(true)
  })
}

describe('контракт /api/v1/me (api/openapi.json)', () => {
  it('ответ строго по спеке — каркас открывается: ФИО и инициалы с сервера, контекст шапки, все разделы', async () => {
    renderApp('/schedule', specOnly)

    await screen.findByRole('heading', { level: 1, name: t('shell:nav.schedule') })
    expect(screen.queryByText(t('common:states.error.title'))).not.toBeInTheDocument()
    // Прав в спеке ещё нет — меню не прячет разделы (закроет сервер ответом 403).
    expect(within(screen.getByRole('navigation', { name: t('shell:nav.label') })).getAllByRole('link')).toHaveLength(6)
    expect(screen.getByRole('button', { name: t('shell:nav.userMenu', { name: 'Тестова Жанна' }) })).toHaveTextContent('ТЖ')
    expect(screen.getByText(/Желтоксан, 1/)).toBeInTheDocument()
  })

  it('клиника не выбрана (context = null) — выбор клиники и филиала, затем тот же адрес', async () => {
    const { router } = renderApp('/patients?filter=children', 'multi')

    const heading = await screen.findByRole('heading', { level: 1 })
    expect(heading).toHaveTextContent(t('auth:selectClinic.heading'))
    expect(heading).toHaveTextContent(t('auth:selectClinic.greeting', { name: 'Оспанова Мадина' }))
    expect(router.state.location.pathname).toBe('/select-clinic')
    expect(router.state.location.search).toEqual({ returnTo: '/patients?filter=children' })
    // Карточка — филиал активного членства (у «Ақ Тіс» их два); отключённое членство выбрать нельзя.
    const list = screen.getByRole('list', { name: t('auth:selectClinic.listLabel') })
    expect(within(list).getAllByRole('button')).toHaveLength(3)

    await userEvent.click(
      screen.getByRole('button', {
        name: t('auth:selectClinic.choose', { branch: 'Сатпаева, 30', clinic: 'Ақ Тіс', city: 'Алматы' }),
      }),
    )

    await screen.findByRole('heading', { level: 1, name: t('shell:nav.patients') })
    expect(router.state.location.pathname).toBe('/patients')
    expect(router.state.location.search).toEqual({ filter: 'children' })
    // Выбранный филиал — в контексте шапки.
    expect(screen.getByText(/Сатпаева, 30/)).toBeInTheDocument()
  })

  it('на выборе клиники с уже выбранной клиникой — сразу на returnTo', async () => {
    const { router } = renderApp('/select-clinic?returnTo=%2Finventory', 'owner')

    await screen.findByRole('heading', { level: 1, name: t('shell:nav.inventory') })
    expect(router.state.location.pathname).toBe('/inventory')
  })

  it('вход: сгенерированный клиент шлёт rememberMe, ответ входа (MeResponse) сразу открывает каркас', async () => {
    let sent: Partial<LoginRequest> | undefined
    server.use(
      http.post('/api/v1/auth/login', async ({ request }) => {
        sent = (await request.json()) as LoginRequest
        return HttpResponse.json(owner)
      }),
    )
    const { router } = renderApp('/login?returnTo=%2Fclinic%2Fprices', 'none')

    await userEvent.type(await screen.findByLabelText(t('auth:login.login')), 'owner@example.kz')
    await userEvent.type(screen.getByLabelText(t('auth:login.password')), 'secret-1')
    await userEvent.click(screen.getByRole('button', { name: t('auth:login.submit') }))

    // /me мок по-прежнему отвечает 401: каркас открылся по ответу входа, без перезапроса.
    await screen.findByRole('heading', { level: 1, name: t('shell:nav.clinic') })
    expect(router.state.location.pathname).toBe('/clinic/prices')
    expect(sent).toEqual({ login: 'owner@example.kz', password: 'secret-1', rememberMe: true })
  })
})

describe('вход и выход (T-IAM-03)', () => {
  it('вход с CSRF: нет cookie — сначала GET /auth/csrf, токен — в заголовке; логин — как набран', async () => {
    const seen: string[] = []
    let token: string | null = null
    let sent: Partial<LoginRequest> | undefined
    server.events.on('request:start', ({ request }) => {
      seen.push(`${request.method} ${new URL(request.url).pathname}`)
    })
    server.use(
      http.post('/api/v1/auth/login', async ({ request }) => {
        token = request.headers.get('X-XSRF-TOKEN')
        sent = (await request.json()) as LoginRequest
        return HttpResponse.json(owner)
      }),
    )
    const { router } = renderApp('/login', 'none')

    await userEvent.type(await screen.findByLabelText(t('auth:login.login')), '87071234567')
    await userEvent.type(screen.getByLabelText(t('auth:login.password')), 'secret-1')
    await userEvent.click(screen.getByRole('checkbox', { name: t('auth:login.remember') }))
    await userEvent.click(screen.getByRole('button', { name: t('auth:login.submit') }))

    await screen.findByRole('heading', { level: 1, name: t('shell:nav.schedule') })
    server.events.removeAllListeners()
    expect(router.state.location.pathname).toBe('/schedule')
    expect(seen.indexOf('GET /api/v1/auth/csrf')).toBeGreaterThanOrEqual(0)
    expect(seen.indexOf('GET /api/v1/auth/csrf')).toBeLessThan(seen.indexOf('POST /api/v1/auth/login'))
    expect(token).toBe('mock-xsrf-token')
    // Номер уходит, как его набрали (маска — только вид), «Запомнить» сняли.
    expect(sent).toEqual({ login: '87071234567', password: 'secret-1', rememberMe: false })
  })

  it('несколько клиник — после входа сразу выбор клиники с тем же returnTo', async () => {
    const { router } = renderApp('/login?returnTo=%2Finventory%2Fitems', 'none')

    await userEvent.type(await screen.findByLabelText(t('auth:login.login')), 'multi@example.kz')
    await userEvent.type(screen.getByLabelText(t('auth:login.password')), 'secret-1')
    await userEvent.click(screen.getByRole('button', { name: t('auth:login.submit') }))

    await screen.findByRole('list', { name: t('auth:selectClinic.listLabel') })
    expect(router.state.location.pathname).toBe('/select-clinic')
    expect(router.state.location.search).toEqual({ returnTo: '/inventory/items' })
  })

  it.each([
    ['disabled@example.kz', 'errors:iam.login.access-disabled'],
    ['offline@example.kz', 'errors:ui.network'],
  ] as const)('%s — текст ошибки по коду: %s', async (login, key) => {
    renderApp('/login', 'none')

    await userEvent.type(await screen.findByLabelText(t('auth:login.login')), login)
    await userEvent.type(screen.getByLabelText(t('auth:login.password')), 'secret-1')
    await userEvent.click(screen.getByRole('button', { name: t('auth:login.submit') }))

    expect(await screen.findByRole('alert')).toHaveTextContent(t(key))
  })

  it('«Выйти» в меню пользователя сайдбара: POST /auth/logout, вход, кэш очищен', async () => {
    let loggedOut = false
    server.use(
      http.post('/api/v1/auth/logout', () => {
        loggedOut = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { router, queryClient } = renderApp('/patients', 'owner')

    await userEvent.click(
      await screen.findByRole('button', { name: t('shell:nav.userMenu', { name: owner.account.fullName }) }),
    )
    await userEvent.click(await screen.findByRole('menuitem', { name: t('shell:more.logout') }))

    await screen.findByRole('heading', { level: 1, name: t('auth:login.title') })
    expect(loggedOut).toBe(true)
    expect(router.state.location.pathname).toBe('/login')
    expect(queryClient.getQueryCache().getAll()).toEqual([])
  })

  it('меню пользователя: «Мой профиль» ведёт в профиль', async () => {
    const { router } = renderApp('/schedule', 'owner')

    await userEvent.click(
      await screen.findByRole('button', { name: t('shell:nav.userMenu', { name: owner.account.fullName }) }),
    )
    await userEvent.click(await screen.findByRole('menuitem', { name: t('shell:nav.profile') }))

    await screen.findByRole('heading', { level: 1, name: t('shell:nav.profile') })
    expect(router.state.location.pathname).toBe('/profile')
  })
})

describe('свежесть /api/v1/me', () => {
  it('фокус вкладки перезапрашивает /me: права отняли — пункт меню пропал, раздел закрылся без навигации', async () => {
    renderApp('/clinic', 'owner')
    await screen.findByText(t('clinic:stub.title'))

    server.use(...personaHandlers('assistant'))
    refocusTab()

    await screen.findByText(t('common:states.forbidden.title', { section: t('shell:nav.clinic') }))
    const nav = screen.getByRole('navigation', { name: t('shell:nav.label') })
    expect(within(nav).getAllByRole('link')).toHaveLength(4)
    expect(within(nav).queryByText(t('shell:nav.clinic'))).not.toBeInTheDocument()
  })

  it('403 на любом запросе перезапрашивает /me и обновляет меню', async () => {
    const { queryClient } = renderApp('/schedule', 'owner')
    const nav = await screen.findByRole('navigation', { name: t('shell:nav.label') })
    expect(within(nav).getAllByRole('link')).toHaveLength(6)

    server.use(...personaHandlers('assistant'))
    await act(async () => {
      await queryClient
        .fetchQuery({ queryKey: ['probe'], queryFn: () => Promise.reject(new ApiError({ status: 403, problem: null })) })
        .catch(() => undefined)
    })

    await waitFor(() => expect(within(nav).getAllByRole('link')).toHaveLength(4))
  })

  it('401 при перезапросе (сессия кончилась) — вход с returnTo и «Сеанс истёк»', async () => {
    const { router } = renderApp('/schedule/week', 'owner')
    await screen.findByRole('heading', { level: 1, name: t('shell:nav.schedule') })

    server.use(...personaHandlers('none'))
    refocusTab()

    await screen.findByRole('heading', { name: t('auth:login.title') })
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toEqual({ returnTo: '/schedule/week', reason: 'expired' })
    expect(screen.getByRole('status')).toHaveTextContent(t('auth:login.sessionExpired.title'))
  })

  it('401 на изменении посреди работы — тоже «Сеанс истёк», после входа — тот же адрес', async () => {
    const { router } = renderApp('/patients?filter=children', 'owner')
    await screen.findByRole('heading', { level: 1, name: t('shell:nav.patients') })

    // Любой защищённый запрос (не /auth/**: там 401 — ответ по существу, неверный пароль).
    server.use(http.delete('/api/v1/account/sessions/:id', () => problems.unauthorized('error.session-expired')))
    await act(async () => {
      await terminateAccountSession('s-1').catch(() => undefined)
    })

    await screen.findByText(t('auth:login.sessionExpired.title'))
    expect(router.state.location.search).toEqual({ returnTo: '/patients?filter=children', reason: 'expired' })

    server.use(...personaHandlers('owner'))
    await userEvent.type(screen.getByLabelText(t('auth:login.login')), 'owner@example.kz')
    await userEvent.type(screen.getByLabelText(t('auth:login.password')), 'secret-1')
    await userEvent.click(screen.getByRole('button', { name: t('auth:login.submit') }))
    await screen.findByRole('heading', { level: 1, name: t('shell:nav.patients') })
    expect(router.state.location.href).toBe('/patients?filter=children')
  })
})

describe('гарды', () => {
  it('без сессии (401 от /api/v1/me) уводит на /login с returnTo', async () => {
    const { router } = renderApp('/clinic/prices', 'none')

    await screen.findByRole('heading', { name: t('auth:login.title') })
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toEqual({ returnTo: '/clinic/prices' })
    // Сессии не было — не «истёк»: обычный вход без сообщения.
    expect(screen.queryByText(t('auth:login.sessionExpired.title'))).not.toBeInTheDocument()
  })

  it.each([
    ['https%3A%2F%2Fevil.example'],
    ['%2F%2Fevil.example'],
    // «/\evil.example»: new URL() читает «\» как «/» и уводит на чужой хост.
    ['%2F%5Cevil.example'],
  ])('returnTo=%s отбрасывается: только относительный путь приложения', async (returnTo) => {
    const { router } = renderApp(`/login?returnTo=${returnTo}`, 'none')

    await screen.findByRole('heading', { name: t('auth:login.title') })
    expect(router.state.location.search).toEqual({})
  })

  it('returnTo=/login отбрасывается: после входа — в кабинет, а не снова на форму входа', async () => {
    const { router } = renderApp('/login?returnTo=%2Flogin%3FreturnTo%3D%252Fclinic', 'none')

    await screen.findByRole('heading', { name: t('auth:login.title') })
    expect(router.state.location.search).toEqual({})
    await userEvent.type(screen.getByLabelText(t('auth:login.login')), 'owner@example.kz')
    await userEvent.type(screen.getByLabelText(t('auth:login.password')), 'secret-1')
    await userEvent.click(screen.getByRole('button', { name: t('auth:login.submit') }))

    await screen.findByRole('heading', { level: 1, name: t('shell:nav.schedule') })
    expect(router.state.location.pathname).toBe('/schedule')
  })

  it('ассистент не видит «Клиника» и «Доступы», владелец видит все 6', async () => {
    const assistant = renderApp('/schedule', 'assistant')
    const nav = await screen.findByRole('navigation', { name: t('shell:nav.label') })
    expect(within(nav).getAllByRole('link')).toHaveLength(4)
    expect(within(nav).queryByText(t('shell:nav.clinic'))).not.toBeInTheDocument()
    expect(within(nav).queryByText(t('shell:nav.access'))).not.toBeInTheDocument()
    assistant.unmount()

    renderApp('/schedule', 'owner')
    const ownerNav = await screen.findByRole('navigation', { name: t('shell:nav.label') })
    expect(within(ownerNav).getAllByRole('link')).toHaveLength(6)
    expect(within(ownerNav).getByText(t('shell:nav.clinic'))).toBeInTheDocument()
  })

  it('раздел без права показывает «нет доступа» внутри каркаса — без ошибки в консоли', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      renderApp('/clinic', 'assistant')

      await screen.findByText(t('common:states.forbidden.title', { section: t('shell:nav.clinic') }))
      expect(screen.getByRole('navigation', { name: t('shell:nav.label') })).toBeInTheDocument()
      // «Нет доступа» — ожидаемое состояние, а не сбой: React о ForbiddenError не пишет (onCaughtError).
      expect(consoleError).not.toHaveBeenCalled()
    } finally {
      consoleError.mockRestore()
    }
  })

  it('корень ведёт в первый доступный раздел', async () => {
    const { router } = renderApp('/', 'owner')

    await screen.findByRole('heading', { level: 1, name: t('shell:nav.schedule') })
    expect(router.state.location.pathname).toBe('/schedule')
  })

  it('«нет доступа» ведёт в первый доступный роли раздел, а не всегда в расписание', async () => {
    const { router } = renderApp('/schedule', storekeeper)

    await screen.findByText(t('common:states.forbidden.title', { section: t('shell:nav.schedule') }))
    await userEvent.click(screen.getByRole('link', { name: t('shell:home.inventory') }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/inventory'))
    await screen.findByText(t('inventory:stub.title'))
  })
})

describe('шапка закрытого раздела', () => {
  it.each([['/access'], ['/access/roles'], ['/clinic/prices']])(
    'ассистент на %s: только заголовок — без вкладок и жёлтой кнопки раздела',
    async (path) => {
      const { container } = renderApp(path, 'assistant')

      await screen.findByText(/закрыт для вашей роли/)
      expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument()
      expect(container.querySelector('[data-primary-action]')).toBeNull()
      expect(screen.queryByRole('navigation', { name: t('shell:tabs.label') })).not.toBeInTheDocument()
      await waitFor(() => expect(document.title).toBe(`${t('common:titles.forbidden')} — Dentify`))
    },
  )

  it('владелец на /access видит вкладки раздела и его главное действие', async () => {
    const { container } = renderApp('/access', 'owner')

    await screen.findByText(t('access:stub.title'))
    expect(screen.getByRole('navigation', { name: t('shell:tabs.label') })).toBeInTheDocument()
    expect(container.querySelector('[data-primary-action="access"]')).toHaveTextContent(t('shell:actions.invite'))
  })

  it.each([
    ['/patients', 'shell:actions.newPatient'],
    ['/staff', 'shell:actions.newStaff'],
    ['/inventory', 'shell:actions.newDocument'],
    ['/access', 'shell:actions.invite'],
  ] as const)('%s: главное действие без экрана — уведомление «в разработке», адрес тот же', async (path, key) => {
    const { router } = renderApp(path, 'owner')

    const action = await screen.findByRole('button', { name: t(key) })
    await userEvent.click(action)

    expect(await screen.findByText(t('shell:actions.soon', { action: t(key) }))).toBeInTheDocument()
    expect(router.state.location.pathname).toBe(path)
  })
})

describe('search-параметры', () => {
  it('ссылка с фильтром и страницей открывает тот же вид', async () => {
    renderApp('/patients?filter=children&page=3', 'owner')

    await screen.findByText(t('patients:pages.current', { page: 3 }))
    const chip = screen.getByRole('link', { name: t('patients:filters.children') })
    expect(chip).toHaveAttribute('aria-current', 'page')
  })

  it('неверные значения заменяются значениями по умолчанию', async () => {
    const { router } = renderApp('/patients?filter=bogus&page=-5', 'owner')

    await screen.findByText(t('patients:pages.current', { page: 1 }))
    expect(screen.getByRole('link', { name: t('patients:filters.all') })).toHaveAttribute('aria-current', 'page')
    // Значения по умолчанию из адреса убираются (stripSearchParams): ссылка остаётся чистой.
    expect(router.state.location.search).toEqual({})
    expect(router.state.location.searchStr).toBe('')
  })

  it('открытая панель живёт в URL (?panel=search)', async () => {
    renderApp('/schedule?panel=search', 'owner')

    expect(await screen.findByRole('dialog', { name: t('shell:search.title') })).toBeInTheDocument()
  })

  it('неизвестная панель (?panel=bogus) — панель закрыта, параметр уходит из адреса', async () => {
    const { router } = renderApp('/schedule?panel=bogus', 'owner')

    await screen.findByRole('heading', { level: 1, name: t('shell:nav.schedule') })
    expect(router.state.location.search).toEqual({})
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})

describe('Ctrl K', () => {
  it('открывает поиск и в русской раскладке (key «л», code KeyK)', async () => {
    const { router } = renderApp('/schedule', 'owner')

    await screen.findByRole('heading', { level: 1, name: t('shell:nav.schedule') })
    fireEvent.keyDown(window, { key: 'л', code: 'KeyK', ctrlKey: true })

    expect(await screen.findByRole('dialog', { name: t('shell:search.title') })).toBeInTheDocument()
    expect(router.state.location.search).toEqual({ panel: 'search' })
  })
})

describe('заголовки и 404', () => {
  it('заголовок вкладки — «<Экран> — Dentify»', async () => {
    renderApp('/schedule/week', 'owner')

    await screen.findByRole('heading', { level: 1, name: t('shell:nav.schedule') })
    await waitFor(() => expect(document.title).toBe(`${t('shell:tabs.schedule.week')} · ${t('shell:nav.schedule')} — Dentify`))
  })

  it('неизвестный путь — 404 внутри каркаса', async () => {
    renderApp('/no-such-page', 'owner')

    await screen.findByText(t('common:states.notFound.title'))
    expect(screen.getByRole('navigation', { name: t('shell:nav.label') })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(t('common:titles.notFound'))
    await waitFor(() => expect(document.title).toBe(`${t('common:titles.notFound')} — Dentify`))
  })
})
