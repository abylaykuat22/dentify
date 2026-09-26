import { expect, type Page, test } from '@playwright/test'

import { appPaths, isDesktop, type Persona, usePersona } from './helpers'

// i18n в живом браузере (PLT-11, dentify R003): на КАЖДОМ экране (адреса — из дерева маршрутов)
// словарь полон — в консоли dev-сборки нет «нет ключа» и ошибок ICU, на странице нет сырых ключей,
// `<html lang="ru">`; переключателя «Рус | Қаз» нет (флаг i18n.kk выключен по умолчанию). Склонения
// ICU видно на витрине и в ошибках входа (api-errors.spec.ts: «через 15 минут»).

const PERSONA: Record<string, Persona> = { '/login': 'none', '/select-clinic': 'multi' }
const SCREENS = appPaths().map((path) => ({ path, persona: PERSONA[path] ?? ('owner' as Persona) }))
const NAMESPACES = /\b(?:common|shell|auth|errors|schedule|patients|staff|inventory|clinic|access|profile|devUi):[\w.-]+/

/** Ошибки i18n в консоли: «i18n: нет ключа …», «i18n: ошибка ICU …» (src/shared/i18n/index.ts). */
function watchI18nConsole(page: Page): string[] {
  const errors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error' && message.text().includes('i18n')) errors.push(message.text())
  })
  return errors
}

test.describe('словарь на всех экранах', () => {
  // Тексты одинаковы на любой ширине — достаточно десктопа (телефон — в mobile.spec.ts).
  test.skip(() => !isDesktop(), 'только десктоп')

  for (const { path, persona } of SCREENS) {
    test(`${path}: lang=ru, нет пропущенных ключей и ошибок ICU, нет «Рус | Қаз»`, async ({ page, context }) => {
      const errors = watchI18nConsole(page)
      await usePersona(context, persona)
      await page.goto(path)
      await expect(page.locator('h1').first()).not.toBeEmpty()

      await expect(page.locator('html')).toHaveAttribute('lang', 'ru')
      await expect(page).toHaveTitle(/ — Dentify$|^Dentify$/)
      expect(await page.locator('body').innerText()).not.toMatch(NAMESPACES)
      await expect(page.locator('[data-language-switch]')).toHaveCount(0)
      expect(errors).toEqual([])
    })
  }
})

test.describe('флаг i18n.kk выключен: переключателя нет ни на входе, ни в профиле, ни в «Ещё»', () => {
  for (const { path, persona } of [
    { path: '/login', persona: 'none' as const },
    { path: '/profile', persona: 'owner' as const },
    { path: '/more', persona: 'owner' as const },
  ]) {
    test(path, async ({ page, context }) => {
      await usePersona(context, persona)
      await page.goto(path)
      await expect(page.locator('h1').first()).not.toBeEmpty()
      await expect(page.getByRole('button', { name: 'Қаз' })).toHaveCount(0)
      await expect(page.getByRole('button', { name: 'Қазақша' })).toHaveCount(0)
      await expect(page.getByText('Язык интерфейса')).toHaveCount(0)
      await expect(page.locator('html')).toHaveAttribute('lang', 'ru')
    })
  }
})

test('ICU-склонение возраста (витрина) и дата шапки через date-fns — на русском', async ({ page }) => {
  const errors = watchI18nConsole(page)
  await page.goto('/dev/ui')
  // Возраст — число из данных, склонение — ICU (`common:age`).
  for (const meta of ['29 лет · № 1042', '41 год · № 877', '35 лет · № 912', '8 лет · № 1030']) {
    await expect(page.getByText(meta).first()).toBeVisible()
  }

  await page.goto('/schedule')
  // «Суббота, 26 сентября · Абая, 150»: день недели с заглавной, месяц в родительном падеже.
  const context = page.locator('[data-shell-header] p').first()
  await expect(context).toHaveText(/^[А-ЯЁ][а-яё]+, \d{1,2} (января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря) · /)
  const expected = await page.evaluate(() =>
    new Intl.DateTimeFormat('ru', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date()),
  )
  await expect(context).toContainText(expected.slice(1))
  expect(errors).toEqual([])
})
