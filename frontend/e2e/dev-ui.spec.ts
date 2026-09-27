import { expect, type Locator, type Page, test } from '@playwright/test'

import { comboRows, isDesktop, seriousA11yViolations } from './helpers'

// Витрина дизайн-системы «Кресло» /dev/ui (T-PLT-06, PLT-20, PLT-21): эталонные скриншоты по
// макету Sistema, шрифты с казахскими буквами, список в портале у края окна и в прокручиваемой
// панели, шаги панели и окна (R023), фокус, reduced motion, axe.

async function openDevUi(page: Page) {
  await page.goto('/dev/ui')
  await expect(page.getByRole('heading', { level: 1, name: 'Дизайн-система «Кресло»' })).toBeVisible()
  await page.evaluate(() => document.fonts.ready)
}

/** Прямоугольник целиком внутри окна. */
async function expectInViewport(page: Page, locator: Locator) {
  const box = await locator.boundingBox()
  const viewport = page.viewportSize()
  expect(box).not.toBeNull()
  expect(viewport).not.toBeNull()
  if (!box || !viewport) return
  expect(box.y).toBeGreaterThanOrEqual(0)
  expect(box.x).toBeGreaterThanOrEqual(0)
  expect(box.y + box.height).toBeLessThanOrEqual(viewport.height + 0.5)
  expect(box.x + box.width).toBeLessThanOrEqual(viewport.width + 0.5)
}

/** Средняя точка списка не перекрыта и не обрезана: сверху лежит сам список. */
async function expectNotClipped(popup: Locator) {
  const onTop = await popup.evaluate((element) => {
    const rect = element.getBoundingClientRect()
    const hit = document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2)
    return hit !== null && element.contains(hit)
  })
  expect(onTop).toBe(true)
}

test.describe('витрина /dev/ui — эталон по макету Sistema', () => {
  // Pixel 7 рисует с плотностью 2.625: полностраничный снимок в ~20 000 px по высоте. Эталон —
  // в CSS-пикселях, раскладка та же.
  test.use({ deviceScaleFactor: 1 })

  test('вся страница совпадает с эталоном', async ({ page }) => {
    await openDevUi(page)
    await expect(page).toHaveScreenshot('dev-ui.png', { fullPage: true })
  })

  test('список с поиском открыт: «Тер» — три совпадения, первое подсвечено', async ({ page }) => {
    await openDevUi(page)
    const card = page.getByRole('region', { name: 'Поля и выбор' })
    const input = card.getByRole('combobox', { name: 'Специальность' })
    await input.pressSequentially('Тер')
    const options = await comboRows(page, card)
    await expect(options).toHaveCount(3)
    await expect(options.nth(2)).toHaveAttribute('title', 'Эстетическая терапия и реставрация зубов')
    await expect(options.first()).toHaveAttribute('data-highlighted', '')
    await expect(card).toHaveScreenshot('fields-combobox-open.png')
  })
})

test.describe('шрифты «Кресла»', () => {
  const LETTERS = 'әғқңөұүһі'
  const FACES = [
    { family: 'Rubik', weights: [500, 600, 700] },
    { family: 'Nunito Sans', weights: [500, 600, 700, 800] },
  ]

  test('казахские ә ғ қ ң ө ұ ү һ і отрисованы своими шрифтами, а не запасными', async ({ page }) => {
    await openDevUi(page)

    // 1. Замер ширины глифов: если буква есть в шрифте, её ширина не зависит от запасного
    //    семейства в списке (monospace или serif — одна и та же); а раз ширины букв разные,
    //    это пропорциональный шрифт, а не моноширинный запасной.
    const widths = await page.evaluate(
      async ({ letters, faces }) => {
        const context = document.createElement('canvas').getContext('2d')
        if (!context) throw new Error('canvas 2d недоступен')
        const measure = (font: string, text: string) => {
          context.font = font
          return context.measureText(text).width
        }
        const rows = []
        for (const face of faces) {
          for (const weight of face.weights) {
            await document.fonts.load(`${weight} 40px "${face.family}"`, letters)
            for (const letter of letters) {
              rows.push({
                face: `${face.family} ${weight}`,
                letter,
                withMonospace: measure(`${weight} 40px "${face.family}", monospace`, letter),
                withSerif: measure(`${weight} 40px "${face.family}", serif`, letter),
              })
            }
          }
        }
        return rows
      },
      { letters: LETTERS, faces: FACES },
    )

    for (const row of widths) {
      expect(row.withMonospace, `${row.face}: «${row.letter}»`).toBeCloseTo(row.withSerif, 2)
    }
    for (const face of new Set(widths.map((row) => row.face))) {
      const faceWidths = widths.filter((row) => row.face === face).map((row) => row.withMonospace)
      expect(Math.max(...faceWidths) - Math.min(...faceWidths), face).toBeGreaterThan(4)
    }

    // 2. Какими шрифтами Chromium действительно отрисовал текст (DevTools → Rendered Fonts).
    await page.evaluate(
      ({ letters, faces }) => {
        const host = document.createElement('div')
        host.setAttribute('aria-hidden', 'true')
        for (const face of faces) {
          for (const weight of face.weights) {
            const probe = document.createElement('span')
            probe.dataset.glyphProbe = `${face.family}|${weight}`
            probe.style.font = `${weight} 24px "${face.family}", monospace`
            probe.textContent = letters
            host.append(probe)
          }
        }
        document.body.append(host)
      },
      { letters: LETTERS, faces: FACES },
    )
    await page.evaluate(() => document.fonts.ready)

    const cdp = await page.context().newCDPSession(page)
    await cdp.send('DOM.enable')
    await cdp.send('CSS.enable')
    const { root } = await cdp.send('DOM.getDocument', { depth: -1 })
    const { nodeIds } = await cdp.send('DOM.querySelectorAll', {
      nodeId: root.nodeId,
      selector: '[data-glyph-probe], [data-kazakh-sample]',
    })
    expect(nodeIds).toHaveLength(FACES.reduce((sum, face) => sum + face.weights.length, 0) + 1)
    for (const nodeId of nodeIds) {
      const { attributes } = await cdp.send('DOM.getAttributes', { nodeId })
      const probe = attributes[attributes.indexOf('data-glyph-probe') + 1]
      const family = attributes.includes('data-kazakh-sample') ? 'Rubik' : probe?.split('|')[0]
      const { fonts } = await cdp.send('CSS.getPlatformFontsForNode', { nodeId })
      const label = probe ?? 'образец на витрине'
      // Ровно один шрифт, и это наш веб-шрифт (имя — из файла: «Rubik Light SemiBold» и т. п.),
      // а не системный запасной.
      expect(fonts, label).toHaveLength(1)
      expect(fonts[0]?.isCustomFont, label).toBe(true)
      expect(fonts[0]?.familyName, label).toMatch(new RegExp(`^${family ?? '-'}`))
    }
  })

  test('шрифты — с нашего домена, @font-face с font-display: swap', async ({ page, baseURL }) => {
    const fontRequests: string[] = []
    page.on('request', (request) => {
      if (request.resourceType() === 'font') fontRequests.push(request.url())
    })
    await openDevUi(page)

    const faces = await page.evaluate(() => {
      const rules: { family: string; weight: string; display: string; src: string }[] = []
      for (const sheet of Array.from(document.styleSheets)) {
        for (const rule of Array.from(sheet.cssRules)) {
          if (!(rule instanceof CSSFontFaceRule)) continue
          const family = rule.style.getPropertyValue('font-family').replace(/["']/g, '')
          if (family !== 'Rubik' && family !== 'Nunito Sans') continue
          rules.push({
            family,
            weight: rule.style.getPropertyValue('font-weight'),
            display: rule.style.getPropertyValue('font-display'),
            src: rule.style.getPropertyValue('src'),
          })
        }
      }
      return rules
    })

    for (const [family, weights] of [
      ['Rubik', ['500', '600', '700']],
      ['Nunito Sans', ['500', '600', '700', '800']],
    ] as const) {
      for (const weight of weights) {
        expect(faces.some((face) => face.family === family && face.weight === weight), `${family} ${weight}`).toBe(true)
      }
    }
    for (const face of faces) {
      expect(face.display, `${face.family} ${face.weight}`).toBe('swap')
      // Только относительные адреса: woff2 отдаёт наш сервер, сторонних доменов нет.
      expect(face.src).not.toMatch(/url\(["']?(https?:)?\/\//)
      expect(face.src).toContain('.woff2')
    }
    expect(fontRequests.length).toBeGreaterThan(0)
    for (const url of fontRequests) expect(url.startsWith(baseURL ?? '')).toBe(true)
  })
})

test.describe('таблица → карточки (PLT-18)', () => {
  test('с 768 px — таблица, на телефоне — карточки; нажимается вся строка', async ({ page }) => {
    await openDevUi(page)
    const card = page.getByRole('region', { name: 'Таблица → карточки' })

    if (isDesktop()) {
      const table = card.getByRole('table', { name: 'Пациенты (пример)' })
      await expect(table.getByRole('columnheader')).toHaveText([
        'Пациент',
        'Телефон',
        'Последний визит',
        'Лечащий врач',
        'Метки',
      ])
      const row = table.getByRole('row').nth(3)
      // Клик по ячейке телефона (не по имени): растянутая кнопка строки лежит поверх всей строки.
      const phone = await row.getByText('+7 775').boundingBox()
      const rowBox = await row.boundingBox()
      await row.click({ position: { x: (phone?.x ?? 0) - (rowBox?.x ?? 0) + 10, y: (rowBox?.height ?? 0) / 2 } })
      await expect(row).toHaveAttribute('data-state', 'selected')

      await page.setViewportSize({ width: 768, height: 900 })
      await expect(card.getByRole('table')).toBeVisible()
    } else {
      await expect(card.getByRole('table')).toHaveCount(0)
      const cards = card.getByRole('list', { name: 'Пациенты (пример)' }).getByRole('listitem')
      await expect(cards).toHaveCount(4)
      // Второстепенная колонка (лечащий врач) на телефоне скрыта, остальное — «подпись: значение».
      await expect(cards.first().getByRole('term')).toHaveText(['Телефон', 'Последний визит', 'Метки'])
      // Тап по середине карточки (по значению, не по имени) — растянутая кнопка ловит его.
      await cards.nth(2).click()
      await expect(cards.nth(2)).toHaveAttribute('data-state', 'selected')
      expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0)
    }
  })

  test('строка-кнопка с клавиатуры: контур фокуса вокруг всей строки', async ({ page }) => {
    await openDevUi(page)
    const button = page.getByRole('button', { name: 'Абенов Нурлан' })
    await button.focus()
    await page.keyboard.press('Shift+Tab')
    await page.keyboard.press('Tab')
    await expect(button).toBeFocused()
    const ring = await button.evaluate((element) => {
      const after = getComputedStyle(element, '::after')
      return `${after.outlineStyle} ${after.outlineWidth} ${after.outlineColor}`
    })
    expect(ring).toBe('solid 3px rgb(15, 61, 76)')
    await page.keyboard.press('Enter')
    await expect(page.locator('[data-state=selected]').getByRole('button', { name: 'Абенов Нурлан' })).toBeVisible()
  })
})

test.describe('вкладки экрана не влезают (PLT-18)', () => {
  test('на телефоне — прокрутка вбок без полосы, выбранная видна; на десктопе влезают все', async ({ page }) => {
    await openDevUi(page)
    const list = page.getByRole('tablist', { name: 'Разделы карточки пациента (пример)' })
    const selected = list.getByRole('tab', { name: 'Документы' })
    await expect(selected).toHaveAttribute('aria-selected', 'true')
    await list.scrollIntoViewIfNeeded()

    const overflows = await list.evaluate((node: HTMLElement) => node.scrollWidth > node.clientWidth)
    expect(overflows).toBe(!isDesktop())
    expect(await list.evaluate((node: HTMLElement) => node.offsetHeight - node.clientHeight)).toBe(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0)
    // Выбранная — пятая из шести: на телефоне за правым краем полосы, пока её не довели.
    await expect(selected).toBeInViewport({ ratio: 1 })

    if (!isDesktop()) {
      // Прокрутили полосу к началу — первая вкладка видна, выбранная ушла за край.
      await list.evaluate((node: HTMLElement) => node.scrollTo({ left: 0 }))
      await expect(list.getByRole('tab', { name: 'Основное' })).toBeInViewport({ ratio: 1 })
      await expect(selected).not.toBeInViewport({ ratio: 1 })
    }
  })
})

test.describe('выпадающий список (R011, R022)', () => {
  test('у нижнего края окна открывается вверх, целиком в окне, в портале', async ({ page }) => {
    await openDevUi(page)
    const input = page.getByRole('combobox', { name: 'Кресло у края' })
    // Поле — у самого низа окна: внизу места меньше, чем нужно списку.
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
    const inputBox = await input.boundingBox()
    expect(inputBox).not.toBeNull()
    expect((page.viewportSize()?.height ?? 0) - ((inputBox?.y ?? 0) + (inputBox?.height ?? 0))).toBeLessThan(150)

    await input.click()
    const popup = page.locator('[data-slot=combobox-content]')
    // Список — в портале, не внутри карточки витрины.
    await expect(await comboRows(page, page.locator('[data-demo-card]'))).toHaveCount(4)
    await expect(popup).toHaveAttribute('data-side', 'top')
    const popupBox = await popup.boundingBox()
    expect((popupBox?.y ?? 0) + (popupBox?.height ?? 0)).toBeLessThanOrEqual((inputBox?.y ?? 0) + 1)
    await expectInViewport(page, popup)
    await expectNotClipped(popup)
  })

  test('в прокручиваемой панели список не обрезается и открывается вверх', async ({ page }) => {
    // Низкое окно: тело панели прокручивается, поле оказывается у нижнего края.
    await page.setViewportSize({ width: page.viewportSize()?.width ?? 390, height: 560 })
    await openDevUi(page)
    await page.getByRole('button', { name: 'Открыть панель' }).click()
    const panel = page.getByRole('dialog', { name: 'Новая запись' })
    await panel.getByRole('button', { name: 'Далее' }).click()
    await expect(panel).toContainText('Шаг 2 из 2')

    const body = panel.locator('[data-slot=panel-body]')
    expect(await body.evaluate((element) => element.scrollHeight > element.clientHeight)).toBe(true)
    await body.evaluate((element) => element.scrollTo(0, element.scrollHeight))

    const input = panel.getByRole('combobox', { name: 'Кресло для записи' })
    await input.click()
    const popup = page.locator('[data-slot=combobox-content]')
    // Список — в портале, не внутри прокручиваемого тела панели.
    const rows = await comboRows(page, body)
    await expect(rows).toHaveCount(4)
    await expect(popup).toHaveAttribute('data-side', 'top')
    await expectInViewport(page, popup)
    await expectNotClipped(popup)

    await rows.filter({ hasText: 'Кресло 3 · ортодонтия' }).click()
    await expect(input).toHaveValue('Кресло 3 · ортодонтия')
    await expect(panel).toBeVisible()
  })
})

test.describe('панель и окно с шагами (R023)', () => {
  test('панель: «Назад», Escape и клик мимо — шаг назад; крестик закрывает всё', async ({ page }) => {
    await openDevUi(page)
    const opener = page.getByRole('button', { name: 'Открыть панель' })
    await opener.click()
    const panel = page.getByRole('dialog', { name: 'Новая запись' })
    await expect(panel).toHaveAttribute('data-panel-mode', isDesktop() ? 'side' : 'sheet')
    await expect(panel).toContainText('Шаг 1 из 2 · Пациент')
    // На первом шаге «Назад» нет — только крестик.
    await expect(panel.getByRole('button', { name: 'Назад' })).toHaveCount(0)

    const next = panel.getByRole('button', { name: 'Далее' })
    await next.click()
    await expect(panel).toContainText('Шаг 2 из 2 · Врач и время')

    await page.keyboard.press('Escape')
    await expect(panel).toContainText('Шаг 1 из 2')

    await next.click()
    await panel.getByRole('button', { name: 'Назад' }).click()
    await expect(panel).toContainText('Шаг 1 из 2')

    await next.click()
    await page.mouse.click(8, 8) // по затемнению вне панели
    await expect(panel).toBeVisible()
    await expect(panel).toContainText('Шаг 1 из 2')

    await next.click()
    await panel.getByRole('button', { name: 'Закрыть панель' }).click()
    await expect(panel).toBeHidden()
    await expect(opener).toBeFocused()

    // На первом шаге Escape закрывает панель.
    await opener.click()
    await expect(panel).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(panel).toBeHidden()
  })

  test('окно: Escape на втором шаге — назад, на первом — закрыть; одна жёлтая кнопка', async ({ page }) => {
    await openDevUi(page)
    await page.getByRole('button', { name: 'Открыть окно' }).click()
    const dialog = page.getByRole('dialog', { name: 'Перенести запись' })
    await expect(dialog).toContainText('Шаг 1 из 2')
    await expect(dialog.locator('[data-slot=button][data-variant=default]')).toHaveCount(1)

    await dialog.getByRole('button', { name: 'Далее' }).click()
    await expect(dialog).toContainText('Шаг 2 из 2')
    await page.keyboard.press('Escape')
    await expect(dialog).toContainText('Шаг 1 из 2')
    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
  })

  test('уведомление с «Вернуть» вместо окна подтверждения, в живой области', async ({ page }) => {
    await openDevUi(page)
    await page.getByRole('button', { name: 'Показать уведомление' }).click()
    const region = page.getByRole('region', { name: 'Уведомления' })
    const toast = region.locator('[data-slot=toast]')
    await expect(toast).toContainText('Запись отменена')
    await expect(toast.locator('[data-slot=toast-content]')).toBeInViewport({ ratio: 1 })
    await toast.getByRole('button', { name: 'Вернуть' }).click()
    await expect(toast).toHaveCount(0)
  })
})

test.describe('доступность (PLT-21)', () => {
  test('видимый фокус — контур 3 px «лагуна»; на тёмной поверхности — белый', async ({ page }) => {
    await openDevUi(page)
    const outline = (locator: Locator) =>
      locator.evaluate((element) => {
        const style = getComputedStyle(element)
        return `${style.outlineStyle} ${style.outlineWidth} ${style.outlineColor} ${style.outlineOffset}`
      })

    await page.keyboard.press('Tab')
    const record = page.getByRole('button', { name: 'Записать' })
    await expect(record).toBeFocused()
    // У кнопок transition-colors плавно меняет и цвет контура — ждём конечного значения.
    await expect.poll(() => outline(record)).toBe('solid 3px rgb(15, 61, 76) 2px')

    // Поле ввода: контур рисует пилюля целиком.
    const combobox = page.getByRole('combobox', { name: 'Специальность' })
    await combobox.focus()
    const group = combobox.locator('xpath=ancestor::*[@data-slot="input-group"][1]')
    await expect.poll(() => outline(group)).toBe('solid 3px rgb(15, 61, 76) 2px')

    // Кнопка в тёмной пилюле уведомления — белый контур (лагуна на «чернилах» не видна).
    const undo = page.getByRole('region', { name: 'Сообщения' }).getByRole('button', { name: 'Вернуть' })
    await undo.focus()
    await expect.poll(() => outline(undo)).toBe('solid 3px rgb(255, 255, 255) 2px')
  })

  test('prefers-reduced-motion: панель и уведомление появляются без анимации', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await openDevUi(page)
    const longest = (locator: Locator) =>
      locator.evaluate((element) => {
        const style = getComputedStyle(element)
        const seconds = [...style.transitionDuration.split(','), ...style.animationDuration.split(',')].map((value) =>
          parseFloat(value),
        )
        return Math.max(...seconds)
      })

    await page.getByRole('button', { name: 'Открыть панель' }).click()
    const panel = page.getByRole('dialog', { name: 'Новая запись' })
    await expect(panel).toBeVisible()
    expect(await longest(panel)).toBeLessThanOrEqual(0.001)
    await page.keyboard.press('Escape')
    await expect(panel).toBeHidden()

    await page.getByRole('button', { name: 'Показать уведомление' }).click()
    const toast = page.locator('[data-slot=toast]')
    await expect(toast).toBeVisible()
    expect(await longest(toast)).toBeLessThanOrEqual(0.001)
  })

  // Без исключений: color-contrast меряет настоящие пары текст/фон всех компонентов, включая
  // инициалы врачей на насыщенных цветах.
  test('axe: на витрине нет нарушений уровня serious и critical', async ({ page }) => {
    await openDevUi(page)
    expect(await seriousA11yViolations(page)).toEqual([])
  })
})
