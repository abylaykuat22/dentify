import { createCn } from 'cn/config'

/**
 * Имена размеров шрифта, радиусов и теней из `@theme` «Кресла» (src/styles/index.css).
 * `cn` должен их знать: иначе `text-body` он примет за цвет и выкинет рядом с `text-ink`,
 * а `rounded-lg rounded-pill` оставит оба класса. Совпадение с CSS проверяет
 * src/styles/tokens.test.ts.
 */
export const THEME_SCALES = {
  text: ['h1', 'h2', 'h3', 'h4', 'body-lg', 'body', 'body-sm', 'label', 'caption', 'nav', 'logo', 'logo-lg', 'avatar-lg'],
  radius: ['pill', 'window', 'card', 'block', 'tile', 'row', 'item', 'mark'],
  shadow: ['popover', 'window', 'raised', 'field', 'dock', 'lamp-glow'],
} as const

/**
 * `cn` — официальная утилита shadcn (замена clsx + tailwind-merge), настроенная на тему
 * «Кресла». Импортировать только отсюда: пакет `cn` напрямую запрещён ESLint'ом.
 */
export const cn = createCn({
  extend: {
    theme: {
      text: [...THEME_SCALES.text],
      radius: [...THEME_SCALES.radius],
      shadow: [...THEME_SCALES.shadow],
    },
  },
})
