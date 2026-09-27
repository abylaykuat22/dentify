import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

import AxeBuilder from '@axe-core/playwright'
import { type BrowserContext, expect, type Locator, type Page, test } from '@playwright/test'

export type Persona = 'owner' | 'assistant' | 'multi' | 'plain' | 'none'

/** Кого возвращает мок /api/v1/me: cookie читают обработчики MSW (shared/api/mocks). */
export async function usePersona(context: BrowserContext, persona: Persona) {
  // Порт у каждого прогона свой (playwright.config.ts) — адрес только из конфига.
  const baseURL = test.info().project.use.baseURL
  if (!baseURL) throw new Error('usePersona: в конфиге Playwright нет baseURL')
  await context.addCookies([{ name: 'dentify_mock_user', value: persona, url: baseURL }])
}

/** Проект десктопа 1440×900: `desktop` (моки) или `stack-desktop` (живой стек). */
export function isDesktop() {
  return test.info().project.name.endsWith('desktop')
}

/**
 * Все адреса приложения — из дерева маршрутов (`FileRoutesByTo` в src/app/routeTree.gen.ts):
 * новый экран сам попадает в общие проверки телефона. Параметры (`$id`) пока не встречаются;
 * `/$` (404) заменяется несуществующим адресом.
 */
export function appPaths(): string[] {
  const tree = readFileSync(fileURLToPath(new URL('../src/app/routeTree.gen.ts', import.meta.url)), 'utf8')
  const block = /export interface FileRoutesByTo \{([\s\S]*?)\n\}/.exec(tree)?.[1] ?? ''
  const paths = [...block.matchAll(/^\s*'([^']+)':/gm)].map((match) => match[1] ?? '')
  if (paths.length === 0) throw new Error('В routeTree.gen.ts не нашлось FileRoutesByTo')
  return paths.map((path) => (path === '/$' ? '/nope/nothing' : path))
}

/** Горизонтальная прокрутка страницы: > 0 — что-то вылезло за ширину окна (PLT-18). */
export function horizontalOverflow(page: Page): Promise<number> {
  return page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
}

/**
 * Страница не прокручивается вбок: `scrollWidth ≤ clientWidth` (PLT-18). С ожиданием — шрифт или
 * ленивый чанк могут дорисоваться после проверки.
 */
export async function assertNoHorizontalScroll(page: Page, where = '') {
  const width = page.viewportSize()?.width ?? 0
  await expect
    .poll(() => horizontalOverflow(page), { message: `scrollWidth > clientWidth на ${width} px ${where}`.trim() })
    .toBeLessThanOrEqual(0)
}

/** Теги axe: WCAG 2.2 AA (PLT-21). */
const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

/**
 * Нарушения axe-core уровня serious и critical на странице (PLT-21: их должно быть 0) — строкой
 * «правило: элементы», чтобы в отчёте было видно, что чинить. Без исключений: color-contrast меряет
 * настоящие пары текст/фон. Ждёт шрифты — контраст считается по отрисованному тексту.
 */
export async function seriousA11yViolations(page: Page): Promise<string[]> {
  await page.evaluate(() => document.fonts.ready)
  const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze()
  return results.violations
    .filter((violation) => violation.impact === 'serious' || violation.impact === 'critical')
    .map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target.join(' ')).join(' | ')}`)
}

/**
 * Видимые цели касания меньше 44×44 (PLT-18, PLT-21) — «подпись: Ш×В». Кнопки, ссылки (кроме
 * ссылок внутри абзаца), поля и вкладки; площадь — с расширением `::after` (`hitArea`).
 */
export async function smallTapTargets(scope: Page | Locator, min = 44): Promise<string[]> {
  const small: string[] = []
  const targets = scope.locator(
    'a[href]:not(p a), button, input:not([type=hidden]):not([aria-hidden=true]), textarea, select, [role=tab]',
  )
  for (const target of await targets.all()) {
    if (!(await target.isVisible())) continue
    const area = await hitArea(target)
    if (area.width < min || area.height < min) {
      const name = (await target.getAttribute('aria-label')) ?? (await target.innerText()).trim()
      small.push(`${name}: ${Math.round(area.width)}×${Math.round(area.height)}`)
    }
  }
  return small
}

/**
 * Строки открытого выпадающего списка поля с поиском (`Combo`, R011): список рисуется в портале
 * (R022), а не внутри карточки или панели поля, поэтому ищется по всей странице — `within`
 * (карточка, прокручиваемое тело панели) не должен его содержать. Открыть список — забота теста
 * (набор текста или клик по полю).
 */
export async function comboRows(page: Page, within?: Locator): Promise<Locator> {
  const popup = page.locator('[data-slot=combobox-content]')
  await expect(popup).toBeVisible()
  if (within) {
    // Портал: список — потомок body, а не карточки поля (иначе его обрезал бы overflow контейнера).
    await expect(within.locator('[data-slot=combobox-content]')).toHaveCount(0)
  }
  return popup.getByRole('option')
}

/**
 * Площадь касания элемента (PLT-21: ≥ 44×44): его прямоугольник вместе с расширением
 * псевдоэлементом `::after`/`::before` (так увеличены маленькие кнопки и вкладки, а у
 * строки-ссылки `rowActionClass` — вся строка).
 */
export function hitArea(locator: Locator): Promise<{ width: number; height: number }> {
  return locator.evaluate((element) => {
    const rect = element.getBoundingClientRect()
    let { left, top, right, bottom } = rect
    for (const pseudo of ['::before', '::after']) {
      const style = getComputedStyle(element, pseudo)
      if (style.content === 'none' || style.position !== 'absolute') continue
      // Абсолютный псевдоэлемент отсчитывается от ближайшего позиционированного предка.
      let block: Element | null = element
      while (block && block !== document.body && getComputedStyle(block).position === 'static') block = block.parentElement
      const base = (block ?? document.body).getBoundingClientRect()
      const inset = ['left', 'top', 'right', 'bottom'].map((side) => parseFloat(style.getPropertyValue(side)))
      if (inset.some(Number.isNaN)) continue
      const [l = 0, t = 0, r = 0, b = 0] = inset
      left = Math.min(left, base.left + l)
      top = Math.min(top, base.top + t)
      right = Math.max(right, base.right - r)
      bottom = Math.max(bottom, base.bottom - b)
    }
    return { width: right - left, height: bottom - top }
  })
}

/**
 * Провести пальцем на `by` (сенсорные события CDP): так Base UI Drawer видит свайп, а не мышь, и
 * так же браузер сам прокручивает полосу вкладок — как настоящий палец.
 */
export async function swipe(
  page: Page,
  from: { x: number; y: number },
  by: { dx?: number; dy?: number },
  steps = 10,
) {
  const { dx = 0, dy = 0 } = by
  const cdp = await page.context().newCDPSession(page)
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [from] })
  for (let step = 1; step <= steps; step += 1) {
    await cdp.send('Input.dispatchTouchEvent', {
      type: 'touchMove',
      touchPoints: [{ x: from.x + (dx * step) / steps, y: from.y + (dy * step) / steps }],
    })
    await page.waitForTimeout(16)
  }
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
  await cdp.detach()
}

/**
 * Где лежит текст подписи (Range, а не блок): для проверки, что подпись уместилась в свою
 * ячейку, а не вылезла за неё.
 */
export function labelBoxes(items: Locator): Promise<{ label: string; cell: DOMRect; text: DOMRect }[]> {
  return items.evaluateAll((elements) =>
    elements.map((element) => {
      // Только текст: иконка над подписью в Range не входит.
      const texts = Array.from(element.childNodes).filter(
        (node) => node.nodeType === Node.TEXT_NODE && (node.textContent ?? '').trim() !== '',
      )
      const range = document.createRange()
      const first = texts[0]
      const last = texts[texts.length - 1]
      if (first && last) {
        range.setStartBefore(first)
        range.setEndAfter(last)
      }
      return {
        label: element.textContent ?? '',
        cell: element.getBoundingClientRect().toJSON() as DOMRect,
        text: range.getBoundingClientRect().toJSON() as DOMRect,
      }
    }),
  )
}

/** Вырез и полоска «домой» iPhone: env(safe-area-inset-*) через CDP (Chromium ≥ 136). */
export async function emulateSafeArea(page: Page, insets: { top: number; bottom: number; left?: number; right?: number }) {
  const cdp = await page.context().newCDPSession(page)
  const { top, bottom, left = 0, right = 0 } = insets
  await cdp.send('Emulation.setSafeAreaInsetsOverride', {
    insets: { top, topMax: top, bottom, bottomMax: bottom, left, leftMax: left, right, rightMax: right },
  })
  return cdp
}
