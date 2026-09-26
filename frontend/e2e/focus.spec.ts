import { expect, test } from '@playwright/test'

import { usePersona } from './helpers'

// PLT-21, WCAG 2.4.3: фокус не падает на <body>, когда кнопка на время запроса становится
// недоступной или ссылка — на краю списка. Иначе клавиатура и экранный диктор начинают с начала
// страницы.

test.describe('фокус не теряется', () => {
  test('вход: после отказа фокус — на плашке ошибки, дальше по Tab — «Войти»', async ({ page, context }) => {
    await usePersona(context, 'none')
    await page.goto('/login')

    await page.getByLabel('Телефон или e-mail').fill('owner@example.kz')
    await page.getByLabel('Пароль', { exact: true }).fill('wrong')
    const submit = page.getByRole('button', { name: 'Войти' })
    await submit.focus()
    await page.keyboard.press('Enter')

    const alert = page.locator('[data-slot="form-alert"]')
    await expect(alert).toHaveText('Неверный телефон, e-mail или пароль')
    await expect(alert).toBeFocused()
    await page.keyboard.press('Tab')
    await expect(submit).toBeFocused()
    await expect(submit).not.toHaveAttribute('aria-disabled', 'true')
  })

  test('вход: пока идёт запрос, кнопка недоступна, но в фокусе (aria-disabled вместо disabled)', async ({
    page,
    context,
  }) => {
    await usePersona(context, 'none')
    await page.goto('/login')
    await page.getByLabel('Телефон или e-mail').fill('owner@example.kz')
    await page.getByLabel('Пароль', { exact: true }).fill('wrong')
    await page.getByRole('button', { name: 'Войти' }).focus()

    // Состояние кнопки в момент запроса, до ответа сервера: каждое изменение её атрибутов.
    const during = await page.evaluate(async () => {
      const button = document.activeElement as HTMLButtonElement
      const seen: { disabled: boolean; ariaDisabled: string | null; focused: boolean }[] = []
      const observer = new MutationObserver(() =>
        seen.push({
          disabled: button.disabled,
          ariaDisabled: button.getAttribute('aria-disabled'),
          focused: document.activeElement === button,
        }),
      )
      observer.observe(button, { attributes: true })
      button.click()
      await new Promise((resolve) => setTimeout(resolve, 100))
      observer.disconnect()
      return seen
    })
    const pending = during.find((state) => state.ariaDisabled === 'true')
    expect(pending, 'кнопка на время запроса помечена aria-disabled').toBeTruthy()
    expect(pending).toMatchObject({ disabled: false, focused: true })
    await expect(page.locator('[data-slot="form-alert"]')).toBeVisible()
  })

  test('пациенты: «назад» на первой странице остаётся в фокусе и никуда не ведёт', async ({ page }) => {
    await page.goto('/patients?page=2')
    const prev = page.getByRole('link', { name: 'Предыдущая страница' })
    await prev.focus()
    await page.keyboard.press('Enter')

    await expect(page).toHaveURL(/\/patients$/)
    await expect(page.locator('[data-page-label]')).toHaveText('Страница 1')
    await expect(prev).toBeFocused()
    await expect(prev).toHaveAttribute('aria-disabled', 'true')

    await page.keyboard.press('Enter')
    await expect(page).toHaveURL(/\/patients$/)
    await expect(prev).toBeFocused()
  })
})
