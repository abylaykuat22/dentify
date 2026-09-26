import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/shared/i18n'
import { renderApp } from '@/test/renderApp'

const t = i18n.t.bind(i18n)

// close() в usePanel: панель открыта здесь (open → push) — закрыть = history.back();
// пришли по ссылке с ?panel= — replace без параметра, чтобы не уйти со страницы.
describe('usePanel: закрытие панели', () => {
  it('открыта кнопкой — закрытие возвращает на шаг назад, история не растёт', async () => {
    const { router } = renderApp('/schedule', 'owner')

    await userEvent.click(await screen.findByRole('button', { name: t('shell:header.searchOpen') }))
    await screen.findByRole('dialog', { name: t('shell:search.title') })
    expect(router.state.location.search).toEqual({ panel: 'search' })
    expect(router.history.canGoBack()).toBe(true)

    await userEvent.click(screen.getByRole('button', { name: t('common:ui.closePanel') }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(router.state.location.pathname).toBe('/schedule')
    expect(router.state.location.search).toEqual({})
    // Вернулись на исходную запись: назад идти некуда, запись с панелью осталась «впереди».
    expect(router.history.canGoBack()).toBe(false)
    expect(router.history.length).toBe(2)
  })

  it('пришли по ссылке с открытой панелью — закрытие заменяет адрес и не уходит со страницы', async () => {
    const { router } = renderApp('/schedule?panel=search', 'owner')

    await screen.findByRole('dialog', { name: t('shell:search.title') })
    await userEvent.click(screen.getByRole('button', { name: t('common:ui.closePanel') }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(router.state.location.pathname).toBe('/schedule')
    expect(router.state.location.search).toEqual({})
    expect(router.history.canGoBack()).toBe(false)
    expect(router.history.length).toBe(1)
  })
})
