import { expect, type Locator, type Page, test } from '@playwright/test'

import { hitArea, horizontalOverflow, isDesktop, swipe } from './helpers'

// Сайдбар в низком окне (PLT-21 «при масштабе 200 % ничего не пропадает»; телефон боком — ширина
// ≥ 768, вид планшета, нижней панели и «Ещё» нет): карточка сайдбара не вылезает за окно, а
// прокручивается сама — каждый раздел и карточка «Мой профиль» достижимы колесом, клавиатурой и
// прокруткой; активный пункт виден сразу; страница вбок не прокручивается.
const SECTIONS = ['Расписание', 'Пациенты', 'Врачи', 'Склад', 'Клиника', 'Доступы']
const PROFILE = 'Мой профиль: Алиева Динара'

const SIZES = [
  { width: 844, height: 390, note: 'iPhone боком, иконки' },
  { width: 915, height: 412, note: 'Pixel 7 боком, иконки' },
  { width: 800, height: 450, note: '1600×900 при 200 %, иконки' },
  { width: 1024, height: 450, note: 'десктоп, низкое окно, подписи' },
]

/** Пункты сайдбара по порядку: разделы, затем карточка профиля. */
function sidebarItems(page: Page): Locator[] {
  const nav = page.getByRole('navigation', { name: 'Разделы' })
  return [...SECTIONS.map((name) => nav.getByRole('link', { name })), page.getByRole('link', { name: PROFILE })]
}

/**
 * Пункт целиком виден в окне и не обрезан карточкой сайдбара: его прямоугольник — внутри видимой
 * части карточки и окна, а в центре пункта — сам пункт (не соседний элемент поверх).
 */
function fullyVisible(item: Locator): Promise<boolean> {
  return item.evaluate((element) => {
    const card = element.closest<HTMLElement>('[data-sidebar-card]')
    if (!card) return false
    const box = element.getBoundingClientRect()
    const clip = card.getBoundingClientRect()
    const inside =
      box.top >= Math.max(clip.top, 0) - 0.5 &&
      box.bottom <= Math.min(clip.bottom, window.innerHeight) + 0.5 &&
      box.left >= clip.left - 0.5 &&
      box.right <= clip.right + 0.5
    const hit = document.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2)
    return inside && !!hit && element.contains(hit)
  })
}

async function openSchedule(page: Page) {
  await page.goto('/schedule')
  await expect(page.getByRole('navigation', { name: 'Разделы' }).getByRole('link')).toHaveCount(SECTIONS.length)
  await page.evaluate(() => document.fonts.ready)
}

test.describe('сайдбар в низком окне', () => {
  test.skip(() => !isDesktop(), 'сайдбар — с 768 px, проверяется на десктопном проекте')

  for (const size of SIZES) {
    test(`${size.width}×${size.height} (${size.note}): карточка в окне, все пункты достижимы, вбок не прокручивается`, async ({
      page,
    }) => {
      await page.setViewportSize(size)
      await openSchedule(page)

      const card = page.locator('[data-sidebar-card]')
      const box = await card.boundingBox()
      expect(box, 'карточка сайдбара на странице').not.toBeNull()
      // Карточка целиком в окне: пункты не вылезают за её низ и за низ окна.
      expect((box?.y ?? 0) + (box?.height ?? 0)).toBeLessThanOrEqual(size.height + 0.5)
      expect(await horizontalOverflow(page), 'scrollWidth > clientWidth').toBeLessThanOrEqual(0)
      // Сайдбар действительно не влез: его прокрутка — не пустая проверка.
      expect(await card.evaluate((element) => element.scrollHeight - element.clientHeight)).toBeGreaterThan(0)
      // Вбок сама карточка не прокручивается (подписи, аватар и контур фокуса умещаются).
      expect(await card.evaluate((element) => element.scrollWidth - element.clientWidth)).toBeLessThanOrEqual(0)

      for (const item of sidebarItems(page)) {
        await item.scrollIntoViewIfNeeded()
        await expect(item).toBeInViewport({ ratio: 1 })
        expect(await fullyVisible(item), (await item.getAttribute('aria-label')) ?? '').toBe(true)
      }

      // Последний пункт действительно нажимается.
      await page.getByRole('link', { name: PROFILE }).click()
      await expect(page).toHaveURL(/\/profile$/)
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Мой профиль')
    })
  }

  test('844×390: колесо над сайдбаром прокручивает только его, до «Мой профиль»', async ({ page }) => {
    await page.setViewportSize({ width: 844, height: 390 })
    await openSchedule(page)

    const profile = page.getByRole('link', { name: PROFILE })
    expect(await fullyVisible(profile)).toBe(false)

    const card = page.locator('[data-sidebar-card]')
    const box = await card.boundingBox()
    const scrollBefore = await page.evaluate(() => window.scrollY)
    await page.mouse.move((box?.x ?? 0) + (box?.width ?? 0) / 2, (box?.y ?? 0) + (box?.height ?? 0) / 2)
    await page.mouse.wheel(0, 600)

    await expect.poll(() => fullyVisible(profile)).toBe(true)
    // overscroll-behavior: contain — прокрутка не перешла на страницу.
    expect(await page.evaluate(() => window.scrollY)).toBe(scrollBefore)
  })

  test('800×450: Tab по сайдбару — каждый пункт в фокусе виден целиком', async ({ page }) => {
    await page.setViewportSize({ width: 800, height: 450 })
    await openSchedule(page)

    // Старт — логотип (первая ссылка сайдбара), дальше только клавиатура.
    await page.locator('[data-sidebar] a').first().focus()
    for (const item of sidebarItems(page)) {
      await page.keyboard.press('Tab')
      await expect(item).toBeFocused()
      await expect.poll(() => fullyVisible(item)).toBe(true)
    }
  })

  for (const { path, name } of [
    { path: '/access', name: 'Доступы' },
    { path: '/profile', name: PROFILE },
  ]) {
    test(`844×390: открыт ${path} — активный пункт виден сразу, без прокрутки руками`, async ({ page }) => {
      await page.setViewportSize({ width: 844, height: 390 })
      await page.goto(path)
      const item = page.locator('[data-sidebar]').getByRole('link', { name, exact: true })
      await expect(item).toHaveAttribute('aria-current', 'page')
      await expect.poll(() => fullyVisible(item)).toBe(true)
    })
  }

  test('844×390: переход по пункту не сбрасывает прокрутку сайдбара, активный остаётся виден', async ({ page }) => {
    await page.setViewportSize({ width: 844, height: 390 })
    await openSchedule(page)

    const access = page.getByRole('navigation', { name: 'Разделы' }).getByRole('link', { name: 'Доступы' })
    await access.scrollIntoViewIfNeeded()
    await access.click()
    await expect(page).toHaveURL(/\/access/)
    await expect(access).toHaveAttribute('aria-current', 'page')
    await expect.poll(() => fullyVisible(access)).toBe(true)
  })
})

test.describe('телефон боком: сайдбар пальцем', () => {
  test.skip(() => isDesktop(), 'сенсорный ввод — мобильный проект (Pixel 7), повёрнутый боком')
  test.use({ viewport: { width: 844, height: 390 } })

  test('свайп вверх по сайдбару докручивает до «Мой профиль», страница не двигается; пункты ≥ 44×44', async ({
    page,
  }) => {
    await openSchedule(page)
    await expect(page.locator('[data-sidebar]')).toHaveAttribute('data-sidebar', 'collapsed')
    expect(await horizontalOverflow(page), 'scrollWidth > clientWidth').toBeLessThanOrEqual(0)

    const small: string[] = []
    for (const item of sidebarItems(page)) {
      const area = await hitArea(item)
      if (area.width < 44 || area.height < 44) small.push(`${await item.getAttribute('aria-label')}: ${area.width}×${area.height}`)
    }
    expect(small).toEqual([])

    const profile = page.getByRole('link', { name: PROFILE })
    expect(await fullyVisible(profile)).toBe(false)

    const box = await page.locator('[data-sidebar-card]').boundingBox()
    const scrollBefore = await page.evaluate(() => window.scrollY)
    await swipe(page, { x: (box?.x ?? 0) + (box?.width ?? 0) / 2, y: (box?.y ?? 0) + (box?.height ?? 0) - 40 }, { dy: -250 })

    await expect.poll(() => fullyVisible(profile)).toBe(true)
    expect(await page.evaluate(() => window.scrollY)).toBe(scrollBefore)
  })
})
