import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useTranslation } from 'react-i18next'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { MEDIA } from '@/shared/lib/breakpoints'
import { isFlagOn } from '@/shared/lib/flags'
import { cn } from '@/shared/lib/utils'
import { i18n } from '@/shared/i18n'
import { addKazakhFixture, removeKazakhFixture } from '@/test/kkFixture'

import { IinInput, PhoneInput } from './input'
import { InputGroup, InputGroupInput } from './input-group'
import { LanguageSwitch } from './language-switch'
import { type ResponsiveColumn, ResponsiveTable, rowActionClass } from './responsive-table'

// Дизайн-система на телефоне (T-PLT-10, PLT-18): таблица → карточки, поля 16 px и цифровая
// клавиатура, переключатель языка под флагом i18n.kk.

const t = i18n.t.bind(i18n)

/** Эмулирует ширину окна для `matchMedia` (в jsdom его нет — заглушка из src/test/setup.ts). */
function emulateViewport(width: number) {
  const matches = (query: string) => {
    const min = /min-width:\s*([\d.]+)px/.exec(query)
    const max = /max-width:\s*([\d.]+)px/.exec(query)
    return (!min || width >= Number(min[1])) && (!max || width <= Number(max[1]))
  }
  vi.spyOn(window, 'matchMedia').mockImplementation(
    (query: string) =>
      ({
        matches: matches(query),
        media: query,
        onchange: null,
        addEventListener: () => {},
        removeEventListener: () => {},
        addListener: () => {},
        removeListener: () => {},
        dispatchEvent: () => false,
      }) as MediaQueryList,
  )
}

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllEnvs()
})

interface Row {
  id: string
  name: string
  phone: string
  doctor: string
}

const ROWS: Row[] = [
  { id: '1', name: 'Тестова Айгерим', phone: '+7 707 ••• •• 45', doctor: 'Ахметова' },
  { id: '2', name: 'Тестов Нурлан', phone: '+7 701 ••• •• 12', doctor: 'Бекетов' },
]

function renderTable(onOpen = vi.fn(), rows: Row[] = ROWS) {
  const columns: ResponsiveColumn<Row>[] = [
    {
      id: 'name',
      header: 'Пациент',
      primary: true,
      cell: (row) => (
        <button type="button" className={rowActionClass} onClick={() => onOpen(row.id)}>
          {row.name}
        </button>
      ),
    },
    { id: 'phone', header: 'Телефон', cell: (row) => row.phone },
    { id: 'doctor', header: 'Лечащий врач', hideOnPhone: true, cell: (row) => row.doctor },
  ]
  return render(
    <ResponsiveTable
      label="Пациенты"
      columns={columns}
      rows={rows}
      rowKey={(row) => row.id}
      isSelected={(row) => row.id === '2'}
      interactive
      empty={<p>Пусто</p>}
    />,
  )
}

describe('ResponsiveTable — таблица на планшете и десктопе, карточки на телефоне', () => {
  it('брейкпоинты: телефон < 768, десктоп ≥ 1024 (MEDIA)', () => {
    expect(MEDIA).toEqual({
      phone: '(max-width: 767.98px)',
      tabletUp: '(min-width: 768px)',
      belowDesktop: '(max-width: 1023.98px)',
      desktop: '(min-width: 1024px)',
    })
  })

  it.each([[768], [1440]])('%i px — таблица с заголовками колонок, выбранная строка подсвечена', (width) => {
    emulateViewport(width)
    renderTable()

    const table = screen.getByRole('table', { name: 'Пациенты' })
    expect(within(table).getAllByRole('columnheader').map((th) => th.textContent)).toEqual([
      'Пациент',
      'Телефон',
      'Лечащий врач',
    ])
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(rows[2]).toHaveAttribute('data-state', 'selected')
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('390 px — карточки: имя — заголовок, остальное «подпись: значение», второстепенное скрыто', async () => {
    emulateViewport(390)
    const onOpen = vi.fn()
    renderTable(onOpen)

    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    const cards = within(screen.getByRole('list', { name: 'Пациенты' })).getAllByRole('listitem')
    expect(cards).toHaveLength(2)
    const first = cards[0]
    if (!first) throw new Error('нет карточки')
    expect(within(first).getByRole('term')).toHaveTextContent('Телефон')
    expect(within(first).getByRole('definition')).toHaveTextContent('+7 707 ••• •• 45')
    expect(within(first).queryByText('Ахметова')).not.toBeInTheDocument()
    expect(cards[1]).toHaveAttribute('data-state', 'selected')

    // Одно действие на карточку — ссылка/кнопка в главной ячейке с доступным именем ФИО.
    await userEvent.click(within(first).getByRole('button', { name: 'Тестова Айгерим' }))
    expect(onOpen).toHaveBeenCalledWith('1')
  })

  it('пустой список — состояние `empty` вместо таблицы', () => {
    emulateViewport(390)
    renderTable(vi.fn(), [])
    expect(screen.getByText('Пусто')).toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })
})

describe('поля на телефоне', () => {
  it('телефон — type="tel", ИИН — цифровая клавиатура, 12 знаков, без автозаполнения', () => {
    render(
      <>
        <PhoneInput aria-label="Телефон" />
        <IinInput aria-label="ИИН" />
      </>,
    )
    const phone = screen.getByRole('textbox', { name: 'Телефон' })
    expect(phone).toHaveAttribute('type', 'tel')
    expect(phone).toHaveAttribute('inputmode', 'tel')
    const iin = screen.getByRole('textbox', { name: 'ИИН' })
    expect(iin).toHaveAttribute('inputmode', 'numeric')
    expect(iin).toHaveAttribute('maxlength', '12')
    expect(iin).toHaveAttribute('autocomplete', 'off')
  })

  it('шрифт полей: 16 px на телефоне (text-body-lg), с 768 px — шкала поля; в группе — от группы', () => {
    render(
      <>
        <PhoneInput aria-label="Телефон" />
        <PhoneInput aria-label="Компактный" density="compact" />
        <InputGroup density="compact">
          <InputGroupInput aria-label="Поиск" />
        </InputGroup>
      </>,
    )
    expect(screen.getByRole('textbox', { name: 'Телефон' })).toHaveClass('text-body-lg', 'md:text-body')
    expect(screen.getByRole('textbox', { name: 'Компактный' })).toHaveClass('text-body-lg', 'md:text-body-sm')
    const inGroup = screen.getByRole('textbox', { name: 'Поиск' })
    expect(inGroup).not.toHaveClass('md:text-body')
    expect(inGroup).toHaveClass('text-[length:inherit]', 'md:text-[length:inherit]')
    expect(inGroup.parentElement).toHaveClass('text-body-lg', 'md:text-body-sm')
  })

  it('cn сохраняет наследование размера в группе на всех ширинах', () => {
    expect(cn('text-body-lg md:text-body', 'text-[length:inherit] md:text-[length:inherit]')).toBe(
      'text-[length:inherit] md:text-[length:inherit]',
    )
  })
})

/** Подпись «Язык» — как в меню «Ещё»: обычный компонент с useTranslation. */
function LanguageLabel() {
  const { t: translate } = useTranslation()
  return <span id="lang">{translate('shell:more.language')}</span>
}

describe('флаг i18n.kk и «Рус | Қаз» (R003)', () => {
  afterEach(removeKazakhFixture)

  it('по умолчанию выключен — переключателя нет, даже если словарь kk есть', () => {
    expect(isFlagOn('i18n.kk')).toBe(false)
    addKazakhFixture()
    const { container } = render(<LanguageSwitch aria-labelledby="lang" />)
    expect(container).toBeEmptyDOMElement()
  })

  it('флаг включён, но словаря kk нет — переключателя тоже нет (переключать не на что)', () => {
    vi.stubEnv('VITE_I18N_KK', 'true')
    const { container } = render(<LanguageSwitch aria-labelledby="lang" />)
    expect(container).toBeEmptyDOMElement()
  })

  it('флаг и словарь kk — сегменты «Рус | Қаз» с lang; выбор меняет язык и атрибут lang страницы', async () => {
    vi.stubEnv('VITE_I18N_KK', 'true')
    expect(isFlagOn('i18n.kk')).toBe(true)
    addKazakhFixture()
    render(
      <>
        <LanguageLabel />
        <LanguageSwitch aria-labelledby="lang" />
      </>,
    )
    const group = screen.getByRole('group', { name: t('shell:more.language') })
    const kk = within(group).getByRole('button', { name: t('common:languages.kk.short') })
    expect(kk).toHaveAttribute('lang', 'kk')
    expect(kk).toHaveAttribute('title', t('common:languages.kk.full'))

    await userEvent.click(kk)
    // Смена языка догружает локаль дат (отдельный чанк) — под нагрузкой это дольше секунды.
    await waitFor(() => expect(i18n.language).toBe('kk'), { timeout: 5000 })
    expect(document.documentElement.lang).toBe('kk')
    // Подпись группы — уже из словаря kk: обычный компонент с useTranslation перерисовался сам.
    expect(await screen.findByRole('group', { name: 'Тіл' })).toBeInTheDocument()

    await removeKazakhFixture()
    expect(document.documentElement.lang).toBe('ru')
  })
})
