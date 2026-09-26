import { expect, test } from '@playwright/test'

test.describe('боковая панель в URL', () => {
  test('Ctrl K открывает панель поиска, «Назад» браузера закрывает её, не уходя со страницы', async ({ page }) => {
    await page.goto('/schedule')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Расписание')

    await page.keyboard.press('Control+k')
    const panel = page.getByRole('dialog', { name: 'Поиск пациента' })
    await expect(panel).toBeVisible()
    await expect(page).toHaveURL(/\/schedule\?panel=search$/)
    await expect(panel.getByRole('searchbox')).toBeFocused()

    await page.goBack()
    await expect(panel).toBeHidden()
    await expect(page).toHaveURL(/\/schedule$/)
    await expect(page.locator('h1')).toHaveText('Расписание')
  })

  test('Ctrl K в русской/казахской раскладке: key «л», физическая клавиша KeyK', async ({ page }) => {
    await page.goto('/schedule')
    await expect(page.locator('h1')).toHaveText('Расписание')

    // Playwright печатает в латинской раскладке, поэтому событие раскладки ЙЦУКЕН — вручную.
    await page.evaluate(() => {
      document.body.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'л', code: 'KeyK', ctrlKey: true, bubbles: true, cancelable: true }),
      )
    })

    await expect(page.getByRole('dialog', { name: 'Поиск пациента' })).toBeVisible()
    await expect(page).toHaveURL(/\/schedule\?panel=search$/)
  })

  test('крестик и Escape закрывают панель, история не растёт, фокус возвращается на поиск', async ({ page }) => {
    await page.goto('/schedule')

    const trigger = page.getByRole('button', { name: 'Найти пациента (Ctrl K)' })
    await trigger.click()
    const panel = page.getByRole('dialog', { name: 'Поиск пациента' })
    await expect(panel).toBeVisible()
    await panel.getByRole('button', { name: 'Закрыть панель' }).click()
    await expect(panel).toBeHidden()
    await expect(page).toHaveURL(/\/schedule$/)
    await expect(trigger).toBeFocused()

    await page.keyboard.press('Control+k')
    await expect(panel).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(panel).toBeHidden()
    await expect(page).toHaveURL(/\/schedule$/)

    // Панель закрывалась через history.back(): «Назад» уводит с /schedule, а не открывает панель.
    await page.goBack()
    await expect(page).not.toHaveURL(/panel=search/)
  })

  test('ссылка с открытой панелью показывает панель сразу', async ({ page }) => {
    await page.goto('/patients?panel=search')

    await expect(page.getByRole('dialog', { name: 'Поиск пациента' })).toBeVisible()
    // На телефоне панель модальная и прячет фон от дерева доступности — берём h1 по тегу.
    await expect(page.locator('h1')).toHaveText('Пациенты')
  })

  test('неизвестная панель (?panel=bogus) — панель закрыта, параметр уходит из адреса', async ({ page }) => {
    await page.goto('/schedule?panel=bogus')

    await expect(page.locator('h1')).toHaveText('Расписание')
    await expect(page).toHaveURL(/\/schedule$/)
    await expect(page.getByRole('dialog')).toHaveCount(0)
  })
})
