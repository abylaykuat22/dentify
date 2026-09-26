import { execFileSync } from 'node:child_process'
import { readdirSync, rmSync, statSync } from 'node:fs'
import { join } from 'node:path'

import { defineConfig, devices } from '@playwright/test'

// e2e ходят в dev-сервер Vite в режиме моков (MSW), бэкенд не нужен.
//
// Прогоны идут параллельно (несколько агентов, `pnpm e2e` рядом с `pnpm verify`), поэтому у
// каждого прогона всё своё: dev-сервер, папка артефактов и HTML-отчёт. Конфиг читают и
// процессы-исполнители — значения выбирает главный процесс и передаёт им через окружение
// (DENTIFY_E2E_RUN, DENTIFY_E2E_PORT): исполнители наследуют его и ничего не выбирают заново.
const RESULTS = 'test-results'
const REPORT = 'playwright-report'

// Свой dev-сервер на каждый прогон. С общим портом и reuseExistingServer второй прогон
// подхватывал сервер первого, а тот гасился вместе с первым прогоном — ERR_CONNECTION_REFUSED,
// прогрев падал и остальные тесты не запускались. Порт — свободный из 5300–5799 (ниже
// динамического диапазона ОС: исходящие соединения его не займут), старт поиска зависит от pid,
// чтобы соседние прогоны не пробовали одни и те же порты. Чужой сервер не подхватывается никогда:
// переиспользовать уже запущенный — только по явной просьбе DENTIFY_E2E_REUSE=1 (порт —
// DENTIFY_E2E_PORT, по умолчанию 5174; сам сервер: `pnpm dev:mock --port 5174`).
const REUSE = process.env.DENTIFY_E2E_REUSE === '1'
if (!process.env.DENTIFY_E2E_PORT) process.env.DENTIFY_E2E_PORT = REUSE ? '5174' : String(freePort())
const PORT = Number(process.env.DENTIFY_E2E_PORT)
const BASE_URL = `http://127.0.0.1:${PORT}`

if (!process.env.DENTIFY_E2E_RUN) {
  process.env.DENTIFY_E2E_RUN = `${Date.now()}-${process.pid}`
  pruneOldRuns(RESULTS)
  pruneOldRuns(REPORT)
}
const RUN = `run-${process.env.DENTIFY_E2E_RUN}`

/**
 * Свободный порт для dev-сервера. Конфиг грузится синхронно, а занять порт на пробу в Node можно
 * только асинхронно — поэтому проба в отдельном `node -e`. Занятые и закрытые ОС порты (EADDRINUSE,
 * EACCES — исключённые диапазоны Windows) пропускаются; известные чужие — не пробуются вовсе.
 */
function freePort(): number {
  const FROM = 5300
  const SPAN = 500
  // 5357 — WSDAPI Windows, 5432/5433 — PostgreSQL (5433 — dev-окружение), 5672 — AMQP.
  const RESERVED = [5357, 5432, 5433, 5672]
  const probe = `
    const net = require('node:net')
    const [from, span, start, ...reserved] = process.argv.slice(1).map(Number)
    const attempt = (i) => {
      if (i >= span) { console.error('нет свободного порта в ' + from + '–' + (from + span - 1)); process.exit(1) }
      const port = from + ((start + i) % span)
      if (reserved.includes(port)) return attempt(i + 1)
      const server = net.createServer()
      server.once('error', () => attempt(i + 1))
      server.listen({ port, host: '127.0.0.1', exclusive: true }, () => server.close(() => process.stdout.write(String(port))))
    }
    attempt(0)`
  const args = [FROM, SPAN, process.pid % SPAN, ...RESERVED].map(String)
  return Number(execFileSync(process.execPath, ['-e', probe, ...args], { encoding: 'utf8' }).trim())
}

/**
 * Папки прошлых прогонов старше суток — удалить (свежие могут принадлежать идущему прогону).
 * Папки идущих прогонов свежие: Playwright пишет в них до конца прогона.
 */
function pruneOldRuns(root: string) {
  const dayAgo = Date.now() - 24 * 60 * 60 * 1000
  let entries: string[]
  try {
    entries = readdirSync(root).filter((name) => name.startsWith('run-'))
  } catch {
    return
  }
  for (const name of entries) {
    const dir = join(root, name)
    try {
      if (statSync(dir).mtimeMs < dayAgo) rmSync(dir, { recursive: true, force: true })
    } catch {
      // Папку держит другой процесс — уберётся в следующий раз.
    }
  }
}

export default defineConfig({
  testDir: './e2e',
  // Артефакты (трассы, скриншоты падений) — test-results/run-<id>: Playwright очищает outputDir при
  // старте, и с общей папкой соседний прогон падал на ENOENT своих трасс.
  outputDir: `${RESULTS}/${RUN}`,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  // HTML-отчёт — тоже свой (playwright-report/run-<id>): репортёр стирает папку перед записью.
  // Путь печатается в конце прогона: `pnpm exec playwright show-report playwright-report/run-<id>`.
  reporter: process.env.CI ? 'github' : [['list'], ['html', { open: 'never', outputFolder: `${REPORT}/${RUN}` }]],
  timeout: 30_000,
  // Эталоны скриншотов (витрина /dev/ui) — в репозитории: e2e/__screenshots__/<спека>/
  // <имя>-<проект>-<ОС>.png. Шрифты рисуются по-разному в разных ОС, поэтому ОС — в имени;
  // обновить эталоны: `pnpm e2e --update-snapshots`.
  snapshotPathTemplate: '{testDir}/__screenshots__/{testFilePath}/{arg}-{projectName}-{platform}{ext}',
  expect: {
    timeout: 10_000,
    toHaveScreenshot: { animations: 'disabled', caret: 'hide', maxDiffPixelRatio: 0.002 },
  },
  use: {
    baseURL: BASE_URL,
    locale: 'ru-RU',
    timezoneId: 'Asia/Almaty',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      // Прогрев dev-сервера (e2e/warmup.setup.ts): остальные проекты стартуют после него.
      name: 'warmup',
      testMatch: /warmup\.setup\.ts$/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
    {
      name: 'desktop',
      dependencies: ['warmup'],
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
    {
      // Телефон — эталон 390×844 (PLT-18): Pixel 7 (сенсорный ввод, мобильный UA) с шириной
      // 390. Общие проверки всех маршрутов — responsive.spec.ts, навигация — mobile.spec.ts.
      name: 'mobile',
      dependencies: ['warmup'],
      use: {
        ...devices['Pixel 7'],
        viewport: { width: 390, height: 844 },
      },
    },
  ],
  webServer: {
    // --strictPort: порт занят — ошибка, а не молчаливый переезд Vite на соседний (тесты ходят в PORT).
    command: `pnpm dev:mock --port ${PORT} --strictPort`,
    url: BASE_URL,
    // Без явной просьбы уже отвечающий на PORT сервер — ошибка Playwright «already used», а не
    // чужой сервер, который погасят посреди нашего прогона.
    reuseExistingServer: REUSE,
    timeout: 60_000,
  },
})
