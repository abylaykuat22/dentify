import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'

import type { MeWithRights } from '@/shared/api/me'
import { owner } from '@/shared/api/mocks/fixtures'
import { server } from '@/shared/api/mocks/node'
import { i18n } from '@/shared/i18n'
import { addKazakhFixture, removeKazakhFixture } from '@/test/kkFixture'
import { renderApp } from '@/test/renderApp'

import { isMoreArea } from './mobileNav'

// Навигация телефона (T-PLT-10, PLT-18): нижняя панель и меню «Ещё» — только то, что разрешил
// /api/v1/me. В jsdom CSS не применяется: панель в DOM на любой ширине (на ≥ 768 px её прячет
// `md:hidden`, это проверяет Playwright).

const t = i18n.t.bind(i18n)

/** Вымышленный врач без склада: «Склад» на панели и в «Ещё» не показывается. */
const doctor: MeWithRights = {
  ...owner,
  account: { ...owner.account, id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e07', fullName: 'Тестова Алия', initials: 'ТА' },
  role: { code: 'doctor', name: 'Врач' },
  permissions: ['schedule.view', 'patients.view', 'staff.view'],
}

/** Вымышленный кладовщик без расписания: «+» (новая запись) не показывается. */
const storekeeper: MeWithRights = {
  ...owner,
  account: { ...owner.account, id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e08', fullName: 'Тестов Ерлан', initials: 'ТЕ' },
  role: { code: 'storekeeper', name: 'Кладовщик' },
  permissions: ['inventory.view'],
}

function bottomNav() {
  return screen.getByRole('navigation', { name: t('shell:nav.mobileLabel') })
}

afterEach(() => {
  vi.unstubAllEnvs()
})

describe('нижняя панель телефона', () => {
  it('владелец: Расписание · Пациенты · + · Склад · Ещё, текущий раздел подсвечен', async () => {
    renderApp('/schedule', 'owner')
    await screen.findByRole('heading', { level: 1, name: t('shell:nav.schedule') })

    const links = within(bottomNav()).getAllByRole('link')
    expect(links.map((link) => link.getAttribute('aria-label') ?? link.textContent)).toEqual([
      t('shell:nav.schedule'),
      t('shell:nav.patients'),
      t('shell:actions.newAppointment'),
      t('shell:nav.inventory'),
      t('shell:nav.more'),
    ])
    expect(within(bottomNav()).getByRole('link', { name: t('shell:nav.schedule') })).toHaveAttribute('aria-current', 'page')
    expect(within(bottomNav()).getByRole('link', { name: t('shell:nav.more') })).not.toHaveAttribute('aria-current')
  })

  it('пункты по правам: без склада нет «Склада», без расписания нет «+»; место пункта остаётся', async () => {
    const view = renderApp('/schedule', doctor)
    await screen.findByRole('heading', { level: 1, name: t('shell:nav.schedule') })
    expect(within(bottomNav()).queryByRole('link', { name: t('shell:nav.inventory') })).not.toBeInTheDocument()
    expect(within(bottomNav()).getByRole('link', { name: t('shell:actions.newAppointment') })).toBeInTheDocument()
    // Пять ячеек сетки на любой роли: «+» по центру, «Ещё» справа.
    expect(bottomNav().firstElementChild?.children).toHaveLength(5)
    view.unmount()

    renderApp('/inventory', storekeeper)
    await screen.findByRole('heading', { level: 1, name: t('shell:nav.inventory') })
    expect(within(bottomNav()).queryByRole('link', { name: t('shell:actions.newAppointment') })).not.toBeInTheDocument()
    expect(within(bottomNav()).queryByRole('link', { name: t('shell:nav.schedule') })).not.toBeInTheDocument()
    expect(within(bottomNav()).getAllByRole('link').map((link) => link.textContent)).toEqual([
      t('shell:nav.inventory'),
      t('shell:nav.more'),
    ])
  })

  it('«+» ведёт на новую запись: заголовок «Новая запись», без вкладок и жёлтой кнопки шапки', async () => {
    const { router, container } = renderApp('/schedule', 'owner')
    await screen.findByRole('heading', { level: 1, name: t('shell:nav.schedule') })

    await userEvent.click(within(bottomNav()).getByRole('link', { name: t('shell:actions.newAppointment') }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/schedule/new'))
    await screen.findByRole('heading', { level: 1, name: t('shell:actions.newAppointment') })
    expect(screen.getByText(t('schedule:newStub.title'))).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: t('shell:tabs.label') })).not.toBeInTheDocument()
    expect(container.querySelector('[data-primary-action]')).toBeNull()
    await waitFor(() => expect(document.title).toBe(`${t('shell:actions.newAppointment')} — Dentify`))
  })

  it('на новой записи текущий пункт один — «Расписание»; «+» — действие без aria-current', async () => {
    renderApp('/schedule/new', 'owner')
    await screen.findByRole('heading', { level: 1, name: t('shell:actions.newAppointment') })

    const current = bottomNav().querySelectorAll('[aria-current]')
    expect(current).toHaveLength(1)
    expect(current[0]).toHaveAttribute('aria-current', 'page')
    expect(current[0]).toHaveTextContent(t('shell:nav.schedule'))
    const plus = within(bottomNav()).getByRole('link', { name: t('shell:actions.newAppointment') })
    expect(plus).not.toHaveAttribute('aria-current')
    expect(plus).toHaveAttribute('href', '/schedule/new')
  })

  it('жёлтая кнопка шапки расписания ведёт туда же — на новую запись', async () => {
    const { container } = renderApp('/schedule', 'owner')
    await screen.findByRole('heading', { level: 1, name: t('shell:nav.schedule') })

    const action = container.querySelector('[data-primary-action="schedule"]')
    // Переход — ссылка (не role="button"), но в виде жёлтой кнопки: одна «лампа» области.
    expect(action?.tagName).toBe('A')
    expect(action).not.toHaveAttribute('role')
    expect(action).toHaveAttribute('href', '/schedule/new')
    expect(action).toHaveAttribute('data-variant', 'default')
    expect(action).toHaveTextContent(t('shell:actions.newAppointment'))
  })

  it.each([['/staff'], ['/clinic/prices'], ['/access/roles'], ['/profile'], ['/more']])(
    '%s — подсвечен «Ещё» (раздел из меню «Ещё»)',
    async (path) => {
      renderApp(path, 'owner')
      await screen.findByRole('heading', { level: 1 })
      expect(within(bottomNav()).getByRole('link', { name: t('shell:nav.more') })).toHaveAttribute('aria-current', 'page')
    },
  )

  it('isMoreArea: разделы панели — нет, остальные и профиль — да', () => {
    expect(isMoreArea('/schedule/week')).toBe(false)
    expect(isMoreArea('/patients')).toBe(false)
    expect(isMoreArea('/inventory/items')).toBe(false)
    expect(isMoreArea('/staff/schedules')).toBe(true)
    expect(isMoreArea('/more')).toBe(true)
    expect(isMoreArea('/profile')).toBe(true)
    expect(isMoreArea('/nope')).toBe(false)
  })
})

describe('меню «Ещё» (Telefon-Eshche)', () => {
  it('карточка пользователя, филиал, разделы с подписями и «Мой профиль»', async () => {
    renderApp('/more', 'owner')

    const heading = await screen.findByRole('heading', { level: 1, name: 'Алиева Динара' })
    expect(heading.parentElement).toHaveTextContent('Владелец · Ақ Тіс')
    expect(screen.getByRole('button', { name: t('shell:more.branch.change', { branch: 'Абая, 150' }) })).toBeInTheDocument()
    await waitFor(() => expect(document.title).toBe(`${t('shell:nav.more')} — Dentify`))

    const sections = screen.getByRole('navigation', { name: t('shell:more.sectionsLabel') })
    const items = within(sections).getAllByRole('link')
    expect(items.map((item) => item.getAttribute('href'))).toEqual(['/staff', '/inventory', '/clinic', '/access', '/profile'])
    expect(items[0]).toHaveTextContent(`${t('shell:nav.staff')}${t('shell:more.captions.staff')}`)
    expect(items[4]).toHaveTextContent(`${t('shell:nav.profile')}${t('shell:more.captions.profile')}`)
  })

  it('только доступные разделы: ассистент без «Клиники» и «Доступов»', async () => {
    renderApp('/more', 'assistant')

    const sections = await screen.findByRole('navigation', { name: t('shell:more.sectionsLabel') })
    expect(within(sections).getAllByRole('link').map((item) => item.getAttribute('href'))).toEqual([
      '/staff',
      '/inventory',
      '/profile',
    ])
  })

  it('ответ /me строго по спеке (без роли): под именем — только клиника', async () => {
    renderApp('/more', 'plain')

    const heading = await screen.findByRole('heading', { level: 1, name: 'Касымова Жанар' })
    expect(heading.parentElement).toHaveTextContent(/^Касымова ЖанарАқ Тіс$/)
  })

  it('смена филиала — пока заглушка: уведомление, адрес не меняется', async () => {
    const { router } = renderApp('/more', 'owner')

    await userEvent.click(await screen.findByRole('button', { name: t('shell:more.branch.change', { branch: 'Абая, 150' }) }))

    expect(await screen.findByText(t('shell:more.branch.soon'))).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/more')
  })

  it('«Рус | Қаз» скрыт флагом i18n.kk (R003); с флагом и словарём kk — переключатель языка', async () => {
    const hidden = renderApp('/more', 'owner')
    await screen.findByRole('navigation', { name: t('shell:more.sectionsLabel') })
    expect(screen.queryByText(t('shell:more.language'))).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: t('common:languages.kk.short') })).not.toBeInTheDocument()
    hidden.unmount()

    vi.stubEnv('VITE_I18N_KK', 'true')
    addKazakhFixture()
    try {
      renderApp('/more', 'owner')
      const group = await screen.findByRole('group', { name: t('shell:more.language') }, { timeout: 5000 })
      expect(within(group).getByRole('button', { name: t('common:languages.ru.short') })).toHaveAttribute('aria-pressed', 'true')
      expect(within(group).getByRole('button', { name: t('common:languages.kk.short') })).toHaveAttribute('lang', 'kk')
    } finally {
      await removeKazakhFixture()
    }
  })

  it('«Выйти» завершает сессию на сервере и открывает вход', async () => {
    const logout = vi.fn()
    server.use(
      http.post('/api/v1/auth/logout', () => {
        logout()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { router } = renderApp('/more', 'owner')

    await userEvent.click(await screen.findByRole('button', { name: t('shell:more.logout') }))

    await screen.findByRole('heading', { name: t('auth:login.title') })
    expect(logout).toHaveBeenCalledTimes(1)
    expect(router.state.location.pathname).toBe('/login')
  })

  it('выход не удался — сообщение, остаёмся в меню', async () => {
    server.use(http.post('/api/v1/auth/logout', () => HttpResponse.json({ status: 500 }, { status: 500 })))
    const { router } = renderApp('/more', 'owner')

    await userEvent.click(await screen.findByRole('button', { name: t('shell:more.logout') }))

    expect(await screen.findByRole('alert')).toHaveTextContent(t('shell:more.logoutFailed'))
    expect(router.state.location.pathname).toBe('/more')
    expect(screen.getByRole('button', { name: t('shell:more.logout') })).toBeEnabled()
  })
})
