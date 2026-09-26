import { describe, expect, it } from 'vitest'

import { isSearchHotkey } from './hotkeys'

const press = (init: KeyboardEventInit) => new KeyboardEvent('keydown', init)

describe('Ctrl K — поиск пациента', () => {
  it('срабатывает в латинской раскладке', () => {
    expect(isSearchHotkey(press({ key: 'k', code: 'KeyK', ctrlKey: true }))).toBe(true)
    expect(isSearchHotkey(press({ key: 'K', code: 'KeyK', ctrlKey: true, shiftKey: true }))).toBe(true)
  })

  it('срабатывает в русской и казахской раскладке: key «л», физическая клавиша KeyK', () => {
    expect(isSearchHotkey(press({ key: 'л', code: 'KeyK', ctrlKey: true }))).toBe(true)
    expect(isSearchHotkey(press({ key: 'л', code: 'KeyK', metaKey: true }))).toBe(true)
  })

  it('не срабатывает без Ctrl/⌘, с Alt и на других клавишах', () => {
    expect(isSearchHotkey(press({ key: 'л', code: 'KeyK' }))).toBe(false)
    expect(isSearchHotkey(press({ key: 'л', code: 'KeyK', ctrlKey: true, altKey: true }))).toBe(false)
    expect(isSearchHotkey(press({ key: 'о', code: 'KeyJ', ctrlKey: true }))).toBe(false)
  })
})
