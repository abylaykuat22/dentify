import AxeBuilder from '@axe-core/playwright'
import { expect, type Locator, type Page, test } from '@playwright/test'

import { usePersona } from './helpers'

// PLT-21 на экранах каркаса и заглушках (витрина /dev/ui — в dev-ui.spec.ts): axe без
// исключений — color-contrast меряет настоящие пары текст/фон, которых нет в ручном списке
// src/styles/contrast.test.ts; и общий контур фокуса 3 px «лагуна» у полей и карточек, которые
// раньше гасили его своим `outline-none`.

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']
const FOCUS_LAGOON = 'solid 3px rgb(15, 61, 76) 2px'

async function seriousViolations(page: Page) {
  await page.evaluate(() => document.fonts.ready)
  const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze()
  return results.violations
    .filter((violation) => violation.impact === 'serious' || violation.impact === 'critical')
    .map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target.join(' ')).join(' | ')}`)
}

function outline(locator: Locator) {
  return locator.evaluate((element) => {
    const style = getComputedStyle(element)
    return `${style.outlineStyle} ${style.outlineWidth} ${style.outlineColor} ${style.outlineOffset}`
  })
}

test.describe('доступность каркаса (PLT-21)', () => {
  // Каркас на обоих проектах (десктоп и телефон 390×844): на телефоне — с нижней панелью,
  // меню «Ещё» и нижним листом панели.
  const SCREENS = [
    '/schedule',
    '/schedule/new',
    '/schedule?panel=search',
    '/patients',
    '/patients?filter=children&page=2',
    '/staff',
    '/inventory',
    '/clinic/settings',
    '/profile',
    '/more',
  ]

  for (const path of SCREENS) {
    test(`axe ${path}: 0 нарушений serious и critical`, async ({ page, context }) => {
      await usePersona(context, 'owner')
      await page.goto(path)
      await expect(page.locator('h1')).not.toBeEmpty()
      if (path.includes('panel=')) await expect(page.getByRole('dialog')).toBeVisible()
      expect(await seriousViolations(page)).toEqual([])
    })
  }

  test('вход: axe чистый, у полей и кнопки — общий контур фокуса', async ({ page, context }) => {
    await usePersona(context, 'none')
    await page.goto('/login')
    await expect(page.getByRole('heading', { level: 1, name: 'Вход' })).toBeVisible()
    expect(await seriousViolations(page)).toEqual([])

    // С клавиатуры: Tab по полям и кнопке — у каждого контур 3 px «лагуна».
    const login = page.getByLabel('Телефон или e-mail')
    const password = page.getByLabel('Пароль')
    const submit = page.getByRole('button', { name: 'Войти' })
    await login.focus()
    await expect.poll(() => outline(login)).toBe(FOCUS_LAGOON)
    await page.keyboard.press('Tab')
    await expect(password).toBeFocused()
    await expect.poll(() => outline(password)).toBe(FOCUS_LAGOON)
    await page.keyboard.press('Tab')
    await expect(submit).toBeFocused()
    await expect.poll(() => outline(submit)).toBe(FOCUS_LAGOON)
  })

  test('выбор клиники: axe чистый, у карточки клиники — общий контур фокуса', async ({ page, context }) => {
    await usePersona(context, 'multi')
    await page.goto('/select-clinic')
    const cards = page.getByRole('list', { name: 'Клиники' }).getByRole('button')
    await expect(cards).toHaveCount(2)
    expect(await seriousViolations(page)).toEqual([])

    await page.keyboard.press('Tab')
    await expect(cards.first()).toBeFocused()
    await expect.poll(() => outline(cards.first())).toBe(FOCUS_LAGOON)
    await page.keyboard.press('Tab')
    await expect(cards.nth(1)).toBeFocused()
    await expect.poll(() => outline(cards.nth(1))).toBe(FOCUS_LAGOON)
  })
})
