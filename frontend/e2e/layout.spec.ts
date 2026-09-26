import { expect, type Page, test } from '@playwright/test'

import { isDesktop } from './helpers'

// Раскладка по макету Raspisanie-Den: шапка и вкладки — на всю ширину над контентом,
// боковая панель — колонкой под шапкой рядом с карточкой контента. Открытие панели
// не сжимает шапку, не переносит строку контекста и не двигает вкладки.

interface ShellMetrics {
  headerHeight: number
  titleClipped: boolean
  contextClipped: boolean
  tabsTop: number
  tabsBottom: number
  tabsScrollbar: number
  mainTop: number
}

function measure(page: Page): Promise<ShellMetrics> {
  return page.evaluate(() => {
    const header = document.querySelector<HTMLElement>('[data-shell-header]')!
    const title = header.querySelector<HTMLElement>('h1')!
    const context = header.querySelector<HTMLElement>('p')!
    const tabs = document.querySelector<HTMLElement>('nav[aria-label="Вкладки раздела"]')!
    const main = document.querySelector<HTMLElement>('main')!
    return {
      headerHeight: header.getBoundingClientRect().height,
      titleClipped: title.scrollWidth > title.clientWidth,
      contextClipped: context.scrollWidth > context.clientWidth,
      tabsTop: tabs.getBoundingClientRect().top,
      tabsBottom: tabs.getBoundingClientRect().bottom,
      tabsScrollbar: tabs.offsetHeight - tabs.clientHeight,
      mainTop: main.getBoundingClientRect().top,
    }
  })
}

/** Активная вкладка целиком видна внутри полосы вкладок (не обрезана прокруткой). */
function activeTabFullyVisible(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const tabs = document.querySelector<HTMLElement>('nav[aria-label="Вкладки раздела"]')!
    const active = tabs.querySelector<HTMLElement>('[aria-current="page"]')!
    const outer = tabs.getBoundingClientRect()
    const inner = active.getBoundingClientRect()
    return inner.left >= outer.left - 0.5 && inner.right <= outer.right + 0.5 && inner.right <= window.innerWidth
  })
}

test.describe('раскладка каркаса (desktop)', () => {
  test.skip(() => !isDesktop(), 'колонка панели — с 1024 px; телефон проверяет mobile.spec')

  for (const size of [
    { width: 1440, height: 900 },
    { width: 1024, height: 800 },
  ]) {
    test(`${size.width} px: панель под шапкой рядом с контентом, шапка и вкладки не двигаются`, async ({ page }) => {
      await page.setViewportSize(size)
      await page.goto('/clinic/dictionaries')
      await expect(page.locator('h1')).toHaveText('Клиника')

      const before = await measure(page)
      expect(before.titleClipped).toBe(false)
      expect(before.contextClipped).toBe(false)
      expect(await activeTabFullyVisible(page)).toBe(true)

      await page.keyboard.press('Control+k')
      const panel = page.getByRole('dialog', { name: 'Поиск пациента' })
      await expect(panel).toBeVisible()
      await expect(panel).toHaveAttribute('data-panel-mode', 'inline')

      const after = await measure(page)
      // Шапка не переносится и не сжимается, вкладки не съезжают.
      expect(after.headerHeight).toBeCloseTo(before.headerHeight, 0)
      expect(after.tabsTop).toBeCloseTo(before.tabsTop, 0)
      expect(after.titleClipped).toBe(false)
      expect(after.contextClipped).toBe(false)
      // Активная вкладка видна, нативной полосы прокрутки под вкладками нет.
      expect(await activeTabFullyVisible(page)).toBe(true)
      expect(after.tabsScrollbar).toBe(0)

      // Панель — ниже вкладок и выровнена по верху карточки контента.
      const panelBox = (await panel.boundingBox())!
      expect(panelBox.y).toBeGreaterThan(after.tabsBottom)
      expect(panelBox.y).toBeCloseTo(after.mainTop, 0)
    })
  }

  test('768 px: шапка в одну строку — жёлтая кнопка рядом с заголовком', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 800 })
    await page.goto('/inventory')

    const title = page.locator('h1')
    await expect(title).toHaveText('Склад')
    const action = page.locator('[data-primary-action="inventory"]')
    await expect(action).toBeVisible()

    const titleBox = (await title.boundingBox())!
    const actionBox = (await action.boundingBox())!
    expect(actionBox.y).toBeLessThan(titleBox.y + titleBox.height)
    expect(actionBox.x).toBeGreaterThan(titleBox.x)
  })
})
