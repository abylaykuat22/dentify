import 'i18next'

// Словарь витрины /dev/ui (только dev-сборка) — отдельное пространство `devUi`: в production
// словарь он не попадает, импорт здесь — только тип.
import type devUi from '@/dev/ui/ru.json'

import type { NAMESPACES, ru } from './resources'

declare module 'i18next' {
  interface CustomTypeOptions {
    /** Все пространства (как `defaultNS` в init): `t('shell:nav.schedule')` типизирован без `useTranslation('shell')`. */
    defaultNS: typeof NAMESPACES
    resources: typeof ru & { devUi: typeof devUi }
    /**
     * Строки — ICU MessageFormat (i18next-icu): `{name}`, `{age, plural, …}`, а не `{{name}}`. Разбор
     * переменных по `{{…}}` в типах выключен — иначе plural дал бы «призрачные» переменные
     * (документация i18next, TypeScript → ICU). Сами строки проверяет dictionary.test.ts.
     */
    parseInterpolation: false
  }
}
