// Функции — по отдельным путям пакета: в тестах и dev не грузится весь date-fns (сотни модулей).
import type { Day, Locale } from 'date-fns'
import { getDefaultOptions } from 'date-fns/getDefaultOptions'
import { setDefaultOptions } from 'date-fns/setDefaultOptions'
import { ru } from 'date-fns/locale/ru'

import { i18n } from './index'

// Даты и месяцы — date-fns с локалью языка интерфейса (PLT-11): формат, названия дней и месяцев,
// начало недели. Локаль и первый день недели ставятся глобально (`setDefaultOptions` date-fns) и
// меняются вместе с языком, поэтому `format`, `startOfWeek`, `eachWeekOfInterval` и т. п. берут их
// сами — после того как этот модуль загружен: импортируйте даты через него или
// `@/shared/lib/format` (сам date-fns в начальную загрузку не входит — только в экраны с датами).
// Календарю (react-day-picker) их передают явно — `dateLocale()` и `weekStartsOn()`.

/**
 * Первый день недели — настройка клиники (макет Klinika-Nastrojki). Пока настроек в API нет —
 * константа: понедельник, как принято в Казахстане.
 * TODO(T-CLN-*): брать из настроек клиники (/api/v1/…), когда они появятся, и звать `applyDateLocale`.
 */
export const CLINIC_WEEK_STARTS_ON: Day = 1

/** Локали date-fns, кроме русской, — отдельными чанками: грузятся вместе со словарём языка. */
const LAZY_LOCALES: Readonly<Record<string, () => Promise<Locale>>> = {
  kk: () => import('date-fns/locale/kk').then((module) => module.kk),
}

const loaded = new Map<string, Locale>([['ru', ru]])

/** Загрузить локаль date-fns языка (для русского — ничего). Неизвестный язык — русская. */
export async function loadDateLocale(language: string): Promise<void> {
  const load = LAZY_LOCALES[language]
  if (loaded.has(language) || !load) return
  loaded.set(language, await load())
}

/** Сделать локаль языка (или русскую, пока она не загружена) и первый день недели клиники текущими. */
export function applyDateLocale(language: string, weekStartsOn: Day = CLINIC_WEEK_STARTS_ON): void {
  setDefaultOptions({ locale: loaded.get(language) ?? ru, weekStartsOn })
}

/** Текущая локаль date-fns — для компонентов, которые принимают её явно (календарь). */
export function dateLocale(): Locale {
  return getDefaultOptions().locale ?? ru
}

/** Текущий первый день недели (0 — воскресенье, 1 — понедельник). */
export function weekStartsOn(): Day {
  return getDefaultOptions().weekStartsOn ?? CLINIC_WEEK_STARTS_ON
}

applyDateLocale(i18n.language)
i18n.on('languageChanged', (language) => applyDateLocale(language))
