import { useMediaQuery } from './useMediaQuery'

/**
 * Брейкпоинты «Кресла», mobile-first (T-PLT-10, PLT-18): разметка пишется для телефона, а
 * `md:` и `lg:` её расширяют.
 *
 * - телефон — до 768 px (эталон 390×844, минимум 360): нижняя панель, панели — нижним листом,
 *   таблицы — карточками;
 * - планшет — 768–1023 px (`md:`): сайдбар из иконок, панель — поверх контента справа;
 * - десктоп — от 1024 px (`lg:`): сайдбар с подписями, панель — колонкой рядом с контентом.
 *
 * `xl:` (1280 px) — не отдельный вид, а уточнение внутри десктопа (шире поиск в шапке).
 * Те же числа — `--breakpoint-*` в src/styles/index.css (совпадение сторожит
 * src/styles/breakpoints.test.ts); других брейкпоинтов (`sm:`, `2xl:`) нет.
 */
export const BREAKPOINTS = {
  /** Планшет: `md:`. */
  md: 768,
  /** Десктоп: `lg:`. */
  lg: 1024,
  /** Широкий десктоп: `xl:` (уточнение внутри десктопа). */
  xl: 1280,
} as const

/** `max-width` на долю пикселя меньше брейкпоинта: при дробном масштабе не бывает «ничьей». */
const below = (px: number) => `(max-width: ${px - 0.02}px)`
const from = (px: number) => `(min-width: ${px}px)`

/** Медиазапросы трёх видов для `useMediaQuery` (в разметке — `md:` / `lg:`). */
export const MEDIA = {
  /** Телефон: < 768 px. */
  phone: below(BREAKPOINTS.md),
  /** Планшет и шире: ≥ 768 px (`md:`). */
  tabletUp: from(BREAKPOINTS.md),
  /** Уже десктопа (телефон и планшет): < 1024 px. */
  belowDesktop: below(BREAKPOINTS.lg),
  /** Десктоп: ≥ 1024 px (`lg:`). */
  desktop: from(BREAKPOINTS.lg),
} as const

export type Viewport = 'phone' | 'tablet' | 'desktop'

/** Текущий вид по ширине окна. Для раскладки предпочитайте `md:`/`lg:` в классах. */
export function useViewport(): Viewport {
  const phone = useMediaQuery(MEDIA.phone)
  const desktop = useMediaQuery(MEDIA.desktop)
  return phone ? 'phone' : desktop ? 'desktop' : 'tablet'
}
