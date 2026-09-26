import { expect, type Page, test } from '@playwright/test'

import { isDesktop, usePersona } from './helpers'

// Контракт /api/v1/me из api/openapi.json: context = null → выбор клиники; /me перезапрашивается
// при фокусе вкладки; «нет доступа» не пишет ошибок в консоль.

/** Вкладка снова видима: TanStack Query слушает visibilitychange и перезапрашивает /me. */
async function refocusTab(page: Page) {
  await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange', { bubbles: true })))
}

async function signIn(page: Page, login: string) {
  await page.getByLabel('Телефон или e-mail').fill(login)
  await page.getByLabel('Пароль', { exact: true }).fill('secret-1')
  await page.getByRole('button', { name: 'Войти' }).click()
}

test.describe('сессия и /api/v1/me', () => {
  test('ответ /me строго по спеке (без роли и прав, как у живого бэкенда) — каркас открывается', async ({
    page,
    context,
  }) => {
    test.skip(!isDesktop(), 'сайдбар только на десктопе')
    await usePersona(context, 'plain')
    await page.goto('/schedule')

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Расписание')
    await expect(page.getByText('Нет связи с сервером')).toHaveCount(0)
    // Прав в спеке ещё нет — разделы не прячутся (закроет сервер ответом 403).
    await expect(page.getByRole('navigation', { name: 'Разделы' }).getByRole('link')).toHaveCount(6)
    // ФИО и инициалы — с сервера; роли нет — строки роли в карточке нет.
    const profile = page.getByRole('button', { name: 'Меню пользователя: Касымова Жанар' })
    await expect(profile).toHaveText('КЖКасымова Жанар')
    await expect(page.locator('[data-shell-header]')).toContainText('Абая, 150')
  })

  test('врач двух клиник: после входа — выбор клиники, затем тот же адрес', async ({ page, context }) => {
    await usePersona(context, 'none')
    await page.goto('/patients?filter=children')
    await expect(page).toHaveURL(/\/login\?returnTo=/)

    await signIn(page, 'multi@example.kz')

    await expect(page).toHaveURL(/\/select-clinic\?returnTo=%2Fpatients%3Ffilter%3Dchildren$/)
    await expect(page).toHaveTitle('Выбор клиники — Dentify')
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Где работаем сегодня?')
    const places = page.getByRole('list', { name: 'Клиники и филиалы' }).getByRole('button')
    // Карточка — филиал активного членства; отключённое членство («Сау Тіс») выбрать нельзя.
    await expect(places).toHaveCount(3)
    await expect(page.getByText('Сау Тіс')).toHaveCount(0)

    await page.getByRole('button', { name: 'Войти: Тұран, 24 — Жас Тіс, Астана' }).click()

    await expect(page).toHaveURL(/\/patients\?filter=children$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Пациенты')
    // Выбранный филиал — в контексте шапки.
    await expect(page.locator('[data-shell-header]')).toContainText('Тұран, 24')
  })

  test('выбор клиники без горизонтальной прокрутки, «Выйти» ведёт на вход', async ({ page, context }) => {
    await usePersona(context, 'multi')
    await page.goto('/schedule')

    await expect(page).toHaveURL(/\/select-clinic\?returnTo=%2Fschedule$/)
    await expect(page.getByRole('list', { name: 'Клиники и филиалы' }).getByRole('button')).toHaveCount(3)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    expect(overflow).toBeLessThanOrEqual(0)

    await page.getByRole('button', { name: 'Выйти' }).click()
    await expect(page).toHaveURL(/\/login$/)
    await expect(page).toHaveTitle('Вход — Dentify')
  })

  test('фокус вкладки перезапрашивает /me: права отняли — меню и раздел меняются без перехода', async ({
    page,
    context,
  }) => {
    test.skip(!isDesktop(), 'сайдбар только на десктопе')
    await page.goto('/clinic/prices')

    const nav = page.getByRole('navigation', { name: 'Разделы' })
    await expect(nav.getByRole('link')).toHaveCount(6)
    await expect(page.getByRole('navigation', { name: 'Вкладки раздела' })).toBeVisible()

    await usePersona(context, 'assistant')
    await refocusTab(page)

    await expect(nav.getByRole('link')).toHaveCount(4)
    await expect(nav.getByRole('link', { name: 'Клиника' })).toHaveCount(0)
    await expect(page.getByText('Раздел «Клиника» закрыт для вашей роли')).toBeVisible()
    await expect(page).toHaveTitle('Нет доступа — Dentify')
    await expect(page).toHaveURL(/\/clinic\/prices$/)
  })

  test('сессия кончилась — при возврате во вкладку вход с «Сеанс истёк» и returnTo', async ({ page, context }) => {
    await page.goto('/inventory/items')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Склад')

    await usePersona(context, 'none')
    await refocusTab(page)

    await expect(page).toHaveURL(/\/login\?returnTo=%2Finventory%2Fitems&reason=expired$/)
    await expect(page.getByRole('status')).toHaveText('Сеанс истёк. Войдите снова — вернём на ту же страницу')
  })

  test('«нет доступа» — ожидаемое состояние: в консоли нет ошибок', async ({ page, context }) => {
    const errors: string[] = []
    page.on('console', (message) => {
      if (message.type() === 'error') errors.push(message.text())
    })
    page.on('pageerror', (error) => errors.push(error.message))
    await usePersona(context, 'assistant')
    await page.goto('/access/roles')

    await expect(page.getByText('Раздел «Доступы» закрыт для вашей роли')).toBeVisible()
    expect(errors).toEqual([])
  })
})
