type HotkeyEvent = Pick<KeyboardEvent, 'ctrlKey' | 'metaKey' | 'altKey' | 'code' | 'key'>

/**
 * Ctrl K / ⌘K — поиск пациента. Проверяем физическую клавишу (`code`), а не символ: в русской
 * и казахской раскладке `key` = «л», а `code` всё равно `KeyK`. `key` — запасной путь для
 * раскладок, где буква K стоит не на месте QWERTY-клавиши (Dvorak и т. п.).
 */
export function isSearchHotkey(event: HotkeyEvent): boolean {
  if (!(event.ctrlKey || event.metaKey) || event.altKey) return false
  return event.code === 'KeyK' || event.key.toLowerCase() === 'k'
}
