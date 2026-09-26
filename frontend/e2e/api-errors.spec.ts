import AxeBuilder from '@axe-core/playwright'
import { expect, type Page, test } from '@playwright/test'

import { hitArea, usePersona } from './helpers'

// Ошибки API в интерфейсе (PLT-07) на экране входа и в каркасе, моки MSW отвечают как сервер
// (src/shared/api/mocks: пустое поле — 400 errors[], `wrong` — 401, `limit` — 429 с Retry-After,
// `boom` — 500 с traceId). Проверяет тот же хелпер форм, что и Vitest, но в живом браузере:
// фокус, контур поля, объявления диктору, буфер обмена.

async function fillLogin(page: Page, login: string, password = 'secret-1') {
  await page.getByLabel('Телефон или e-mail').fill(login)
  await page.getByLabel('Пароль').fill(password)
}

const submit = (page: Page) => page.getByRole('button', { name: 'Войти' }).click()

test.describe('ошибки API на форме входа', () => {
  test.beforeEach(async ({ page, context }) => {
    await usePersona(context, 'none')
    await page.goto('/login')
    await expect(page.getByRole('heading', { level: 1, name: 'Вход' })).toBeVisible()
  })

  test('пустые поля: сервер вернул errors[] — текст под полями, красный контур, фокус на первом', async ({ page }) => {
    const login = page.getByLabel('Телефон или e-mail')
    const password = page.getByLabel('Пароль')
    await password.fill('secret-1')
    await password.fill('')
    await submit(page)

    await expect(login).toBeFocused()
    await expect(login).toHaveAttribute('aria-invalid', 'true')
    await expect(password).toHaveAttribute('aria-invalid', 'true')
    await expect(page.locator('#login-login-error')).toHaveText('Заполните поле')
    await expect(page.locator('#login-password-error')).toHaveText('Заполните поле')
    // Поле связано со своей ошибкой — диктор читает её вместе с полем.
    await expect(login).toHaveAccessibleDescription('Заполните поле')
    await expect(login).toHaveCSS('border-color', 'rgb(155, 28, 18)')
    // Всё легло на поля — общей плашки нет.
    await expect(page.locator('[data-slot="form-alert"]')).toHaveCount(0)

    // Поле исправили — его ошибка ушла сразу, у второго осталась.
    await login.fill('owner@example.kz')
    await expect(login).toHaveAttribute('aria-invalid', 'false')
    await expect(page.locator('#login-login-error')).toHaveCount(0)
    await expect(page.locator('#login-password-error')).toHaveText('Заполните поле')
  })

  test('неверный пароль (401) — плашка над кнопкой, остаёмся на входе', async ({ page }) => {
    await fillLogin(page, 'owner@example.kz', 'wrong')
    await submit(page)

    const alert = page.locator('[data-slot="form-alert"]')
    await expect(alert).toHaveText('Неверный телефон, e-mail или пароль')
    await expect(alert).toHaveAttribute('role', 'alert')
    await expect(page).toHaveURL(/\/login$/)

    // Следующая попытка сбрасывает прошлую ошибку.
    await fillLogin(page, 'owner@example.kz')
    await submit(page)
    await expect(page).toHaveURL(/127\.0\.0\.1:\d+\/schedule$/)
  })

  test('лимит попыток (429) — через сколько повторить, из Retry-After', async ({ page }) => {
    await fillLogin(page, 'limit@example.kz')
    await submit(page)

    await expect(page.locator('[data-slot="form-alert"]')).toHaveText('Слишком много попыток. Повторите через 15 минут')
  })

  test('сбой сервера (500) — код ошибки для поддержки копируется в буфер', async ({ page, context, browserName }) => {
    test.skip(browserName !== 'chromium', 'права на буфер обмена — только Chromium')
    await context.grantPermissions(['clipboard-read', 'clipboard-write'])
    await fillLogin(page, 'boom@example.kz')
    await submit(page)

    const alert = page.locator('[data-slot="form-alert"]')
    await expect(alert).toContainText('Что-то пошло не так. Если ошибка повторится, сообщите в поддержку код ошибки')
    await expect(alert).toContainText('Код ошибки: 0f1e2d3c4b5a69788796a5b4c3d2e1f0')
    // Плашка с ошибкой проходит axe (контраст красного на розовом), кнопка — цель касания ≥ 44 (PLT-21).
    const results = await new AxeBuilder({ page }).include('[data-slot="form-alert"]').analyze()
    expect(results.violations.map((violation) => violation.id)).toEqual([])
    const copy = alert.getByRole('button', { name: 'Скопировать код' })
    const area = await hitArea(copy)
    expect(Math.min(area.width, area.height)).toBeGreaterThanOrEqual(44)
    await copy.click()
    await expect(alert.getByRole('button', { name: 'Код скопирован' })).toBeVisible()
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('0f1e2d3c4b5a69788796a5b4c3d2e1f0')
  })
})

test.describe('401 посреди работы', () => {
  test('сеанс истёк: вход с сообщением, после входа — тот же адрес', async ({ page, context }) => {
    await usePersona(context, 'owner')
    await page.goto('/patients?filter=children')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Пациенты')

    // Сервер завершил сессию; следующий запрос (здесь — /me при возврате во вкладку) получает 401.
    await usePersona(context, 'none')
    await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange', { bubbles: true })))

    await expect(page).toHaveURL(/\/login\?returnTo=%2Fpatients%3Ffilter%3Dchildren&reason=expired$/)
    await expect(page).toHaveTitle('Вход — Dentify')
    await expect(page.getByRole('status')).toHaveText('Сеанс истёк. Войдите снова — вернём на ту же страницу')

    await fillLogin(page, 'owner@example.kz')
    await submit(page)
    await expect(page).toHaveURL(/\/patients\?filter=children$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Пациенты')
  })

  test('первый заход без сессии — обычный вход, без «Сеанс истёк»', async ({ page, context }) => {
    await usePersona(context, 'none')
    await page.goto('/staff')

    await expect(page).toHaveURL(/\/login\?returnTo=%2Fstaff$/)
    await expect(page.getByRole('heading', { level: 1, name: 'Вход' })).toBeVisible()
    await expect(page.getByText('Сеанс истёк.')).toHaveCount(0)
  })
})
