import { describe, expect, it } from 'vitest'

import {
  cyrillicLiterals,
  findKeyRefs,
  flattenKeys,
  icuArguments,
  icuProblems,
  languageProblems,
  listProblems,
  missingKeys,
  unusedKeys,
} from './integrity'

// Сама проверка словаря (integrity.ts) — на заготовках: видно, что `pnpm i18n:check` падает на каждой
// ошибке, а не просто зелёный.

const NS = { namespaces: ['common', 'shell', 'errors'] }

const dictionary = flattenKeys({
  common: { ui: { close: 'Закрыть', status: { planned: 'Придёт', done: 'Готово' } } },
  shell: { nav: { schedule: 'Расписание' } },
  errors: { iam: { login: { invalid: 'Неверный пароль' } } },
})

describe('flattenKeys', () => {
  it('строки словаря — `пространство:путь`', () => {
    expect([...dictionary.keys()]).toEqual([
      'common:ui.close',
      'common:ui.status.planned',
      'common:ui.status.done',
      'shell:nav.schedule',
      'errors:iam.login.invalid',
    ])
  })
})

describe('findKeyRefs', () => {
  const source = [
    "const title = t('shell:nav.schedule')",
    "const tab = { labelKey: 'shell:nav.nope' }",
    'const status = t(`common:ui.status.${status}`)',
    "const bare = t('ui.close')",
    "const other = get('X-XSRF-TOKEN'); const url = 'https://example.kz'",
  ].join('\n')

  it('ключи в t(), в конфигах и шаблоны; строки не-ключи пропускаются', () => {
    const { refs, bare } = findKeyRefs('Demo.tsx', source, NS)
    expect(refs.map((ref) => [ref.line, ref.key ?? ref.pattern?.source])).toEqual([
      [1, 'shell:nav.schedule'],
      [2, 'shell:nav.nope'],
      [3, '^common:ui\\.status\\..+$'],
    ])
    expect(bare.map((ref) => [ref.line, ref.text])).toEqual([[4, 'ui.close']])
  })

  it('в витрине ключ без префикса — ключ её пространства', () => {
    const { refs, bare } = findKeyRefs('DevUi.tsx', "t('colors.title'); t(`colors.${name}.hint`)", {
      namespaces: ['devUi'],
      defaultNamespace: 'devUi',
    })
    expect(bare).toEqual([])
    expect(refs.map((ref) => ref.key ?? ref.pattern?.source)).toEqual(['devUi:colors.title', '^devUi:colors\\..+\\.hint$'])
  })
})

describe('missingKeys и unusedKeys', () => {
  const { refs } = findKeyRefs('Demo.tsx', "t('shell:nav.schedule'); t('shell:nav.nope'); t(`common:ui.status.${s}`)", NS)

  it('ключа нет в словаре — ошибка с файлом и строкой; шаблон без совпадений — тоже', () => {
    expect(missingKeys(refs, dictionary).map((problem) => problem.message)).toEqual([
      'Demo.tsx:1: нет ключа «shell:nav.nope» в словаре ru',
    ])
    const [template] = findKeyRefs('Demo.tsx', 't(`common:ui.color.${c}`)', NS).refs
    expect(missingKeys(template ? [template] : [], dictionary)).toHaveLength(1)
  })

  it('строка, на которую нет ссылки, — лишняя; шаблон покрывает все свои ключи', () => {
    expect(unusedKeys(dictionary, refs).map((problem) => problem.message)).toEqual([
      '«common:ui.close» не используется в коде — удалите строку или сошлитесь на неё',
      '«errors:iam.login.invalid» не используется в коде — удалите строку или сошлитесь на неё',
    ])
  })
})

describe('ICU', () => {
  it('аргументы строки — и внутри вариантов plural/select', () => {
    expect(icuArguments('Шаг {current} из {total}')).toEqual(['current', 'total'])
    expect(
      icuArguments('через {unit, select, minute {{value, plural, one {# минуту} other {# минуты}}} other {{value} с}}'),
    ).toEqual(['unit', 'value'])
  })

  it('сломанная строка, старый синтаксис {{x}} и plural без other — ошибки; шаблон дат проверяет date-fns', () => {
    const problems = icuProblems(
      new Map([
        ['common:ok', '{age, plural, one {# год} few {# года} many {# лет} other {# года}}'],
        ['common:legacy', 'Мой профиль: {{name}}'],
        ['common:unclosed', 'Шаг {current из {total}'],
        ['common:noOther', '{n, plural, one {# год}}'],
        ['common:dates.ok', 'EEEE, d MMMM'],
        ['common:dates.bad', 'd MMMM in HH:mm'],
      ]),
      'ru',
    )
    expect(problems.map((problem) => `${problem.kind} ${problem.message.split('»')[0]}»`)).toEqual([
      'icu «common:legacy»',
      'icu «common:unclosed»',
      'icu «common:noOther»',
      'date «common:dates.bad»',
    ])
  })
})

describe('словарь другого языка', () => {
  const base = new Map([
    ['shell:nav.schedule', 'Расписание'],
    ['common:age', '{age, plural, one {# год} few {# года} many {# лет} other {# года}}'],
    ['common:user.avatar', 'Инициалы {name}'],
  ])

  it('полный, с теми же аргументами — без замечаний', () => {
    const kk = new Map([
      ['shell:nav.schedule', 'Кесте'],
      ['common:age', '{age, plural, one {# жас} other {# жас}}'],
      ['common:user.avatar', '{name} бас әріптері'],
    ])
    expect(languageProblems(base, kk, 'kk')).toEqual([])
  })

  it('нет перевода, лишний ключ, другие аргументы — ошибки', () => {
    const kk = new Map([
      ['common:age', '{years} жас'],
      ['common:user.avatar', '{name} бас әріптері'],
      ['shell:nav.old', 'Ескі'],
    ])
    expect(languageProblems(base, kk, 'kk').map((problem) => problem.message)).toEqual([
      'kk: нет перевода «shell:nav.schedule»',
      'kk: у «common:age» аргументы {years}, в ru — {age}',
      'kk: лишний ключ «shell:nav.old» — его нет в ru',
    ])
  })
})

describe('списки в словаре', () => {
  it('массив — ошибка с путём; ключи по смыслу — нет', () => {
    const problems = listProblems({
      devUi: { overlays: { chairs: ['Кресло 1', 'Кресло 2'], chair: 'Кресло' } },
      common: { ui: { steps: { first: 'Пациент', second: 'Время' } } },
    })
    expect(problems.map((problem) => problem.message)).toEqual([
      '«devUi:overlays.chairs» — список в словаре: каждая строка — свой ключ по смыслу',
    ])
  })
})

describe('русская строка мимо словаря', () => {
  const lines = (problems: { message: string }[]) => problems.map((problem) => Number(problem.message.split(':')[1]))

  it('строка, шаблон и текст JSX с кириллицей — где угодно, не только в JSX', () => {
    const source = [
      "toast.add({ title: 'Готово' })",
      "const label = 'Сохранить'",
      'const count = `Всего ${n}`',
      "export const Route = createFileRoute('/x')({ head: () => ({ meta: [{ title: 'Пациенты' }] }) })",
      'const view = <p>Пусто</p>',
      'const hint = <p title={`Пациентов: ${n}`} />',
      "const kk = 'Қаз'",
    ].join('\n')
    const problems = cyrillicLiterals('Demo.tsx', source)
    expect(lines(problems)).toEqual([1, 2, 3, 4, 5, 6, 7])
    expect(problems[0]?.message).toBe("Demo.tsx:1: 'Готово' — текст для пользователя пишется ключом i18n")
  })

  it('комментарии, латиница, ключи и сообщения разработчику — не ошибка', () => {
    const source = [
      '// Комментарий по-русски',
      '/** Документация: «Расписание» */',
      "const key = t('shell:nav.schedule'); const url = 'https://example.kz'",
      'console.error(`i18n: нет ключа ${key}`)',
      "throw new Error('Нет токена')",
      'const failure = new TypeError(`Ожидался цвет, получено «${hex}»`)',
      'const pattern = /[А-Яа-я]/',
    ].join('\n')
    expect(cyrillicLiterals('Demo.ts', source)).toEqual([])
  })
})
