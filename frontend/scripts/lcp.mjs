// Замер LCP/CLS первого экрана на телефоне (PLT-22: Lighthouse mobile, LCP ≤ 2.5 с, CLS ≤ 0.1).
// Сборка — обычная, без моков (`pnpm build`): старт MSW раздул бы замер.
//
//   pnpm build && pnpm lcp            — HTTP/2 + TLS, как отдаёт прод (Caddy, deploy/caddy)
//   pnpm verify:perf                  — то же вместе со сборкой и size-limit
//   pnpm lcp --http1                  — HTTP/1.1, как `vite preview` (лабораторно хуже: 6 соединений)
//   pnpm lcp --runs 5 --path /patients
//
// Отдаёт `dist/` со сжатием и заглушкой GET /api/v1/me (вымышленный владелец, без бэкенда), гоняет
// Lighthouse (`npx lighthouse@13`, профиль mobile: Moto G, 4G, CPU ×4) и печатает медиану. Код выхода
// 1 — медиана LCP > 2.5 с или CLS > 0.1. Критерий LCP 2,5 с держится **только по HTTP/2** (2026-09-26:
// 2,48 с; по HTTP/1.1 — 3,5 с), поэтому `--http1` — замер справочный: код выхода — только по CLS. Для
// TLS нужен `openssl` в PATH (есть в Git Bash); нет — замер по HTTP/1.1 с предупреждением, и бюджет
// 2,5 с к нему применяется как есть (упадёт — ставьте openssl). Порт 4390. В `verify` не входит: замер
// долгий и чувствителен к нагрузке на машину; гонять при каждой правке стартового маршрута (каркас,
// `/` → первый раздел, index.html, начальный чанк) — `pnpm verify:perf` (TODO(T-SCH): в `verify` по
// экрану расписания, когда он появится).
import { execFileSync, spawn } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { createServer } from 'node:http'
import { createSecureServer } from 'node:http2'
import { tmpdir } from 'node:os'
import { extname, join, normalize, resolve } from 'node:path'
import { gzipSync } from 'node:zlib'

const args = process.argv.slice(2)
const option = (name, fallback) => {
  const index = args.indexOf(name)
  return index >= 0 ? args[index + 1] : fallback
}
const runs = Number(option('--runs', '3'))
const path = option('--path', '/')
const port = Number(option('--port', '4390'))
let http1 = args.includes('--http1')
// Бюджет LCP задан для HTTP/2 (как прод); явный `--http1` — справочный замер.
const reference = http1

const DIST = resolve('dist')
if (!existsSync(join(DIST, 'index.html'))) throw new Error('lcp: нет dist/index.html — сначала `pnpm build`')

// Вымышленные данные (как в моках): владелец клиники «Ақ Тіс».
const clinic = { id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e10', name: 'Ақ Тіс', city: 'Алматы' }
const branch = { id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e20', name: 'Абая, 150', city: 'Алматы' }
const ME = JSON.stringify({
  account: {
    id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e01',
    fullName: 'Алиева Динара',
    initials: 'АД',
    language: 'ru',
    phone: '+77010000001',
    email: 'dinara@example.kz',
  },
  context: { clinic, branch },
  memberships: [{ id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e30', clinic, status: 'ACTIVE', branches: [branch] }],
  role: { code: 'owner', name: 'Владелец' },
  permissions: ['schedule.view', 'patients.view', 'staff.view', 'inventory.view', 'clinic.view', 'access.view'],
})

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.woff2': 'font/woff2',
  '.svg': 'image/svg+xml',
  '.json': 'application/json',
}

function handler(request, response) {
  const url = new URL(request.url, 'http://localhost')
  if (url.pathname === '/api/v1/me') {
    response.writeHead(200, { 'content-type': 'application/json' })
    response.end(ME)
    return
  }
  if (url.pathname.startsWith('/api/')) {
    response.writeHead(404)
    response.end()
    return
  }
  let file = normalize(join(DIST, decodeURIComponent(url.pathname)))
  if (!file.startsWith(DIST) || !existsSync(file) || !extname(file)) file = join(DIST, 'index.html')
  const headers = { 'content-type': TYPES[extname(file)] ?? 'application/octet-stream' }
  if (file.includes(`${join(DIST, 'assets')}`)) headers['cache-control'] = 'public, max-age=31536000, immutable'
  let body = readFileSync(file)
  if (/\.(html|js|css|svg|json)$/.test(file) && /gzip/.test(request.headers['accept-encoding'] ?? '')) {
    body = gzipSync(body)
    headers['content-encoding'] = 'gzip'
  }
  response.writeHead(200, headers)
  response.end(body)
}

const certDir = mkdtempSync(join(tmpdir(), 'dentify-lcp-'))
let server
if (!http1) {
  try {
    execFileSync(
      'openssl',
      ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1', '-subj', '/CN=127.0.0.1',
        '-addext', 'subjectAltName=IP:127.0.0.1', '-keyout', join(certDir, 'key.pem'), '-out', join(certDir, 'cert.pem')],
      { stdio: 'ignore', env: { ...process.env, MSYS_NO_PATHCONV: '1' } },
    )
    server = createSecureServer(
      { allowHTTP1: true, key: readFileSync(join(certDir, 'key.pem')), cert: readFileSync(join(certDir, 'cert.pem')) },
      handler,
    )
  } catch {
    console.warn('lcp: нет openssl — замер по HTTP/1.1 (хуже прода: у Caddy HTTP/2), а бюджет LCP 2,5 с — для HTTP/2')
    http1 = true
  }
}
server ??= createServer(handler)
await new Promise((ready) => server.listen(port, '127.0.0.1', ready))

const target = `${http1 ? 'http' : 'https'}://127.0.0.1:${port}${path}`
const results = []
try {
  for (let run = 1; run <= runs; run += 1) {
    const report = join(certDir, `lh-${run}.json`)
    // Одной строкой через оболочку: так npx находится и в Windows, а флаги Chrome — одним аргументом.
    const command = [
      'npx --yes lighthouse@13',
      `"${target}"`,
      '--form-factor=mobile --only-categories=performance --output=json --quiet',
      `"--output-path=${report}"`,
      '"--chrome-flags=--headless=new --no-first-run --ignore-certificate-errors"',
    ].join(' ')
    // Код выхода не смотрим: в Windows chrome-launcher падает с EPERM, убирая свою временную папку
    // Chrome, — уже после того, как отчёт записан. Есть отчёт — замер состоялся.
    // Асинхронно: сервер замера живёт в этом же процессе и должен отвечать, пока идёт Lighthouse.
    await new Promise((done) => spawn(command, { stdio: ['ignore', 'ignore', 'inherit'], shell: true }).on('close', done))
    if (!existsSync(report)) throw new Error('lcp: Lighthouse не записал отчёт')
    const { audits, runtimeError } = JSON.parse(readFileSync(report, 'utf8'))
    if (runtimeError) throw new Error(`lcp: Lighthouse — ${runtimeError.code}: ${runtimeError.message}`)
    const row = {
      fcp: audits['first-contentful-paint'].numericValue,
      lcp: audits['largest-contentful-paint'].numericValue,
      cls: audits['cumulative-layout-shift'].numericValue,
    }
    results.push(row)
    console.log(`#${run}: FCP ${(row.fcp / 1000).toFixed(2)} с · LCP ${(row.lcp / 1000).toFixed(2)} с · CLS ${row.cls.toFixed(3)}`)
  }
} finally {
  server.close()
  rmSync(certDir, { recursive: true, force: true })
}

const median = (key) => [...results.map((row) => row[key])].sort((a, b) => a - b)[Math.floor(results.length / 2)]
const lcp = median('lcp')
const cls = median('cls')
console.log(
  `${target} (${http1 ? 'HTTP/1.1' : 'HTTP/2'}), медиана ${runs}: LCP ${(lcp / 1000).toFixed(2)} с (≤ 2.5), CLS ${cls.toFixed(3)} (≤ 0.1)`,
)
if (reference) {
  console.log('lcp: --http1 — справочно: бюджет LCP 2,5 с держится только по HTTP/2 (`pnpm lcp`); код выхода — по CLS')
  process.exit(cls <= 0.1 ? 0 : 1)
}
process.exit(lcp <= 2500 && cls <= 0.1 ? 0 : 1)
