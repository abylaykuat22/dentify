import { defineConfig } from 'orval'

// Клиент API из спеки бэкенда (только чтение): `pnpm api:generate`. Спека растёт по ходу —
// перегенерация дешёвая; сгенерированное лежит в репозитории, чтобы сборка не зависела от orval,
// а `pnpm api:check` (в verify) падает, если закоммиченный клиент отстал от спеки.
//
// Два проекта — у каждого свои папки: orval чистит (`clean`) папку проекта перед записью, и общая
// папка досталась бы тому, кто запишет последним.
//   generated/endpoints/<тег>/<тег>.ts — функции и хуки TanStack Query (через мутатор http.ts);
//   generated/model/                   — типы схем (MeResponse, ProblemDetail …);
//   generated/zod/<тег>/<тег>.zod.ts   — zod-схемы тел, параметров и ответов (моки, тесты контракта).
//
// ORVAL_OUT — корень вывода; его подменяет только `scripts/api-check.mjs` (соседняя с generated
// временная папка: глубина та же, поэтому относительный импорт мутатора совпадает).
const OUT = process.env.ORVAL_OUT ?? './src/shared/api/generated'
const SPEC = '../api/openapi.json'

export default defineConfig({
  dentify: {
    input: { target: SPEC },
    output: {
      mode: 'tags-split',
      client: 'react-query',
      httpClient: 'fetch',
      target: `${OUT}/endpoints/dentify.ts`,
      schemas: `${OUT}/model`,
      clean: true,
      override: {
        mutator: {
          path: './src/shared/api/http.ts',
          name: 'http',
        },
        fetch: {
          includeHttpResponseReturnType: false,
        },
        query: {
          // GET → useQuery (+ useSuspenseQuery для экранов с loader), остальное → useMutation.
          // useQuery/useMutation глобально не включать: флаг делает хуком этого вида ВСЕ операции.
          useSuspenseQuery: true,
          signal: true,
          shouldExportKeys: true,
          // Сброс кэша после изменений — по ключам (PLT-23): мутация → какие запросы устарели.
          // Новое правило — сюда же, по operationId из api/openapi.json.
          mutationInvalidates: [
            {
              onMutations: ['terminateAccountSession', 'terminateAccountSessions'],
              invalidates: ['listAccountSessions'],
            },
          ],
        },
      },
    },
  },
  dentifyZod: {
    input: { target: SPEC },
    output: {
      mode: 'tags-split',
      client: 'zod',
      target: `${OUT}/zod/dentify.ts`,
      schemas: `${OUT}/zod/schemas`,
      fileExtension: '.zod.ts',
      clean: true,
      override: { zod: { generateReusableSchemas: true } },
    },
  },
})
