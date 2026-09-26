import { expect, test } from '@playwright/test'

import { isDesktop } from './helpers'

test.describe('каркас (desktop)', () => {
  test.skip(() => !isDesktop(), 'сайдбар и вкладки — десктоп; мобильную навигацию проверяет mobile.spec')

  test('корень открывает расписание: сайдбар на 6 разделов, заголовок вкладки', async ({ page }) => {
    await page.goto('/')

    await expect(page).toHaveURL(/\/schedule$/)
    await expect(page).toHaveTitle('Расписание — Dentify')

    const nav = page.getByRole('navigation', { name: 'Разделы' })
    await expect(nav.getByRole('link')).toHaveCount(6)
    await expect(nav.getByRole('link', { name: 'Расписание' })).toHaveAttribute('aria-current', 'page')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Расписание')
    await expect(page.getByRole('link', { name: 'Мой профиль' })).toBeVisible()
  })

  test('вкладки-пилюли переключают маршрут и заголовок', async ({ page }) => {
    await page.goto('/schedule')

    await page.getByRole('navigation', { name: 'Вкладки раздела' }).getByRole('link', { name: 'Неделя' }).click()
    await expect(page).toHaveURL(/\/schedule\/week$/)
    await expect(page).toHaveTitle('Неделя · Расписание — Dentify')
    await expect(page.getByRole('navigation', { name: 'Разделы' }).getByRole('link', { name: 'Расписание' })).toHaveAttribute(
      'aria-current',
      'page',
    )
  })

  test('неизвестный адрес — «Такой страницы нет» внутри каркаса', async ({ page }) => {
    await page.goto('/nope/nothing')

    await expect(page.getByText('Такой страницы нет')).toBeVisible()
    await expect(page.getByRole('navigation', { name: 'Разделы' })).toBeVisible()
    await expect(page).toHaveTitle('Страница не найдена — Dentify')
  })
})
