import { expect, test } from '@playwright/test'

import { isDesktop, usePersona } from './helpers'

test.describe('гарды', () => {
  test('без сессии — вход с returnTo, после входа — обратно на тот же адрес', async ({ page, context }) => {
    await usePersona(context, 'none')
    await page.goto('/clinic/prices')

    await expect(page).toHaveURL(/\/login\?returnTo=%2Fclinic%2Fprices$/)
    await expect(page).toHaveTitle('Вход — Dentify')

    await page.getByLabel('Телефон или e-mail').fill('owner@example.kz')
    await page.getByLabel('Пароль').fill('secret-1')
    await page.getByRole('button', { name: 'Войти' }).click()

    await expect(page).toHaveURL(/\/clinic\/prices$/)
    await expect(page).toHaveTitle('Прайс · Клиника — Dentify')
  })

  test('returnTo на чужой хост («/\\evil.example») отбрасывается, после входа — на главную', async ({
    page,
    context,
  }) => {
    await usePersona(context, 'none')
    await page.goto('/login?returnTo=%2F%5Cevil.example')

    await expect(page).toHaveURL(/\/login$/)
    await page.getByLabel('Телефон или e-mail').fill('owner@example.kz')
    await page.getByLabel('Пароль').fill('secret-1')
    await page.getByRole('button', { name: 'Войти' }).click()

    await expect(page).toHaveURL(/127\.0\.0\.1:\d+\/schedule$/)
    await expect(page.getByRole('alert')).toHaveCount(0)
  })

  test('неверный пароль — ошибка от сервера, остаёмся на входе', async ({ page, context }) => {
    await usePersona(context, 'none')
    await page.goto('/login')

    await page.getByLabel('Телефон или e-mail').fill('owner@example.kz')
    await page.getByLabel('Пароль').fill('wrong')
    await page.getByRole('button', { name: 'Войти' }).click()

    await expect(page.getByRole('alert')).toHaveText('Неверный телефон, e-mail или пароль')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('ассистент без прав на Клинику и Доступы не видит этих пунктов', async ({ page, context }) => {
    test.skip(!isDesktop(), 'сайдбар только на десктопе')
    await usePersona(context, 'assistant')
    await page.goto('/schedule')

    const nav = page.getByRole('navigation', { name: 'Разделы' })
    await expect(nav.getByRole('link')).toHaveCount(4)
    await expect(nav.getByRole('link', { name: 'Клиника' })).toHaveCount(0)
    await expect(nav.getByRole('link', { name: 'Доступы' })).toHaveCount(0)
  })

  test('прямая ссылка на закрытый раздел — «нет доступа» внутри каркаса', async ({ page, context }) => {
    await usePersona(context, 'assistant')
    await page.goto('/clinic/prices')

    await expect(page.getByText('Раздел «Клиника» закрыт для вашей роли')).toBeVisible()
    await expect(page).toHaveTitle('Нет доступа — Dentify')
    await page.getByRole('link', { name: 'К расписанию' }).click()
    await expect(page).toHaveURL(/\/schedule$/)
  })

  for (const [path, title] of [
    ['/access', 'Доступы'],
    ['/access/roles', 'Доступы'],
    ['/clinic/prices', 'Клиника'],
  ] as const) {
    test(`ассистент на ${path}: в шапке только заголовок — без вкладок и жёлтой кнопки`, async ({
      page,
      context,
    }) => {
      await usePersona(context, 'assistant')
      await page.goto(path)

      await expect(page.getByText(`Раздел «${title}» закрыт для вашей роли`)).toBeVisible()
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(title)
      await expect(page.locator('[data-primary-action]')).toHaveCount(0)
      await expect(page.getByRole('navigation', { name: 'Вкладки раздела' })).toHaveCount(0)
    })
  }

  test('владелец на /access: вкладки раздела и главное действие на месте', async ({ page }) => {
    test.skip(!isDesktop(), 'жёлтая кнопка шапки — с 768 px')
    await page.goto('/access')

    await expect(page.getByRole('navigation', { name: 'Вкладки раздела' })).toBeVisible()
    await expect(page.locator('[data-primary-action="access"]')).toHaveText('Пригласить')
  })
})
