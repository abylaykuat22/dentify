# frontend

React 19 SPA (Vite 8, TanStack Router/Query, shadcn/ui на Base UI, Tailwind 4). Каркас и
навигация — T-PLT-05; дизайн-система «Кресло» (токены и компоненты) — T-PLT-06; мобильная
навигация и адаптив (нижняя панель, меню «Ещё», таблицы → карточки) — T-PLT-10; клиент API
(orval: хуки TanStack Query и zod, мутатор, ошибки ProblemDetail и форм) — T-PLT-07; i18n
(пространства имён по модулям, ICU-склонения, date-fns, флаг казахского `VITE_I18N_KK`) — T-PLT-11.
Бизнес-логики здесь нет: всё считает и проверяет бэкенд (dentify R002). Пользовательские
строки хранятся только в i18n-словаре (dentify R003).

Соглашения, структура, скрипты и как добавить экран — `../docs/frontend.md`.

```
pnpm install
pnpm dev:mock   # без бэкенда, моки MSW (http://127.0.0.1:5173)
pnpm dev        # /api → бэкенд на 127.0.0.1:8080
                # витрина дизайн-системы (только dev): http://127.0.0.1:5173/dev/ui
pnpm api:generate  # клиент API из ../api/openapi.json (orval), результат — в репозиторий
pnpm i18n:check # словарь: ключи из кода есть в ru, лишних строк нет, строки — ICU
pnpm verify     # typecheck + lint + i18n:check + api:check + test + build + size + e2e
pnpm build && pnpm lcp  # LCP/CLS первого экрана, Lighthouse mobile (HTTP/2, как прод)
```
