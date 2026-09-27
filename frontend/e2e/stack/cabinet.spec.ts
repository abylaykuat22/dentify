import { expect } from '@playwright/test'

import { assertNoHorizontalScroll, isDesktop, seriousA11yViolations, smallTapTargets } from '../helpers'
import { bottomNav, OWNER, SECTIONS, test } from './stack'

// Пустой кабинет dev-владельца на живом стеке (PLT-17, PLT-18, PLT-21): страница уже вошла (storageState из
// stack-setup). Каждый раздел — заголовок, пустое состояние (макет Sostoyaniya), axe без serious/critical, без
// прокрутки вбок; на телефоне ещё цели касания ≥ 44×44 (нижняя панель, «Ещё»). Сценарий входа и выхода —
// demo.spec.ts, проверки безопасности — security.spec.ts.
//
// С сервера здесь — только каркас: гард и шапка по /me (сессия, ФИО, клиника, филиал). Пустые состояния разделов —
// заглушки этапа 0 (ScheduleStubPage, PatientsListPage…): в API они не ходят, и пустота не зависит от данных
// dev-базы. Когда разделы начнут запрашивать данные (T-PAT, T-SCH), пустоту проверять по ответу сервера
// (`page.waitForResponse`) и перейти на свою клинику на прогон (docs/frontend.md «Живой стек»).

const PROFILE = {
  path: '/profile',
  title: 'Мой профиль',
  empty: 'Телефон, пароль и сеансы',
} as const

test.describe('пустой кабинет владельца: живой бэкенд', () => {
  test('каркас: разделы по /me, шапка с филиалом, пользователь с сервера', async ({ page }) => {
    await page.goto('/')
    // «/» — первый доступный раздел; прав в /me пока нет (T-IAM-07) — меню полное.
    await expect(page).toHaveURL(/\/schedule$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Расписание')
    await expect(page).toHaveTitle('Расписание — Dentify')
    // Контекст шапки «дата · филиал»: филиал — из контекста сессии (/me).
    await expect(page.locator('[data-shell-header]')).toContainText(OWNER.branch)

    if (isDesktop()) {
      const sections = page.getByRole('navigation', { name: 'Разделы' }).getByRole('link')
      await expect(sections).toHaveText(SECTIONS.map((section) => section.title))
      await expect(sections.first()).toHaveAttribute('aria-current', 'page')
      // ФИО и инициалы — с сервера (R002); роли в /me ещё нет — строки роли нет.
      await expect(page.getByRole('button', { name: `Меню пользователя: ${OWNER.fullName}` })).toHaveText(
        `${OWNER.initials}${OWNER.fullName}`,
      )
    } else {
      await expect(page.locator('[data-sidebar]')).toBeHidden()
      await expect(bottomNav(page).getByRole('link')).toHaveText(['Расписание', 'Пациенты', '', 'Склад', 'Ещё'])
    }
  })

  for (const section of [...SECTIONS, PROFILE]) {
    test(`${section.path}: пустое состояние, axe, без прокрутки вбок; на телефоне — цели касания ≥ 44`, async ({ page }) => {
      await page.goto(section.path)
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(section.title)
      // Пустое состояние — заглушка раздела этапа 0 (см. выше), а не ответ сервера о пустом списке.
      await expect(page.locator('[data-state-screen=empty]')).toContainText(section.empty)

      expect(await seriousA11yViolations(page)).toEqual([])
      await assertNoHorizontalScroll(page)
      if (!isDesktop()) expect(await smallTapTargets(page)).toEqual([])
    })
  }

  test('«Ещё» (Telefon-Eshche): пользователь, клиника, филиал, разделы, «Выйти»', async ({ page }) => {
    // На телефоне — пункт нижней панели; на десктопе всё есть в сайдбаре, а «Ещё» открывается по адресу.
    if (isDesktop()) {
      await page.goto('/more')
    } else {
      await page.goto('/schedule')
      await bottomNav(page).getByRole('link', { name: 'Ещё' }).click()
    }

    await expect(page).toHaveURL(/\/more$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(OWNER.fullName)
    // Роли в /me ещё нет — под именем только клиника.
    await expect(page.locator('main')).toContainText(OWNER.clinic)
    await expect(page.getByRole('button', { name: `Филиал: ${OWNER.branch}. Сменить филиал` })).toBeVisible()
    await expect(page.getByRole('navigation', { name: 'Другие разделы' }).getByRole('link')).toHaveText([
      /^Врачи/,
      /^Склад/,
      /^Клиника/,
      /^Доступы/,
      /^Мой профиль/,
    ])
    await expect(page.getByRole('button', { name: 'Выйти' })).toBeVisible()

    expect(await seriousA11yViolations(page)).toEqual([])
    await assertNoHorizontalScroll(page)
    // Цели касания ≥ 44 — требование телефона (PLT-18); на десктопе логотип сайдбара — ссылка 110×29.
    if (!isDesktop()) expect(await smallTapTargets(page)).toEqual([])
  })
})
