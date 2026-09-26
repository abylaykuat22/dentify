// Проверка production-сборки: витрина дизайн-системы /dev/ui (src/dev/**) живёт только в
// dev-сборке. Маршрут в production отвечает 404, а ветка с витриной мёртвая
// (`import.meta.env.DEV` = false) — здесь проверяем, что её код и словарь не попали в dist.
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const ASSETS = 'dist/assets'
const devDictionary = JSON.parse(readFileSync('src/dev/ui/ru.json', 'utf8'))
const markers = ['data-dev-ui', 'ensureDevUiI18n', devDictionary.title, devDictionary.more.title]

const leaks = []
for (const file of readdirSync(ASSETS)) {
  if (!file.endsWith('.js')) continue
  const source = readFileSync(join(ASSETS, file), 'utf8')
  for (const marker of markers) {
    if (source.includes(marker)) leaks.push(`${file}: «${marker}»`)
  }
}

if (leaks.length > 0) {
  console.error(`check-build: код витрины /dev/ui попал в production-сборку:\n  ${leaks.join('\n  ')}`)
  process.exit(1)
}
console.log('check-build: витрины /dev/ui в сборке нет')
