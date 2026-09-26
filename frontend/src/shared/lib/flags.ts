/**
 * Флаги функций фронта. Пока — на сборку: переменная окружения `VITE_<ИМЯ>=true`, где имя — имя
 * флага с `_` вместо точки (`i18n.kk` → `VITE_I18N_KK`, как `dentify.i18n.kk` бэкенда). Выключенный
 * флаг прячет интерфейс целиком — пункта нет, а не «серый». По умолчанию флаг выключен.
 *
 * TODO(PLT-34): одинаковое значение с флагом бэкенда `dentify.i18n.kk` — отдавать флаги с сервера
 * (публичный конфиг или /me) и читать их здесь же, не меняя вызовы `isFlagOn`.
 */
const FLAG_ENV = {
  /**
   * dentify R003: казахский интерфейс. Выключен до PLT-34 — переключателя «Рус | Қаз» нет ни на
   * входе, ни в профиле, ни в меню «Ещё»; все работают на русском. Включён — переключатель есть,
   * если есть словарь kk (`src/shared/i18n/locales/kk/`).
   */
  'i18n.kk': 'VITE_I18N_KK',
} as const satisfies Record<string, keyof ImportMetaEnv>

export type FlagName = keyof typeof FLAG_ENV

/** Включён ли флаг. Читается при каждом вызове: тесты переключают его через `vi.stubEnv`. */
export function isFlagOn(flag: FlagName): boolean {
  return import.meta.env[FLAG_ENV[flag]] === 'true'
}
