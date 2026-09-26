import i18next from 'i18next'
import ICU from 'i18next-icu'
import resourcesToBackend from 'i18next-resources-to-backend'
import { initReactI18next } from 'react-i18next'

import { isFlagOn } from '@/shared/lib/flags'

import { NAMESPACES, ru } from './resources'

export { NAMESPACES, type Namespace } from './resources'

// dentify R003, PLT-11: интерфейс только на русском, но готов к казахскому без правок компонентов.
// Строки — ICU MessageFormat (i18next-icu): «{age, plural, one {# год} few {# года} many {# лет} …}».
// Включить казахский (PLT-34) = положить словари `locales/kk/*.json` и включить флаг i18n.kk.

/** Языки интерфейса: русский (базовый; в MVP единственный) и казахский (PLT-34). */
export const UI_LANGUAGES = ['ru', 'kk'] as const
export type UiLanguage = (typeof UI_LANGUAGES)[number]
export const BASE_LANGUAGE = 'ru' satisfies UiLanguage

/**
 * Выбор языка на этом устройстве — до входа и между перезагрузками.
 * TODO(IAM-31): язык аккаунта из /me (`account.language`) и его смена через API.
 */
const STORAGE_KEY = 'dentify-lang'

// Словари языков, кроме русского, — `locales/<язык>/<пространство>.json`, каждый своим чанком: в
// начальную загрузку не входят. Язык появляется, как только его файлы лежат в папке.
const LAZY_DICTIONARIES = import.meta.glob<Record<string, unknown>>(
  ['./locales/*/*.json', '!./locales/ru/*.json'],
  { import: 'default' },
)

function loadDictionary(language: string, namespace: string): Promise<Record<string, unknown>> {
  const load = LAZY_DICTIONARIES[`./locales/${language}/${namespace}.json`]
  return load ? load() : Promise.reject(new Error(`i18n: нет словаря ${language}/${namespace}`))
}

/** Есть ли словарь языка: русский — всегда; другой — файлы в `locales/` или уже добавленный набор. */
export function hasDictionary(language: UiLanguage): boolean {
  return (
    language === BASE_LANGUAGE ||
    `./locales/${language}/common.json` in LAZY_DICTIONARIES ||
    i18next.hasResourceBundle(language, 'common')
  )
}

/**
 * Языки, между которыми можно переключаться: флаг i18n.kk выключен (dentify R003) — только русский;
 * включён — языки со словарём. Меньше двух — переключателя «Рус | Қаз» нет нигде.
 */
export function uiLanguages(): UiLanguage[] {
  if (!isFlagOn('i18n.kk')) return [BASE_LANGUAGE]
  return UI_LANGUAGES.filter(hasDictionary)
}

/** Сменить язык интерфейса: словарь и локаль дат грузятся до смены, выбор запоминается. */
export async function changeUiLanguage(language: UiLanguage): Promise<void> {
  const target = uiLanguages().includes(language) ? language : BASE_LANGUAGE
  // Локаль дат — до смены языка: экраны перерисуются уже с ней (модуль дат грузится лениво).
  const { loadDateLocale } = await import('./dates')
  await loadDateLocale(target)
  await i18next.changeLanguage(target)
  try {
    localStorage.setItem(STORAGE_KEY, target)
  } catch {
    // Хранилище недоступно (приватный режим) — язык не запомнится, но сменится.
  }
}

function storedLanguage(): UiLanguage | undefined {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return UI_LANGUAGES.find((language) => language === value)
  } catch {
    return undefined
  }
}

// Атрибут lang страницы — язык интерфейса: чтение с экрана, переносы, выбор глифов шрифта. Даты
// (date-fns) следуют за языком сами — `./dates`.
i18next.on('languageChanged', (language) => {
  if (typeof document !== 'undefined') document.documentElement.lang = language
})

const icu = new ICU({
  // Скомпилированные строки кэшируются; словарь догрузили или заменили — кэш сбрасывается.
  bindI18nStore: 'added removed',
  // Сломанная ICU-строка ловится ещё в тестах (dictionary.test.ts); если всё же дошла — видно в консоли.
  parseErrorHandler: (error, key, text) => {
    if (import.meta.env.DEV) console.error(`i18n: ошибка ICU в «${key}»`, error)
    return text
  },
})

void i18next
  .use(icu)
  .use(resourcesToBackend(loadDictionary))
  .use(initReactI18next)
  .init({
    lng: BASE_LANGUAGE,
    fallbackLng: BASE_LANGUAGE,
    load: 'currentOnly',
    // Копии: i18next меняет эти массивы на месте (addNamespaces / removeNamespaces).
    ns: [...NAMESPACES],
    // Все пространства: ключ пишется полностью (`shell:nav.schedule`) в любом компоненте. Ключ без
    // префикса i18next ищет по порядку во всех пространствах и берёт первое совпадение
    // (`nav.schedule` найдётся в `shell`) — неоднозначно, поэтому в приложении он запрещён: его
    // ловит `pnpm i18n:check` (dictionary.test.ts).
    defaultNS: [...NAMESPACES],
    fallbackNS: false,
    resources: { ru },
    // Русский — в сборке, остальные языки догружает resourcesToBackend; init синхронный.
    partialBundledLanguages: true,
    initAsync: false,
    interpolation: { escapeValue: false },
    react: { useSuspense: false },
    // Нет ключа — ошибка в консоли dev-сборки и тестов (e2e проверяет консоль на всех экранах).
    saveMissing: import.meta.env.DEV,
    missingKeyHandler: (languages, namespace, key) => {
      console.error(`i18n: нет ключа ${namespace}:${key} (${languages.join(', ')})`)
    },
  })

export const i18n = i18next

/**
 * Язык при запуске: сохранённый на устройстве, если он сейчас доступен (флаг и словарь), иначе
 * русский. Точка входа ждёт его до первого экрана — без мигания русским.
 */
export const i18nReady: Promise<void> = (async () => {
  const stored = storedLanguage()
  if (stored === undefined || stored === BASE_LANGUAGE || !uiLanguages().includes(stored)) return
  try {
    await changeUiLanguage(stored)
  } catch {
    // Словарь не догрузился (нет связи) — работаем на русском.
  }
})()
