import { expect, test as setup } from '@playwright/test'

import { apiLogin, OWNER, ownerStatePath, STACK_URL } from './stack'

// Проект stack-setup: один вход dev-владельцем через API (как SPA: CSRF-токен, затем POST /auth/login) и
// storageState — cookie сессии и XSRF-TOKEN — для спек, которым вход не нужен как сценарий (cabinet.spec.ts).
// Выход этой сессией — owner.teardown.ts (stack-teardown), после всех проектов.

setup('вход dev-владельцем: сессия для stack-desktop и stack-mobile', async ({ playwright }, testInfo) => {
  const api = await playwright.request.newContext({ baseURL: STACK_URL })
  try {
    const me = await apiLogin(api)
    // Воспроизводимый владелец из dev-сида: одна клиника с одним филиалом — вход выбирает их сразу.
    expect(me.account.fullName).toBe(OWNER.fullName)
    expect(me.context?.clinic.name).toBe(OWNER.clinic)
    expect(me.context?.branch?.name).toBe(OWNER.branch)
    await api.storageState({ path: ownerStatePath(testInfo) })
  } finally {
    await api.dispose()
  }
})
