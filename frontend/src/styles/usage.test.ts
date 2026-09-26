import { describe, expect, it } from 'vitest'

// PLT-20: все экраны собираются из одного набора «Кресла». Тест читает исходники и ловит
// размеры шрифта, радиусы и отступы мимо токенов (src/styles/index.css). Витрина src/dev/**
// повторяет макет Sistema один в один и сюда не входит.

// Исходники как текст (Vite `?raw`): ключ — «/src/shared/ui/avatar.tsx».
const RAW = import.meta.glob<string>(
  ['/src/shared/ui/**/*.{ts,tsx}', '/src/app/shell/**/*.{ts,tsx}', '/src/modules/**/*.{ts,tsx}', '!**/*.test.{ts,tsx}'],
  { query: '?raw', import: 'default', eager: true },
)

/** Файлы из папок `src/<dir>`: путь — от src («shared/ui/avatar.tsx»). */
function sources(...dirs: string[]): { file: string; text: string }[] {
  const files = Object.entries(RAW).map(([path, text]) => ({ file: path.replace(/^\/src\//, ''), text }))
  const picked = files.filter(({ file }) => dirs.some((dir) => file.startsWith(`${dir}/`)))
  if (picked.length === 0) throw new Error(`Нет исходников в ${dirs.join(', ')}`)
  return picked
}

/** Классы Tailwind из исходника: всё, что похоже на утилиту, с вариантами (`lg:px-3.5`). */
function classTokens(text: string): string[] {
  return text.match(/[\w:[\]().%,/*&=>+-]+/g) ?? []
}

/** Утилита без вариантов: `data-[status=active]:lg:px-3.5` → `px-3.5`. */
function utility(token: string): string {
  let depth = 0
  let start = 0
  for (let index = 0; index < token.length; index += 1) {
    const char = token[index]
    if (char === '[') depth += 1
    else if (char === ']') depth -= 1
    else if (char === ':' && depth === 0) start = index + 1
  }
  return token.slice(start).replace(/^!/, '')
}

/** Утилита и её варианты: `after:-inset-y-[3px]` → { variants: ['after'], utility: '-inset-y-[3px]' }. */
function variantsOf(token: string): string[] {
  const rest = token.slice(0, token.length - utility(token).length)
  return rest.split(':').filter(Boolean)
}

/** Исключения — только из макетов, с назначением (design-directions, docs/frontend.md). */
const FONT_SIZE_EXCEPTIONS: Record<string, string[]> = {}

const SCREEN_DIRS = ['shared/ui', 'app/shell', 'modules']

describe('экраны и компоненты — только токены «Кресла» (PLT-20)', () => {
  const files = sources(...SCREEN_DIRS)

  it('размер шрифта — токен (text-h1 … text-caption, text-nav, text-logo), не text-sm и не text-[15px]', () => {
    const offenders: string[] = []
    for (const { file, text } of files) {
      for (const token of classTokens(text)) {
        const name = utility(token)
        const tailwindDefault = /^text-(xs|sm|base|lg|[2-9]?xl)$/.test(name)
        const arbitrarySize = /^text-\[\d[\d.]*(px|rem|em)\]$/.test(name)
        if ((tailwindDefault || arbitrarySize) && !FONT_SIZE_EXCEPTIONS[file]?.includes(name)) {
          offenders.push(`${file}: ${token}`)
        }
      }
    }
    expect(offenders).toEqual([])
  })

  it('радиус — токен (rounded-pill, -window, -card, -block, -tile, -row, -item), не rounded-md', () => {
    const offenders: string[] = []
    for (const { file, text } of files) {
      for (const token of classTokens(text)) {
        if (/^rounded(-[trblse]{1,2})?-(xs|sm|md|lg|xl|[2-4]xl)$/.test(utility(token))) {
          offenders.push(`${file}: ${token}`)
        }
      }
    }
    expect(offenders).toEqual([])
  })
})

/**
 * Отступы (design-directions § Формы): ритм между блоками — 4 · 8 · 12 · 16 · 24 · 32
 * (шаги 1 · 2 · 3 · 4 · 6 · 8); внутри компонентов — шаги макетов 2 · 6 · 10 · 14 · 18 ·
 * 20 · 22 · 28 (у пилюль, чипов, полей, окон). Остальное — резерв места под соседний элемент,
 * по файлам и с назначением.
 */
const RHYTHM_STEPS = ['0', 'px', '1', '2', '3', '4', '6', '8']
const COMPONENT_STEPS = ['0.5', '1.5', '2.5', '3.5', '4.5', '5', '5.5', '7']
const RESERVED_SPACING: Record<string, string[]> = {
  'shared/ui/combobox.tsx': ['pr-9'], // место под галочку выбранного пункта
  'shared/ui/dialog.tsx': ['pr-14'], // заголовок окна не заходит под крестик
  'shared/ui/StateScreen.tsx': ['py-16'], // воздух пустого состояния — два шага по 32
}

describe('отступы — шкала «Кресла» (PLT-20)', () => {
  it('в shared/ui и каркасе — только шаги шкалы и макетов', () => {
    const spacing = /^-?(p[xytrblse]?|m[xytrblse]?|gap(-[xy])?|space-[xy])-(.+)$/
    const allowed = new Set([...RHYTHM_STEPS, ...COMPONENT_STEPS])
    const offenders: string[] = []
    for (const { file, text } of sources('shared/ui', 'app/shell')) {
      for (const token of classTokens(text)) {
        const name = utility(token)
        const match = spacing.exec(name)
        if (!match) continue
        const step = match[3] ?? ''
        // Цель касания (`after:` у маленьких кнопок) — геометрия, а не отступ.
        if (variantsOf(token).includes('after')) continue
        // Арбитражное значение — только с safe-area iOS (`env(safe-area-inset-bottom)`).
        if (step.startsWith('[')) {
          if (!step.includes('env(safe-area-inset')) offenders.push(`${file}: ${token}`)
          continue
        }
        if (allowed.has(step) || RESERVED_SPACING[file]?.includes(name)) continue
        // `auto`, `full` и т. п. — не отступы шкалы.
        if (!/^\d/.test(step)) continue
        offenders.push(`${file}: ${token}`)
      }
    }
    expect(offenders).toEqual([])
  })

  it('разбор классов: варианты и произвольные значения', () => {
    expect(utility('data-[status=active]:lg:px-3.5')).toBe('px-3.5')
    expect(utility('has-[>svg:first-child]:pl-4')).toBe('pl-4')
    expect(variantsOf('after:-inset-y-[3px]')).toEqual(['after'])
    expect(classTokens("'h-12 px-5.5 lg:text-[15px]'")).toEqual(['h-12', 'px-5.5', 'lg:text-[15px]'])
    expect(classTokens("'pb-[calc(7rem+env(safe-area-inset-bottom))] md:pb-6'")).toEqual([
      'pb-[calc(7rem+env(safe-area-inset-bottom))]',
      'md:pb-6',
    ])
  })
})
