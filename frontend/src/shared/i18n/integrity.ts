import { format } from 'date-fns/format'
import { IntlMessageFormat } from 'intl-messageformat'
import ts from 'typescript'

// Проверка целостности словаря (PLT-11, dentify R003) — чистые функции без побочных эффектов.
// Их зовёт dictionary.test.ts (`pnpm i18n:check`, входит в `pnpm test` и verify) на настоящих
// исходниках и словарях, а integrity.test.ts — на заготовках: так видно, что проверка ловит ошибки.
// В сборку приложения модуль не попадает — его импортируют только тесты.

/** Словари одного языка: пространство → содержимое JSON. */
export type Dictionaries = Record<string, unknown>

/** Строки словаря: `пространство:путь` → текст. Узлы (объекты) — отдельно, для шаблонных ключей. */
export function flattenKeys(dictionaries: Dictionaries): Map<string, string> {
  const keys = new Map<string, string>()
  const walk = (node: unknown, path: string) => {
    if (typeof node === 'string') {
      keys.set(path, node)
    } else if (node && typeof node === 'object' && !Array.isArray(node)) {
      for (const [key, value] of Object.entries(node)) walk(value, path.endsWith(':') ? `${path}${key}` : `${path}.${key}`)
    } else {
      keys.set(path, String(node))
    }
  }
  for (const [namespace, body] of Object.entries(dictionaries)) walk(body, `${namespace}:`)
  return keys
}

/**
 * Списки (массивы JSON) в словаре: `t(key, { returnObjects: true })` отдал бы плагину ICU массив
 * целиком, мимо разбора строк. Список пишется ключами по смыслу (`overlays.chairs.chair1`) и массивом
 * id в коде — шаблонный ключ `overlays.chairs.${id}`.
 */
export function listProblems(dictionaries: Dictionaries): Problem[] {
  const problems: Problem[] = []
  const walk = (node: unknown, path: string) => {
    if (Array.isArray(node)) {
      problems.push({ kind: 'icu', message: `«${path}» — список в словаре: каждая строка — свой ключ по смыслу` })
    } else if (node && typeof node === 'object') {
      for (const [key, value] of Object.entries(node)) walk(value, path.endsWith(':') ? `${path}${key}` : `${path}.${key}`)
    }
  }
  for (const [namespace, body] of Object.entries(dictionaries)) walk(body, `${namespace}:`)
  return problems
}

/** Ссылка на ключ из исходника: точный ключ или шаблон (`common:ui.status.${status}`). */
export interface KeyRef {
  file: string
  line: number
  /** Как написано в коде (шаблон — с `${…}`). */
  text: string
  /** Точный ключ `пространство:путь`; у шаблона — нет. */
  key?: string
  /** Шаблон как регулярное выражение по ключам словаря. */
  pattern?: RegExp
}

export interface Problem {
  kind: 'missing' | 'bare' | 'unused' | 'icu' | 'date' | 'language' | 'literal'
  message: string
}

const CYRILLIC = /\p{Script=Cyrillic}/u

/** Текст в исходнике: строка, шаблонная строка (каждый её кусок) и текст JSX. */
function isTextNode(node: ts.Node): node is ts.StringLiteral | ts.NoSubstitutionTemplateLiteral | ts.TemplateLiteralLikeNode | ts.JsxText {
  return (
    ts.isStringLiteral(node) ||
    ts.isNoSubstitutionTemplateLiteral(node) ||
    ts.isTemplateHead(node) ||
    ts.isTemplateMiddle(node) ||
    ts.isTemplateTail(node) ||
    ts.isJsxText(node)
  )
}

/** Сообщение для разработчика, не для пользователя: `console.*(…)` и `new Error(…)` (`TypeError`, …). */
function isDeveloperMessage(node: ts.Node): boolean {
  if (!ts.isCallExpression(node) && !ts.isNewExpression(node)) return false
  const callee = node.expression
  if (ts.isPropertyAccessExpression(callee)) return ts.isIdentifier(callee.expression) && callee.expression.text === 'console'
  return ts.isIdentifier(callee) && /Error$/.test(callee.text)
}

/**
 * Русская строка в коде мимо словаря (dentify R003): литерал с кириллицей — строка, шаблонная строка,
 * текст JSX — где угодно, а не только в JSX, как видит ESLint: `toast.add({ title: 'Готово' })`,
 * заголовок маршрута, строка в хуке, `const label = 'Сохранить'` с показом `{label}` позже. Интерфейс
 * пока только на русском, так что кириллица в коде — почти наверняка текст для человека. Комментарии не
 * в счёт (парсер TypeScript их не видит как строки); сообщения для разработчика — аргументы
 * `console.*(…)` и `new Error(…)` — можно. Какие файлы проверять (не тесты, не моки, не витрина) —
 * решает вызывающий (dictionary.test.ts).
 */
export function cyrillicLiterals(file: string, source: string): Problem[] {
  const kind = file.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS
  const sourceFile = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true, kind)
  const problems: Problem[] = []
  const visit = (node: ts.Node): void => {
    if (isDeveloperMessage(node)) return
    if (isTextNode(node) && CYRILLIC.test(node.text)) {
      const line = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile)).line + 1
      const text = node.getText(sourceFile).trim()
      const shown = text.length > 60 ? `${text.slice(0, 59)}…` : text
      problems.push({ kind: 'literal', message: `${file}:${line}: ${shown} — текст для пользователя пишется ключом i18n` })
    }
    ts.forEachChild(node, visit)
  }
  visit(sourceFile)
  return problems
}

const escapeRegExp = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

/** `common:ui.status.${status}` → /^common:ui\.status\..+$/ (подстановка — любой непустой хвост). */
function templatePattern(template: string): RegExp {
  const parts = template.split(/\$\{[^}]*\}/)
  return new RegExp(`^${parts.map(escapeRegExp).join('.+')}$`)
}

function lineOf(source: string, index: number): number {
  return source.slice(0, index).split('\n').length
}

interface FindOptions {
  /** Известные пространства: строка `пространство:путь` — ключ словаря. */
  namespaces: readonly string[]
  /**
   * Пространство для ключей без префикса в `t('…')` (витрина: `useTranslation('devUi')`). Не задано —
   * ключ без префикса в `t('…')` — ошибка: в приложении ключ пишется полностью.
   */
  defaultNamespace?: string
}

/**
 * Ключи в исходнике: строки `'пространство:путь'` где угодно (вызовы `t`, конфиги с `TKey`,
 * заголовки маршрутов), шаблоны `` `пространство:путь.${…}` `` и ключи без префикса в `t('…')`.
 */
export function findKeyRefs(file: string, source: string, { namespaces, defaultNamespace }: FindOptions): {
  refs: KeyRef[]
  bare: KeyRef[]
} {
  const ns = namespaces.map(escapeRegExp).join('|')
  const refs: KeyRef[] = []
  const bare: KeyRef[] = []

  for (const match of source.matchAll(new RegExp(`(['"])((?:${ns}):[\\w.-]+)\\1`, 'g'))) {
    const key = match[2] ?? ''
    refs.push({ file, line: lineOf(source, match.index), text: key, key })
  }
  for (const match of source.matchAll(new RegExp(`\`((?:${ns}):[^\`]*?\\$\\{[^\`]*)\``, 'g'))) {
    const text = match[1] ?? ''
    refs.push({ file, line: lineOf(source, match.index), text, pattern: templatePattern(text) })
  }
  // t('ключ') / t(`ключ.${x}`) без пространства.
  for (const match of source.matchAll(/\bt\(\s*(['"`])([^'"`]+)\1/g)) {
    const text = match[2] ?? ''
    if (text.includes(':') && !text.startsWith('${')) continue
    const line = lineOf(source, match.index)
    if (!defaultNamespace) {
      bare.push({ file, line, text })
      continue
    }
    const full = `${defaultNamespace}:${text}`
    refs.push(text.includes('${') ? { file, line, text: full, pattern: templatePattern(full) } : { file, line, text: full, key: full })
  }
  return { refs, bare }
}

const where = (ref: KeyRef) => `${ref.file}:${ref.line}`

/** Ключ из кода, которого нет в словаре; шаблон, под который не подходит ни один ключ. */
export function missingKeys(refs: readonly KeyRef[], keys: ReadonlyMap<string, string>): Problem[] {
  const all = [...keys.keys()]
  return refs.flatMap((ref): Problem[] => {
    if (ref.key !== undefined) {
      return keys.has(ref.key) ? [] : [{ kind: 'missing', message: `${where(ref)}: нет ключа «${ref.key}» в словаре ru` }]
    }
    const pattern = ref.pattern
    return pattern && all.some((key) => pattern.test(key))
      ? []
      : [{ kind: 'missing', message: `${where(ref)}: под шаблон «${ref.text}» не подходит ни один ключ словаря ru` }]
  })
}

/** Строки словаря, на которые не ссылается код (ни точно, ни шаблоном). */
export function unusedKeys(keys: ReadonlyMap<string, string>, refs: readonly KeyRef[]): Problem[] {
  const exact = new Set(refs.flatMap((ref) => (ref.key === undefined ? [] : [ref.key])))
  const patterns = refs.flatMap((ref) => (ref.pattern ? [ref.pattern] : []))
  return [...keys.keys()]
    .filter((key) => !exact.has(key) && !patterns.some((pattern) => pattern.test(key)))
    .map((key) => ({ kind: 'unused', message: `«${key}» не используется в коде — удалите строку или сошлитесь на неё` }))
}

type IcuElement = ReturnType<IntlMessageFormat['getAst']>[number]

/** Имена аргументов ICU-строки: `{name}`, `{age, plural, …}` и аргументы внутри вариантов. */
export function icuArguments(message: string, locale = 'ru'): string[] {
  const names = new Set<string>()
  const walk = (elements: readonly IcuElement[]) => {
    for (const element of elements) {
      // 0 — текст, 7 — «#» внутри plural: не аргументы.
      if (element.type !== 0 && element.type !== 7 && 'value' in element && typeof element.value === 'string') {
        names.add(element.value)
      }
      if ('options' in element) {
        for (const option of Object.values(element.options)) walk(option.value)
      }
      if ('children' in element) walk(element.children)
    }
  }
  walk(new IntlMessageFormat(message, locale, undefined, { ignoreTag: true }).getAst())
  return [...names].sort()
}

/**
 * Каждая строка — корректный ICU MessageFormat (i18next-icu): разбирается, у plural/select есть
 * `other`, нет старого синтаксиса `{{x}}` i18next. Шаблоны дат (`common:dates.*`) — ещё и date-fns.
 */
export function icuProblems(keys: ReadonlyMap<string, string>, locale: string): Problem[] {
  const problems: Problem[] = []
  for (const [key, message] of keys) {
    try {
      icuArguments(message, locale)
    } catch (error) {
      problems.push({ kind: 'icu', message: `«${key}» (${locale}): не ICU — ${(error as Error).message}` })
      continue
    }
    if (key.startsWith('common:dates.')) {
      try {
        format(new Date(2026, 8, 24, 14, 5), message)
      } catch (error) {
        problems.push({ kind: 'date', message: `«${key}» (${locale}): не шаблон date-fns — ${(error as Error).message}` })
      }
    }
  }
  return problems
}

/**
 * Словарь другого языка (kk) повторяет русский: те же ключи и те же аргументы ICU в каждой строке
 * (PLT-34: полнота kk обязательна). Ключ, которого нет в ru, — опечатка или устаревшая строка.
 */
export function languageProblems(
  base: ReadonlyMap<string, string>,
  other: ReadonlyMap<string, string>,
  language: string,
): Problem[] {
  const problems: Problem[] = []
  for (const [key, message] of base) {
    const translated = other.get(key)
    if (translated === undefined) {
      problems.push({ kind: 'language', message: `${language}: нет перевода «${key}»` })
      continue
    }
    try {
      const expected = icuArguments(message).join(', ')
      const actual = icuArguments(translated, language).join(', ')
      if (expected !== actual) {
        problems.push({ kind: 'language', message: `${language}: у «${key}» аргументы {${actual}}, в ru — {${expected}}` })
      }
    } catch {
      // Сломанный ICU покажет icuProblems.
    }
  }
  for (const key of other.keys()) {
    if (!base.has(key)) problems.push({ kind: 'language', message: `${language}: лишний ключ «${key}» — его нет в ru` })
  }
  return problems
}
