import { expect, test } from '@playwright/test'

test.describe('состояние экрана в URL', () => {
  test('ссылка на список пациентов с фильтром «Дети» и страницей 3 открывает тот же вид', async ({ page }) => {
    await page.goto('/patients?filter=children&page=3')

    const filters = page.getByRole('navigation', { name: 'Быстрые фильтры' })
    await expect(filters.getByRole('link', { name: 'Дети' })).toHaveAttribute('aria-current', 'page')
    // Выбран ровно один чип: «Все» (значение по умолчанию) не отмечается заодно.
    await expect(filters.locator('[aria-current=page]')).toHaveCount(1)
    await expect(page.getByText('Страница 3')).toBeVisible()
    await expect(page).toHaveTitle('Пациенты — Dentify')
  })

  test('неверные значения параметров заменяются значениями по умолчанию', async ({ page }) => {
    await page.goto('/patients?filter=bogus&page=-1')

    await expect(page.getByRole('link', { name: 'Все' })).toHaveAttribute('aria-current', 'page')
    await expect(page.getByText('Страница 1')).toBeVisible()
  })

  test('переход по фильтру и странице меняет URL, «Назад» возвращает вид', async ({ page }) => {
    await page.goto('/patients')

    await page.getByRole('link', { name: 'Дети' }).click()
    await expect(page).toHaveURL(/\/patients\?filter=children$/)
    await page.getByRole('link', { name: 'Следующая страница' }).click()
    await expect(page).toHaveURL(/filter=children&page=2$/)
    await expect(page.getByText('Страница 2')).toBeVisible()

    await page.goBack()
    await expect(page.getByText('Страница 1')).toBeVisible()
    await expect(page.getByRole('link', { name: 'Дети' })).toHaveAttribute('aria-current', 'page')
  })
})
