import js from '@eslint/js'
import i18next from 'eslint-plugin-i18next'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import { defineConfig, globalIgnores } from 'eslint/config'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default defineConfig([
  globalIgnores([
    'dist',
    'node_modules',
    'playwright-report',
    'test-results',
    'public/mockServiceWorker.js',
    'src/app/routeTree.gen.ts',
    'src/shared/lib/cn-tables.ts',
    'src/shared/api/generated/**',
    'src/shared/api/.generated-check/**',
  ]),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: { ...globals.browser, ...globals.node },
    },
    rules: {
      '@typescript-eslint/consistent-type-imports': ['error', { fixStyle: 'inline-type-imports' }],
    },
  },
  {
    // Тема «Кресла»: `cn` — только настроенный из @/shared/lib/utils (знает text-body,
    // rounded-pill и т. п.); Base UI — по частям, а не из общего входа пакета.
    files: ['src/**/*.{ts,tsx}'],
    ignores: ['src/shared/lib/utils.ts'],
    rules: {
      // Версия typescript-eslint: `allowTypeImports` — типы из общего входа можно (они стираются).
      'no-restricted-imports': 'off',
      '@typescript-eslint/no-restricted-imports': [
        'error',
        {
          paths: [
            { name: 'cn', message: 'cn — из @/shared/lib/utils: он знает размеры, радиусы и тени «Кресла».' },
            { name: '@base-ui/react', message: 'Импортируйте часть: @base-ui/react/<компонент>.' },
            {
              name: 'date-fns',
              allowTypeImports: true,
              message: "Функцию — по пути (import { format } from 'date-fns/format'): общий вход тянет сотни модулей.",
            },
          ],
        },
      ],
    },
  },
  {
    // dentify R003: ни одной пользовательской строки в JSX — только ключи i18n (PLT-11). Проверяется
    // всё внутри JSX: текст, выражения и любые атрибуты, кроме технических (ниже). Правило видит типы
    // TypeScript: значение из объединения литералов (`variant="outline"`, `side="right"`,
    // `aria-hidden="true"`) — не текст и не ловится.
    files: ['src/**/*.tsx'],
    ignores: ['src/**/*.test.tsx', 'src/test/**'],
    plugins: { i18next },
    languageOptions: {
      parserOptions: { projectService: true, tsconfigRootDir: import.meta.dirname },
    },
    rules: {
      'i18next/no-literal-string': [
        'error',
        {
          mode: 'jsx-only',
          // Шаблонные строки тоже: `{`Всего ${n}`}`, `title={`Пациентов: ${n}`}` (по умолчанию
          // плагин их пропускает).
          'should-validate-template': true,
          'jsx-attributes': {
            exclude: [
              'className',
              'style',
              'key',
              'id',
              'data-.*',
              'to',
              'href',
              'src',
              'htmlFor',
              'name',
              'lang',
              'scope',
              // Значение выбора (вкладка, пункт списка) — код, подпись у него своя.
              'value',
              'defaultValue',
              'aria-(labelledby|describedby|controls|owns|keyshortcuts)',
            ],
          },
          // То же в объектах внутри JSX: `{ value: 'therapy', label: t('…') }`, `{ type: 'success' }`.
          'object-properties': { exclude: ['[A-Z_-]+', 'value', 'type', 'id', 'key'] },
        },
      ],
    },
  },
  {
    files: ['src/app/routes/**/*.tsx', 'src/shared/ui/**/*.tsx', 'src/test/**/*.tsx'],
    rules: {
      // Файл маршрута экспортирует `Route` рядом с компонентом (TanStack Router),
      // компоненты shadcn — варианты cva рядом с компонентом. Это их официальный формат.
      // Помощники тестов (src/test) горячей перезагрузкой не пользуются.
      'react-refresh/only-export-components': 'off',
    },
  },
])
