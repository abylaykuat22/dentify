import { expect, type Page, test } from '@playwright/test'

import { isDesktop } from './helpers'

// PLT-20: жёлтая кнопка («лампа», Button variant=default) — одна на область. Область —
// страница, открытая панель или окно (под затемнением кнопки фона не считаются). «+» нижней
// панели телефона — навигация, а не кнопка области (screens.md), в `lampsPerArea` его нет; но
// рядом с жёлтым «+» в шапке телефона жёлтого быть не должно — это отдельная проверка каркаса ниже.

/** Видимые жёлтые кнопки по областям: страница и каждое открытое окно/панель. */
function lampsPerArea(page: Page): Promise<Record<string, number>> {
  return page.evaluate(() => {
    const visible = (element: Element) => {
      const rect = element.getBoundingClientRect()
      return rect.width > 0 && rect.height > 0 && getComputedStyle(element).visibility !== 'hidden'
    }
    const counts: Record<string, number> = {}
    for (const lamp of Array.from(document.querySelectorAll('[data-slot=button][data-variant=default]'))) {
      if (!visible(lamp)) continue
      const dialog = lamp.closest('[role=dialog]')
      const area = dialog ? `dialog:${dialog.getAttribute('aria-labelledby') ?? '?'}` : 'page'
      counts[area] = (counts[area] ?? 0) + 1
    }
    return counts
  })
}

const SCREENS = [
  '/schedule',
  '/schedule/week',
  '/patients',
  '/staff',
  '/inventory',
  '/inventory/documents',
  '/clinic',
  '/access',
  '/profile',
]

test.describe('одна жёлтая кнопка на область', () => {
  for (const path of SCREENS) {
    test(`${path}: на странице не больше одной, в открытой панели — своя`, async ({ page }) => {
      await page.goto(path)
      await expect(page.locator('h1')).not.toBeEmpty()

      for (const count of Object.values(await lampsPerArea(page))) expect(count).toBeLessThanOrEqual(1)

      await page.getByRole('button', { name: 'Найти пациента (Ctrl K)' }).click()
      await expect(page.getByRole('dialog', { name: 'Поиск пациента' })).toBeVisible()
      for (const count of Object.values(await lampsPerArea(page))) expect(count).toBeLessThanOrEqual(1)
    })
  }
})

// Каркас (шапка и нижняя панель телефона) — не больше одного жёлтого: на десктопе это главное
// действие раздела, на телефоне — «+» панели, а действие раздела в шапке — белый круг рядом с
// поиском (два жёлтых «добавить» на одном экране читаются как одно и то же). Считается то, что
// выглядит жёлтым (вычисленный фон = --color-lamp), а не data-variant: ловит и «+», и любую
// самодельную жёлтую кнопку.
const SHELL = ['/schedule', '/patients', '/staff', '/inventory', '/clinic', '/access', '/profile', '/more']

test.describe('каркас: шапка и нижняя панель — одно жёлтое', () => {
  for (const path of SHELL) {
    test(`${path}: в шапке и нижней панели не больше одной жёлтой кнопки`, async ({ page }) => {
      await page.goto(path)
      await expect(page.locator('h1')).not.toBeEmpty()

      const yellow = await page.evaluate(() => {
        const probe = document.createElement('div')
        probe.style.background = 'var(--color-lamp)'
        document.body.append(probe)
        const lamp = getComputedStyle(probe).backgroundColor
        probe.remove()
        const chrome = document.querySelectorAll('[data-shell-header], [data-bottom-nav]')
        const names: string[] = []
        for (const area of Array.from(chrome)) {
          for (const control of Array.from(area.querySelectorAll('a, button, [role=button]'))) {
            const rect = control.getBoundingClientRect()
            const shown = rect.width > 0 && rect.height > 0 && getComputedStyle(control).visibility !== 'hidden'
            if (shown && getComputedStyle(control).backgroundColor === lamp) {
              names.push(control.getAttribute('aria-label') ?? control.textContent?.trim() ?? '?')
            }
          }
        }
        return names
      })
      expect(yellow.length, `жёлтые: ${yellow.join(', ')}`).toBeLessThanOrEqual(1)
      // На телефоне жёлтый — «+» панели (у владельца есть право на запись), не кнопка шапки.
      if (!isDesktop()) expect(yellow).toEqual(['Новая запись'])
    })
  }
})

// Главное действие не бывает «мёртвым»: у каждой видимой кнопки главного действия шапки есть
// отклик — переход на экран действия или уведомление, что экран в разработке (пока экрана нет). И на
// телефоне, где кнопка — белый круг рядом с поиском, и на десктопе, где она жёлтая.
const ACTIONS = ['/schedule', '/patients', '/staff', '/inventory', '/access']

test.describe('у главного действия шапки есть отклик', () => {
  for (const path of ACTIONS) {
    test(`${path}: нажатие переходит на экран действия или сообщает, что он в разработке`, async ({ page }) => {
      await page.goto(path)
      await expect(page.locator('h1')).not.toBeEmpty()

      const action = page.locator('[data-primary-action]')
      if (!(await action.isVisible())) {
        // Телефон, расписание: «Новая запись» — это «+» нижней панели, в шапке её нет.
        expect(path).toBe('/schedule')
        const nav = page.getByRole('navigation', { name: 'Главное меню' })
        await expect(nav.getByRole('link', { name: 'Новая запись' })).toBeVisible()
        return
      }
      const box = (await action.boundingBox())!
      expect(box.height).toBeGreaterThanOrEqual(44)
      expect(box.width).toBeGreaterThanOrEqual(44)

      const label = (await action.textContent())?.trim() ?? ''
      expect(label).not.toBe('')
      await action.click()
      await expect
        .poll(async () => {
          if (!page.url().endsWith(path)) return 'переход'
          return (await page.getByText(`Экран «${label}» пока в разработке`).isVisible()) ? 'уведомление' : 'ничего'
        })
        .not.toBe('ничего')
    })
  }
})

