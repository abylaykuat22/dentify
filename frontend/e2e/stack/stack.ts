import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

import {
  type APIRequestContext,
  test as base,
  type BrowserContext,
  expect,
  type Page,
  type Request,
  type TestInfo,
} from '@playwright/test'

import type { AccountSessionResponse, MeResponse } from '../../src/shared/api/generated/model'
import { isDesktop } from '../helpers'

// Живой стек (T-PLT-16): бэкенд с профилем dev и `vite preview` сборки без моков, поднятые заранее
// (`../verify.ps1 -E2E` или руками, docs/frontend.md «Тесты»). Адреса — из окружения, договор с verify
// (T-PLT-15): фронт — DENTIFY_STACK_URL (`http://localhost:<порт>`: на 127.0.0.1 браузер не шлёт
// Secure-cookie сессии), прямой адрес API — DENTIFY_STACK_API_URL (страница туда ходить не должна). Обе
// обязательны: без адреса API проверка «мимо своего origin» молча не делалась бы. В спецификации T-PLT-16 они
// названы DENTIFY_E2E_BASE_URL и DENTIFY_E2E_API — имена взяты из уже действующего договора с verify.
//
// Кто входит — dev-сид бэкенда (docs/architecture.md «Dev-сид»): владелец owner@demo.dentify.kz, клиника
// «Dentify Demo» с одним филиалом «Абая, 150» — вход сразу выбирает их, кабинет этапа 0 пуст. Пароль —
// DENTIFY_DEV_OWNER_PASSWORD из окружения или из deploy/.env (тот же файл читает бэкенд с профилем dev).

export const STACK_URL = requiredEnv('DENTIFY_STACK_URL')
export const STACK_API_URL = requiredEnv('DENTIFY_STACK_API_URL')

/** Владелец из dev-сида (`DemoOwnerSeed`, `DemoClinicSeed`): данные вымышленные. */
export const OWNER = {
  login: 'owner@demo.dentify.kz',
  fullName: 'Демо Владелец',
  initials: 'ДВ',
  clinic: 'Dentify Demo',
  branch: 'Абая, 150',
} as const

/** Шесть разделов каркаса (PLT-17) и их пустые состояния (макет Sostoyaniya) — в порядке сайдбара. */
export const SECTIONS = [
  { path: '/schedule', title: 'Расписание', empty: 'Записей на сегодня пока нет', phone: 'bottom' },
  { path: '/patients', title: 'Пациенты', empty: 'Пациентов пока нет', phone: 'bottom' },
  { path: '/staff', title: 'Врачи', empty: 'Сотрудников пока нет', phone: 'more' },
  { path: '/inventory', title: 'Склад', empty: 'Склад пока пуст', phone: 'bottom' },
  { path: '/clinic', title: 'Клиника', empty: 'Клиника ещё не настроена', phone: 'more' },
  { path: '/access', title: 'Доступы', empty: 'Доступы пока не настроены', phone: 'more' },
] as const

/** Контекст без сессии: `test.use({ storageState: NO_SESSION })` — вход в самом тесте. */
export const NO_SESSION = { cookies: [], origins: [] }

/**
 * `test` спек живого стека: страница уже вошла dev-владельцем — storageState (cookie сессии
 * `DENTIFY_SESSION` и `XSRF-TOKEN`), который сохранил проект stack-setup (owner.setup.ts).
 */
export const test = base.extend({
  // Файл — в папке артефактов прогона (её выбирает verify через --output): у параллельных прогонов свой.
  // eslint-disable-next-line no-empty-pattern -- так Playwright объявляет фикстуру без зависимостей
  storageState: async ({}, provide, testInfo) => {
    await provide(ownerStatePath(testInfo))
  },
})

/** Где лежит storageState владельца: общий для stack-setup, stack-desktop, stack-mobile и stack-teardown. */
export function ownerStatePath(testInfo: TestInfo) {
  return join(testInfo.project.outputDir, '.auth', 'owner.json')
}

// ---------- вход и сессии через API (второе устройство, storageState) ----------

/** Значение cookie `XSRF-TOKEN`: его шлют заголовком `X-XSRF-TOKEN` с каждым изменением (PLT-04). */
export async function xsrfToken(from: APIRequestContext | BrowserContext): Promise<string> {
  const cookies = 'cookies' in from ? await from.cookies(STACK_URL) : (await from.storageState()).cookies
  const token = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value
  if (!token) throw new Error('нет cookie XSRF-TOKEN: сначала GET /api/v1/auth/csrf')
  return token
}

/** Вход владельцем через API, как SPA: CSRF-токен → POST /auth/login с ним. Ответ — как у GET /me. */
export async function apiLogin(request: APIRequestContext, { rememberMe = false } = {}): Promise<MeResponse> {
  const csrf = await request.get('/api/v1/auth/csrf')
  expect(csrf.status(), 'GET /api/v1/auth/csrf').toBe(204)
  const response = await request.post('/api/v1/auth/login', {
    headers: { 'X-XSRF-TOKEN': await xsrfToken(request) },
    data: { login: OWNER.login, password: ownerPassword(), rememberMe },
  })
  expect(response.status(), `вход ${OWNER.login}: ${await response.text()}`).toBe(200)
  return (await response.json()) as MeResponse
}

/** Выход через API: 204, сессия уничтожена на сервере. */
export async function apiLogout(request: APIRequestContext) {
  const response = await request.post('/api/v1/auth/logout', { headers: { 'X-XSRF-TOKEN': await xsrfToken(request) } })
  expect(response.status(), 'POST /api/v1/auth/logout').toBe(204)
}

/** Сессии своего аккаунта (экран «Активные сессии», IAM): открытые id, у текущей `current`. */
export async function accountSessions(request: APIRequestContext): Promise<AccountSessionResponse[]> {
  const response = await request.get('/api/v1/account/sessions')
  expect(response.status(), 'GET /api/v1/account/sessions').toBe(200)
  return (await response.json()) as AccountSessionResponse[]
}

/** Завершить одну сессию аккаунта со списка (DELETE /account/sessions/{id}) — как с другого устройства. */
export async function terminateSession(request: APIRequestContext, id: string) {
  const response = await request.delete(`/api/v1/account/sessions/${encodeURIComponent(id)}`, {
    headers: { 'X-XSRF-TOKEN': await xsrfToken(request) },
  })
  expect(response.status(), `DELETE /api/v1/account/sessions/${id}`).toBe(204)
}

// ---------- вход и выход через интерфейс ----------

/** Вход владельцем на экране входа (макет Vhod); «Запомнить это устройство» по умолчанию включено (IAM-03). */
export async function login(page: Page, { remember = true } = {}) {
  await expect(page.getByRole('heading', { level: 1, name: 'Вход' })).toBeVisible()
  await page.getByLabel('Телефон или e-mail').fill(OWNER.login)
  await page.getByLabel('Пароль', { exact: true }).fill(ownerPassword())
  await page.getByRole('checkbox', { name: 'Запомнить это устройство' }).setChecked(remember)
  await page.getByRole('button', { name: 'Войти' }).click()
}

/** Выход: на десктопе — меню пользователя в сайдбаре, на телефоне — «Ещё» → «Выйти». */
export async function logout(page: Page) {
  if (isDesktop()) {
    await page.getByRole('button', { name: `Меню пользователя: ${OWNER.fullName}` }).click()
    await page.getByRole('menuitem', { name: 'Выйти' }).click()
  } else {
    await bottomNav(page).getByRole('link', { name: 'Ещё' }).click()
    await page.getByRole('button', { name: 'Выйти' }).click()
  }
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Вход' })).toBeVisible()
}

/** Нижняя панель телефона (макет Telefon). */
export function bottomNav(page: Page) {
  return page.getByRole('navigation', { name: 'Главное меню' })
}

/** Вкладка снова видна: TanStack Query слушает visibilitychange и перезапрашивает /me. */
export async function refocusTab(page: Page) {
  await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange', { bubbles: true })))
}

/** Снимок шага — вложением в HTML-отчёт (в трейсе кадры есть и так). */
export async function snap(page: Page, name: string) {
  await page.evaluate(() => document.fonts.ready)
  await base.info().attach(name, { body: await page.screenshot(), contentType: 'image/png' })
}

// ---------- проверки безопасности с фронта (PLT-04, PLT-27) ----------

/** Все запросы страниц контекста (API-запросы самого теста через `page.request` сюда не попадают). */
export function recordRequests(context: BrowserContext): Request[] {
  const requests: Request[] = []
  context.on('request', (request) => requests.push(request))
  return requests
}

/** PLT-27: страница не обращается к сторонним доменам — и к API в обход своего origin тоже. */
export function expectSameOrigin(requests: Request[]) {
  const origin = new URL(STACK_URL).origin
  const api = new URL(STACK_API_URL).origin
  const urls = requests.map((request) => request.url()).filter((url) => !/^(data|blob):/.test(url))
  expect(urls.length, 'страница ничего не запросила — проверять нечего').toBeGreaterThan(0)
  expect(api, 'DENTIFY_STACK_API_URL — прямой адрес бэкенда, а не адрес фронта').not.toBe(origin)
  expect(urls.filter((url) => new URL(url).origin !== origin)).toEqual([])
  expect(urls.filter((url) => new URL(url).origin === api)).toEqual([])
}

/**
 * PLT-04: каждое изменение страницы (POST, PUT, PATCH, DELETE к /api) несёт `X-XSRF-TOKEN`, равный cookie
 * `XSRF-TOKEN` этого же запроса. Возвращает «МЕТОД путь» проверенных изменений.
 */
export async function expectCsrfOnChanges(requests: Request[]): Promise<string[]> {
  const changes = requests.filter(
    (request) => !['GET', 'HEAD', 'OPTIONS'].includes(request.method()) && new URL(request.url()).pathname.startsWith('/api/'),
  )
  const checked: string[] = []
  for (const request of changes) {
    const name = `${request.method()} ${new URL(request.url()).pathname}`
    const headers = await request.allHeaders()
    const cookie = /(?:^|;\s*)XSRF-TOKEN=([^;]+)/.exec(headers['cookie'] ?? '')?.[1]
    expect(headers['x-xsrf-token'], `${name}: заголовок X-XSRF-TOKEN`).toBeTruthy()
    expect(headers['x-xsrf-token'], `${name}: заголовок = cookie XSRF-TOKEN`).toBe(cookie)
    checked.push(name)
  }
  return checked
}

/**
 * PLT-04: cookie сессии — HttpOnly (скрипту страницы не видна), Secure, SameSite=Lax; `XSRF-TOKEN` скрипту
 * виден — так задумано (его читает SPA, `csrf.spa()`). Возвращает cookie сессии.
 */
export async function expectHttpOnlySession(page: Page) {
  const session = (await page.context().cookies(STACK_URL)).find((cookie) => cookie.name === 'DENTIFY_SESSION')
  expect(session, 'cookie DENTIFY_SESSION').toBeDefined()
  expect(session).toMatchObject({ httpOnly: true, secure: true, sameSite: 'Lax', path: '/' })
  const visible = await page.evaluate(() => document.cookie)
  expect(visible).not.toContain('DENTIFY_SESSION')
  expect(visible).toContain('XSRF-TOKEN=')
  return session!
}

/**
 * PLT-04: в localStorage и sessionStorage нет токенов — ни значений cookie сессии и CSRF, ни JWT, ни ключей
 * с «token», «session», «auth». Возвращает всё, что там лежит (для отчёта).
 */
export async function expectNoTokensInWebStorage(page: Page) {
  const secrets = (await page.context().cookies(STACK_URL))
    .filter((cookie) => cookie.name === 'DENTIFY_SESSION' || cookie.name === 'XSRF-TOKEN')
    .map((cookie) => cookie.value)
  const entries = await page.evaluate(() =>
    (['localStorage', 'sessionStorage'] as const).flatMap((area) =>
      Object.entries(window[area]).map(([key, value]) => ({ area, key, value: String(value) })),
    ),
  )
  const leaks = entries.filter(
    ({ key, value }) =>
      /token|jwt|bearer|session|auth|csrf|xsrf/i.test(key) ||
      /eyJ[\w-]+\.[\w-]+/.test(value) ||
      secrets.some((secret) => value.includes(secret)),
  )
  expect(leaks).toEqual([])
  return entries
}

// ---------- окружение ----------

function requiredEnv(name: string): string {
  const value = process.env[name]
  if (!value) throw new Error(`${name} не задан: проекты stack-* работают только против поднятого стека (docs/frontend.md «Тесты»)`)
  return value
}

let password: string | undefined

/** Пароль dev-владельца: DENTIFY_DEV_OWNER_PASSWORD из окружения, иначе из deploy/.env. */
export function ownerPassword(): string {
  password ??= process.env.DENTIFY_DEV_OWNER_PASSWORD ?? readEnvFile('DENTIFY_DEV_OWNER_PASSWORD')
  if (!password) {
    throw new Error('нет DENTIFY_DEV_OWNER_PASSWORD ни в окружении, ни в deploy/.env — запустите deploy/dev.ps1 up')
  }
  return password
}

function readEnvFile(name: string): string | undefined {
  const file = fileURLToPath(new URL('../../../deploy/.env', import.meta.url))
  if (!existsSync(file)) return undefined
  const line = readFileSync(file, 'utf8')
    .split(/\r?\n/)
    .find((row) => row.startsWith(`${name}=`))
  return line
    ?.slice(name.length + 1)
    .trim()
    .replace(/^(['"])(.*)\1$/, '$2')
}
