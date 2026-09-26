// `pnpm api:check` (входит в verify): закоммиченный клиент API совпадает с тем, что orval делает из
// ../api/openapi.json сейчас (PLT-23: «DTO изменили, клиент не перегенерировали — сборка падает»).
//
// Клиент перегенерируется во временную папку `src/shared/api/.generated-check` — соседнюю с generated:
// глубина та же, поэтому относительный импорт мутатора (`../../../http`) в файлах совпадает — и
// сравнивается с src/shared/api/generated файл за файлом. Папка удаляется в любом случае.
// Расхождение → список файлов и код 1; чинится `pnpm api:generate` (и правкой вызовов, если спека
// сломала их — это покажет typecheck).
import { existsSync, readdirSync, readFileSync, rmSync } from 'node:fs'
import { join } from 'node:path'

const GENERATED = 'src/shared/api/generated'
const CHECK = 'src/shared/api/.generated-check'

/** Все файлы папки, пути относительно неё через «/». */
function listFiles(root, dir = '') {
  if (!existsSync(join(root, dir))) return []
  return readdirSync(join(root, dir), { withFileTypes: true })
    .flatMap((entry) => {
      const path = dir ? `${dir}/${entry.name}` : entry.name
      return entry.isDirectory() ? listFiles(root, path) : [path]
    })
    .sort()
}

// Git на Windows может выдать файл с CRLF — концы строк не различие.
const read = (root, path) => readFileSync(join(root, path), 'utf8').replace(/\r\n/g, '\n')

rmSync(CHECK, { recursive: true, force: true })
process.env.ORVAL_OUT = `./${CHECK}`
try {
  const { default: generate } = await import('orval')
  await generate('./orval.config.ts')

  const expected = listFiles(CHECK)
  const actual = listFiles(GENERATED)
  if (expected.length === 0) throw new Error(`api:check: orval ничего не записал в ${CHECK}`)

  const missing = expected.filter((path) => !actual.includes(path))
  const extra = actual.filter((path) => !expected.includes(path))
  const changed = expected.filter((path) => actual.includes(path) && read(CHECK, path) !== read(GENERATED, path))

  if (missing.length + extra.length + changed.length > 0) {
    const lines = [
      ...missing.map((path) => `  + ${path}   (есть в спеке, нет в клиенте)`),
      ...extra.map((path) => `  - ${path}   (в клиенте, но не в спеке)`),
      ...changed.map((path) => `  ~ ${path}`),
    ]
    console.error(`\napi:check: клиент ${GENERATED} отстал от ../api/openapi.json:\n${lines.join('\n')}`)
    console.error('Обновите: pnpm api:generate (затем pnpm typecheck — покажет вызовы, которые сломала спека).\n')
    process.exitCode = 1
  } else {
    console.log(`api:check: клиент совпадает со спекой (${expected.length} файлов).`)
  }
} finally {
  rmSync(CHECK, { recursive: true, force: true })
}
