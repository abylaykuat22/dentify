import { describe, expect, it } from 'vitest'

import devUi from '@/dev/ui/ru.json'
import { codeForStatus } from '@/shared/api/http'
import { dictionaryText } from '@/shared/api/problem'

import {
  cyrillicLiterals,
  type Dictionaries,
  findKeyRefs,
  flattenKeys,
  icuProblems,
  type KeyRef,
  languageProblems,
  listProblems,
  missingKeys,
  unusedKeys,
} from './integrity'
import { NAMESPACES } from './resources'

// Целостность словаря (PLT-11, dentify R003) на настоящих исходниках: `pnpm i18n:check`, входит в
// `pnpm test` и verify. Падает, если код ссылается на ключ, которого нет в ru; если ключ написан без
// пространства; если строка словаря не нужна коду; если строка — не ICU; если словарь другого языка
// (locales/<язык>/) не повторяет русский ключ в ключ; если в коде приложения русская строка мимо словаря
// (не только в JSX — там её ловит ESLint, — но и в toast, хуке, константе). Логика — integrity.ts (её тест
// — integrity.test.ts).

// Исходники как текст (Vite `?raw`): ключ — «/src/app/shell/Header.tsx».
const SOURCES = import.meta.glob<string>(
  ['/src/**/*.{ts,tsx}', '!/src/shared/api/generated/**', '!/src/shared/i18n/integrity*.ts', '!/src/shared/i18n/dictionary.test.ts'],
  { query: '?raw', import: 'default', eager: true },
)
// Словари всех языков: «./locales/ru/common.json».
const LOCALES = import.meta.glob<Record<string, unknown>>('./locales/*/*.json', { import: 'default', eager: true })

function dictionariesOf(language: string): Dictionaries {
  const dictionaries: Dictionaries = {}
  for (const [path, body] of Object.entries(LOCALES)) {
    const match = /^\.\/locales\/([^/]+)\/([^/]+)\.json$/.exec(path)
    if (match?.[1] === language && match[2]) dictionaries[match[2]] = body
  }
  return dictionaries
}

const languages = [...new Set(Object.keys(LOCALES).map((path) => path.split('/')[2] ?? ''))].sort()
const ru = flattenKeys({ ...dictionariesOf('ru'), devUi })

const isTest = (file: string) => /\.test\.tsx?$/.test(file) || file.startsWith('/src/test/')
const isDevUi = (file: string) => file.startsWith('/src/dev/')
// Моки MSW изображают бэкенд: вымышленные клиники, сотрудники и тексты ProblemDetail — его данные.
const isMock = (file: string) => file.startsWith('/src/shared/api/mocks/')

function scan(files: (file: string) => boolean): { refs: KeyRef[]; bare: KeyRef[] } {
  const found = Object.entries(SOURCES)
    .filter(([file]) => files(file))
    .map(([file, source]) =>
      findKeyRefs(file.slice(1), source, {
        namespaces: [...NAMESPACES, 'devUi'],
        // Витрина пишет ключи своего пространства без префикса (`useTranslation('devUi')`).
        defaultNamespace: isDevUi(file) ? 'devUi' : undefined,
      }),
    )
  return { refs: found.flatMap((item) => item.refs), bare: found.flatMap((item) => item.bare) }
}

const app = scan((file) => !isTest(file))
const tests = scan(isTest)
const messages = (problems: { message: string }[]) => problems.map((problem) => problem.message)

describe('словарь ru ↔ код (pnpm i18n:check)', () => {
  it('пространства ru — ровно NAMESPACES, по файлу на пространство', () => {
    expect(Object.keys(dictionariesOf('ru')).sort()).toEqual([...NAMESPACES].sort())
  })

  it('каждый ключ из кода есть в словаре ru (строки, конфиги TKey, шаблоны)', () => {
    expect(app.refs.length).toBeGreaterThan(100)
    expect(messages(missingKeys(app.refs, ru))).toEqual([])
  })

  it('тесты ссылаются только на существующие ключи', () => {
    expect(messages(missingKeys(tests.refs, ru))).toEqual([])
  })

  it('в приложении ключ пишется полностью — `пространство:путь`', () => {
    expect(app.bare.map((ref) => `${ref.file}:${ref.line}: t('${ref.text}') — без пространства`)).toEqual([])
  })

  it('в словаре нет строк, не нужных коду', () => {
    expect(messages(unusedKeys(ru, app.refs))).toEqual([])
  })

  it('каждая строка — ICU MessageFormat (i18next-icu), шаблоны дат — date-fns', () => {
    expect(messages(icuProblems(ru, 'ru'))).toEqual([])
  })

  it('в словаре нет списков (массивов): каждая строка — свой ключ и проходит ICU', () => {
    expect(messages(listProblems({ ...dictionariesOf('ru'), devUi }))).toEqual([])
  })

  it('в коде приложения нет русских строк мимо словаря (моки, витрина и сообщения разработчику — можно)', () => {
    const files = Object.entries(SOURCES).filter(([file]) => !isTest(file) && !isMock(file) && !isDevUi(file))
    expect(files.length).toBeGreaterThan(50)
    expect(messages(files.flatMap(([file, source]) => cyrillicLiterals(file.slice(1), source)))).toEqual([])
  })

  it('текст есть у каждого кода, который фронт подставляет сам по HTTP-статусу', () => {
    const codes = new Set(Array.from({ length: 200 }, (_, index) => codeForStatus(400 + index)))
    expect([...codes].filter((code) => dictionaryText(code) === null)).toEqual([])
  })
})

describe('словари других языков (locales/<язык>/, PLT-34)', () => {
  it('повторяют ru ключ в ключ, с теми же аргументами ICU; строки — ICU (сейчас других языков нет)', () => {
    const base = flattenKeys(dictionariesOf('ru'))
    const problems = languages
      .filter((language) => language !== 'ru')
      .flatMap((language) => {
        const keys = flattenKeys(dictionariesOf(language))
        return [
          ...languageProblems(base, keys, language),
          ...icuProblems(keys, language),
          ...listProblems(dictionariesOf(language)),
        ]
      })
    expect(messages(problems)).toEqual([])
  })
})
