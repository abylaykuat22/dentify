import access from './locales/ru/access.json'
import auth from './locales/ru/auth.json'
import clinic from './locales/ru/clinic.json'
import common from './locales/ru/common.json'
import errors from './locales/ru/errors.json'
import inventory from './locales/ru/inventory.json'
import patients from './locales/ru/patients.json'
import profile from './locales/ru/profile.json'
import schedule from './locales/ru/schedule.json'
import shell from './locales/ru/shell.json'
import staff from './locales/ru/staff.json'

/**
 * Пространства имён словаря (PLT-11) — по модулям: `common` (дизайн-система, заголовки, состояния
 * экранов), `shell` (каркас: навигация, вкладки, шапка, «Ещё»), `auth` (вход, выбор клиники),
 * `errors` (тексты по коду ProblemDetail), дальше — модули домена. Файл пространства —
 * `locales/<язык>/<пространство>.json`. Ключ в коде всегда полный — `пространство:путь`: без префикса
 * i18next искал бы его по порядку во всех пространствах (`defaultNS` — весь список) и брал первое
 * совпадение, поэтому ключ без префикса запрещён (ловит `pnpm i18n:check`).
 */
export const NAMESPACES = [
  'common',
  'shell',
  'auth',
  'errors',
  'schedule',
  'patients',
  'staff',
  'inventory',
  'clinic',
  'access',
  'profile',
] as const

export type Namespace = (typeof NAMESPACES)[number]

/**
 * Русский словарь — базовый язык (dentify R003): входит в сборку целиком, без запроса. Словари
 * других языков грузятся лениво (`index.ts`, `loadDictionary`).
 */
export const ru = {
  common,
  shell,
  auth,
  errors,
  schedule,
  patients,
  staff,
  inventory,
  clinic,
  access,
  profile,
} as const satisfies Record<Namespace, object>
