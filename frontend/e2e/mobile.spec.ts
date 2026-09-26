import { expect, type Page, test } from '@playwright/test'

import { emulateSafeArea, hitArea, horizontalOverflow, isDesktop, labelBoxes, swipe, usePersona } from './helpers'

// Телефон 390×844 (T-PLT-10, PLT-18): нижняя панель по макету Telefon, меню «Ещё» по макету
// Telefon-Eshche, панель — нижним листом, safe-area iOS, клавиатура и цели касания.

function bottomNav(page: Page) {
  return page.getByRole('navigation', { name: 'Главное меню' })
}

test.describe('телефон 390×844: нижняя панель', () => {
  test.skip(() => isDesktop(), 'только мобильный проект')

  test('каркас без горизонтальной прокрутки, сайдбар скрыт, нижняя панель по макету Telefon', async ({ page }) => {
    await page.goto('/schedule')

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Расписание')
    await expect(page.locator('[data-sidebar]')).toBeHidden()
    const nav = bottomNav(page)
    await expect(nav).toBeVisible()
    await expect(nav.getByRole('link')).toHaveText(['Расписание', 'Пациенты', '', 'Склад', 'Ещё'])
    await expect(nav.getByRole('link', { name: 'Расписание' })).toHaveAttribute('aria-current', 'page')
    expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0)

    // Геометрия макета: плавающая панель 12 px от краёв, 70 px высотой; «+» 60 px выступает над ней.
    const bar = await nav.boundingBox()
    expect(bar).toEqual({ x: 12, y: 844 - 12 - 70, width: 390 - 24, height: 70 })
    const plus = await nav.getByRole('link', { name: 'Новая запись' }).boundingBox()
    expect(plus?.width).toBe(60)
    expect(plus?.height).toBe(60)
    expect((plus?.y ?? 0) + 60 / 2).toBeLessThan((bar?.y ?? 0) + 70 / 2)
  })

  test('все пункты нижней панели — цели касания не меньше 44×44', async ({ page }) => {
    await page.goto('/schedule')
    const items = bottomNav(page).getByRole('link')
    await expect(items).toHaveCount(5)
    for (const item of await items.all()) {
      const area = await hitArea(item)
      expect(area.width, await item.innerText()).toBeGreaterThanOrEqual(44)
      expect(area.height, await item.innerText()).toBeGreaterThanOrEqual(44)
    }
  })

  for (const width of [360, 375, 390]) {
    test(`${width} px: каждая подпись панели умещается в свою ячейку и не слипается с соседней`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 })
      // Выбранный пункт — жирнее (800): самая широкая подпись — «Расписание» на расписании,
      // «Пациенты» — на пациентах.
      for (const path of ['/schedule', '/patients']) {
        await page.goto(path)
        await expect(bottomNav(page)).toBeVisible()
        await page.evaluate(() => document.fonts.ready)

        const labels = await labelBoxes(bottomNav(page).locator('[data-bottom-item]:not([data-bottom-item=create])'))
        expect(labels.map((item) => item.label)).toEqual(['Расписание', 'Пациенты', 'Склад', 'Ещё'])
        for (const { label, cell, text } of labels) {
          expect(text.left, `${path} ${label}: слева`).toBeGreaterThanOrEqual(cell.left)
          expect(text.right, `${path} ${label}: справа`).toBeLessThanOrEqual(cell.right)
        }
        // Соседние подписи — с просветом (у макета на 390 — около 9 px).
        const [schedule, patients] = labels
        expect((patients?.text.left ?? 0) - (schedule?.text.right ?? 0), path).toBeGreaterThanOrEqual(6)
      }
      expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0)
    })
  }

  test('«+» ведёт на новую запись (заглушка мастера), «Назад» возвращает', async ({ page }) => {
    await page.goto('/schedule')
    await bottomNav(page).getByRole('link', { name: 'Новая запись' }).click()

    await expect(page).toHaveURL(/\/schedule\/new$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Новая запись')
    await expect(page).toHaveTitle('Новая запись — Dentify')
    // Текущий пункт панели один — раздел «Расписание»; «+» — действие, не «текущая страница».
    await expect(bottomNav(page).locator('[aria-current]')).toHaveCount(1)
    await expect(bottomNav(page).getByRole('link', { name: 'Расписание' })).toHaveAttribute('aria-current', 'page')
    await expect(page.getByRole('navigation', { name: 'Вкладки раздела' })).toHaveCount(0)
    await page.goBack()
    await expect(page).toHaveURL(/\/schedule$/)
  })

  test('пункты по правам: ассистент видит склад, врач двух клиник — нет', async ({ page, context }) => {
    await usePersona(context, 'multi')
    await page.goto('/select-clinic')
    await page.getByRole('button', { name: /Ақ Тіс/ }).click()
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Расписание')

    await expect(bottomNav(page).getByRole('link')).toHaveText(['Расписание', 'Пациенты', '', 'Ещё'])
    // Место «Склада» пустое: «Ещё» остаётся справа, «+» — по центру.
    const more = await bottomNav(page).getByRole('link', { name: 'Ещё' }).boundingBox()
    expect(Math.round((more?.x ?? 0) + (more?.width ?? 0))).toBe(390 - 12 - 6)
  })

  test('safe-area iOS: панель над полоской «домой», шапка под вырезом, контент не прячется под панель', async ({
    page,
  }) => {
    await page.goto('/schedule')
    await emulateSafeArea(page, { top: 47, bottom: 34 })

    await expect.poll(async () => 844 - ((await bottomNav(page).boundingBox())?.y ?? 0) - 70).toBe(34)
    const heading = await page.getByRole('heading', { level: 1 }).boundingBox()
    expect(heading?.y ?? 0).toBeGreaterThanOrEqual(47)
    // Низ контента при прокрутке до конца — выше панели.
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
    const content = await page.locator('main').boundingBox()
    const bar = await bottomNav(page).boundingBox()
    expect((content?.y ?? 0) + (content?.height ?? 0)).toBeLessThanOrEqual((bar?.y ?? 0) - 10)
  })

  test('экранная клавиатура: пока фокус в поле, нижняя панель спрятана', async ({ page }) => {
    await page.goto('/schedule')
    await page.getByRole('button', { name: 'Найти пациента (Ctrl K)' }).click()
    const field = page.getByRole('dialog', { name: 'Поиск пациента' }).getByRole('searchbox')
    await expect(field).toBeFocused()
    await expect(bottomNav(page)).toBeHidden()

    await page.keyboard.press('Escape')
    await expect(bottomNav(page)).toBeVisible()

    // Клавиатура уменьшает окно (а не перекрывает его): лист с полем поднимается над ней.
    await expect(page.locator('meta[name=viewport]')).toHaveAttribute('content', /interactive-widget=resizes-content/)
  })

  test('поля — шрифт 16 px (без увеличения страницы в iOS)', async ({ page }) => {
    await page.goto('/schedule?panel=search')
    const field = page.getByRole('dialog', { name: 'Поиск пациента' }).getByRole('searchbox')
    await expect(field).toHaveCSS('font-size', '16px')
  })
})

test.describe('телефон 390×844: меню «Ещё» (Telefon-Eshche)', () => {
  test.skip(() => isDesktop(), 'только мобильный проект')

  test('пользователь, филиал, разделы с подписями, «Выйти»; «Ещё» подсвечен', async ({ page }) => {
    await page.goto('/schedule')
    await bottomNav(page).getByRole('link', { name: 'Ещё' }).click()

    await expect(page).toHaveURL(/\/more$/)
    await expect(page).toHaveTitle('Ещё — Dentify')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Алиева Динара')
    await expect(page.getByText('Владелец · Ақ Тіс')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Филиал: Абая, 150. Сменить филиал' })).toBeVisible()
    const sections = page.getByRole('navigation', { name: 'Другие разделы' })
    await expect(sections.getByRole('link')).toHaveText([
      'ВрачиПрофили и графики работы',
      'СкладОстатки, приход, поставщики',
      'КлиникаФилиалы, кресла, прайс',
      'ДоступыСотрудники, роли, приглашения',
      'Мой профильТелефон, пароль, сеансы',
    ])
    await expect(bottomNav(page).getByRole('link', { name: 'Ещё' })).toHaveAttribute('aria-current', 'page')
    // Язык скрыт флагом i18n.kk (R003).
    await expect(page.getByText('Язык', { exact: true })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Выйти' })).toBeVisible()
    expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0)
  })

  test('все элементы меню «Ещё» — цели касания не меньше 44×44', async ({ page }) => {
    await page.goto('/more')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    const targets = page.locator('main').locator('a, button')
    expect(await targets.count()).toBe(7)
    for (const target of await targets.all()) {
      const area = await hitArea(target)
      const name = (await target.getAttribute('aria-label')) ?? (await target.innerText())
      expect(area.width, name).toBeGreaterThanOrEqual(44)
      expect(area.height, name).toBeGreaterThanOrEqual(44)
    }
  })

  test('только доступные разделы: ассистенту нет «Клиники» и «Доступов»', async ({ page, context }) => {
    await usePersona(context, 'assistant')
    await page.goto('/more')
    await expect(page.getByRole('navigation', { name: 'Другие разделы' }).getByRole('link')).toHaveText([
      /^Врачи/,
      /^Склад/,
      /^Мой профиль/,
    ])
  })

  test('пункт «Врачи» открывает раздел, «Ещё» остаётся подсвеченным', async ({ page }) => {
    await page.goto('/more')
    await page.getByRole('link', { name: /^Врачи/ }).click()
    await expect(page).toHaveURL(/\/staff$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Врачи')
    await expect(bottomNav(page).getByRole('link', { name: 'Ещё' })).toHaveAttribute('aria-current', 'page')
  })

  test('смена филиала — заглушка с уведомлением', async ({ page }) => {
    await page.goto('/more')
    await page.getByRole('button', { name: 'Филиал: Абая, 150. Сменить филиал' }).click()
    await expect(page.getByText('Смена филиала появится вместе с выбором клиники и филиала')).toBeVisible()
    await expect(page).toHaveURL(/\/more$/)
  })

  test('«Выйти» завершает сессию: вход, а назад в каркас без входа не пускает', async ({ page }) => {
    await page.goto('/more')
    await page.getByRole('button', { name: 'Выйти' }).click()

    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByRole('heading', { level: 1, name: 'Вход' })).toBeVisible()
    await page.goto('/more')
    await expect(page).toHaveURL(/\/login\?returnTo=/)
  })
})

test.describe('телефон 390×844: панель — нижний лист (Telefon-Zapis)', () => {
  test.skip(() => isDesktop(), 'только мобильный проект')

  test('панель поиска — нижний лист поверх контента, закрывается «Назад»', async ({ page }) => {
    await page.goto('/schedule')

    await page.getByRole('button', { name: 'Найти пациента (Ctrl K)' }).click()
    const panel = page.getByRole('dialog', { name: 'Поиск пациента' })
    await expect(panel).toBeVisible()
    await expect(panel).toHaveAttribute('data-panel-mode', 'sheet')
    await page.goBack()
    await expect(panel).toBeHidden()
    await expect(page).toHaveURL(/\/schedule$/)
  })

  test('смахивание вниз закрывает лист (и убирает ?panel из адреса)', async ({ page }) => {
    await page.goto('/schedule?panel=search')
    const panel = page.getByRole('dialog', { name: 'Поиск пациента' })
    await expect(panel).toBeVisible()
    const header = await panel.locator('[data-slot=panel-header]').boundingBox()
    await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 500)))

    await swipe(page, { x: 195, y: (header?.y ?? 600) + 20 }, { dy: 400 })

    await expect(panel).toBeHidden()
    await expect(page).toHaveURL(/\/schedule$/)
  })

  test('короткое смахивание не закрывает — лист возвращается на место', async ({ page }) => {
    await page.goto('/schedule?panel=search')
    const panel = page.getByRole('dialog', { name: 'Поиск пациента' })
    await expect(panel).toBeVisible()
    const header = await panel.locator('[data-slot=panel-header]').boundingBox()
    await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 500)))

    await swipe(page, { x: 195, y: (header?.y ?? 600) + 20 }, { dy: 12 }, 3)

    await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 600)))
    await expect(panel).toBeVisible()
    await expect(page).toHaveURL(/panel=search/)
  })

  test('крестик закрывает лист; цель касания крестика не меньше 44×44', async ({ page }) => {
    await page.goto('/schedule?panel=search')
    const close = page.getByRole('dialog', { name: 'Поиск пациента' }).getByRole('button', { name: 'Закрыть панель' })
    const area = await hitArea(close)
    expect(area.width).toBeGreaterThanOrEqual(44)
    expect(area.height).toBeGreaterThanOrEqual(44)

    await close.click()
    await expect(page.getByRole('dialog', { name: 'Поиск пациента' })).toBeHidden()
    await expect(page).toHaveURL(/\/schedule$/)
  })

  test('модальная панель держит фокус внутри и возвращает его на кнопку поиска', async ({ page }) => {
    await page.goto('/schedule')

    const trigger = page.getByRole('button', { name: 'Найти пациента (Ctrl K)' })
    await trigger.click()
    const panel = page.getByRole('dialog', { name: 'Поиск пациента' })
    await expect(panel.getByRole('searchbox')).toBeFocused()

    // Tab не уходит из панели: поле → крестик → снова внутри панели.
    for (let i = 0; i < 4; i++) {
      await page.keyboard.press('Tab')
      expect(await panel.evaluate((node) => node.contains(document.activeElement))).toBe(true)
    }

    await page.keyboard.press('Escape')
    await expect(panel).toBeHidden()
    await expect(trigger).toBeFocused()
  })

  test('главное действие раздела — белый круг 48 px рядом с поиском; жёлтый — только «+» панели', async ({
    page,
  }) => {
    for (const [path, name] of [
      ['/patients', 'Новый пациент'],
      ['/inventory', 'Новый документ'],
      ['/staff', 'Добавить сотрудника'],
      ['/access', 'Пригласить'],
    ] as const) {
      await page.goto(path)
      const action = page.getByRole('button', { name, exact: true })
      await expect(action).toBeVisible()
      const box = (await action.boundingBox())!
      expect(Math.round(box.width)).toBe(48)
      expect(Math.round(box.height)).toBe(48)
      // Рядом с поиском, в той же строке шапки.
      const search = (await page.getByRole('button', { name: 'Найти пациента (Ctrl K)' }).boundingBox())!
      expect(Math.abs(box.y - search.y)).toBeLessThan(2)
      expect(box.x).toBeGreaterThan(search.x)
      expect(await horizontalOverflow(page)).toBe(0)
      // Белый, как поиск, значок «лагуной»: жёлтый круг на экране один — «+» нижней панели.
      await expect(action).not.toHaveAttribute('data-variant', 'default')
      await expect(action).toHaveCSS('background-color', 'rgb(255, 255, 255)')
      await expect(action.locator('svg:visible')).toHaveCSS('color', 'rgb(15, 61, 76)')
      await expect(bottomNav(page).getByRole('link', { name: 'Новая запись' })).toHaveCSS(
        'background-color',
        'rgb(255, 203, 71)',
      )
    }

    await page.goto('/patients')
    await page.getByRole('button', { name: 'Новый пациент', exact: true }).click()
    await expect(page.getByText('Экран «Новый пациент» пока в разработке')).toBeVisible()
    await expect(page).toHaveURL(/\/patients$/)

    await page.goto('/schedule')
    await expect(page.locator('[data-primary-action]')).toBeHidden()
    await expect(bottomNav(page).getByRole('link', { name: 'Новая запись' })).toBeVisible()
  })

  test('поиск в шапке — круглая кнопка без «Ctrl K», как в макете Telefon', async ({ page }) => {
    await page.goto('/schedule')

    const trigger = page.getByRole('button', { name: 'Найти пациента (Ctrl K)' })
    await expect(trigger).toBeVisible()
    await expect(trigger.locator('kbd')).toBeHidden()
    const box = await trigger.boundingBox()
    expect(Math.round(box?.width ?? 0)).toBe(Math.round(box?.height ?? -1))
  })

  test('вкладки не влезают — прокручиваются вбок, активная видна, полосы прокрутки нет', async ({ page }) => {
    await page.goto('/clinic/settings')

    const tabs = page.getByRole('navigation', { name: 'Вкладки раздела' })
    const active = tabs.getByRole('link', { name: 'Настройки' })
    await expect(active).toHaveAttribute('aria-current', 'page')
    await expect(active).toBeInViewport({ ratio: 1 })
    expect(await tabs.evaluate((node: HTMLElement) => node.scrollWidth > node.clientWidth)).toBe(true)
    const scrollbar = await tabs.evaluate((node: HTMLElement) => node.offsetHeight - node.clientHeight)
    expect(scrollbar).toBe(0)
    expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0)

    // Первая вкладка — за левым краем полосы; прокрутка вбок её показывает.
    await page.evaluate(() => document.fonts.ready)
    await tabs.evaluate((node: HTMLElement) => node.scrollTo({ left: 0 }))
    await expect(tabs.getByRole('link', { name: 'Профиль' })).toBeInViewport({ ratio: 1 })
  })

  test('полосу прокрутили пальцем — поздняя смена ширины вкладок (шрифт, язык) её не отбрасывает', async ({
    page,
  }) => {
    await page.goto('/clinic/settings')
    const tabs = page.getByRole('navigation', { name: 'Вкладки раздела' })
    await expect(tabs.getByRole('link', { name: 'Настройки' })).toBeInViewport({ ratio: 1 })
    await page.evaluate(() => document.fonts.ready)

    const box = await tabs.boundingBox()
    await swipe(page, { x: 40, y: (box?.y ?? 0) + (box?.height ?? 0) / 2 }, { dx: 320 })
    await expect.poll(() => tabs.evaluate((node: HTMLElement) => node.scrollLeft)).toBe(0)

    // Как если бы догрузилось начертание шрифта: пилюли стали шире, полоса поменяла размер.
    await tabs.evaluate((node: HTMLElement) => {
      for (const link of Array.from(node.querySelectorAll('a'))) link.style.letterSpacing = '0.04em'
    })
    await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))))
    expect(await tabs.evaluate((node: HTMLElement) => node.scrollLeft)).toBe(0)
    await expect(tabs.getByRole('link', { name: 'Профиль' })).toBeInViewport({ ratio: 1 })

    // Нажали другую вкладку — выбранная снова подъезжает сама.
    await tabs.getByRole('link', { name: 'Прайс' }).click()
    await expect(page).toHaveURL(/\/clinic\/prices$/)
    await expect(tabs.getByRole('link', { name: 'Прайс' })).toBeInViewport({ ratio: 1 })
  })

  test('пока полосу не трогали, смена ширины вкладок доводит выбранную обратно в видимую область', async ({
    page,
  }) => {
    await page.goto('/clinic/settings')
    const tabs = page.getByRole('navigation', { name: 'Вкладки раздела' })
    const active = tabs.getByRole('link', { name: 'Настройки' })
    await expect(active).toBeInViewport({ ratio: 1 })
    await page.evaluate(() => document.fonts.ready)

    await tabs.evaluate((node: HTMLElement) => {
      for (const link of Array.from(node.querySelectorAll('a'))) link.style.letterSpacing = '0.08em'
    })
    await expect(active).toBeInViewport({ ratio: 1 })
  })
})

test.describe('планшет и десктоп: нижней панели нет', () => {
  test.skip(() => !isDesktop(), 'десктопный проект')

  for (const width of [768, 1024, 1440]) {
    test(`${width} px: сайдбар вместо нижней панели; «Ещё» открывается и без неё`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 })
      await page.goto('/more')
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Алиева Динара')
      await expect(bottomNav(page)).toBeHidden()
      await expect(page.locator('[data-sidebar]')).toBeVisible()
      expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0)
    })
  }
})
