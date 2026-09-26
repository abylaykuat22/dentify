import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

import { type BrowserContext, type Locator, type Page, test } from '@playwright/test'

export type Persona = 'owner' | 'assistant' | 'multi' | 'plain' | 'none'

/** Кого возвращает мок /api/v1/me: cookie читают обработчики MSW (shared/api/mocks). */
export async function usePersona(context: BrowserContext, persona: Persona) {
  // Порт у каждого прогона свой (playwright.config.ts) — адрес только из конфига.
  const baseURL = test.info().project.use.baseURL
  if (!baseURL) throw new Error('usePersona: в конфиге Playwright нет baseURL')
  await context.addCookies([{ name: 'dentify_mock_user', value: persona, url: baseURL }])
}

export function isDesktop() {
  return test.info().project.name === 'desktop'
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
