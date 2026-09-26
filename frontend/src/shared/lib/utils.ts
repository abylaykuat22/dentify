import { createCn } from 'cn/engine'

import tables from './cn-tables'

/**
 * `cn` — официальная утилита shadcn (замена clsx + tailwind-merge) на таблицах, собранных заранее
 * под тему «Кресла» (`cn build`, плагин в vite.config.ts → `cn-tables.ts`, не править руками): она знает
 * размеры, радиусы и тени из `@theme` (src/styles/index.css) — `text-body` не примет за цвет и не выкинет
 * рядом с `text-ink`, из `rounded-lg rounded-pill` оставит последний. Это сторожит
 * src/styles/tokens.test.ts. Импортировать только отсюда: пакет `cn` напрямую запрещён ESLint'ом.
 */
export const cn = createCn(tables)
