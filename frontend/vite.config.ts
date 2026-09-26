/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import { tanstackRouter } from '@tanstack/router-plugin/vite'
import react from '@vitejs/plugin-react'
import { cn as cnTables } from 'cn/vite'
import { defineConfig } from 'vite'

// Моки MSW включаются либо `vite --mode mock` (скрипт dev:mock, e2e), либо переменной
// окружения VITE_API_MOCK=true. В остальных режимах /api проксируется на живой бэкенд.
export default defineConfig(({ mode }) => {
  const apiMock = mode === 'mock' || process.env.VITE_API_MOCK === 'true'
  // Vitest запускает конфиг в режиме `test`.
  const unitTests = mode === 'test'
  // Куда проксировать /api (dev; preview берёт то же из server.proxy): живой бэкенд, по умолчанию
  // 127.0.0.1:8080. Порт занят — бэкенд на другом, адрес — DENTIFY_API_TARGET=http://127.0.0.1:18080.
  const apiTarget = process.env.DENTIFY_API_TARGET ?? 'http://127.0.0.1:8080'
  const proxy = { '/api': { target: apiTarget, changeOrigin: false } }

  return {
    plugins: [
      tanstackRouter({
        target: 'react',
        // В юнит-тестах маршруты не делятся на чанки: первый рендер каждого экрана в jsdom иначе
        // ждёт трансформации и загрузки его ленивого модуля — под нагрузкой параллельного прогона
        // это секунды и таймауты. Деление проверяют сборка (size-limit) и e2e.
        autoCodeSplitting: !unitTests,
        routesDirectory: './src/app/routes',
        generatedRouteTree: './src/app/routeTree.gen.ts',
      }),
      react(),
      tailwindcss(),
      // Таблицы слияния классов для `cn` (src/shared/lib/utils.ts) — собранные заранее под тему «Кресла»
      // (`cn build`, официальный плагин пакета): в начальной загрузке только движок и таблицы, без
      // компилятора конфигурации (−5 kB gzip, PLT-22). `full` — все группы классов Tailwind, а не
      // только найденные в исходниках: таблицы зависят лишь от темы в index.css и не устаревают от
      // новых классов. Плагин пересобирает файл при старте dev/сборки/тестов и при правке темы.
      cnTables({ full: true, css: 'src/styles/index.css', out: 'src/shared/lib/cn-tables.ts' }),
    ],
    define: {
      'import.meta.env.VITE_API_MOCK': JSON.stringify(apiMock ? 'true' : 'false'),
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      // Явный IPv4: `localhost` в Node резолвится в ::1, а прокси/e2e ходят на 127.0.0.1.
      host: '127.0.0.1',
      port: 5173,
      proxy,
    },
    build: {
      manifest: true,
      sourcemap: false,
      // Начальная загрузка — один чанк (~650 kB до сжатия); её бюджет по gzip сторожит size-limit
      // (.size-limit.js, 225 kB), поэтому предупреждение Vite о 500 kB без сжатия здесь лишнее.
      // Самые большие ленивые чанки — словари zxcvbn-ts: английский (~1,2 MB) и русский (~1,05 MB, из них
      // 0,83 — wikipedia-ru): грузятся только в поле нового пароля (PasswordField mode="new"), не на входе.
      chunkSizeWarningLimit: 1250,
      rolldownOptions: {
        output: {
          // Всё, что нужно до первого экрана (точка входа и её статические импорты), — одним чанком,
          // а не двумя десятками мелких: каждый файл на телефоне — ещё один запрос в цепочке до
          // первого экрана (PLT-22, LCP). Ленивые маршруты и моки делятся как прежде.
          codeSplitting: { groups: [{ name: 'app', tags: ['$initial'] }] },
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: false,
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      exclude: ['e2e/**', 'node_modules/**'],
      // Тесты рендерят приложение целиком (роутер, моки MSW, словари) в jsdom; когда параллельно
      // работают другие процессы (сборка, e2e, соседние агенты), 5 с по умолчанию не хватает.
      // Ожидания Testing Library (findBy*, waitFor) — src/test/setup.ts.
      testTimeout: 20_000,
      hookTimeout: 30_000,
      // CSS в тестах не обрабатывается; исключение — файл темы, который тесты токенов и
      // контраста читают как текст (`index.css?raw`).
      css: { include: [/src\/styles\/index\.css/] },
    },
  }
})
