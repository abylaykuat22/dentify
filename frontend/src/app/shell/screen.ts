import { useMatches } from '@tanstack/react-router'

import type { TKey } from '@/shared/i18n/keys'

// Как каркас показывает экран — `staticData` маршрута. Не делится autoCodeSplitting'ом
// (остаётся в дереве маршрутов), поэтому шапка знает о нём до загрузки чанка экрана.
declare module '@tanstack/react-router' {
  interface StaticDataRouteOption {
    /** Заголовок шапки вместо названия раздела: экран внутри раздела («Новая запись»). */
    titleKey?: TKey
    /** Без вкладок раздела и без жёлтой кнопки шапки: мастер, карточка — у них свои действия. */
    bare?: boolean
    /** Экран рисует свою шапку (меню «Ещё»: карточка пользователя вместо заголовка). */
    ownHeader?: boolean
  }
}

/** Настройки экрана из `staticData` самого глубокого совпавшего маршрута. */
export function useScreenOptions() {
  const titleKey = useMatches({ select: (matches) => matches.at(-1)?.staticData.titleKey })
  const bare = useMatches({ select: (matches) => matches.at(-1)?.staticData.bare === true })
  const ownHeader = useMatches({ select: (matches) => matches.at(-1)?.staticData.ownHeader === true })
  return { titleKey, bare, ownHeader }
}
