import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { i18n } from '@/shared/i18n'

import { toast, Toaster, toastUndo } from './toast'

const t = i18n.t.bind(i18n)

// App грузит Toaster лениво (вне бюджета начальной загрузки), а менеджер Base UI без очереди:
// без буфера уведомление, вызванное до загрузки чанка, терялось бы молча.
describe('toast — вызовы до появления Toaster не теряются', () => {
  it('уведомление и «Вернуть», вызванные до монтирования, показываются после', async () => {
    const onUndo = vi.fn()
    toastUndo({ title: 'Запись отменена', onUndo })
    toast.add({ title: 'Нет связи с сервером', type: 'error' })

    render(<Toaster />)

    expect(await screen.findByText('Запись отменена')).toBeInTheDocument()
    expect(screen.getByText('Нет связи с сервером')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: t('common:ui.toast.undo') }))
    expect(onUndo).toHaveBeenCalledOnce()
  })

  it('после монтирования уведомление показывается сразу, повторов из буфера нет', async () => {
    render(<Toaster />)
    toast.add({ title: 'Пациент сохранён', type: 'success' })

    expect(await screen.findByText('Пациент сохранён')).toBeInTheDocument()
    expect(screen.queryByText('Запись отменена')).not.toBeInTheDocument()
  })

  it('Toaster сняли и поставили снова — вызов между ними тоже доходит', async () => {
    const first = render(<Toaster />)
    first.unmount()
    toast.add({ title: 'Приём завершён', type: 'success' })

    render(<Toaster />)

    await waitFor(() => expect(screen.getByText('Приём завершён')).toBeInTheDocument())
  })
})
