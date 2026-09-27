import { expect, test } from '@playwright/test'

import type { MeResponse, ProblemDetail } from '../../src/shared/api/generated/model'
import {
  expectCsrfOnChanges,
  expectHttpOnlySession,
  expectNoTokensInWebStorage,
  expectSameOrigin,
  login,
  logout,
  NO_SESSION,
  recordRequests,
  SECTIONS,
  STACK_URL,
  xsrfToken,
} from './stack'

// Безопасность сессии, как её видит фронт на живом бэкенде (PLT-04, PLT-27): вход без «Запомнить», обход всех
// разделов, изменение с CSRF-токеном и без него, выход. Серверную сторону (сроки, отзыв, лимиты) держат тесты
// бэкенда; здесь — что браузер получил и что страница отправила.

test.use({ storageState: NO_SESSION })

test('сессия — только HttpOnly-cookie, токенов в хранилищах нет, изменения — с CSRF, запросы — на свой origin', async ({
  page,
  context,
  playwright,
}) => {
  const requests = recordRequests(context)

  await page.goto('/login')
  await login(page, { remember: false })
  await expect(page).toHaveURL(/\/schedule$/)

  // Cookie сессии: HttpOnly, Secure, SameSite=Lax; без «Запомнить» — сессионная, до закрытия браузера.
  const session = await expectHttpOnlySession(page)
  expect(session.expires, 'без «Запомнить» у cookie нет срока').toBe(-1)

  for (const section of SECTIONS) {
    await page.goto(section.path)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(section.title)
  }
  const stored = await expectNoTokensInWebStorage(page)
  await test.info().attach('localStorage и sessionStorage', { body: JSON.stringify(stored, null, 2), contentType: 'application/json' })

  // Изменение без заголовка X-XSRF-TOKEN — 403 error.csrf, с ним — проходит (приёмка PLT-04). Выбор клиники —
  // та же клиника и филиал, что уже в сессии: состояние не меняется.
  const me = (await (await page.request.get('/api/v1/me')).json()) as MeResponse
  const choice = { clinicId: me.context?.clinic.id, branchId: me.context?.branch?.id ?? null }
  const bare = await page.request.post('/api/v1/me/context', { data: choice })
  expect(bare.status()).toBe(403)
  expect(((await bare.json()) as ProblemDetail).code).toBe('error.csrf')
  const signed = await page.request.post('/api/v1/me/context', {
    data: choice,
    headers: { 'X-XSRF-TOKEN': await xsrfToken(context) },
  })
  expect(signed.status()).toBe(200)

  await logout(page)

  // Сессия уничтожена на сервере, а не только стёрта в браузере: та же cookie с другого клиента — 401.
  const replay = await playwright.request.newContext({
    baseURL: STACK_URL,
    extraHTTPHeaders: { Cookie: `DENTIFY_SESSION=${session.value}` },
  })
  try {
    const stale = await replay.get('/api/v1/me')
    expect(stale.status()).toBe(401)
    expect(((await stale.json()) as ProblemDetail).code).toBe('error.session-expired')
  } finally {
    await replay.dispose()
  }

  // Все изменения, которые отправила сама страница (вход и выход), — с CSRF-токеном из cookie.
  expect(await expectCsrfOnChanges(requests)).toEqual(['POST /api/v1/auth/login', 'POST /api/v1/auth/logout'])
  // PLT-27: ни одного запроса на сторонние домены (шрифты, картинки, аналитика) и мимо своего origin.
  expectSameOrigin(requests)
})
