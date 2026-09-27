import { existsSync, rmSync } from 'node:fs'

import { expect, test as teardown } from '@playwright/test'

import type { ProblemDetail } from '../../src/shared/api/generated/model'
import { apiLogout, ownerStatePath, STACK_URL } from './stack'

// Проект stack-teardown (после stack-desktop и stack-mobile): выход сессией stack-setup — в Valkey не остаётся
// живой сессии владельца, а файл storageState с её cookie удаляется из артефактов.

teardown('выход сессией stack-setup: на сервере её больше нет', async ({ playwright }, testInfo) => {
  const state = ownerStatePath(testInfo)
  teardown.skip(!existsSync(state), 'stack-setup не вошёл — выходить нечем')

  const api = await playwright.request.newContext({ baseURL: STACK_URL, storageState: state })
  // Та же cookie с другого «устройства» после выхода — 401 error.session-expired: сессия уничтожена на
  // сервере, а не только стёрта в браузере (PLT-04).
  const stale = await playwright.request.newContext({ baseURL: STACK_URL, storageState: state })
  try {
    await apiLogout(api)
    const me = await stale.get('/api/v1/me')
    expect(me.status()).toBe(401)
    expect(((await me.json()) as ProblemDetail).code).toBe('error.session-expired')
  } finally {
    await api.dispose()
    await stale.dispose()
    rmSync(state, { force: true })
  }
})
