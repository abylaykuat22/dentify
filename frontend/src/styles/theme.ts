/**
 * Разбор цветов-токенов из CSS «Кресла» (`--color-*` в `@theme`) — для юнит-тестов контраста
 * и сверки токенов. В приложение не входит (импортируют только тесты).
 */

/** `--color-lagoon: #0f3d4c;` → { lagoon: '#0f3d4c' }; ссылки `var(--color-x)` разрешены. */
export function parseColorTokens(css: string): Map<string, string> {
  const raw = new Map<string, string>()
  const declaration = /--color-([a-z0-9-]+)\s*:\s*([^;]+);/g
  for (const match of css.matchAll(declaration)) {
    const [, name, value] = match
    if (name && value && value.trim() !== 'initial') raw.set(name, value.trim())
  }

  const resolved = new Map<string, string>()
  const resolve = (name: string, seen: Set<string> = new Set()): string => {
    const value = raw.get(name)
    if (value === undefined) throw new Error(`Нет токена --color-${name}`)
    const ref = /^var\(--color-([a-z0-9-]+)\)$/.exec(value)
    if (!ref?.[1]) return value.toLowerCase()
    if (seen.has(name)) throw new Error(`Цикл ссылок у --color-${name}`)
    seen.add(name)
    return resolve(ref[1], seen)
  }
  for (const name of raw.keys()) {
    // Переменные shadcn (`--color-primary: var(--primary)`) — не токены «Кресла».
    if (/^var\(--(?!color-)/.test(raw.get(name) ?? '')) continue
    resolved.set(name, resolve(name))
  }
  return resolved
}

/** Имена токенов пространства `--<ns>-*` из блоков `@theme` («text» → h1, body, …). */
export function themeNames(css: string, namespace: string): string[] {
  const names = new Set<string>()
  const pattern = new RegExp(`--${namespace}-([a-z0-9-]+)\\s*:`, 'g')
  for (const match of css.matchAll(pattern)) {
    const name = match[1]
    // `--text-h1--line-height` — это подсвойство размера h1, а не отдельный размер.
    if (name && !name.includes('--') && !name.endsWith('-')) names.add(name)
  }
  return [...names]
}

function channel(value: number): number {
  const srgb = value / 255
  return srgb <= 0.04045 ? srgb / 12.92 : ((srgb + 0.055) / 1.055) ** 2.4
}

/** Относительная яркость по WCAG 2.2 для `#rrggbb`. */
export function luminance(hex: string): number {
  const match = /^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(hex)
  if (!match) throw new Error(`Ожидался цвет #rrggbb, получено «${hex}»`)
  const [r, g, b] = [match[1], match[2], match[3]].map((part) => channel(parseInt(part ?? '0', 16)))
  return 0.2126 * (r ?? 0) + 0.7152 * (g ?? 0) + 0.0722 * (b ?? 0)
}

/**
 * Полупрозрачный цвет поверх фона — итоговый непрозрачный цвет, как его видит глаз
 * (`bg-white/10` на лагуне). Смешение в sRGB — так же, как рисует браузер.
 */
export function blend(top: string, alpha: number, bottom: string): string {
  const parse = (hex: string) => {
    const match = /^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(hex)
    if (!match) throw new Error(`Ожидался цвет #rrggbb, получено «${hex}»`)
    return [match[1], match[2], match[3]].map((part) => parseInt(part ?? '0', 16))
  }
  const [over, under] = [parse(top), parse(bottom)]
  const mixed = over.map((value, index) => Math.round(value * alpha + (under[index] ?? 0) * (1 - alpha)))
  return `#${mixed.map((value) => value.toString(16).padStart(2, '0')).join('')}`
}

/** Контраст двух цветов по WCAG 2.2: от 1 до 21. */
export function contrastRatio(foreground: string, background: string): number {
  const [lighter, darker] = [luminance(foreground), luminance(background)].sort((a, b) => b - a)
  return ((lighter ?? 0) + 0.05) / ((darker ?? 0) + 0.05)
}
