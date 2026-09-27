import { expect, type Page, test } from '@playwright/test'

import { horizontalOverflow, isDesktop, usePersona } from './helpers'

// Экран входа по макету Vhod и выход (T-IAM-03: IAM-02, IAM-03, IAM-05, IAM-34) — на обоих проектах,
// десктоп 1440×900 и телефон 390×844. Моки MSW отвечают как сервер (src/shared/api/mocks: CSRF —
// без X-XSRF-TOKEN 403, `wrong` — 401, `limit` — 429 с Retry-After, `multi` — две клиники).

// PLT-27: отметка о размещении данных — на экранах без входа при любой ширине: с 1024 px на панели «О сервисе»,
// уже (IAM-34, панели нет) — под формой. Видимая — ровно одна.
const DATA_RESIDENCY = 'Данные хранятся в Казахстане'

function dataResidency(page: Page) {
  return page.getByText(DATA_RESIDENCY).filter({ visible: true })
}

async function signIn(page: Page, login: string, password = 'secret-1') {
  await page.getByLabel('Телефон или e-mail').fill(login)
  await page.getByLabel('Пароль', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Войти' }).click()
}

test.describe('вход', () => {
  test.beforeEach(async ({ context }) => {
    await usePersona(context, 'none')
  })

  test('успешный вход по телефону: маска +7, цифровая клавиатура, CSRF-токен в заголовке, в кабинет', async ({ page }) => {
    const logins: { token: string | null; body: unknown }[] = []
    page.on('request', (request) => {
      if (request.method() === 'POST' && request.url().endsWith('/api/v1/auth/login')) {
        logins.push({ token: request.headers()['x-xsrf-token'] ?? null, body: request.postDataJSON() })
      }
    })
    await page.goto('/login')
    await expect(page).toHaveTitle('Вход — Dentify')
    await expect(page.getByRole('heading', { level: 1, name: 'Вход' })).toBeVisible()

    const login = page.getByLabel('Телефон или e-mail')
    await expect(login).toHaveAttribute('inputmode', 'email')
    await login.pressSequentially('87071234567')
    await expect(login).toHaveValue('+7 707 123 45 67')
    await expect(login).toHaveAttribute('inputmode', 'tel')

    const remember = page.getByRole('checkbox', { name: 'Запомнить это устройство' })
    await expect(remember).toBeChecked()
    await page.getByLabel('Пароль', { exact: true }).fill('secret-1')
    await page.getByRole('button', { name: 'Войти' }).click()

    await expect(page).toHaveURL(/127\.0\.0\.1:\d+\/schedule$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Расписание')
    // Мутатор взял токен (GET /auth/csrf) и отправил его; логин — как набран, маска — только вид.
    expect(logins).toEqual([
      { token: 'mock-xsrf-token', body: { login: '87071234567', password: 'secret-1', rememberMe: true } },
    ])
  })

  test('«глаз» показывает и прячет пароль', async ({ page }) => {
    await page.goto('/login')
    const password = page.getByLabel('Пароль', { exact: true })
    await password.fill('secret-1')
    await expect(password).toHaveAttribute('type', 'password')

    await page.getByRole('button', { name: 'Показать пароль' }).click()
    await expect(password).toHaveAttribute('type', 'text')
    await expect(page.getByRole('button', { name: 'Скрыть пароль' })).toHaveAttribute('aria-pressed', 'true')
    await page.getByRole('button', { name: 'Скрыть пароль' }).click()
    await expect(password).toHaveAttribute('type', 'password')
  })

  test('неверный пароль — один текст по коду ProblemDetail, остаёмся на входе', async ({ page }) => {
    await page.goto('/login')
    await signIn(page, 'owner@example.kz', 'wrong')

    await expect(page.locator('[data-slot="form-alert"]')).toHaveText('Неверный телефон, e-mail или пароль')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('лимит попыток (429) — через сколько повторить, из Retry-After', async ({ page }) => {
    await page.goto('/login')
    await signIn(page, 'limit@example.kz')

    await expect(page.locator('[data-slot="form-alert"]')).toHaveText('Слишком много попыток. Повторите через 15 минут')
  })

  test('нет связи — общая ошибка сети', async ({ page }) => {
    await page.goto('/login')
    await signIn(page, 'offline@example.kz')

    await expect(page.locator('[data-slot="form-alert"]')).toHaveText('Нет связи с сервером. Проверьте интернет и повторите')
  })

  test('глубокая ссылка без сессии — вход, после входа — исходный адрес', async ({ page }) => {
    await page.goto('/inventory/items')
    await expect(page).toHaveURL(/\/login\?returnTo=%2Finventory%2Fitems$/)
    await expect(page.getByText('Сеанс истёк.')).toHaveCount(0)

    await signIn(page, 'owner@example.kz')

    await expect(page).toHaveURL(/\/inventory\/items$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Склад')
  })

  test('несколько членств — выбор филиала карточкой, затем исходный адрес', async ({ page }) => {
    await page.goto('/patients?filter=children')
    await signIn(page, 'multi@example.kz')

    await expect(page).toHaveURL(/\/select-clinic\?returnTo=%2Fpatients%3Ffilter%3Dchildren$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Здравствуйте, Оспанова Мадина!Где работаем сегодня?')
    const places = page.getByRole('list', { name: 'Клиники и филиалы' }).getByRole('button')
    // Ақ Тіс — два филиала, Жас Тіс — один; отключённое членство (Сау Тіс) не показывается.
    await expect(places).toHaveCount(3)
    await expect(page.getByText('Сау Тіс')).toHaveCount(0)
    expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0)

    await page.getByRole('button', { name: 'Войти: Сатпаева, 30 — Ақ Тіс, Алматы' }).click()

    await expect(page).toHaveURL(/\/patients\?filter=children$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Пациенты')
    await expect(page.locator('[data-shell-header]')).toContainText('Сатпаева, 30')
  })

  test('«Забыли пароль?» и «Зарегистрировать» — заглушки с возвратом ко входу', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('link', { name: 'Забыли пароль?' }).click()
    await expect(page).toHaveURL(/\/forgot-password$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Восстановление пароля')
    await expect(dataResidency(page)).toHaveCount(1)
    await page.getByRole('link', { name: 'Вернуться ко входу' }).click()

    await page.getByRole('link', { name: 'Зарегистрировать' }).click()
    await expect(page).toHaveURL(/\/register$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Регистрация клиники')
    await page.getByRole('link', { name: 'Вернуться ко входу' }).click()
    await expect(page).toHaveURL(/\/login$/)
  })

  test('раскладка Vhod: панель «О сервисе» — только с 1024 px, «Данные хранятся в Казахстане» — при любой ширине, «Рус | Қаз» скрыт флагом, без прокрутки вбок', async ({
    page,
  }) => {
    await page.goto('/login')
    await expect(page.getByRole('heading', { level: 1, name: 'Вход' })).toBeVisible()

    const about = page.getByRole('complementary', { name: 'О сервисе' })
    await expect(dataResidency(page)).toHaveCount(1)
    if (isDesktop()) {
      await expect(about).toBeVisible()
      await expect(about.getByText(DATA_RESIDENCY)).toBeVisible()
    } else {
      await expect(about).toBeHidden()
      await expect(page.getByRole('main').getByText(DATA_RESIDENCY)).toBeVisible()
    }
    await expect(page.locator('[data-language-switch]')).toHaveCount(0)
    expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0)
  })
})

test.describe('сеанс истёк', () => {
  test('401 посреди работы — вход с «Сеанс истёк», после входа — тот же адрес; на входе без повтора', async ({
    page,
    context,
  }) => {
    await page.goto('/staff')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Врачи')

    await usePersona(context, 'none')
    await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange', { bubbles: true })))

    await expect(page).toHaveURL(/\/login\?returnTo=%2Fstaff&reason=expired$/)
    await expect(page.getByRole('status')).toHaveText('Сеанс истёк. Войдите снова — вернём на ту же страницу')
    // Уже на входе новые 401 не уводят повторно и плашка одна.
    await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange', { bubbles: true })))
    await expect(page.locator('[data-slot="session-expired"]')).toHaveCount(1)

    await signIn(page, 'owner@example.kz')
    await expect(page).toHaveURL(/\/staff$/)
  })
})

test.describe('выход', () => {
  test('из меню пользователя (сайдбар) или «Ещё» (телефон): вход, кэш очищен — «Назад» кабинет не показывает', async ({
    page,
  }) => {
    await page.goto('/inventory')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Склад')

    // Переходы внутри приложения (без перезагрузки страницы), как у человека.
    if (isDesktop()) {
      await page.getByRole('navigation', { name: 'Разделы' }).getByRole('link', { name: 'Пациенты' }).click()
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Пациенты')
      await page.getByRole('button', { name: 'Меню пользователя: Алиева Динара' }).click()
      await page.getByRole('menuitem', { name: 'Выйти' }).click()
    } else {
      await page.getByRole('navigation', { name: 'Главное меню' }).getByRole('link', { name: 'Ещё' }).click()
      await page.getByRole('button', { name: 'Выйти' }).click()
    }

    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByRole('heading', { level: 1, name: 'Вход' })).toBeVisible()

    // «Назад» браузера: гард заново спрашивает /api/v1/me (кэш пуст) — 401 и снова вход.
    await page.goBack()
    await expect(page).toHaveURL(/\/login\?returnTo=%2Finventory$/)
    await expect(page.getByRole('heading', { level: 1, name: 'Вход' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Склад' })).toHaveCount(0)
  })
})

test.describe('меню пользователя — своим чанком после первого экрана (PLT-22)', () => {
  test.skip(() => !isDesktop(), 'карточка пользователя — в сайдбаре, он с 768 px')

  const CARD = 'Меню пользователя: Алиева Динара'

  /**
   * Держать чанк меню, пока тест не отпустит: до него на месте карточки — простая кнопка. Маршрут — у
   * контекста, а не у страницы: запросы страницы идут через service worker MSW, и чанк качает уже он.
   */
  async function holdMenuChunk(page: Page): Promise<() => void> {
    let release = () => {}
    const released = new Promise<void>((resolve) => {
      release = resolve
    })
    await page.context().route(/\/src\/app\/shell\/UserMenuDropdown\.tsx/, async (route) => {
      await released
      await route.continue()
    })
    return release
  }

  test('без задержки: карточка сама становится кнопкой меню, первый экран её не ждёт', async ({ page }) => {
    await page.goto('/inventory')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Склад')
    await expect(page.getByRole('button', { name: CARD })).toHaveAttribute('data-user-menu', 'menu')
  })

  test('нажали на карточку, пока меню грузится, — меню открывается, когда придёт', async ({ page }) => {
    const release = await holdMenuChunk(page)
    await page.goto('/inventory')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Склад')
    const card = page.getByRole('button', { name: CARD })
    await expect(card).toHaveAttribute('data-user-menu', 'placeholder')
    await expect(card).toHaveAttribute('aria-haspopup', 'menu')

    await card.click()
    await expect(page.getByRole('menu')).toHaveCount(0)
    release()

    const menu = page.getByRole('menu')
    await expect(menu.getByRole('menuitem')).toHaveText(['Мой профиль', 'Выйти'])
    await expect(card).toHaveAttribute('data-user-menu', 'menu')
    await page.keyboard.press('Escape')
    await expect(menu).toHaveCount(0)
    await expect(card).toBeFocused()
  })

  test('фокус на карточке, пока меню грузится, — остаётся на ней; Enter открывает меню', async ({ page }) => {
    const release = await holdMenuChunk(page)
    await page.goto('/inventory')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Склад')
    // Клавиатура: с последнего пункта меню разделов — Tab на карточку.
    await page.getByRole('navigation', { name: 'Разделы' }).getByRole('link', { name: 'Доступы' }).focus()
    await page.keyboard.press('Tab')
    const card = page.getByRole('button', { name: CARD })
    await expect(card).toBeFocused()
    await expect(card).toHaveAttribute('data-user-menu', 'placeholder')

    release()
    await expect(card).toHaveAttribute('data-user-menu', 'menu')
    await expect(card).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(page.getByRole('menuitem', { name: 'Мой профиль' })).toBeFocused()
    await page.keyboard.press('ArrowDown')
    await expect(page.getByRole('menuitem', { name: 'Выйти' })).toBeFocused()
  })

  test('Enter на карточке, пока меню грузится, — меню открывается с фокусом на первом пункте', async ({ page }) => {
    const release = await holdMenuChunk(page)
    await page.goto('/inventory')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Склад')
    await page.getByRole('navigation', { name: 'Разделы' }).getByRole('link', { name: 'Доступы' }).focus()
    await page.keyboard.press('Tab')
    const card = page.getByRole('button', { name: CARD })
    await expect(card).toHaveAttribute('data-user-menu', 'placeholder')
    // Нажатие с клавиатуры до прихода чанка доигрывается как клавиатурное, а не как клик мышью.
    await page.keyboard.press('Enter')
    await expect(page.getByRole('menu')).toHaveCount(0)

    release()
    await expect(page.getByRole('menuitem', { name: 'Мой профиль' })).toBeFocused()
    await expect(card).toHaveAttribute('data-user-menu', 'menu')
    await page.keyboard.press('ArrowDown')
    await expect(page.getByRole('menuitem', { name: 'Выйти' })).toBeFocused()
    await page.keyboard.press('Escape')
    await expect(card).toBeFocused()
  })
})

test.describe('общий компонент пароля (IAM-05) — витрина /dev/ui', () => {
  test('новый пароль: индикатор zxcvbn-ts и чек-лист по мере ввода; повтор — «Пароли не совпадают»', async ({
    page,
  }) => {
    await page.goto('/dev/ui')
    const created = page.getByLabel('Придумайте пароль', { exact: true })
    await created.scrollIntoViewIfNeeded()
    const meter = page.locator('[data-slot="password-hints"] [role="img"]')
    await expect(meter).toHaveAccessibleName('Надёжность пароля: не оценена')

    await created.fill('12345678')
    // Словари zxcvbn-ts грузятся при первом вводе отдельными чанками.
    await expect(meter).toHaveAccessibleName('Надёжность пароля: низкая, 1 из 4', { timeout: 20_000 })
    await created.fill('Kx9#vLm2$Qw8zT!r')
    await expect(meter).toHaveAccessibleName('Надёжность пароля: высокая, 4 из 4', { timeout: 20_000 })
    await expect(page.locator('[data-rule][data-met]')).toHaveCount(3)

    const repeat = page.getByLabel('Повторите пароль', { exact: true })
    await repeat.fill('Kx9#vLm2')
    await expect(repeat).not.toHaveAttribute('aria-invalid', 'true')
    await repeat.blur()
    await expect(repeat).toHaveAttribute('aria-invalid', 'true')
    await expect(page.getByText('Пароли не совпадают')).toBeVisible()
    await repeat.fill('Kx9#vLm2$Qw8zT!r')
    await expect(repeat).not.toHaveAttribute('aria-invalid', 'true')
    await expect(page.getByText('Пароли не совпадают')).toHaveCount(0)
  })
})

test.describe('макет Vhod — эталон', () => {
  // Эталон в CSS-пикселях: Pixel 7 рисует с плотностью 2.625.
  test.use({ deviceScaleFactor: 1 })

  test('экран входа совпадает с эталоном (сверен с design/mockups/Vhod.dc.html)', async ({ page, context }) => {
    await usePersona(context, 'none')
    await page.goto('/login')
    await expect(page.getByRole('heading', { level: 1, name: 'Вход' })).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    // Подсказка режима моков — не часть макета.
    await page.addStyleTag({ content: '[data-mock-hint] { display: none !important; }' })
    await expect(page).toHaveScreenshot('login.png')
  })
})
