import { ESLint } from 'eslint'
import { beforeAll, describe, expect, it } from 'vitest'

// dentify R003: `pnpm lint` (и verify) падает на строке в JSX без ключа i18n. Проверяем настоящую
// конфигурацию eslint.config.js (eslint-plugin-i18next, правило `no-literal-string` с типами TS) на
// кусочках экрана: текст, атрибуты, свои пропсы, выражения и шаблонные строки ловятся, коды и ключи —
// нет.

const eslint = new ESLint()
// Существующий файл экрана: typescript-eslint берёт типы из проекта, текст — наш.
const FILE = 'src/modules/profile/ProfilePage.tsx'

async function literalErrors(jsx: string): Promise<string[]> {
  const source = `import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { StateScreen } from '@/shared/ui/StateScreen'

export function ProfilePage({ ok }: { ok: boolean }) {
  const { t } = useTranslation()
  return (<>${jsx}</>)
}
`
  const [result] = await eslint.lintText(source, { filePath: FILE })
  return (result?.messages ?? [])
    .filter((message) => message.ruleId === 'i18next/no-literal-string')
    .map((message) => message.message)
}

// Первый прогон с типами TS строит программу проекта — это десятки секунд под нагрузкой; прогреваем
// один раз, а кусочки дальше проверяются быстро. Пределы — с запасом на параллельные прогоны.
const WARM_UP = 180_000
const CASE = 60_000

describe('ESLint: строка в JSX без ключа i18n — ошибка', () => {
  beforeAll(async () => {
    await literalErrors('<p />')
  }, WARM_UP)

  it.each([
    ['текст', '<p>Сохранить</p>'],
    ['подсказка', '<Button title="Сохранить изменения" />'],
    ['имя для диктора', '<span aria-label="Закрыть" />'],
    ['свой пропс экрана', '<StateScreen kind="empty" title={t(\'common:states.loading\')} text="Пока пусто" />'],
    ['выражение', "<p>{ok ? 'Да' : 'Нет'}</p>"],
    ['латиница', '<p>Loading</p>'],
    // Шаблонные строки (`should-validate-template`): с подстановкой и без, в тексте и в атрибутах.
    ['шаблон в тексте', '<p>{`Всего ${ok}`}</p>'],
    ['шаблон без подстановки', '<p>{`Всего`}</p>'],
    ['шаблон в атрибуте', '<p title={`Пациентов: ${ok}`} />'],
    ['шаблон в своём пропсе', '<StateScreen kind="empty" title={`Пусто: ${ok}`} />'],
  ])('%s', async (_, jsx) => {
    expect((await literalErrors(jsx)).length).toBeGreaterThan(0)
  }, CASE)

  it('ключи, коды и технические атрибуты — не ошибка', async () => {
    const jsx = [
      "<p>{t('common:ui.close')}</p>",
      '<Button variant="outline" size="compact" type="button" data-slot="demo" className="p-4" />',
      '<span aria-hidden="true" lang="kk" id="x" aria-labelledby="x" />',
      // Знаки без букв и пробелов — не текст.
      '<p>+</p>',
      // Шаблон в техническом атрибуте и в ключе — не текст.
      "<p className={`p-4 ${ok ? 'flex' : 'grid'}`} data-state={`s-${ok}`} />",
      "<p>{t(`common:ui.${ok ? 'close' : 'back'}`)}</p>",
    ].join('\n')
    expect(await literalErrors(jsx)).toEqual([])
  }, CASE)
})
