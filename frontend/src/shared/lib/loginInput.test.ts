import { describe, expect, it } from 'vitest'

import { applyLoginEdit, type LoginEdit, loginInputMode, loginKind, loginState, type LoginState } from './loginInput'

/** Набрать текст по символу в конец поля — как клавиатура. */
function type(text: string, from: LoginState = loginState('')): LoginEdit {
  let state: LoginEdit = { ...from, caret: from.display.length }
  for (const char of text) {
    const next = state.display.slice(0, state.caret) + char + state.display.slice(state.caret)
    state = applyLoginEdit(state, next, state.caret + 1)
  }
  return state
}

/** Backspace `times` раз с конца поля. */
function backspace(from: LoginEdit, times = 1): LoginEdit {
  let state = from
  for (let step = 0; step < times; step += 1) {
    const next = state.display.slice(0, state.caret - 1) + state.display.slice(state.caret)
    state = applyLoginEdit(state, next, state.caret - 1)
  }
  return state
}

describe('loginKind: тип логина по вводу (IAM-02: есть «@» — e-mail, иначе телефон)', () => {
  it.each([
    ['', 'empty'],
    ['   ', 'empty'],
    ['8 707 123 45 67', 'phone'],
    ['+7 (707) 123-45-67', 'phone'],
    ['+', 'phone'],
    ['dinara@example.kz', 'email'],
    ['87071234567@mail.ru', 'email'],
    ['dinara', 'email'],
  ] as const)('%j → %s', (value, kind) => {
    expect(loginKind(value)).toBe(kind)
  })

  it('клавиатура: цифровая для телефона, с «@» — для e-mail и пустого поля', () => {
    expect(loginInputMode('8707')).toBe('tel')
    expect(loginInputMode('')).toBe('email')
    expect(loginInputMode('dinara@')).toBe('email')
  })
})

describe('маска телефона +7', () => {
  it.each([
    ['87071234567', '+7 707 123 45 67'],
    ['+77071234567', '+7 707 123 45 67'],
    ['7071234567', '+7 707 123 45 67'],
    ['77071234567', '+7 707 123 45 67'],
    ['7771234567', '+7 777 123 45 67'],
  ])('набор %s → %s, значение — набранные цифры', (typed, display) => {
    const state = type(typed)
    expect(state.display).toBe(display)
    expect(state.raw).toBe(typed)
    expect(state.caret).toBe(display.length)
  })

  it('«8» сразу показывает «+7 », курсор — после префикса', () => {
    expect(type('8')).toMatchObject({ display: '+7 ', raw: '8', caret: 3 })
  })

  it('«+» и «7» — свой код страны; «+» с другим кодом — без маски', () => {
    expect(type('+')).toMatchObject({ display: '+', raw: '+' })
    expect(type('+7707')).toMatchObject({ display: '+7 707', raw: '+7707' })
    expect(type('+4930123')).toMatchObject({ display: '+4930123', raw: '+4930123', trunk: null })
  })

  it('вставка номера в любой записи — одна маска', () => {
    for (const pasted of ['+7 (707) 123-45-67', '8 707 123 45 67', '8(707)1234567']) {
      const state = applyLoginEdit(loginState(''), pasted)
      expect(state.display).toBe('+7 707 123 45 67')
      expect(state.caret).toBe(state.display.length)
    }
  })

  it('пробел и лишние знаки в номере не ставятся, курсор остаётся на месте', () => {
    const state = type('8707 ')
    expect(state).toMatchObject({ display: '+7 707', raw: '8707', caret: 6 })
  })

  it('backspace стирает цифры, затем префикс; поле пустеет', () => {
    const full = type('87071234567')
    expect(backspace(full)).toMatchObject({ display: '+7 707 123 45 6', raw: '8707123456' })
    const onlyPrefix = backspace(full, 10)
    expect(onlyPrefix).toMatchObject({ display: '+7 ', raw: '8' })
    expect(backspace(onlyPrefix)).toMatchObject({ display: '', raw: '' })
    // Номер без кода: последняя цифра — и поле пустое.
    expect(backspace(type('7'))).toMatchObject({ display: '', raw: '' })
  })

  it('правка в середине номера: курсор — сразу после вставленной цифры', () => {
    const full = type('87071234567')
    // Курсор после «707» (позиция 6), набрали «9».
    const next = `${full.display.slice(0, 6)}9${full.display.slice(6)}`
    const edited = applyLoginEdit(full, next, 7)
    expect(edited.raw).toBe('870791234567')
    expect(edited.display.slice(0, edited.caret)).toBe('+7 707 9')
  })

  it('перестроенный номер «7 707…» при правке не прыгает обратно', () => {
    const full = type('77071234567')
    expect(full.trunk).toBe('7')
    expect(backspace(full)).toMatchObject({ display: '+7 707 123 45 6', raw: '7707123456' })
  })
})

describe('e-mail — без маски', () => {
  it('буквы и «@» — как ввели', () => {
    expect(type('dinara@example.kz')).toMatchObject({ display: 'dinara@example.kz', raw: 'dinara@example.kz', trunk: null })
  })

  it('e-mail из цифр телефона: после «@» цифры возвращаются как набраны', () => {
    const state = type('87071234567@mail.ru')
    expect(state).toMatchObject({ display: '87071234567@mail.ru', raw: '87071234567@mail.ru', trunk: null })
    expect(type('7071234567@').raw).toBe('7071234567@')
    expect(type('+77071234567@').raw).toBe('+77071234567@')
  })

  it('стёрли «@…» — снова телефон под маской', () => {
    const email = type('87071234567@')
    expect(backspace(email)).toMatchObject({ display: '+7 707 123 45 67', raw: '87071234567' })
  })
})
