import { expect, test } from '@playwright/test'

import { isDesktop } from './helpers'

// PLT-17: на 768–1023 px сайдбар свёрнут до иконок, у каждой иконки подсказка с названием,
// подсказка доступна и с клавиатуры (Base UI Tooltip открывается по фокусу).
const SECTIONS = ['Расписание', 'Пациенты', 'Врачи', 'Склад', 'Клиника', 'Доступы']
const PROFILE = 'Меню пользователя: Алиева Динара'

test.describe('сайдбар на 768–1023 px', () => {
  test.skip(() => !isDesktop(), 'проверяется на десктопном проекте с уменьшенным окном')
  test.use({ viewport: { width: 900, height: 800 } })

  // Закрывающаяся подсказка соседнего пункта остаётся в DOM на время анимации — берём открытую.
  const openTooltip = '[data-slot="tooltip-content"][data-open]'
  const anyTooltip = '[data-slot="tooltip-content"]'

  test('свёрнут до иконок: подписи скрыты, у каждой иконки и у профиля подсказка при наведении', async ({
    page,
  }) => {
    await page.goto('/schedule')

    const sidebar = page.locator('[data-sidebar]')
    await expect(sidebar).toHaveAttribute('data-sidebar', 'collapsed')
    expect((await sidebar.boundingBox())?.width ?? 0).toBeLessThan(120)

    const nav = page.getByRole('navigation', { name: 'Разделы' })
    await expect(nav.getByRole('link')).toHaveCount(SECTIONS.length)
    const tooltip = page.locator(openTooltip)

    for (const name of SECTIONS) {
      const link = nav.getByRole('link', { name })
      await expect(link).toBeVisible()
      await expect(link.getByText(name)).toBeHidden()
      await link.hover()
      await expect(tooltip).toHaveText(name)
      // Увести мышь и дождаться, пока подсказка закроется: наведение на соседний пункт, пока эта
      // ещё закрывается, под нагрузкой не всегда открывало следующую (падение на «Доступах»).
      await page.mouse.move(600, 600)
      await expect(page.locator(anyTooltip)).toHaveCount(0)
    }

    const profile = page.getByRole('button', { name: PROFILE })
    await profile.hover()
    await expect(tooltip).toHaveText(PROFILE)

    await page.mouse.move(600, 600)
    await expect(tooltip).toHaveCount(0)
  })

  test('подсказки открываются с клавиатуры: Tab по всем иконкам и карточке пользователя', async ({ page }) => {
    await page.goto('/schedule')

    const nav = page.getByRole('navigation', { name: 'Разделы' })
    await expect(nav.getByRole('link')).toHaveCount(SECTIONS.length)
    const tooltip = page.locator(openTooltip)

    // Старт — логотип (первая ссылка сайдбара), дальше только клавиатура.
    await page.locator('[data-sidebar] a').first().focus()
    for (const name of SECTIONS) {
      await page.keyboard.press('Tab')
      await expect(nav.getByRole('link', { name })).toBeFocused()
      await expect(tooltip).toHaveText(name)
    }

    await page.keyboard.press('Tab')
    const profile = page.getByRole('button', { name: PROFILE })
    await expect(profile).toBeFocused()
    await expect(tooltip).toHaveText(PROFILE)

    // Enter открывает меню пользователя (справа от свёрнутого сайдбара), фокус — на первом пункте.
    await page.keyboard.press('Enter')
    const item = page.getByRole('menuitem', { name: 'Мой профиль' })
    await expect(item).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(page).toHaveURL(/\/profile$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Мой профиль')
  })

  test('клик по карточке пользователя открывает меню: «Мой профиль» и «Выйти»', async ({ page }) => {
    await page.goto('/inventory')

    await page.getByRole('button', { name: PROFILE }).click()
    const menu = page.getByRole('menu')
    await expect(menu.getByRole('menuitem')).toHaveText(['Мой профиль', 'Выйти'])
    // Меню — справа от свёрнутого сайдбара, в окне.
    const sidebar = await page.locator('[data-sidebar-card]').boundingBox()
    const popup = await menu.boundingBox()
    expect(popup?.x ?? 0).toBeGreaterThanOrEqual((sidebar?.x ?? 0) + (sidebar?.width ?? 0))
    await menu.getByRole('menuitem', { name: 'Мой профиль' }).click()
    await expect(page).toHaveURL(/\/profile$/)
    await expect(page).toHaveTitle('Мой профиль — Dentify')
    await expect(page.getByRole('button', { name: PROFILE })).toHaveAttribute('data-current', 'true')
  })
})
