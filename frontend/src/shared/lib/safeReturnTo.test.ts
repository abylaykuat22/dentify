import { describe, expect, it } from 'vitest'

import { isReturnPath, isSameOriginPath } from './safeReturnTo'

const ORIGIN = 'http://127.0.0.1:5173'

describe('returnTo — только путь этого приложения', () => {
  it('пропускает относительные пути с параметрами', () => {
    expect(isSameOriginPath('/clinic/prices', ORIGIN)).toBe(true)
    expect(isSameOriginPath('/patients?filter=children&page=3', ORIGIN)).toBe(true)
    expect(isSameOriginPath('/', ORIGIN)).toBe(true)
  })

  it.each([
    ['https://evil.example'],
    ['//evil.example'],
    // «\» браузер читает как «/»: new URL('/\\evil.example', origin) → http://evil.example/
    ['/\\evil.example'],
    ['\\\\evil.example'],
    ['javascript:alert(1)'],
    ['/\t/evil.example'],
    ['/\n/evil.example'],
    ['clinic/prices'],
    [''],
  ])('отсекает %j', (value) => {
    expect(isSameOriginPath(value, ORIGIN)).toBe(false)
  })
})

describe('returnTo — не сам вход', () => {
  it.each([['/login'], ['/login?returnTo=%2Fclinic'], ['/Login/'], ['/%6Cogin'], ['/login#top']])('отсекает %j', (value) => {
    expect(isReturnPath(value, ORIGIN)).toBe(false)
  })

  it('пропускает экраны кабинета и чужие для него пути с «login» внутри', () => {
    expect(isReturnPath('/clinic/prices', ORIGIN)).toBe(true)
    expect(isReturnPath('/access/logins', ORIGIN)).toBe(true)
    expect(isReturnPath('/?returnTo=%2Flogin', ORIGIN)).toBe(true)
    expect(isReturnPath('//evil.example', ORIGIN)).toBe(false)
  })
})
