import type { BranchRef, ClinicRef, MeMembership, MeResponse } from '../generated/model'
import { type MeWithRights, PERMISSIONS } from '../me'

// Ответы /api/v1/me по схеме MeResponse из api/openapi.json (сгенерированные типы) плюс
// расширение прав MeRightsExtension — его живой бэкенд пока не присылает (TODO в me.ts).
// Данные вымышленные (CLAUDE.md: никаких реальных пациентов и сотрудников).

export type MockPersona = 'owner' | 'assistant' | 'multi' | 'plain' | 'none'

/** Cookie, по которой моки выбирают пользователя; `none` — сессии нет (401). */
export const MOCK_USER_COOKIE = 'dentify_mock_user'

/**
 * Ключ sessionStorage с клиникой, выбранной через POST /api/v1/me/context (персона `multi`):
 * «сессия» мока живёт, пока открыта вкладка, вход её сбрасывает. Не cookie: MSW хранит cookie
 * ответов в своём хранилище, и оно пережило бы тест в Vitest.
 */
export const MOCK_CLINIC_KEY = 'dentify_mock_clinic'

const akTis: ClinicRef = { id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e10', name: 'Ақ Тіс', city: 'Алматы' }
const zhasTis: ClinicRef = { id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e11', name: 'Жас Тіс', city: 'Астана' }
const sauTis: ClinicRef = { id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e12', name: 'Сау Тіс', city: 'Шымкент' }

const abaya: BranchRef = { id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e20', name: 'Абая, 150', city: 'Алматы' }
const satpayeva: BranchRef = { id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e21', name: 'Сатпаева, 30', city: 'Алматы' }
const turan: BranchRef = { id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e22', name: 'Тұран, 24', city: 'Астана' }

export const owner: MeWithRights = {
  account: {
    id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e01',
    fullName: 'Алиева Динара',
    initials: 'АД',
    language: 'ru',
    phone: '+77010000001',
    email: 'dinara@example.kz',
  },
  context: { clinic: akTis, branch: abaya },
  memberships: [{ id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e30', clinic: akTis, status: 'ACTIVE', branches: [abaya] }],
  role: { code: 'owner', name: 'Владелец' },
  permissions: [...PERMISSIONS],
}

export const assistant: MeWithRights = {
  account: {
    id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e02',
    fullName: 'Нурланова Асель',
    initials: 'НА',
    language: 'ru',
    phone: '+77010000002',
    email: null,
  },
  context: { clinic: akTis, branch: abaya },
  memberships: [{ id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e31', clinic: akTis, status: 'ACTIVE', branches: [abaya] }],
  role: { code: 'assistant', name: 'Ассистент' },
  permissions: ['schedule.view', 'patients.view', 'staff.view', 'inventory.view'],
}

/**
 * Врач в двух клиниках: после входа клиника не выбрана (`context: null`) — каркас ведёт на
 * выбор клиники. Третье членство отключено: выбрать его нельзя (сервер ответит 403).
 */
export const multi: MeWithRights = {
  account: {
    id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e03',
    fullName: 'Оспанова Мадина',
    initials: 'ОМ',
    language: 'ru',
    phone: null,
    email: 'madina@example.kz',
  },
  context: null,
  memberships: [
    { id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e32', clinic: akTis, status: 'ACTIVE', branches: [abaya, satpayeva] },
    { id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e33', clinic: zhasTis, status: 'ACTIVE', branches: [turan] },
    { id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e34', clinic: sauTis, status: 'DISABLED', branches: [] },
  ],
  role: { code: 'doctor', name: 'Врач' },
  permissions: ['schedule.view', 'patients.view'],
}

/** Ответ строго по спеке, как у живого бэкенда сейчас: без роли и прав (MeRightsExtension). */
export const plain: MeResponse = {
  account: {
    id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e06',
    fullName: 'Касымова Жанар',
    initials: 'КЖ',
    language: 'ru',
    phone: '+77010000006',
    email: null,
  },
  context: { clinic: akTis, branch: abaya },
  memberships: [{ id: '019a0c3e-2f6a-7c1b-9d2e-0b1f4a6c8e35', clinic: akTis, status: 'ACTIVE', branches: [abaya] }],
}

export const personas: Record<Exclude<MockPersona, 'none'>, MeWithRights> = { owner, assistant, multi, plain }

/**
 * Ответ /me с клиникой сессии, как её выбрал бы сервер: из активного членства; филиал — если он
 * единственный (SelectContextRequest.branchId = null). Нет такого членства — undefined.
 */
export function withContext(me: MeWithRights, clinicId: string | undefined): MeWithRights | undefined {
  const membership: MeMembership | undefined = me.memberships.find(
    (item) => item.clinic.id === clinicId && item.status === 'ACTIVE',
  )
  if (!membership) return undefined
  const branch = membership.branches.length === 1 ? (membership.branches[0] ?? null) : null
  return { ...me, context: { clinic: membership.clinic, branch } }
}
