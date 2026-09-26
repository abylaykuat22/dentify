/**
 * Известные боковые панели (значения search-параметра `?panel=`). Неизвестное значение
 * в адресе заменяется «панель закрыта» (validateSearch в `_app.tsx`). Модуль без
 * зависимостей: его читает схема маршрута, которая живёт в начальном чанке.
 */
export const PANELS = ['search'] as const

export type PanelName = (typeof PANELS)[number]

export const SEARCH_PANEL: PanelName = 'search'
