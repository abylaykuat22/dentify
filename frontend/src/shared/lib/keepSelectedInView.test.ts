import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { keepSelectedInView } from './keepSelectedInView'

// Полоса вкладок на телефоне (T-PLT-10): выбранная подъезжает в видимую область, но поздняя смена
// ширины (шрифт, язык) не отбрасывает полосу, которую пользователь уже прокрутил сам. В jsdom нет
// раскладки: геометрия — заглушки, ResizeObserver — ручной (вызываем его, как браузер после
// загрузки шрифта). Настоящую раскладку проверяет e2e/mobile.spec.ts.

/**
 * Полоса 150 px; в ней ряд пилюль с отступом 4 px и вкладками по 100 px: 4–104, 104–204, 204–304
 * (в координатах содержимого, ширина содержимого — 308).
 */
function makeStrip(selectedIndex: number, initialTabWidth = 100) {
  const strip = document.createElement('div')
  const row = document.createElement('div')
  strip.append(row)
  const tabs = [0, 1, 2].map((index) => {
    const tab = document.createElement('button')
    tab.setAttribute('role', 'tab')
    tab.setAttribute('aria-selected', String(index === selectedIndex))
    row.append(tab)
    return tab
  })

  let scrollLeft = 0
  /** Ширина вкладки: растёт, когда «догружается шрифт». */
  let tabWidth = initialTabWidth
  Object.defineProperty(strip, 'scrollLeft', {
    get: () => scrollLeft,
    set: (value: number) => {
      scrollLeft = Math.max(0, Math.min(value, tabWidth * 3 + 8 - 150))
    },
  })
  const rect = (left: number, width: number) => ({ left, right: left + width, width }) as DOMRect
  strip.getBoundingClientRect = () => rect(0, 150)
  tabs.forEach((tab, index) => {
    tab.getBoundingClientRect = () => rect(4 + index * tabWidth - scrollLeft, tabWidth)
  })
  document.body.append(strip)

  return {
    strip,
    tabs,
    widen(width: number) {
      tabWidth = width
    },
    /** Пользователь прокрутил полосу (палец или скрипт): позиция сменилась, пришло событие scroll. */
    userScrollTo(left: number) {
      strip.scrollLeft = left
      strip.dispatchEvent(new Event('scroll'))
    },
  }
}

let resize: (() => void)[] = []
const disconnect = vi.fn()

beforeEach(() => {
  resize = []
  vi.stubGlobal(
    'ResizeObserver',
    class {
      constructor(callback: () => void) {
        resize.push(callback)
      }
      observe() {}
      disconnect = disconnect
    },
  )
})

afterEach(() => {
  vi.unstubAllGlobals()
  disconnect.mockClear()
  document.body.innerHTML = ''
})

/** Ширина пилюль поменялась: браузер зовёт ResizeObserver. */
function fireResize() {
  for (const callback of resize) callback()
}

/** MutationObserver отвечает микрозадачей. */
const flush = () => new Promise((resolve) => setTimeout(resolve, 0))

describe('keepSelectedInView — выбранная вкладка в видимой области', () => {
  it('при появлении полосы выбранная подъезжает целиком (с запасом 4 px у края)', () => {
    const { strip } = makeStrip(2)
    keepSelectedInView(strip)
    expect(strip.scrollLeft).toBe(158)
  })

  it('пилюли стали шире (догрузился шрифт) — пока пользователь не трогал полосу, выбранная доводится', () => {
    const { strip, widen } = makeStrip(2)
    keepSelectedInView(strip)
    widen(110)
    fireResize()
    expect(strip.scrollLeft).toBe(188)
  })

  it('пока полосу не трогали, её место — от начала и не зависит от того, каким шрифтом её показали сначала', () => {
    // Сначала запасной шрифт (пилюли по 150 px): вторая вкладка — у правого края.
    const { strip, widen } = makeStrip(1, 150)
    keepSelectedInView(strip)
    expect(strip.scrollLeft).toBe(158)
    // Догрузился свой шрифт (по 100 px): выбранная и так видна, но место — как у того, кому шрифт
    // пришёл сразу: 104–204 от начала, у правого края 150 → 58 (а не «ближайшее» 100).
    widen(100)
    fireResize()
    expect(strip.scrollLeft).toBe(58)

    const fresh = makeStrip(1)
    keepSelectedInView(fresh.strip)
    expect(fresh.strip.scrollLeft).toBe(58)
  })

  it('пользователь прокрутил полосу — поздняя смена ширины её не отбрасывает', () => {
    const { strip, widen, userScrollTo } = makeStrip(2)
    keepSelectedInView(strip)
    userScrollTo(0)
    widen(110)
    fireResize()
    expect(strip.scrollLeft).toBe(0)
  })

  it.each(['pointerdown', 'touchstart', 'wheel', 'keydown'])('после %s полоса сама больше не едет', (type) => {
    const { strip, widen } = makeStrip(2)
    keepSelectedInView(strip)
    strip.dispatchEvent(new Event(type))
    strip.scrollLeft = 10
    widen(110)
    fireResize()
    expect(strip.scrollLeft).toBe(10)
  })

  it('своя прокрутка не считается пользовательской: событие scroll после неё не останавливает доводку', () => {
    const { strip, widen } = makeStrip(2)
    keepSelectedInView(strip)
    strip.dispatchEvent(new Event('scroll'))
    widen(110)
    fireResize()
    expect(strip.scrollLeft).toBe(188)
  })

  it('сменилась выбранная — показать её, даже после ручной прокрутки, и снова следить за шириной', async () => {
    const { strip, tabs, widen, userScrollTo } = makeStrip(2)
    keepSelectedInView(strip)
    userScrollTo(100)

    tabs[2]?.setAttribute('aria-selected', 'false')
    tabs[0]?.setAttribute('aria-selected', 'true')
    await flush()
    expect(strip.scrollLeft).toBe(0)

    // Доводка снова включена: полосу сдвинуло без пользователя — после смены ширины выбранная
    // снова целиком видна.
    strip.scrollLeft = 50
    widen(110)
    fireResize()
    expect(strip.scrollLeft).toBe(0)
  })

  it('вкладка-маршрут: выбранная — aria-current="page"', async () => {
    const { strip, tabs } = makeStrip(-1)
    keepSelectedInView(strip)
    expect(strip.scrollLeft).toBe(0)
    tabs[1]?.setAttribute('aria-current', 'page')
    await flush()
    expect(strip.scrollLeft).toBe(58)
  })

  it('внешний сигнал смены (onRendered роутера) доводит выбранную после чужой прокрутки; очистка отписывает', () => {
    const { strip, userScrollTo } = makeStrip(2)
    let select: (() => void) | undefined
    const unsubscribe = vi.fn()
    const cleanup = keepSelectedInView(strip, (callback) => {
      select = callback
      return unsubscribe
    })

    // Роутер вернул полосе позицию со старого адреса — для полосы это «чужая» прокрутка.
    userScrollTo(0)
    select?.()
    expect(strip.scrollLeft).toBe(158)

    cleanup?.()
    expect(unsubscribe).toHaveBeenCalledTimes(1)
  })

  it('очистка снимает наблюдателей и слушателей', async () => {
    const { strip, tabs } = makeStrip(0)
    const cleanup = keepSelectedInView(strip)
    cleanup?.()
    expect(disconnect).toHaveBeenCalled()

    tabs[0]?.setAttribute('aria-selected', 'false')
    tabs[2]?.setAttribute('aria-selected', 'true')
    await flush()
    expect(strip.scrollLeft).toBe(0)
  })

  it('без узла — ничего', () => {
    expect(keepSelectedInView(null)).toBeUndefined()
  })
})
