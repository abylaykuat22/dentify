// Бюджеты первого экрана (PLT-22), gzip: начальная загрузка ≤ 218,5 kB и чанки каркаса ≤ 21 kB.
//
// Бюджет держит LCP, а не потолок требования (250 kB). LCP в лаборатории (Lighthouse, медленный 4G)
// растёт ступенями: байты идут круговыми задержками по ~30 kB, лишняя задержка — +50 мс к LCP. 2026-09-26:
// начальная загрузка 216,2 kB, чанки каркаса 19,7 kB, LCP 2,49 с при критерии 2,5 с; +2,5–3 kB к любому из
// них — ступень, 2,54 с (docs/frontend.md, «Производительность»). Поэтому пределы — вплотную к текущим
// размерам: рост валит `pnpm verify`, а не тихо съедает LCP. Поднять предел можно только вместе с замером
// `pnpm lcp` (или `pnpm verify:perf`).
//
// Считается не один `index-*.js`, а точка входа вместе со всеми её статическими импортами
// (общие чанки, которые index.html подгружает через <link rel="modulepreload">) и их CSS.
// Граф берётся из манифеста сборки (`build.manifest: true` в vite.config.ts): от `index.html`
// рекурсивно по `imports`; `dynamicImports` (ленивые маршруты, моки MSW) в бюджет не входят.
// Если тяжёлая библиотека уедет в общий статический чанк, проверка это увидит.
//
// Страховка от расхождения манифеста и страницы: всё, что dist/index.html грузит сам
// (<script src>, modulepreload, stylesheet), обязано быть в списке — иначе ошибка, а не
// тихо заниженный размер. Шрифты (woff2) в бюджет не входят: браузер берёт по unicode-range
// только нужные подмножества, и они не блокируют запуск приложения.
import { existsSync, readFileSync } from 'node:fs'

const MANIFEST = 'dist/.vite/manifest.json'
const INDEX_HTML = 'dist/index.html'
const ENTRY = 'index.html'
/** Компонент лейаута `_app` (autoCodeSplitting TanStack Router) — корень чанков каркаса. */
const SHELL = 'src/app/routes/_app.tsx?tsr-split=component'

/** Файлы начальной загрузки: entry + статические импорты (рекурсивно) + их CSS. */
export function initialFiles(manifest, entry = ENTRY) {
  const files = new Set()
  const seen = new Set()
  const visit = (key) => {
    if (seen.has(key)) return
    seen.add(key)
    const chunk = manifest[key]
    if (!chunk) throw new Error(`size-limit: в манифесте нет чанка «${key}»`)
    files.add(`dist/${chunk.file}`)
    for (const css of chunk.css ?? []) files.add(`dist/${css}`)
    for (const imported of chunk.imports ?? []) visit(imported)
  }
  visit(entry)
  return [...files].sort()
}

/**
 * Чанки каркаса первого экрана: лейаут `_app` (сайдбар, шапка, нижняя панель) и его статические импорты,
 * кроме начальной загрузки. Они ленивые — в бюджет начальной загрузки не входят, — но стартовый маршрут
 * тянет их сразу за ней (app/startup.ts), до первого экрана: меню пользователя, положенное в каркас
 * статически, подняло LCP на 0,14 с, а бюджет начальной загрузки этого не видел.
 */
export function shellFiles(manifest, shell = SHELL, entry = ENTRY) {
  const initial = new Set(initialFiles(manifest, entry))
  const files = new Set()
  const seen = new Set()
  const visit = (key) => {
    if (seen.has(key)) return
    seen.add(key)
    const chunk = manifest[key]
    if (!chunk) throw new Error(`size-limit: в манифесте нет чанка «${key}»`)
    for (const file of [chunk.file, ...(chunk.css ?? [])]) if (!initial.has(`dist/${file}`)) files.add(`dist/${file}`)
    for (const imported of chunk.imports ?? []) visit(imported)
  }
  visit(shell)
  return [...files].sort()
}

/** JS и CSS, которые index.html загружает сам: entry-скрипт, modulepreload, stylesheet. */
export function htmlAssets(html) {
  const assets = new Set()
  const tags = html.match(/<(?:script|link)\b[^>]*>/g) ?? []
  for (const tag of tags) {
    const isScript = tag.startsWith('<script') && /\btype="module"/.test(tag)
    const isPreload = /\brel="(?:modulepreload|stylesheet)"/.test(tag)
    if (!isScript && !isPreload) continue
    const url = /\b(?:src|href)="\/?([^"]+)"/.exec(tag)?.[1]
    if (url && /\.(?:js|css)$/.test(url)) assets.add(`dist/${url}`)
  }
  return [...assets].sort()
}

if (!existsSync(MANIFEST) || !existsSync(INDEX_HTML)) {
  throw new Error(`size-limit: нет ${MANIFEST} или ${INDEX_HTML} — сначала \`pnpm build\``)
}

const manifest = JSON.parse(readFileSync(MANIFEST, 'utf8'))
const files = initialFiles(manifest)
const missing = htmlAssets(readFileSync(INDEX_HTML, 'utf8')).filter((file) => !files.includes(file))
if (missing.length > 0) {
  throw new Error(`size-limit: index.html грузит файлы, которых нет в бюджете: ${missing.join(', ')}`)
}

export default [
  {
    name: 'initial load: entry + static imports, JS + CSS (gzip)',
    path: files,
    limit: '218.5 kB',
    gzip: true,
  },
  {
    name: 'shell of the first screen: _app layout + static imports beyond the initial load (gzip)',
    path: shellFiles(manifest),
    limit: '21 kB',
    gzip: true,
  },
]
