import { expect, test as setup } from '@playwright/test'

import { appPaths, usePersona } from './helpers'

// Прогрев dev-сервера перед тестами (проект `warmup`, от него зависят desktop и mobile). Vite
// собирает модули по первому запросу: на свежем сервере первые страницы, открытые параллельно
// всеми исполнителями, грузились дольше таймаутов — пустая страница и ложные падения первых тестов.
// Здесь каждый адрес открывается один раз по очереди, с запасом по времени.
setup.setTimeout(180_000)

setup('прогрев: все экраны, вход и выбор клиники собраны dev-сервером', async ({ page, context }) => {
  for (const path of [...appPaths(), '/dev/ui']) {
    await page.goto(path)
    await expect(page.locator('h1').first()).toBeVisible({ timeout: 60_000 })
  }
  await usePersona(context, 'multi')
  await page.goto('/select-clinic')
  await expect(page.locator('h1').first()).toBeVisible({ timeout: 60_000 })
  await usePersona(context, 'none')
  await page.goto('/login')
  await expect(page.locator('h1').first()).toBeVisible({ timeout: 60_000 })
})
