import { describe, expect, it } from 'vitest'

import { isSameOriginPath } from './safeReturnTo'

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
