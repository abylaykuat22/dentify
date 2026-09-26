import { describe, expect, it } from 'vitest'

import { APPOINTMENT_STATUSES, DOCTOR_COLORS } from '@/shared/ui/tokens'

// Текст самой темы: vite.config.ts открывает тестам только этот CSS-файл (как строку).
import css from './index.css?raw'
import { blend, contrastRatio, parseColorTokens } from './theme'

// PLT-21: контраст текста ≥ 4.5:1 (крупного — ≥ 3:1). Значения берутся из самого CSS
// (`@theme` в index.css), а не копией: поменяли токен — тест пересчитал.
const tokens = parseColorTokens(css)
const color = (name: string) => {
  const value = tokens.get(name)
  if (!value) throw new Error(`Нет токена --color-${name}`)
  return value
}

/** Фон `bg-<name>/<alpha>` поверх другого токена — как его видит глаз («туман» под белым 10 %). */
const overlay = (name: string, alpha: number, under: string) => blend(color(name), alpha, color(under))

interface Pair {
  /** Токен цвета текста. */
  fg: string
  /** Токен фона или готовый `#rrggbb` (полупрозрачная заливка поверх токена — `overlay`). */
  bg: string
  /** Где встречается — для понятного сообщения об ошибке. */
  where: string
}

const resolve = (value: string) => (value.startsWith('#') ? value : color(value))

/**
 * Пары текст/фон, которые дают компоненты «Кресла». Новый текстовый токен или новая пара в
 * компоненте — сюда. Список ведётся руками, поэтому страховка — axe `color-contrast` без
 * исключений в e2e (dev-ui.spec.ts — витрина, a11y.spec.ts — экраны каркаса): он меряет
 * настоящие пары на отрисованных страницах.
 */
const TEXT_PAIRS: Pair[] = [
  // Основной текст и подписи на всех фонах
  ...['white', 'fog', 'mist', 'highlight', 'match', 'past', 'lamp'].map((bg) => ({
    fg: 'ink',
    bg,
    where: 'текст «чернила»',
  })),
  ...['white', 'fog', 'mist', 'highlight', 'past'].map((bg) => ({ fg: 'graphite', bg, where: 'подписи, плейсхолдер' })),
  ...['white', 'fog', 'mist'].map((bg) => ({ fg: 'slate', bg, where: 'метки полей' })),
  { fg: 'lagoon', bg: 'white', where: 'ссылка при наведении' },
  // Выбранное состояние, тёмные поверхности
  { fg: 'white', bg: 'ink', where: 'выбранный чип, вкладка, уведомление' },
  { fg: 'white', bg: overlay('ink', 0.9, 'white'), where: 'выбранная кнопка при наведении (ink 90 %)' },
  { fg: 'lamp', bg: 'ink', where: 'иконка и кнопка в уведомлении' },
  { fg: 'lagoon-muted', bg: 'ink', where: 'пояснение в уведомлении' },
  { fg: 'white', bg: 'lagoon', where: 'сайдбар' },
  { fg: 'lagoon-muted', bg: 'lagoon', where: 'пункты меню сайдбара' },
  // Сайдбар: наведение на пункт (белый 10 %), карточка «Мой профиль» (10 / 15 / 20 %).
  ...[0.1, 0.15, 0.2].flatMap((alpha) =>
    ['white', 'lagoon-muted'].map((fg) => ({
      fg,
      bg: overlay('white', alpha, 'lagoon'),
      where: `сайдбар: пункт и профиль на белом ${alpha * 100} %`,
    })),
  ),
  { fg: 'lamp', bg: 'lagoon', where: 'активный пункт нижней панели' },
  { fg: 'ink', bg: 'lamp', where: 'активный пункт сайдбара, жёлтая кнопка' },
  { fg: 'white', bg: 'graphite', where: 'аватар не-врача' },
  // Опасное
  { fg: 'danger', bg: 'white', where: 'красная текстовая кнопка, «Не пришёл»' },
  { fg: 'danger', bg: 'danger-bg', where: 'плашка аллергии' },
  { fg: 'white', bg: 'danger', where: 'красная заливка' },
  { fg: 'white', bg: overlay('danger', 0.9, 'white'), where: 'красная заливка при наведении (90 %)' },
  // Цвета врачей: текст записи на фоне записи
  ...DOCTOR_COLORS.map((doctor) => ({
    fg: `doctor-${doctor}-fg`,
    bg: `doctor-${doctor}`,
    where: `запись, мягкий аватар, метка (${doctor})`,
  })),
  // Инициалы врача: белые на насыщенном цвете (Rubik 600, 12.5–22 px — обычный, не крупный
  // текст). Порог 4.5:1 — и для подписанного аватара (role="img"), и для скрытого от диктора:
  // WCAG 1.4.3 от aria-hidden не освобождает. Бирюза, коралл и песок ради этого чуть темнее
  // макета (design-directions § Токены).
  ...DOCTOR_COLORS.map((doctor) => ({
    fg: 'white',
    bg: `doctor-${doctor}-strong`,
    where: `инициалы врача (${doctor})`,
  })),
  // Статусы: слово на «тумане» (карточка) и на белом (статус на цветной записи)
  ...['fog', 'white'].flatMap((bg) =>
    ['status-planned-fg', 'status-arrived-fg', 'status-in-chair-fg', 'status-done-fg', 'status-cancelled-fg'].map(
      (fg) => ({ fg, bg, where: 'статус записи' }),
    ),
  ),
  { fg: 'status-arrived-fg', bg: 'highlight', where: 'плашка-предупреждение' },
]

/** Нетекстовая графика (WCAG 1.4.11, ≥ 3:1): контур фокуса на всех поверхностях. */
const GRAPHIC_PAIRS: Pair[] = [
  { fg: 'lagoon', bg: 'white', where: 'контур фокуса на карточке' },
  { fg: 'lagoon', bg: 'fog', where: 'контур фокуса на фоне' },
  { fg: 'white', bg: 'lagoon', where: 'контур фокуса в сайдбаре' },
  { fg: 'white', bg: 'ink', where: 'контур фокуса в уведомлении' },
]

const describePair = ({ fg, bg, where }: Pair) => `${fg} на ${bg} (${where})`

describe('контраст токенов «Кресла» (WCAG 2.2 AA)', () => {
  it.each(TEXT_PAIRS.map((pair) => [describePair(pair), pair] as const))('текст: %s ≥ 4.5:1', (_, pair) => {
    expect(contrastRatio(color(pair.fg), resolve(pair.bg))).toBeGreaterThanOrEqual(4.5)
  })

  it.each(GRAPHIC_PAIRS.map((pair) => [describePair(pair), pair] as const))('графика: %s ≥ 3:1', (_, pair) => {
    expect(contrastRatio(color(pair.fg), resolve(pair.bg))).toBeGreaterThanOrEqual(3)
  })

  it('каждый текстовый токен (`*-fg`) проверен хотя бы в одной паре', () => {
    const textTokens = [...tokens.keys()].filter((name) => name.endsWith('-fg'))
    const covered = new Set(TEXT_PAIRS.map((pair) => pair.fg))
    expect(textTokens.filter((name) => !covered.has(name))).toEqual([])
  })

  it('у каждого статуса записи есть цвет слова', () => {
    // Статусы из tokens.ts ↔ токены: «Не пришёл» — danger, остальные — свой *-fg.
    const withOwnColor = APPOINTMENT_STATUSES.filter((status) => status !== 'noShow').map(
      (status) => `status-${status.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)}-fg`,
    )
    for (const token of withOwnColor) expect(tokens.has(token), token).toBe(true)
  })

  it('расчёт контраста совпадает с эталоном WCAG', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 5)
    expect(contrastRatio('#ffffff', '#ffffff')).toBeCloseTo(1, 5)
    expect(contrastRatio('#767676', '#ffffff')).toBeCloseTo(4.54, 2)
  })

  it('полупрозрачная заливка смешивается с фоном, как в браузере', () => {
    expect(blend('#ffffff', 0.5, '#000000')).toBe('#808080')
    expect(blend('#ffffff', 0.1, '#0f3d4c')).toBe('#27505e')
    expect(blend('#0e2230', 1, '#ffffff')).toBe('#0e2230')
  })
})
