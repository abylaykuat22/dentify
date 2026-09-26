import { expect, type Page, test } from '@playwright/test'

import { appPaths, hitArea, horizontalOverflow, isDesktop, type Persona, usePersona } from './helpers'

// Общая проверка телефона по ВСЕМ маршрутам (T-PLT-10, PLT-18, PLT-21): адреса берутся из
// дерева маршрутов, поэтому новый экран попадает сюда сам. На 390 px (эталон) и 360 px
// (минимум): нет горизонтальной прокрутки, поля — шрифт ≥ 16 px, цели касания ≥ 44×44.

/** Кем открывать адрес: вход — без сессии, выбор клиники — врач двух клиник. */
const PERSONA: Record<string, Persona> = { '/login': 'none', '/select-clinic': 'multi' }

/** Дополнительные состояния каркаса: закрытый раздел, открытая панель. */
const EXTRA: { path: string; persona: Persona }[] = [
  { path: '/clinic', persona: 'assistant' },
  { path: '/schedule?panel=search', persona: 'owner' },
  { path: '/patients?filter=children&page=2', persona: 'owner' },
]

const SCREENS = [
  ...appPaths().map((path) => ({ path, persona: PERSONA[path] ?? ('owner' as Persona) })),
  ...EXTRA,
]

async function openScreen(page: Page, path: string) {
  await page.goto(path)
  // Экран дорисован: есть заголовок h1 (на открытой панели он под затемнением — берём любой).
  await expect(page.locator('h1').first()).toBeAttached()
  await expect(page.locator('h1').first()).not.toBeEmpty()
  // Боковая панель — ленивый чанк: по ссылке с `?panel=` она открывается чуть позже заголовка, а
  // открывшись, забирает фокус (нижняя панель прячется, пока фокус в поле). Меряем уже после этого.
  if (path.includes('panel=')) {
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect.poll(() => page.evaluate(() => Boolean(document.activeElement?.closest('[role=dialog]')))).toBe(true)
  }
  await page.evaluate(() => document.fonts.ready)
}

/** Поля ввода, видимые на экране, со шрифтом меньше 16 px (iOS увеличит страницу при фокусе). */
function smallFields(page: Page): Promise<string[]> {
  return page.evaluate(() =>
    Array.from(document.querySelectorAll<HTMLElement>('input, textarea, select'))
      .filter((field) => {
        if (field instanceof HTMLInputElement && ['hidden', 'checkbox', 'radio'].includes(field.type)) return false
        const rect = field.getBoundingClientRect()
        return rect.width > 1 && rect.height > 1 && getComputedStyle(field).visibility !== 'hidden'
      })
      .filter((field) => parseFloat(getComputedStyle(field).fontSize) < 16)
      .map((field) => `${field.id || field.getAttribute('name') || field.tagName}: ${getComputedStyle(field).fontSize}`),
  )
}

test.describe('телефон: все маршруты', () => {
  test.skip(() => isDesktop(), 'только мобильный проект (390×844)')

  for (const { path, persona } of SCREENS) {
    test(`${path} (${persona}): 390 и 360 px без горизонтальной прокрутки, поля ≥ 16 px`, async ({ page, context }) => {
      await usePersona(context, persona)
      await openScreen(page, path)

      expect(await horizontalOverflow(page), 'scrollWidth > clientWidth на 390 px').toBeLessThanOrEqual(0)
      expect(await smallFields(page)).toEqual([])

      await page.setViewportSize({ width: 360, height: 780 })
      await expect.poll(() => horizontalOverflow(page), { message: 'scrollWidth > clientWidth на 360 px' }).toBeLessThanOrEqual(0)
    })
  }

  for (const { path, persona } of SCREENS) {
    test(`${path} (${persona}): все кнопки, ссылки и поля — цели касания ≥ 44×44`, async ({ page, context }) => {
      await usePersona(context, persona)
      await openScreen(page, path)

      const small: string[] = []
      // Видимые интерактивные элементы, кроме ссылок внутри текста абзаца.
      const targets = page.locator(
        'a[href]:not(p a), button, input:not([type=hidden]):not([aria-hidden=true]), textarea, select, [role=tab]',
      )
      for (const target of await targets.all()) {
        if (!(await target.isVisible())) continue
        const area = await hitArea(target)
        if (area.width < 44 || area.height < 44) {
          const name = (await target.getAttribute('aria-label')) ?? (await target.innerText()).trim()
          small.push(`${name}: ${Math.round(area.width)}×${Math.round(area.height)}`)
        }
      }
      expect(small).toEqual([])
    })
  }
})
