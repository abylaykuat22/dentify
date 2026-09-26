import { describe, expect, it } from 'vitest'

import type { MeResponse } from './generated/model'
import { hasPermission, PERMISSIONS, toCurrentUser } from './me'
import { assistant, multi, owner } from './mocks/fixtures'

/** Ответ /api/v1/me строго по схеме спеки: без роли и прав (их бэкенд добавит в T-IAM-06/07). */
const specOnly: MeResponse = {
  account: {
    id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e09',
    fullName: 'Тестова Жанна',
    initials: 'ТЖ',
    language: 'ru',
    phone: null,
    email: null,
  },
  context: {
    clinic: { id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e19', name: 'Тест Тіс', city: 'Алматы' },
    branch: null,
  },
  memberships: [],
}

describe('адаптер /api/v1/me', () => {
  it('берёт ФИО и инициалы аккаунта с сервера, клинику и филиал — из context', () => {
    const user = toCurrentUser(owner)
    expect(user.fullName).toBe('Алиева Динара')
    expect(user.initials).toBe('АД')
    expect(user.role?.name).toBe('Владелец')
    expect(user.clinic?.name).toBe('Ақ Тіс')
    expect(user.branch?.name).toBe('Абая, 150')
  })

  it('оставляет только известные фронту права', () => {
    const user = toCurrentUser({ ...assistant, permissions: [...(assistant.permissions ?? []), 'reports.view'] })
    expect(user.permissions).toEqual(['schedule.view', 'patients.view', 'staff.view', 'inventory.view'])
    expect(hasPermission(user, 'clinic.view')).toBe(false)
    expect(hasPermission(user, 'schedule.view')).toBe(true)
  })

  it('ответ по спеке без роли и прав не ломается: роли нет, разделы не прячутся', () => {
    const user = toCurrentUser(specOnly)
    expect(user.fullName).toBe('Тестова Жанна')
    expect(user.role).toBeNull()
    expect(user.branch).toBeNull()
    expect(user.permissions).toEqual([...PERMISSIONS])
  })

  it('пустой список прав — прав нет (поле есть, значит сервер их уже сообщает)', () => {
    expect(toCurrentUser({ ...specOnly, permissions: [] }).permissions).toEqual([])
  })

  it('context = null (и поля нет) — клиника и филиал не выбраны', () => {
    expect(toCurrentUser(multi).clinic).toBeNull()
    const user = toCurrentUser({ account: specOnly.account, memberships: [] })
    expect(user.clinic).toBeNull()
    expect(user.branch).toBeNull()
  })
})
