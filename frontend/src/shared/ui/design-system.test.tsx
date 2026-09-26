import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { i18n } from '@/shared/i18n'

import { Combo } from './combo'
import { findMatch } from './highlight'
import { SearchField } from './search-field'
import { Segmented } from './segmented'
import { StatusBadge } from './status-badge'
import { stepAwareOpenChange } from './steps'
import { APPOINTMENT_STATUSES } from './tokens'

const t = i18n.t.bind(i18n)

describe('findMatch — подсветка совпадения как у фильтра Base UI', () => {
  it('без учёта регистра, в середине слова', () => {
    expect(findMatch('Эстетическая терапия', 'ТЕР', 'ru')).toEqual([13, 16])
    expect(findMatch('Терапия детская', 'тер', 'ru')).toEqual([0, 3])
  })

  it('нет совпадения или пустой запрос — null', () => {
    expect(findMatch('Хирургия', 'тер', 'ru')).toBeNull()
    expect(findMatch('Хирургия', '   ', 'ru')).toBeNull()
    expect(findMatch('Ор', 'Ортопедия', 'ru')).toBeNull()
  })

  it('казахские буквы', () => {
    expect(findMatch('Сағатбекқызы Әлия', 'әли', 'ru')).toEqual([13, 16])
  })
})

describe('stepAwareOpenChange — R023: в шаге Escape и клик мимо ведут назад', () => {
  const details = (reason: string) => ({ reason, cancel: vi.fn() })

  it('в шаге Escape, клик мимо и системная «Назад» — шаг назад, окно не закрывается', () => {
    const now = vi.spyOn(performance, 'now')
    const onOpenChange = vi.fn()
    const onBack = vi.fn()
    const handler = stepAwareOpenChange(onOpenChange, onBack)
    let time = 10_000
    for (const reason of ['escape-key', 'outside-press', 'close-watcher']) {
      time += 1_000
      now.mockReturnValue(time)
      const event = details(reason)
      handler(false, event)
      expect(event.cancel).toHaveBeenCalled()
    }
    expect(onBack).toHaveBeenCalledTimes(3)
    expect(onOpenChange).not.toHaveBeenCalled()
    now.mockRestore()
  })

  it('эхо close-watcher сразу за Escape (Chrome на телефоне) не закрывает окно с предыдущего шага', () => {
    const now = vi.spyOn(performance, 'now').mockReturnValue(50_000)
    const onOpenChange = vi.fn()
    const onBack = vi.fn()
    stepAwareOpenChange(onOpenChange, onBack)(false, details('escape-key'))
    // Шаг уже первый — у нового обработчика onBack нет, но эхо того же нажатия гасится.
    now.mockReturnValue(50_010)
    const echo = details('close-watcher')
    stepAwareOpenChange(onOpenChange)(false, echo)
    expect(echo.cancel).toHaveBeenCalled()
    expect(onBack).toHaveBeenCalledTimes(1)
    expect(onOpenChange).not.toHaveBeenCalled()
    now.mockRestore()
  })

  it('крестик и смахивание закрывают окно целиком', () => {
    const onOpenChange = vi.fn()
    const onBack = vi.fn()
    const handler = stepAwareOpenChange(onOpenChange, onBack)
    handler(false, details('close-press'))
    handler(false, details('swipe'))
    expect(onOpenChange).toHaveBeenCalledTimes(2)
    expect(onBack).not.toHaveBeenCalled()
  })

  it('на первом шаге (без onBack) Escape закрывает', () => {
    const onOpenChange = vi.fn()
    const event = details('escape-key')
    stepAwareOpenChange(onOpenChange)(false, event)
    expect(onOpenChange).toHaveBeenCalledWith(false, event)
    expect(event.cancel).not.toHaveBeenCalled()
  })
})

describe('StatusBadge — точка и слово', () => {
  it.each(APPOINTMENT_STATUSES)('%s подписан словом из словаря', (status) => {
    render(<StatusBadge status={status} />)
    const badge = screen.getByText(t(`common:ui.status.${status}`))
    expect(badge.closest('[data-status]')).toHaveAttribute('data-status', status)
  })
})

describe('Segmented', () => {
  function Harness({ onChange }: { onChange: (value: string) => void }) {
    const [value, setValue] = useState<'chairs' | 'doctors'>('chairs')
    return (
      <Segmented
        aria-label="Колонки"
        value={value}
        onValueChange={(next) => {
          setValue(next)
          onChange(next)
        }}
        options={[
          { value: 'chairs', label: 'Кресла' },
          { value: 'doctors', label: 'Врачи' },
        ]}
      />
    )
  }

  it('переключает вариант, а повторное нажатие не оставляет без выбора', async () => {
    const onChange = vi.fn()
    render(<Harness onChange={onChange} />)
    const chairs = screen.getByRole('button', { name: 'Кресла' })
    const doctors = screen.getByRole('button', { name: 'Врачи' })
    expect(chairs).toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(chairs)
    expect(chairs).toHaveAttribute('aria-pressed', 'true')
    expect(onChange).not.toHaveBeenCalled()

    await userEvent.click(doctors)
    expect(onChange).toHaveBeenCalledWith('doctors')
    expect(doctors).toHaveAttribute('aria-pressed', 'true')
    expect(chairs).toHaveAttribute('aria-pressed', 'false')
  })
})

describe('SearchField', () => {
  function Harness() {
    const [value, setValue] = useState('')
    return <SearchField label="Поиск пациента" placeholder="ФИО, телефон или ИИН" value={value} onValueChange={setValue} />
  }

  it('поиск по вводу; крестик очищает поле и возвращает в него фокус', async () => {
    render(<Harness />)
    const input = screen.getByRole('searchbox', { name: 'Поиск пациента' })
    expect(input).toHaveAttribute('placeholder', 'ФИО, телефон или ИИН')

    await userEvent.type(input, 'Сейт')
    expect(input).toHaveValue('Сейт')

    await userEvent.click(screen.getByRole('button', { name: t('common:ui.search.clear') }))
    expect(input).toHaveValue('')
    expect(input).toHaveFocus()
    expect(screen.queryByRole('button', { name: t('common:ui.search.clear') })).not.toBeInTheDocument()
  })
})

describe('Combo — выбор с поиском (R011)', () => {
  const ITEMS = ['Терапия', 'Терапия детская', 'Эстетическая терапия и реставрация зубов', 'Хирургия']

  function Harness() {
    const [value, setValue] = useState<string | null>(null)
    return <Combo aria-label="Специальность" items={ITEMS} value={value} onValueChange={setValue} />
  }

  it('фильтрует по вводу, у пункта — полный текст в title, Enter выбирает первое совпадение', async () => {
    render(<Harness />)
    const input = screen.getByRole('combobox', { name: 'Специальность' })

    await userEvent.type(input, 'тер')
    const listbox = await screen.findByRole('listbox')
    const options = within(listbox).getAllByRole('option')
    expect(options.map((option) => option.textContent)).toEqual(ITEMS.slice(0, 3))
    expect(options[2]).toHaveAttribute('title', 'Эстетическая терапия и реставрация зубов')
    // Совпадение подсвечено <mark>.
    expect(options[2]?.querySelector('mark')).toHaveTextContent('тер')

    await userEvent.keyboard('{Enter}')
    await waitFor(() => expect(input).toHaveValue('Терапия'))
    expect(input).toHaveAttribute('title', 'Терапия')
  })

  it('ничего не нашлось — пустое состояние из словаря', async () => {
    render(<Harness />)
    await userEvent.type(screen.getByRole('combobox', { name: 'Специальность' }), 'ортодонт')
    expect(await screen.findByText(t('common:ui.combo.empty'))).toBeInTheDocument()
  })
})
