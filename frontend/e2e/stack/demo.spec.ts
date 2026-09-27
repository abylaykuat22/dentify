import { expect, type Page, test } from '@playwright/test'

import { assertNoHorizontalScroll, isDesktop, seriousA11yViolations } from '../helpers'
import {
  accountSessions,
  bottomNav,
  expectSameOrigin,
  login,
  logout,
  NO_SESSION,
  OWNER,
  ownerStatePath,
  recordRequests,
  refocusTab,
  SECTIONS,
  snap,
  STACK_URL,
  terminateSession,
} from './stack'

// Демо этапа 0 на живом стеке (T-PLT-16: PLT-04, PLT-17, PLT-18, PLT-21, PLT-27) — на десктопе 1440×900 и
// телефоне 390×844: вход владельцем → пустой кабинет (6 разделов с пустыми состояниями, сайдбар или нижняя
// панель и «Ещё», шапка с филиалом) → сессию завершают с другого устройства (API сессий) → «Сеанс истёк» →
// снова вход на тот же экран → выход → /login. Снимки шагов — вложениями в отчёт и в трейсе.
//
// «Другое устройство» — сессия stack-setup (тот же аккаунт, свой клиент API), а не новый вход: входов с одного IP
// бэкенд пропускает 30 в минуту, и каждый лишний вход отнимает место у параллельных прогонов (docs/frontend.md
// «Живой стек»). Завершать все сессии аккаунта здесь нельзя: их делят параллельные спеки и прогоны — «после
// завершить все — 401 с любого устройства» (PLT-04) проверяет бэкенд (AccountSessionsTests).

test.use({ storageState: NO_SESSION })

/** Перейти в раздел, как человек: сайдбар на десктопе; нижняя панель или «Ещё» на телефоне. */
async function openSection(page: Page, section: (typeof SECTIONS)[number]) {
  if (isDesktop()) {
    await page.getByRole('navigation', { name: 'Разделы' }).getByRole('link', { name: section.title }).click()
  } else if (section.phone === 'bottom') {
    await bottomNav(page).getByRole('link', { name: section.title }).click()
  } else {
    await bottomNav(page).getByRole('link', { name: 'Ещё' }).click()
    await expect(page).toHaveURL(/\/more$/)
    await page.getByRole('navigation', { name: 'Другие разделы' }).getByRole('link', { name: new RegExp(`^${section.title}`) }).click()
  }
  await expect(page).toHaveURL(new RegExp(`${section.path}$`))
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(section.title)
  await expect(page.locator('[data-state-screen=empty]')).toContainText(section.empty)
}

test('демо этапа 0: вход → пустой кабинет → «Сеанс истёк» → снова вход → выход', async ({ page, context, playwright }, testInfo) => {
  test.setTimeout(120_000)
  const requests = recordRequests(context)
  // Сбои скриптов страницы и ошибки в консоли, кроме ожидаемых 401 (первый заход без сессии, завершённая сессия).
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error' && !/status of 401/.test(message.text())) errors.push(message.text())
  })

  await test.step('вход по макету Vhod', async () => {
    await page.goto('/')
    await expect(page).toHaveURL(/\/login(\?|$)/)
    await expect(page).toHaveTitle('Вход — Dentify')
    // PLT-27: отметка о размещении данных — при любой ширине: на панели «О сервисе» с 1024 px (макет Vhod),
    // уже — под формой (IAM-34 убирает панель, но не отметку). Видимая — ровно одна.
    await expect(page.getByText('Данные хранятся в Казахстане').filter({ visible: true })).toHaveCount(1)
    expect(await seriousA11yViolations(page)).toEqual([])
    await assertNoHorizontalScroll(page)
    await snap(page, '01 вход')

    await login(page)
    await expect(page).toHaveURL(/\/schedule$/)
  })

  await test.step('пустой кабинет: шапка с филиалом, навигация, 6 разделов', async () => {
    await expect(page.locator('[data-shell-header]')).toContainText(OWNER.branch)
    if (isDesktop()) {
      await expect(page.getByRole('navigation', { name: 'Разделы' }).getByRole('link')).toHaveCount(SECTIONS.length)
      await expect(page.getByRole('button', { name: `Меню пользователя: ${OWNER.fullName}` })).toBeVisible()
    } else {
      await expect(bottomNav(page)).toBeVisible()
    }
    await expect(page.locator('[data-state-screen=empty]')).toContainText(SECTIONS[0].empty)
    await snap(page, '02 расписание')

    for (const [index, section] of SECTIONS.entries()) {
      if (index === 0) continue
      await openSection(page, section)
      await assertNoHorizontalScroll(page, section.path)
      await snap(page, `0${index + 2} ${section.title.toLowerCase()}`)
    }

    if (!isDesktop()) {
      // Меню «Ещё» (Telefon-Eshche): пользователь, клиника, филиал, разделы, «Выйти».
      await bottomNav(page).getByRole('link', { name: 'Ещё' }).click()
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(OWNER.fullName)
      await expect(page.getByRole('button', { name: `Филиал: ${OWNER.branch}. Сменить филиал` })).toBeVisible()
      await snap(page, '08 ещё')
      await page.getByRole('navigation', { name: 'Другие разделы' }).getByRole('link', { name: /^Доступы/ }).click()
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Доступы')
    }
  })

  await test.step('сессию завершили с другого устройства → «Сеанс истёк», вход с возвратом', async () => {
    // Эта сессия — `current` в списке сессий аккаунта, если спросить с её cookie.
    const current = (await accountSessions(page.request)).find((session) => session.current)
    expect(current, 'текущая сессия браузера в списке').toBeDefined()

    // Другое устройство — сессия stack-setup (её выход — в stack-teardown, здесь не выходим).
    const device = await playwright.request.newContext({ baseURL: STACK_URL, storageState: ownerStatePath(testInfo) })
    try {
      await terminateSession(device, current!.id)
      expect((await accountSessions(device)).map((session) => session.id)).not.toContain(current!.id)
    } finally {
      await device.dispose()
    }

    // Следующий запрос страницы (перезапрос /me при возврате во вкладку) — 401, SPA открывает вход.
    await refocusTab(page)
    await expect(page).toHaveURL(/\/login\?returnTo=%2Faccess&reason=expired$/)
    await expect(page.locator('[data-slot=session-expired]')).toHaveText(
      'Сеанс истёк. Войдите снова — вернём на ту же страницу',
    )
    expect(await seriousA11yViolations(page)).toEqual([])
    await snap(page, '09 сеанс истёк')

    await login(page)
    await expect(page).toHaveURL(/\/access$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Доступы')
  })

  await test.step('выход → /login, «Назад» кабинет не показывает', async () => {
    await logout(page)
    await snap(page, '10 выход')
    await page.goBack()
    await expect(page).toHaveURL(/\/login\?returnTo=/)
    await expect(page.getByRole('heading', { level: 1, name: 'Вход' })).toBeVisible()
  })

  expectSameOrigin(requests)
  expect(errors).toEqual([])
})
