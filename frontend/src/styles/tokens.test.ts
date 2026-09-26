import { describe, expect, it } from 'vitest'

import { cn, THEME_SCALES } from '@/shared/lib/utils'

// Текст самой темы: vite.config.ts открывает тестам только этот CSS-файл (как строку).
import css from './index.css?raw'
import { parseColorTokens, themeNames } from './theme'

// Значения — из wiki/projects/dentify/design-directions.md (§ Токены); текст статусов и
// контур «Не пришёл» — из макета Sistema. Тест сторожит, чтобы тема не разошлась с ними.
const EXPECTED_COLORS: Record<string, string> = {
  lagoon: '#0f3d4c',
  lamp: '#ffcb47',
  'lamp-deep': '#f2b51e',
  ink: '#0e2230',
  graphite: '#5a6772',
  slate: '#4f5b66',
  fog: '#eceff3',
  mist: '#f5f7f9',
  white: '#ffffff',
  line: '#e1e6ec',
  danger: '#9b1c12',
  'danger-bg': '#fde2e0',
  highlight: '#fff4d1',
  match: '#ffe9a8',
  past: '#f0f2f5',
  'doctor-teal': '#d3eeee',
  'doctor-teal-fg': '#0b4a4f',
  'doctor-teal-strong': '#1a8388',
  'doctor-coral': '#fce0da',
  'doctor-coral-fg': '#7a2a1e',
  'doctor-coral-strong': '#ca4d3a',
  'doctor-lilac': '#e5e1fb',
  'doctor-lilac-fg': '#3d3394',
  'doctor-lilac-strong': '#5b4fd1',
  'doctor-sky': '#ddebfb',
  'doctor-sky-fg': '#1e4f8f',
  'doctor-sky-strong': '#2f6fd1',
  'doctor-sand': '#f6eacb',
  'doctor-sand-fg': '#6b4a0b',
  'doctor-sand-strong': '#a16a1b',
  'doctor-rose': '#f9ddea',
  'doctor-rose-fg': '#7b1f4b',
  'doctor-rose-strong': '#b83280',
  'status-planned': '#3a6fd8',
  'status-arrived': '#f2b51e',
  'status-in-chair': '#16a064',
  // Состояния зуба — цвета врачей: кариес — песок, пломба — небо, каналы — роза,
  // коронка — сирень, имплант — бирюза, удалён — пунктир #B8C2CC.
  'tooth-caries': '#f6eacb',
  'tooth-filling': '#ddebfb',
  'tooth-canals': '#f9ddea',
  'tooth-crown': '#e5e1fb',
  'tooth-implant': '#d3eeee',
  'tooth-removed-line': '#b8c2cc',
}

const EXPECTED_RADII: Record<string, string> = {
  pill: '999px',
  window: '28px',
  card: '24px',
  block: '20px',
  tile: '18px',
  item: '14px',
}

const TSHIRT = /^(\d?x?s|sm|md|lg|\d?xl|full|none)$/

describe('токены «Кресла» в @theme', () => {
  const colors = parseColorTokens(css)

  it.each(Object.entries(EXPECTED_COLORS))('--color-%s = %s', (name, value) => {
    expect(colors.get(name)).toBe(value)
  })

  it.each(Object.entries(EXPECTED_RADII))('--radius-%s = %s', (name, value) => {
    expect(css).toMatch(new RegExp(`--radius-${name}:\\s*${value};`))
  })

  it('палитра Tailwind по умолчанию выключена — в разметке доступны только цвета «Кресла»', () => {
    expect(css).toMatch(/--color-\*:\s*initial;/)
  })

  it('шрифты — Rubik (заголовки) и Nunito Sans (текст)', () => {
    expect(css).toMatch(/--font-heading:\s*"Rubik"/)
    expect(css).toMatch(/--font-sans:\s*"Nunito Sans"/)
  })

  it('фокус — контур 3 px «лагуна», reduced motion учитывается', () => {
    expect(css).toMatch(/--focus-color:\s*var\(--color-lagoon\)/)
    expect(css).toMatch(/:focus-visible\s*\{\s*outline:\s*3px solid var\(--focus-color\)/)
    expect(css).toMatch(/@media \(prefers-reduced-motion: reduce\)/)
  })
})

describe('cn знает размеры, радиусы и тени «Кресла»', () => {
  for (const [namespace, known] of [
    ['text', THEME_SCALES.text],
    ['radius', THEME_SCALES.radius],
    ['shadow', THEME_SCALES.shadow],
  ] as const) {
    it(`все --${namespace}-* из CSS перечислены в THEME_SCALES`, () => {
      const custom = themeNames(css, namespace).filter((name) => !TSHIRT.test(name))
      expect([...custom].sort()).toEqual([...known].sort())
    })
  }

  it.each(THEME_SCALES.text)('text-%s не спорит с цветом текста', (name) => {
    expect(cn(`text-${name}`, 'text-ink')).toBe(`text-${name} text-ink`)
    expect(cn('text-body-sm', `text-${name}`)).toBe(`text-${name}`)
  })

  it.each(THEME_SCALES.radius)('rounded-%s вытесняет rounded-lg', (name) => {
    expect(cn('rounded-lg', `rounded-${name}`)).toBe(`rounded-${name}`)
  })

  it.each(THEME_SCALES.shadow)('shadow-%s вытесняет shadow-md', (name) => {
    expect(cn('shadow-md', `shadow-${name}`)).toBe(`shadow-${name}`)
  })
})
