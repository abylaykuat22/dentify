import { describe, expect, it } from 'vitest'

import { BREAKPOINTS, MEDIA } from '@/shared/lib/breakpoints'

import css from './index.css?raw'

// Брейкпоинты mobile-first (T-PLT-10): телефон < 768 · планшет md: 768–1023 · десктоп lg: ≥ 1024
// (+ xl: 1280 — уточнение внутри десктопа). Один источник чисел — и в CSS, и в JS.

// Все исходники приложения и витрины как текст: ключ — «/src/app/shell/Header.tsx».
const RAW = import.meta.glob<string>(['/src/**/*.{ts,tsx}', '!**/*.test.{ts,tsx}', '!/src/shared/api/generated/**'], {
  query: '?raw',
  import: 'default',
  eager: true,
})

describe('брейкпоинты «Кресла»', () => {
  it('в теме только md 768, lg 1024 и xl 1280 (px) — стандартные sm и 2xl выключены', () => {
    expect(css).toMatch(/--breakpoint-\*:\s*initial;/)
    const declared = Object.fromEntries(
      [...css.matchAll(/--breakpoint-([a-z0-9]+):\s*(\d+)px;/g)].map((match) => [match[1], Number(match[2])]),
    )
    expect(declared).toEqual(BREAKPOINTS)
  })

  it('MEDIA для useMediaQuery — те же числа', () => {
    expect(MEDIA.phone).toBe(`(max-width: ${BREAKPOINTS.md - 0.02}px)`)
    expect(MEDIA.tabletUp).toBe(`(min-width: ${BREAKPOINTS.md}px)`)
    expect(MEDIA.belowDesktop).toBe(`(max-width: ${BREAKPOINTS.lg - 0.02}px)`)
    expect(MEDIA.desktop).toBe(`(min-width: ${BREAKPOINTS.lg}px)`)
  })

  it('в разметке нет sm: и 2xl: — такой класс молча не сгенерировался бы', () => {
    const offenders: string[] = []
    for (const [file, text] of Object.entries(RAW)) {
      for (const match of text.matchAll(/(?<![\w-])(?:max-)?(?:sm|2xl):[\w[\]-]+/g)) {
        offenders.push(`${file}: ${match[0]}`)
      }
    }
    expect(offenders).toEqual([])
  })

  it('медиазапросы в коде — только через MEDIA (src/shared/lib/breakpoints.ts)', () => {
    const offenders = Object.entries(RAW)
      .filter(([file, text]) => file !== '/src/shared/lib/breakpoints.ts' && /\((?:min|max)-width:/.test(text))
      .map(([file]) => file)
    expect(offenders).toEqual([])
  })
})
