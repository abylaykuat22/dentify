# Фронтенд Dentify — соглашения

SPA в `frontend/`: Vite 8 · React 19 · TypeScript strict · TanStack Router (file-based) и Query ·
Tailwind 4 (`@theme`) · shadcn/ui 4 на Base UI · react-hook-form · react-i18next · orval 8 (хуки
TanStack Query и zod-схемы из `api/openapi.json`) · MSW · Vitest 5 + Testing Library · Playwright ·
ESLint 10 (flat) · size-limit.
Каркас собран в T-PLT-05; токены и компоненты «Кресла» — T-PLT-06; телефон и адаптив (брейкпоинты,
нижняя панель, меню «Ещё», таблицы → карточки) — T-PLT-10; клиент API (хуки TanStack Query и zod из
orval, мутатор, ошибки ProblemDetail, «Сеанс истёк», ошибки форм) — T-PLT-07; i18n (пространства имён,
ICU, date-fns, флаг `i18n.kk`, проверка словаря) — T-PLT-11; экран входа Vhod, общие поля логина и пароля,
выбор клиники и филиала, меню пользователя и выход — T-IAM-03 (см. «Вход и выход»); Playwright на живом стеке и демо
этапа 0 — T-PLT-16 (см. «Тесты»).

Правила (обязательны, см. `CLAUDE.md`): вся логика на бэкенде — фронт показывает ответ API и
шлёт намерения (R002); ни одной пользовательской строки в JSX — только ключи i18n (R003, это
проверяют ESLint `i18next/no-literal-string` и `pnpm i18n:check`, см. «i18n»); любая UI-правка прогоняется в Playwright (R018);
всплывающее — в портале (R022, Base UI Tooltip/Popover/Dialog); select/input — с поиском и
tooltip (R011); жёлтая кнопка — одна на область.

## Скрипты

| Скрипт | Что делает |
|---|---|
| `pnpm dev` | dev-сервер на http://127.0.0.1:5173, `/api` проксируется на бэкенд `127.0.0.1:8080` (другой адрес — `DENTIFY_API_TARGET=http://127.0.0.1:18080 pnpm dev`, так же для `pnpm preview`) |
| `pnpm dev:mock` | то же, но `/api` отвечают моки MSW (`vite --mode mock`); бэкенд не нужен |
| `pnpm build` / `pnpm preview` | production-сборка в `dist/` (с `.vite/manifest.json`) + `scripts/check-build.mjs` (витрины `/dev/ui` в сборке нет) / её просмотр |
| `pnpm typecheck` | `tsc -b` (проекты `tsconfig.app.json` + `tsconfig.node.json`) |
| `pnpm lint` | ESLint (TS, react-hooks, react-refresh, i18next — строка в JSX без ключа i18n — ошибка) |
| `pnpm i18n:check` | целостность словаря: ключи из кода есть в ru, лишних строк нет, строки — ICU, словари других языков повторяют ru (см. «i18n») |
| `pnpm test` / `pnpm test:watch` | Vitest (jsdom, `src/**/*.test.{ts,tsx}`) |
| `pnpm e2e` / `pnpm e2e:ui` | Playwright; сам поднимает свой `dev:mock` на свободном порту из 5300–5799 и сначала прогревает его (проект `warmup`). У каждого прогона свои сервер, артефакты (`test-results/run-<id>/`) и HTML-отчёт (`playwright-report/run-<id>/`) — прогоны можно запускать параллельно (см. «Тесты»). Обновить эталоны скриншотов — `pnpm e2e --update-snapshots`. С `DENTIFY_STACK_URL` — проекты живого стека `stack-*` вместо моков (см. «Тесты», «Живой стек») |
| `pnpm size` | бюджеты первого экрана (size-limit, после `pnpm build`): начальная загрузка ≤ 218,5 kB gzip, чанки каркаса ≤ 21 kB (см. «Производительность») |
| `pnpm lcp` | LCP/CLS первого экрана на телефоне: Lighthouse mobile по `dist/` (после `pnpm build`, без моков) с заглушкой `/me`; по умолчанию HTTP/2 + TLS, как прод, `--http1` — как `vite preview`, справочно (бюджет 2,5 с — только HTTP/2). В `verify` не входит (см. «Производительность») |
| `pnpm verify:perf` | build → size → lcp: гонять при любой правке стартового маршрута (каркас, `/` → первый раздел, `index.html`, начальный чанк) |
| `pnpm api:generate` | перегенерировать клиент из `../api/openapi.json` (orval) — см. «API-клиент» |
| `pnpm api:check` | клиент в репозитории совпадает со спекой (перегенерация во временную папку и сравнение) |
| `pnpm verify` | всё вместе: typecheck → lint → i18n:check → api:check → test → build → size → e2e |

Моки включаются режимом `mock` или переменной `VITE_API_MOCK=true` (например, для сборки демо:
`VITE_API_MOCK=true pnpm build`). Флаги функций — такими же переменными: `VITE_I18N_KK=true` —
казахский интерфейс (см. «i18n»). Файлы `.env*` не используются (корневой `.gitignore` их прячет).

## Структура

```
frontend/
├── e2e/                      # Playwright на моках: *.spec.ts, helpers.ts (персона мока, проект, прокрутка вбок,
│   │                         #   axe, цели касания, строки списка в портале)
│   ├── stack/                #   живой стек (T-PLT-16): stack.ts (адреса, dev-владелец, вход, проверки безопасности),
│   │                         #   owner.setup.ts / owner.teardown.ts, demo/cabinet/security.spec.ts
│   └── __screenshots__/      #   эталоны скриншотов (в репозитории): <спека>/<имя>-<проект>-<ОС>.png
├── public/                   # favicon, mockServiceWorker.js (msw init, не править руками)
├── .size-limit.js            # бюджет начальной загрузки (граф из манифеста сборки)
├── scripts/check-build.mjs   # после `vite build`: код витрины /dev/ui не попал в dist
├── scripts/api-check.mjs     # `pnpm api:check`: клиент orval в репозитории совпадает со спекой
├── scripts/lcp.mjs           # `pnpm lcp`: замер LCP/CLS сборки (Lighthouse mobile)
├── src/
│   ├── main.tsx              # точка входа: шрифты, стили, MSW (в режиме моков), язык (i18nReady), роутер;
│   │                         #   сразу — /me и чанки стартового маршрута (app/startup.ts)
│   ├── app/                  # оболочка приложения
│   │   ├── App.tsx           # провайдеры: QueryClient, TooltipProvider, RouterProvider; Toaster — в простое
│   │   ├── startup.ts        # запуск без водопада: /me и чанки стартового адреса параллельно (PLT-22)
│   │   ├── idle.ts           # whenIdle: после шрифтов и первого кадра — то, чего не видно на первом экране
│   │   ├── useLanguageRefresh.ts # смена языка: пересчёт заголовков (head) и перезапрос ответов сервера
│   │   ├── router.tsx        # createAppRouter / createAppQueryClient, регистрация типов
│   │   ├── routerContext.ts  # контекст роутера ({ queryClient })
│   │   ├── guards.ts         # requireSession, guardSection(id), sectionHead, ForbiddenError
│   │   ├── errors.ts         # onCaughtError корня React (ForbiddenError в консоль не пишет)
│   │   ├── sessionExpiry.ts  # 401 посреди работы → вход с «Сеанс истёк» (подключён в __root)
│   │   ├── routeTree.gen.ts  # генерируется плагином, в lint не входит
│   │   ├── routes/           # file-based маршруты (см. ниже)
│   │   └── shell/            # каркас: AppShell, Sidebar, Header, PillTabs, SidePanel,
│   │                         #   BottomNav + MorePage (телефон), mobileNav.ts (пункты панели
│   │                         #   и «Ещё»), UserMenu (карточка пользователя сайдбара) + UserMenuDropdown
│   │                         #   (само меню «Мой профиль», «Выйти» — своим чанком), sections.ts
│   │                         #   (конфиг разделов), screen.ts (staticData
│   │                         #   экрана), panels.ts (известные панели), usePanel.ts, hotkeys.ts
│   │                         #   (Ctrl K), useSessionSync.ts (свежесть /me: фокус, 403, 401)
│   ├── modules/              # экраны по доменам — по папке на раздел:
│   │   └── <module>/         #   schedule, patients, staff, inventory, clinic, access, profile;
│   │                         #   access/ — ещё экраны без входа: LoginPage (Vhod), SelectClinicPage,
│   │                         #   AuthLayout (панель «О сервисе»), AuthIllustration, AuthStubPage
│   ├── shared/
│   │   ├── api/              # клиент API (см. «API-клиент»): http.ts (мутатор orval — единственный
│   │   │                     #   fetch: ApiError/NetworkError, CSRF, Accept-Language, 401 →
│   │   │                     #   onUnauthorized), problem.ts (текст ошибки по коду), forms.ts
│   │   │                     #   (errors[] → react-hook-form), query.ts (повторы, `live`,
│   │   │                     #   invalidateApi), me.ts (адаптер /me, meQueryOptions),
│   │   │                     #   useCurrentUser.ts (живой /me для каркаса), useSignOut.ts
│   │   │   ├── generated/    #   orval, не править руками (`pnpm api:generate`): endpoints/<тег>/
│   │   │   │                 #   — функции и хуки Query, model/ — типы, zod/ — zod-схемы
│   │   │   └── mocks/        #   MSW: fixtures.ts, problems.ts (ответы-ошибки), handlers.ts,
│   │   │                     #   browser.ts, node.ts, contract.test.ts (моки = схемы спеки)
│   │   ├── i18n/             # см. «i18n»: index.ts (init, языки, changeUiLanguage), resources.ts
│   │   │                     #   (NAMESPACES, словарь ru), locales/<язык>/<пространство>.json, keys.ts
│   │   │                     #   (TKey), dates.ts (date-fns: локаль, первый день недели), localized.ts
│   │   │                     #   (LocalizedText {ru, kk}), integrity.ts + тесты (проверка словаря)
│   │   ├── lib/              # utils (cn с темой «Кресла») + cn-tables.ts (таблицы cn, генерирует
│   │   │                     #   `cn build`, не править руками), breakpoints (MEDIA, useViewport), flags
│   │   │                     #   (флаги функций, i18n.kk), title, format (даты по шаблонам словаря),
│   │   │                     #   useMediaQuery, safeReturnTo, loginInput (маска «Телефон или e-mail»),
│   │   │                     #   passwordStrength (zxcvbn-ts лениво, чек-лист, сверка повтора),
│   │   │                     #   keepSelectedInView (выбранная вкладка в полосе, прокрутка вбок)
│   │   └── ui/               # дизайн-система «Кресло»: компоненты shadcn (kebab-case) +
│   │                         #   свои составные (combo, panel, search-field …), tokens.ts,
│   │                         #   StateScreen, StubScreen — см. «Дизайн-система»
│   ├── styles/               # index.css (Tailwind + @theme «Кресло» + переменные shadcn),
│   │                         #   fonts.ts (self-hosted шрифты @fontsource), theme.ts + тесты
│   │                         #   токенов и контраста
│   ├── dev/ui/               # витрина /dev/ui (только dev-сборка) и её словарь `devUi`
│   └── test/                 # setup.ts (jest-dom, MSW, полифилы), renderApp.tsx, kkFixture.ts
│                             #   (фикстурный словарь kk для тестов переключения языка)
├── components.json           # shadcn: style base-nova, алиасы на src/shared/*
├── Dockerfile, .dockerignore # образ web для compose (T-PLT-15): pnpm build → Caddy (deploy/caddy/Caddyfile.app)
├── orval.config.ts, playwright.config.ts, vite.config.ts, eslint.config.js
```

Алиас `@/` → `src/`. Слои: `modules` и `app` могут импортировать `shared`; `shared` не
импортирует `modules`/`app` (кроме типа `FileRoutesByTo` из сгенерированного дерева).
`src/dev/` — только для dev-сборки: его импортирует лишь маршрут `/dev/ui` (и тип словаря
в `i18next.d.ts`); из экранов и `shared` — нельзя.
Страницы разделов живут в `modules/<раздел>/` даже пока они заглушки (`ScheduleStubPage`,
`StaffStubPage`, …): следующие задачи заменяют заглушку экраном в той же папке.

## Маршруты (TanStack Router, file-based)

Файлы в `src/app/routes/`, плагин `@tanstack/router-plugin` с `autoCodeSplitting: true`:
`component` маршрута выносится в отдельный ленивый чанк автоматически, `.lazy.tsx` не нужен.
**Условие: опции переданы литералом объекта** — `createFileRoute('…')({ component: … })`.
Опции из вызова функции (`createFileRoute('…')(makeOptions())`) плагин не видит и не делит:
компонент остаётся в начальном бандле. Своя нарезка маршрута — `codeSplitGroupings` в его опциях:
лейауты разделов (`component: Outlet`) делят только `errorComponent` — отдельный чанк ради `Outlet`
был бы лишним запросом в цепочке первого экрана. В юнит-тестах (`mode === 'test'`) маршруты не
делятся вовсе (`autoCodeSplitting: !unitTests` в vite.config.ts): первая отрисовка экрана в jsdom
иначе ждёт загрузки ленивого модуля, а под нагрузкой это таймауты.

- `__root.tsx` — `createRootRouteWithContext<RouterContext>()`, `<HeadContent />`, 404 вне каркаса.
- `login.tsx` — `/login?returnTo=&reason=expired`, экран входа по макету Vhod (`LoginPage`, см. «Вход и
  выход»). `returnTo` — только путь этого же приложения и не сам вход (`isReturnPath`; иначе значение по
  умолчанию, см. «Гарды»), `reason=expired` — плашка «Сеанс истёк».
- `select-clinic.tsx` — `/select-clinic?returnTo=`: выбор клиники и филиала сессии, минимальный
  VyborKliniki (`SelectClinicPage`; записи на сегодня, кресла, последний выбор — T-IAM-08). Клиника уже
  выбрана — сразу на `returnTo`.
- `forgot-password.tsx`, `register.tsx` — заглушки «Забыли пароль?» (T-IAM-17) и «Зарегистрировать»
  (T-CLN-05) с экрана входа: та же раскладка без входа, жёлтая «Вернуться ко входу» (`AuthStubPage`).
- `_app.tsx` — pathless-лейаут всех экранов за входом: `beforeLoad` → `requireSession` (guards.ts:
  `queryClient.ensureQueryData(meQueryOptions())`, 401 → `redirect({ to: '/login', search:
  { returnTo: location.href } })`); клиника не выбрана (`context = null`) → `/select-clinic?returnTo=`;
  пользователь кладётся в контекст (`context.me`) — его читают гарды в `beforeLoad`. Первый гард после
  запуска берёт `/me`, который точка входа уже запросила (`takeStartupSession`, app/startup.ts), —
  без второго запроса и при 401. Общий search-параметр `panel` (открытая боковая панель,
  `z.catch(z.optional(z.enum(PANELS)), undefined)` на `zod/mini`).
- `_app/index.tsx` — корень → первый доступный раздел (`homePathFor(me)`). `_app/$.tsx` — 404
  внутри каркаса.
- `_app/<section>.tsx` — лейаут раздела: `beforeLoad: guardSection('<section>')`,
  `head: sectionHead` (вкладка браузера «Нет доступа — Dentify»), `errorComponent: SectionError`
  («Раздел закрыт для вашей роли» внутри каркаса), `component: Outlet`.
- `_app/<section>/index.tsx`, `_app/<section>/<tab>.tsx` — вкладки раздела. Заглушка вкладки:

  ```tsx
  export const Route = createFileRoute('/_app/clinic/prices')({
    head: () => ({ meta: [{ title: tabTitle('shell:tabs.clinic.prices', 'shell:nav.clinic') }] }),
    component: () => <ClinicStubPage soon />,
  })
  ```
- `_app/profile.tsx` — «Мой профиль», без права.
- `_app/more.tsx` — `/more`, меню «Ещё» (макет Telefon-Eshche), без права; на телефоне — пункт
  нижней панели, на планшете и десктопе открывается по адресу (там всё есть в сайдбаре).
- `_app/schedule/new.tsx` — `/schedule/new`, новая запись: сюда ведут «+» нижней панели и жёлтая
  кнопка шапки расписания. Пока заглушка; мастер и `?patientId&doctorId&start&chairId` — T-SCH-16.
- `dev/ui.tsx` — `/dev/ui`, витрина дизайн-системы вне каркаса и без входа. Только dev-сборка:
  в production `beforeLoad` бросает `notFound()`, а компонент — `import.meta.env.DEV ?
  <DevUiPage /> : null`, поэтому сборка вырезает витрину и её словарь (сторожит
  `scripts/check-build.mjs`).

### Добавить экран

1. Создать файл маршрута в `src/app/routes/_app/<section>/<name>.tsx`; после сохранения плагин
   допишет путь в `createFileRoute('…')` и обновит `routeTree.gen.ts` (при запущенном dev или build).
2. Страницу положить в `src/modules/<section>/<Name>Page.tsx`; в маршруте — `component: NamePage`
   (литералом, см. выше). Доступ к search/params/context внутри страницы —
   `getRouteApi('/_app/<section>/<name>')` (не импортировать `Route` из файла маршрута: цикл импортов).
3. Как каркас показывает экран — `staticData` маршрута (`src/app/shell/screen.ts`, читается у самого
   глубокого совпадения): `titleKey` — заголовок шапки вместо названия раздела (полный ключ
   `shell:actions.newAppointment` — «Новая запись»,
   «Мой профиль»); `bare: true` — без вкладок раздела и жёлтой кнопки шапки (мастер, карточка);
   `ownHeader: true` — шапки каркаса нет, экран рисует свою (меню «Ещё»). `staticData` не делится
   autoCodeSplitting'ом — шапка знает о нём до загрузки чанка.
4. Заголовок вкладки — `head: () => ({ meta: [{ title: pageTitle('пространство:ключ') }] })` →
   «<Экран> — Dentify»; вкладка раздела — `tabTitle('shell:tabs.…', 'shell:nav.…')` → «<Вкладка> ·
   <Раздел> — Dentify»; готовая строка (имя пациента) — `pageTitleOf(name)`. При смене языка
   заголовки пересчитываются сами (`useLanguageRefresh`).
5. Новая вкладка раздела — добавить в `SECTIONS[…].tabs` (`src/app/shell/sections.ts`): пилюли
   в шапке рисуются из конфига. Новый раздел — туда же (`permission`, `icon`, `labelKey`,
   `primaryAction` — жёлтая кнопка: `labelKey`, `icon` для телефона, `to`, если экран действия уже
   есть, иначе TODO с id задачи; ключи — `shell:nav.*`,
   `shell:tabs.<раздел>.*`, `shell:actions.*`), плюс подпись «домой» `shell:home.<id>`; на телефоне — место на нижней панели или пункт «Ещё» с подписью и цветом
   (`BOTTOM_NAV` / `MORE_SECTIONS` в `src/app/shell/mobileNav.ts`).
6. Отдельно проверять телефон не нужно: `e2e/responsive.spec.ts` берёт все адреса из
   `routeTree.gen.ts` и сам прогоняет новый экран на 390 и 360 px (прокрутка вбок, поля ≥ 16 px,
   цели касания ≥ 44). Экран с особой персоной (как `/login`) — строка в `PERSONA` там же.

### Состояние экрана — в URL

Всё, что определяет вид (вкладка, фильтр, страница, дата, филиал, открытая панель) — search-
параметры со схемой в `validateSearch`; неверное значение заменяется значением по умолчанию
через `z.catch(…, default)`, а значения по умолчанию убираются из адреса `stripSearchParams(defaults)`.
Схемы маршрутов — на **`zod/mini`** (`import * as z from 'zod/mini'`, Standard Schema — TanStack Router
принимает её без адаптера): они в начальной загрузке, а полный zod — ~20 kB gzip (PLT-22). Полный
`zod` — только в ленивых формах, моках и тестах:

```ts
import * as z from 'zod/mini'

const search = z.object({
  filter: z.catch(z.enum(PATIENT_FILTERS), 'all'),
  page: z.catch(z.int().check(z.minimum(1)), 1),
})
export const Route = createFileRoute('/_app/patients/')({
  validateSearch: search,
  search: { middlewares: [stripSearchParams({ filter: 'all', page: 1 })] },
})
```

Боковая панель — параметр `panel` (`usePanel()` в `src/app/shell/usePanel.ts`): `open('search')`
делает push, «Назад» браузера закрывает панель; `close()` — `history.back()`, если панель открыли
здесь, иначе (пришли по ссылке) `replace` без параметра. Escape закрывает. Неизвестное значение
(`?panel=bogus`) — «панель закрыта», параметр уходит из адреса. Новая панель — значение в
`PANELS` (`src/app/shell/panels.ts`) и ветка в `SidePanel.tsx` (позже — по разделам).

## Каркас (AppShell) — по макету Raspisanie-Den

- Сетка: слева плавающий сайдбар; справа сверху — шапка и вкладки-пилюли на всю ширину, под
  ними две колонки: контент (`<main>`) и боковая панель (≥ 1024 px). Верх панели выровнен по
  карточке контента; открытие панели не сжимает шапку и не двигает вкладки.
- Шапка: заголовок раздела, контекст «дата · филиал» (заглушка; TODO(T-PLT-17): «сегодня» и пояс
  клиники — с сервера), «Найти пациента Ctrl K» (уже 1024 px — круглая кнопка с подсказкой, как в
  макете Telefon) и одна жёлтая кнопка раздела. На закрытом для роли разделе — только заголовок:
  без вкладок и главного действия (R002). TODO(T-IAM-07): главное действие — по `allowedActions`
  из API, когда они появятся.
- Вкладки-пилюли (`PillTabs`): не влезают — горизонтальная прокрутка без полосы, выбранная
  вкладка подъезжает в видимую область (и после загрузки шрифта), пока полосу не прокрутили
  руками — см. «Телефон и адаптив», «Вкладки».
- Ctrl K / ⌘K (`hotkeys.ts`) проверяет физическую клавишу `event.code === 'KeyK'`: в русской и
  казахской раскладке `key` = «л». Новые горячие клавиши — так же, по `code`.
- Боковая панель (`SidePanel`) — отдельный чанк: монтируется при первом открытии (`?panel=`) и
  догружается в простое после первого экрана (`whenIdle`), чтобы Ctrl K открывал её без задержки.
  Это общий `Panel` дизайн-системы: ≥ 1024 px немодальная колонка
  рядом с контентом (клик мимо не закрывает), 768–1023 px модальная панель справа, < 768 px —
  нижний лист (Base UI Drawer); в модальных видах фокус заперт внутри и после закрытия
  возвращается на кнопку поиска. Поле — `SearchField`.
- Вкладки-пилюли и жёлтая кнопка шапки — компоненты дизайн-системы (`pillTabVariants`,
  `Button`). Тёмные поверхности (сайдбар, нижняя панель) помечены `surface-dark`: контур фокуса
  на них белый.
- Сайдбар: 768–1023 px — только иконки с подсказками Base UI Tooltip (открываются и по фокусу с
  клавиатуры), ≥ 1024 px — с подписями; < 768 px скрыт, вместо него нижняя панель (см. «Телефон»).
  Внизу — карточка пользователя (`UserMenu`): кнопка меню «Меню пользователя: <ФИО>» с пунктами «Мой
  профиль» и «Выйти» (`DropdownMenu` в портале; на 768–1023 px — справа от сайдбара, на десктопе — над
  карточкой её шириной). На «Моём профиле» карточка подсвечена (`data-current`: это кнопка, а не ссылка).
  Само меню (Base UI Menu) — своим чанком (`UserMenuDropdown`), как боковая панель: в чанке каркаса оно
  стояло бы в цепочке до первого экрана (+0,14 с к LCP, PLT-22). До его прихода на месте карточки — та
  же карточка простой кнопкой (`data-user-menu="placeholder"`, после — `"menu"`); чанк грузится в простое
  после первого экрана (`whenIdle`) или сразу, как к карточке потянулись (наведение, фокус, нажатие). Что
  человек успел сделать, доигрывается: нажал (мышь, Enter, пробел, стрелка) — меню откроется, фокус был на
  карточке — останется на кнопке меню; под нажатым указателем кнопка не меняется (клик ушёл бы мимо).
  Нажатие с клавиатуры доигрывается кликом с `detail: 0` — Base UI по нему считает открытие клавиатурным и
  ставит фокус на первый пункт, как от Enter по кнопке меню (`click()` дал бы `detail: 1` — меню без фокуса на
  пункте). Доигрывание — в следующем кадре после монтирования: в dev StrictMode повторяет эффекты, и повтор
  эффекта списка Base UI при ещё закрытом меню стирал отметку «с клавиатуры» (флейк `sidebar-collapsed.spec.ts`,
  исправлен 2026-09-26 в T-PLT-15).
  Окно ниже сайдбара (~500 px: телефон боком — это вид планшета без нижней панели, масштаб 200 %,
  PLT-21) — карточка сайдбара не вылезает за окно, а прокручивается сама (`overflow-y-auto
  overscroll-contain`: колесо, палец, Tab; страница не двигается), без полосы прокрутки, как у
  вкладок: в карточке 72 px полоса отняла бы место у иконок и аватара. Активный пункт подъезжает в
  видимую часть карточки (`keepActiveInView` в `Sidebar.tsx`: при появлении, после перехода и при
  смене размеров). Медиазапросы по высоте не заводим — брейкпоинты только по ширине (`MEDIA`).
- Жёлтая кнопка шапки — `primaryAction` раздела (`SectionAction` в sections.ts: `labelKey`, `icon`,
  `to`). Есть `to` — это ссылка в виде кнопки (`buttonVariants` + `data-slot="button"
  data-variant="default"`, чтобы e2e считал «лампы»). Нет — экрана ещё нет (TODO с id задачи у
  раздела), и кнопка по нажатию показывает уведомление «Экран «…» пока в разработке»
  (`shell:actions.soon`; модуль уведомлений грузится по нажатию): «мёртвой» лампы быть не должно —
  это сторожит `one-lamp.spec.ts` («у главного действия шапки есть отклик»). ≥ 768 px — пилюля
  «+ Новый пациент», как в макетах; **на телефоне — белый круг 48 px рядом с поиском** (место и вид
  круглой кнопки в шапке макета Telefon: `bg-card`, тень поля, значок раздела «лагуной», `icon` — не
  путается с «+» панели), подпись — для диктора, `data-variant="outline"`. Жёлтый круг на экране
  телефона один — «+» нижней панели: два жёлтых «добавить» рядом (круг в шапке и «+») читались бы
  как одно и то же. Вид выбирает `useMediaQuery(MEDIA.phone)` (в jsdom — лампа). «Новая запись» на
  телефоне в шапке не дублируется: это «+» нижней панели. Решение (вместо «контекстного +») — до
  слова владельца, см. «Открытые вопросы».

**Почему сайдбар свой, а не shadcn `Sidebar` (R021).** Готовый компонент смотрели
(shadcn 4.21, `variant="floating"`, `collapsible="icon"`): его свёрнутость — состояние, которое
переключает пользователь (`SidebarProvider`, cookie `sidebar_state`, Ctrl+B), а у нас она задана
шириной экрана; ниже 768 px он превращается в выезжающий `Sheet`, а по макету на телефоне —
нижняя панель; его хоткей проверяет `event.key` и не работает в раскладке ЙЦУКЕН; вид «Кресла»
(лагуна, «лампа», пункты 48 px, скругления 24/14) потребовал бы переопределить почти все классы
и тянул бы Sheet/Separator/Skeleton/Input в начальный бандл. Свой код здесь — только разметка:
доступность и порталы дают официальные примитивы (Base UI Tooltip, Dialog; ссылки TanStack
Router с `aria-current`). Если понадобится сворачивание по кнопке — переходим на shadcn `Sidebar`.

## Телефон и адаптив (T-PLT-10, PLT-18)

**Брейкпоинты — mobile-first.** Разметка пишется для телефона, `md:` и `lg:` её расширяют:
телефон < 768 px (эталон 390×844, минимум 360), планшет `md:` 768–1023, десктоп `lg:` ≥ 1024;
`xl:` 1280 — только уточнение внутри десктопа (ширина поиска в шапке). В теме
`--breakpoint-*: initial` — `sm:` и `2xl:` не существуют (класс молча не сгенерируется, поэтому
их ловит `src/styles/breakpoints.test.ts`). В коде — только `MEDIA.phone | tabletUp |
belowDesktop | desktop` и `useViewport()` из `src/shared/lib/breakpoints.ts`, никаких строк
`(max-width: …)`: числа сверяются с CSS тестом.

| Что | Телефон < 768 | Планшет 768–1023 | Десктоп ≥ 1024 |
|---|---|---|---|
| Навигация | нижняя панель + меню «Ещё» | сайдбар из иконок с подсказками | сайдбар с подписями |
| Боковая панель (`Panel`) | нижний лист: свайп вниз и крестик | модальная справа | колонка рядом с контентом |
| Таблица (`ResponsiveTable`) | карточки | таблица | таблица |
| Жёлтая кнопка шапки | нет: жёлтый — «+» панели, действие раздела — белый круг | есть | есть |
| Поля | шрифт 16 px, компактные 44 px | 15 / 14 px, компактные 40 px | так же |

**Нижняя панель** (`BottomNav`, макет Telefon): «Расписание · Пациенты · + · Склад · Ещё» на
плавающей «лагуне» 70 px (`shadow-dock`), сетка из 5 ячеек. Пункты — по правам `/me`; место
скрытого пункта остаётся пустым, «+» и «Ещё» не прыгают между ролями. «+» (60 px,
`shadow-lamp-glow`) — навигация на `/schedule/new`, виден при праве на расписание (TODO:
`allowedActions`). «Ещё» подсвечен и на разделах из меню «Ещё» (Врачи, Клиника, Доступы,
профиль): `isMoreArea` в `mobileNav.ts`, ссылке ставится `aria-current="page"`. Пункты — 56 px в
высоту и ⅕ ширины. «+» — действие, `aria-current` у него нет никогда (`useLinkProps` вместо `Link`,
который ставит его сам): на `/schedule/new` текущий пункт один — «Расписание». Панель уже макета
(< 366 px, то есть телефоны 360–389 px) — контейнерный запрос `@max-[22.875rem]:` к самой панели
(`@container`), а не новый брейкпоинт: «+» занимает ровно свой круг, края тоньше (`px-0.5`),
подписи `tracking-tight` — «Расписание» (самая длинная, 800) помещается в свою ячейку с
просветом ≥ 6 px до соседней; на 390 px — ровно геометрия макета. Проверка — `mobile.spec.ts`
(Range текста подписи внутри ячейки на 360/375/390). Новая или переведённая подпись (казахская)
длиннее «Расписания» — сначала этот тест.

**Меню «Ещё»** (`MorePage`, макет Telefon-Eshche): карточка пользователя (ФИО, «роль · клиника»
из `/me`; без роли — только клиника), текущий филиал (TODO(T-IAM-08, T-PLT-17): список в портале
и смена; пока — уведомление «появится»), разделы с подписями и цветом кружка — только
разрешённые плюс «Мой профиль», «Рус | Қаз» — только при флаге `i18n.kk` и словаре kk (`LanguageSwitch`, см. «i18n»),
«Выйти» — `useSignOut()` (POST `/auth/logout` → `/login`; ошибка — `role="alert"`).

**Safe-area iOS** (`viewport-fit=cover` в `index.html`): панель приподнята над полоской «домой»
(`bottom-[max(0.75rem,env(safe-area-inset-bottom))]`), каркас — отступы `max(1rem,
env(safe-area-inset-*))` сверху и по бокам, снизу `calc(7rem + inset)` под панель; нижний лист —
подвал или тело с `env(safe-area-inset-bottom)`. Произвольные значения отступов тест
`usage.test.ts` разрешает только с `env(safe-area-inset-*)`.

**Клавиатура.** `interactive-widget=resizes-content` — экранная клавиатура уменьшает окно, и
нижний лист с полем поднимается над ней. **Ограничение iOS:** Safari этот ключ не поддерживает —
клавиатура там перекрывает окно, а не уменьшает его; e2e проверяет только сам `meta` (Chromium).
Для этапа 0 (в листе — одно поле поиска в верхней части) этого достаточно; в телефонном мастере
записи (T-SCH-26) лист с полями проверить на iPhone или в WebKit и при нужде поднимать его по
`window.visualViewport` (`resize` → нижний отступ листа). Пока фокус в текстовом поле, нижняя панель спрятана:
вариант `typing:` (`@custom-variant` в `index.css`) — `typing:hidden`. Поля на телефоне — 16 px
(`text-body-lg md:text-body`), иначе iOS увеличивает страницу при фокусе. Телефон — `PhoneInput`
(`type="tel"`), ИИН — `IinInput` (`inputMode="numeric"`, 12 знаков, без автозаполнения):
цифровая клавиатура. Проверки номера и ИИН — на сервере (R002).

**Вкладки** прокручиваются вбок без полосы: вкладки-маршруты (`PillTabs`) и вкладки экрана
(`TabsList`). Выбранную (`aria-current="page"` или `aria-selected="true"`) в видимую область
доводит общий ref-колбэк `keepSelectedInView` (`src/shared/lib/keepSelectedInView.ts`): при
появлении полосы, при смене выбранной или набора вкладок и при смене ширины пилюль (поздний
шрифт, язык — `ResizeObserver`). Смену ширины он доводит, **только пока пользователь не взялся
за полосу**: касание, колесо, клавиши или прокрутка не им — и полоса сама больше не едет (иначе
догрузившийся шрифт отбрасывал бы её из-под пальца) до следующей смены выбранной. Пока полосу не
трогали, её место считается от начала (0 или выбранная у правого края) — не зависит от того,
каким шрифтом её показали сначала (скриншоты стабильны). Роутер после перехода восстанавливает
прокрутку полосы (позицию со старого адреса), поэтому `PillTabs` доводит выбранную ещё раз по
`router.subscribe('onRendered')` (второй аргумент `keepSelectedInView`). `TabsList` адрес не
меняет; если вкладки экрана свяжут с адресом (`?tab=`), им нужен тот же второй аргумент. `ref` у
`TabsList` занят и не передаётся.

## Гарды и права

**Откуда пользователь.** `GET /api/v1/me` описан в `api/openapi.json` (`operationId: getMe`): `MeResponse
{ account { id, fullName, initials, language, email, phone }, context { clinic, branch | null } | null,
memberships[] }`. Адаптер `toCurrentUser` (`src/shared/api/me.ts`) переводит его в `CurrentUser`:
ФИО и инициалы — с сервера (фронт их не считает, R002), клиника и филиал — из `context`. Тот же
`MeResponse` возвращают `POST /auth/login` и `POST /me/context` — их ответ кладётся прямо в кэш
(`queryClient.setQueryData(ME_QUERY_KEY, toCurrentUser(resp))`), `/me` не перезапрашивается.

**Роль и права — пока расширение с TODO.** В спеке их ещё нет: бэкенд добавит поля с каталогом прав
(T-IAM-06, T-IAM-07), «не ломая ответ». До тех пор `MeRightsExtension { role?, permissions?: string[] }`
(`MeWithRights = MeResponse & MeRightsExtension`) заполняют только моки. Живой бэкенд поля не
присылает — тогда роли нет (строка роли в сайдбаре и профиле не выводится) и **разделы не
прячутся**: меню полное, закрытое закроет сервер ответом 403 (после него `/me` перезапрашивается).
Поле есть (даже пустое) — остаются только известные фронту коды (`schedule.view`, `patients.view`,
`staff.view`, `inventory.view`, `clinic.view`, `access.view`). Когда поля появятся в спеке —
`pnpm api:generate`, убрать расширение и поправить только `toCurrentUser`.

**Клиника не выбрана** (`context = null`: врач в нескольких клиниках, клиника с несколькими
филиалами) — каркас не открывается, `_app` уводит на `/select-clinic?returnTo=…`: «Здравствуйте, <ФИО>!
Где работаем сегодня?» и карточка на каждый филиал из `branches` активных членств (членство без
филиалов в ответе — карточка клиники), клик → `POST /api/v1/me/context { clinicId, branchId }` (без
филиала — `null`, единственный сервер выберет сам) → ответ в кэш `/me` → обратно на `returnTo`; отказ
сервера (членство отключили только что) — его текстом; там же «Выйти». TODO(T-IAM-08, T-PLT-17):
записи на сегодня, кресла, последний выбор и переключатель филиала в каркасе.

**Гарды.** Пункты сайдбара и нижней панели — `visibleSections(me)`; прямая ссылка на закрытый
раздел — `ForbiddenError` из `beforeLoad` раздела и экран «нет доступа» (`SectionError`). Ссылка
«домой» на «нет доступа» и 404 ведёт на `/` (он отправит в первый доступный роли раздел) с
подписью по этому разделу («К расписанию», «На склад»…). Фронт ничего не решает сам: нет права —
нет пункта, сервер всё равно ответит 403.

«Нет доступа» — ожидаемое состояние, не сбой: React о `ForbiddenError` в консоль не пишет
(`onCaughtError` в `src/app/errors.ts`, подключён в `main.tsx` и `renderApp`). Остаётся одна строка
dev-предупреждения роутера `Warning: Error in route match: /_app/<раздел>` — TanStack Router пишет
её (только в dev) на любую ошибку, пойманную `errorComponent`, и отключить её нельзя. Это
ожидаемый шум; всё остальное в консоли — настоящие ошибки. Без броска ошибки «нет доступа» не
сделать: при флаге в контексте дочерние вкладки загрузились бы и перебили заголовок вкладки
браузера «Нет доступа — Dentify» своим (побеждает самый глубокий `head`).

**Свежесть `/me`.** По спеке интерфейс перезапрашивает `/me` при фокусе вкладки и после ответа 403:
`meQueryOptions` — `refetchOnWindowFocus: 'always'` (глобально — только устаревшее), каркас читает
пользователя через `useCurrentUser()` (`useSuspenseQuery`, данные уже в кэше после `beforeLoad`),
а `useSessionSync()` в `AppShell`:
- 403 на любом запросе или изменении → `invalidateQueries(ME_QUERY_KEY)`;
- `/me` изменился → `router.invalidate()`: гарды разделов и корня перепроверяются, открытый
  закрытый раздел сразу показывает «нет доступа». `CurrentUser` — простые данные (массивы, не
  `Set`), поэтому неизменившийся ответ остаётся тем же объектом и лишних перезагрузок нет;
- 401 здесь не ловится: его видит сам мутатор на любом запросе, и корень приложения уводит на
  `/login?returnTo=<текущий адрес>&reason=expired` с «Сеанс истёк» (см. «API-клиент»); кэш
  прошлой сессии очищает экран входа при открытии.

`returnTo` после входа и выбора клиники — только путь этого же приложения
(`src/shared/lib/safeReturnTo.ts`): отсекаются `//host`, `/\host` (браузер читает «\» как «/»),
схемы и управляющие символы, итог сверяется с `location.origin` (`isSameOriginPath`); сам вход (`/login`
в любом регистре, со «/» в конце) — тоже: после входа — в кабинет, а не снова на форму (`isReturnPath`).

## API-клиент (T-PLT-07: PLT-07, PLT-23)

Весь обмен с сервером — через клиент, сгенерированный orval из `api/openapi.json`: хуки TanStack
Query, функции и типы. Ручных `fetch` нет (PLT-23); форма тела и ответа сверяется со спекой на
`pnpm typecheck`. Логики в клиенте нет (R002): он показывает ответ и шлёт намерения.

### Генерация (`orval.config.ts`)

Два проекта orval, у каждого свои папки (orval чистит папку проекта перед записью — общая
досталась бы последнему):

| Папка `src/shared/api/generated/` | Что там | Для чего |
|---|---|---|
| `endpoints/<тег>/<тег>.ts` | функции (`getMe`, `login` …), ключи (`getGetMeQueryKey`), `get…QueryOptions`, хуки `use…` (GET → `useQuery` + `use…Suspense`, остальное → `useMutation`) | экраны и маршруты |
| `model/` | типы схем (`MeResponse`, `ProblemDetail` …) | типизация |
| `zod/<тег>/<тег>.zod.ts`, `zod/schemas/` | zod-схемы тел, параметров, ответов и каждой схемы спеки (`ProblemDetail`, `LoginRequest` …) | моки (проверка тела запроса, как у сервера) и тест контракта моков; в начальную загрузку не попадают |

- `pnpm api:generate` — перегенерировать после изменения спеки (результат коммитится, сборка от
  orval не зависит). Затем `pnpm typecheck`: переименованный `operationId` или поле ломает вызов —
  это и есть сигнал поправить экран.
- `pnpm api:check` (в `verify`) — перегенерирует во временную `src/shared/api/.generated-check`
  (соседняя с `generated`: та же глубина — тот же относительный импорт мутатора) и сравнивает файл
  за файлом; отстал клиент — список файлов и код 1. openapi-diff на ломающие изменения спеки —
  задача CI и бэкенда (T-PLT-07, T-PLT-25).
- Глобально `useQuery`/`useMutation` в `override.query` **не включать**: флаг делает хуком этого
  вида все операции (GET станут мутациями).

### Мутатор (`src/shared/api/http.ts`)

Единственный транспорт, его зовут все сгенерированные функции (`override.mutator`):
- `credentials: 'include'` (cookie сессии), `Accept: application/json, application/problem+json`,
  `Accept-Language` = язык интерфейса (сервер отвечает текстами на нём);
- изменяющий запрос (не GET/HEAD/OPTIONS) несёт `X-XSRF-TOKEN` из cookie `XSRF-TOKEN`; cookie нет —
  мутатор сам сначала делает `GET /api/v1/auth/csrf` (параллельные изменения ждут один запрос);
  ответ 403 `error.csrf` (токен сменился) — новый токен и **один** повтор. Звать `getCsrfToken()`
  перед изменениями не нужно;
- не-2xx → `ApiError`: `status`, `problem` (ProblemDetail или `null`, если ответил не наш сервер),
  `code` (из ProblemDetail, без него — тот же код по статусу, что у бэкенда: 404 → `error.not-found`,
  502–504 → `error.unavailable`), `traceId`, `fieldErrors` (`errors[]`), `retryAfter` (секунды из
  `Retry-After` у 429/503; HTTP-дата тоже понимается), `method`, `url`. Разбор мягкий
  (`parseProblem`), а не `ProblemDetail.parse` из zod: сервер новее фронта не должен стирать код и
  ошибки полей. Нет ответа → `NetworkError { method, url }`;
- 401 вне `/api/v1/auth/**` и `/api/v1/public/**` → событие `onUnauthorized(listener)` (на входе 401 —
  ответ по существу, `iam.login.invalid`).

### Сеанс истёк (401 посреди работы)

`useSessionExpiry()` (`src/app/sessionExpiry.ts`, корень `__root.tsx`) слушает `onUnauthorized`:
была сессия (в кэше есть `/me`) → `/login?returnTo=<где был>&reason=expired`, экран входа
показывает «Сеанс истёк. Войдите снова — вернём на ту же страницу» (`auth:login.sessionExpired`), после
входа — тот же адрес. Работает на любом запросе: перезапрос `/me` при фокусе, изменение, ручной
вызов. Первый заход без сессии (401 от `/me` в гарде) — обычный вход без сообщения, туда уводит
гард `_app`. Экран входа при открытии чистит кэш — запоздалые 401 старых запросов повторно не уводят.

### QueryClient (`createAppQueryClient`, политика — `src/shared/api/query.ts`)

- **Повторы только для чтения:** `shouldRetry` — GET и только без ответа по существу: сеть,
  502/503/504, до 2 раз; пауза — `Retry-After` сервера, иначе 1 с, 2 с … ≤ 30 с. 4xx (ответ сервера)
  и 500 (ошибка в коде) не повторяются; изменения — никогда (`mutations.retry: false`).
- **Фокус:** при возврате во вкладку обновляется устаревшее (`refetchOnWindowFocus: true`,
  `staleTime` 30 с); `/me` — всегда (`meQueryOptions`).
- **«Живой» экран** (расписание, склад): опция `live` — опрос раз в 30 с, пока вкладка видна, и
  перезапрос при каждом возврате, даже если данные свежие:
  `useListAppointments(params, { query: live })` или `useQuery({ ...getXQueryOptions(p), ...live })`.
- **Сброс кэша по ключам.** Ключ запроса orval — путь (`['/api/v1/patients', params]`,
  `['/api/v1/patients/<id>']`). Пара «мутация → устаревшие запросы» — правило
  `override.query.mutationInvalidates` в `orval.config.ts` (сгенерированный хук сам сбросит кэш
  после успеха; отключить на вызове — `skipInvalidation: true`). Остальное (изменение задело чужой
  модуль) — `invalidateApi(queryClient, '/api/v1/patients')`: список со всеми фильтрами, карточки и
  вложенное (`/api/v1/patients/<id>/files`), но не соседний `/api/v1/patients-import`.
- Кэш `/me` — отдельный: `meQueryOptions()` (ключ `ME_QUERY_KEY`, данные — адаптированный
  `CurrentUser`). Сгенерированный `useGetMe` не использовать — у него свой ключ и сырой ответ.

### Ошибки: текст для человека (`src/shared/api/problem.ts`)

`describeApiError(error)` → `{ message, code, traceId }`, `fieldErrorMessage(fieldError)`. Порядок
текста: словарь фронта по коду (пространство `errors`, ключ = код: `iam.login.invalid` →
`errors:iam.login.invalid`) → текст сервера (`detail`/`message`, уже на языке пользователя) →
общий текст по статусу. Особые случаи: 409 `error.version-conflict` — «Запись уже изменена
(Алиева Динара, 26 сентября, 14:05)» из `modifiedByName`/`modifiedAt`; 429 с `Retry-After` —
«Повторите через 15 минут» (`retryWait` + ICU-склонение в словаре: «через 1 минуту», «через 15 минут»);
нет сети — `errors:ui.network`. Код для
поддержки (`traceId`) показывается только у сбоев 5xx — у ответов по существу он человеку не нужен.
В словарь — только коды **без параметров** (у `validation.size` числа знает лишь сервер).

Где показывать: ошибка поля — под полем; общая ошибка формы — плашка `FormAlert` над кнопкой
отправки; ошибка вне формы (кнопка в списке, фоновое действие) — `toastApiError(error)` (текст,
код ошибки и «Скопировать код»); 403/404/500 целого экрана — состояния `StateScreen` (PLT-16).

### Формы: ошибки сервера → react-hook-form (`src/shared/api/forms.ts`)

Форма ничего не проверяет сама (R002) — шлёт как есть (`noValidate`, без `required`), а ответ
раскладывает хук `useApiErrorForm` (внутри — `applyApiErrorToForm`):

```tsx
const form = useForm<LoginRequest>({ defaultValues: { login: '', password: '', rememberMe: true } })
const applyApiError = useApiErrorForm(form)
const login = useLogin()
const onSubmit = form.handleSubmit(async (data) => {
  try {
    const me = await login.mutateAsync({ data })
    // …успех
  } catch (error) {
    applyApiError(error, { fields: ['login', 'password'] })
  }
})

<Controller name="login" control={form.control} render={({ field, fieldState }) => (
  <Field data-invalid={fieldState.invalid}>
    <FieldLabel htmlFor="login-login">{t('auth:login.login')}</FieldLabel>
    <Input {...field} id="login-login" aria-invalid={fieldState.invalid}
      aria-describedby={fieldState.invalid ? 'login-login-error' : undefined} />
    <FieldError id="login-login-error" errors={fieldErrorList(fieldState.error)} />
  </Field>
)} />
<FormAlert errors={form.formState.errors.root} />
```

- `errors[]` → `setError` по полям (путь сервера `items[0].quantity` → `items.0.quantity`; другое
  имя — `fieldMap`), текст — `fieldErrorMessage`; несколько ошибок поля — все (`fieldErrorList`);
  фокус — на первое ошибочное поле **в порядке формы**, а не ответа (`name` поля = путь: Controller
  передаёт его в `Input`). Правка поля снимает его ошибку (перепроверка по `onChange`).
- Всё остальное — общая ошибка: `root.server` (+ `root.serverTrace` — код для поддержки у 5xx),
  react-hook-form сбрасывает её при следующей отправке. Ошибки без `field` и полей, которых форма
  не показывает, — туда же текстом сервера. Поля, «которые показывает форма», — по умолчанию все
  со значением в `defaultValues`; скрытые (галочка по умолчанию) — перечислить в `fields`, иначе
  их ошибка пропадёт молча.
- `general: 'toast'` — общая ошибка уведомлением вместо плашки (форма без места под плашку).
- **Фокус не теряется (PLT-21, WCAG 2.4.3).** Кнопка на время запроса — `disabled` **вместе с**
  `focusableWhenDisabled` (Base UI Button: `aria-disabled`, клик и повторная отправка, в том числе
  Enter в поле, гасятся, фокус остаётся на кнопке); голый `disabled` сбрасывает фокус на `<body>`, и
  клавиатура начинает с начала страницы. Свой `<button>`/ссылка — `aria-disabled` + проверка в
  обработчике (`choose()` выбора клиники, «назад» пагинации на первой странице). После ответа:
  ошибки полей — фокус на первом ошибочном поле; общая ошибка — `FormAlert` при появлении забирает
  фокус себе (`tabIndex=-1`), если фокус не в поле. Проверки — `e2e/focus.spec.ts`,
  `forms.test.tsx`.
- Смена языка (PLT-11): тексты под полями и в плашке — готовые строки в состоянии react-hook-form, сами
  они не переведутся. Хук помнит последнюю ошибку и на `languageChanged` раскладывает её тексты заново —
  только то, что ещё на экране (ошибку, снятую правкой поля или новой отправкой, не возвращает).
  Поэтому на экране — `useApiErrorForm`, а `applyApiErrorToForm` напрямую — только вне React (тесты,
  особые случаи). Текст сервера (`detail`/`message`) остаётся на языке ответа; уведомление не
  переводится — оно живёт секунды.

### Добавить вызов API

1. Эндпоинт есть в `api/openapi.json` → `pnpm api:generate`, `pnpm typecheck`.
2. Чтение — `use<Operation>` (или `get<Operation>QueryOptions` в `loader` маршрута +
   `use<Operation>Suspense`); «живой» экран — `{ query: live }`.
3. Изменение — `use<Operation>()` + `mutateAsync({ data })`; какие запросы устаревают — правило в
   `mutationInvalidates` (orval.config.ts) или `invalidateApi` в `onSuccess`.
4. Ошибки: форма — `useApiErrorForm` + `FormAlert`; вне формы — `toastApiError`; новый код, для
   которого текст сервера не годится (нужны данные ответа), — `errors:<code>` в словаре.
5. Мок — обработчик в `mocks/handlers.ts` по схеме спеки, ошибки — `problems.*`; новая фикстура —
   строка в тесте контракта моков (`mocks/contract.test.ts`).

## Вход и выход (T-IAM-03: IAM-02, IAM-03, IAM-05, IAM-34)

**Экран входа** (`modules/access/LoginPage.tsx`, макет Vhod) — в раскладке `AuthLayout`, общей для
экранов без входа (сброс пароля, приглашение, регистрация): с 1024 px слева панель «О сервисе» (логотип
48, слоган `text-tagline` 32, картинка расписания `AuthIllustration` — украшение, скрыто от диктора,
«Данные хранятся в Казахстане»), справа форма по центру; уже 1024 px — одна колонка без панели, логотип
над формой (IAM-34), а отметка «Данные хранятся в Казахстане» — внизу под формой: IAM-34 убирает панель, но не
отметку (PLT-27 — на Vhod, Parol и Priglashenie при любой ширине). «Рус | Қаз» — в углу, только при флаге `i18n.kk`
(см. «i18n»). Форма: «Телефон или e-mail» (`LoginField`), «Пароль» (`PasswordField`, режим входа — только «глаз»), «Запомнить это
устройство» (`Checkbox`, включено по умолчанию — IAM-03), одна жёлтая «Войти» (52 px, как в макете),
ссылки «Забыли пароль?» и «Зарегистрировать» (заглушки, см. «Маршруты»). В режиме моков под формой —
подсказка, какие логины что делают (`data-mock-hint`, в эталон скриншота не входит).

- **Вход** — `useLogin()` (POST `/api/v1/auth/login`): CSRF-токен мутатор берёт сам (cookie
  `XSRF-TOKEN` → `X-XSRF-TOKEN`; cookie нет — сначала GET `/api/v1/auth/csrf`). Логин уходит, как его
  набрали (маска — только вид), номер нормализует сервер. Ответ — тот же `MeResponse`, что у `/me`: он
  кладётся в кэш, кабинет `/me` не перезапрашивает. Клиника выбрана (одна клиника с одним филиалом) —
  `router.history.replace(returnTo ?? '/')` (`returnTo` — путь с поиском, поэтому история, а не
  `navigate({ to })`; `/` уводит в первый раздел по правам); нет — `/select-clinic?returnTo=`.
- **Ошибки** — `useApiErrorForm`: 400 `errors[]` — под полями (логин, пароль, «Запомнить»); остальное —
  плашка `FormAlert` над «Войти» текстом по коду ProblemDetail из словаря `errors`: 401
  `iam.login.invalid` — одно «Неверный телефон, e-mail или пароль», 403 `iam.login.access-disabled`,
  429 — «Слишком много попыток. Повторите через 15 минут» из `Retry-After`, 5xx — с кодом для
  поддержки, нет ответа — `errors:ui.network`.
- **Экран входа при открытии чистит кэш** TanStack Query: данные прошлой сессии не переживают выход и 401.
- **«Сеанс истёк»** — `/login?reason=expired` (уводит `useSessionExpiry`, см. «API-клиент»): плашка
  `Alert` (`role="status"`, `data-slot="session-expired"`) над полями. Уже на `/login` новые 401 не
  уводят повторно — плашка одна, адрес `returnTo` не затирается.
- **Выход** — `useSignOut()` (`shared/api/useSignOut.ts`), общий для меню пользователя в сайдбаре,
  меню «Ещё» и выбора клиники: POST `/api/v1/auth/logout` → `/login` (replace) → `queryClient.clear()`
  после перехода (кабинет уже размонтирован и не перезапросит данные без сессии). «Назад» браузера
  после выхода кабинет не показывает: гард заново спрашивает `/me` и получает 401. Не вышло (сеть) —
  уведомление (сайдбар) или `role="alert"` («Ещё», выбор клиники), пользователь остаётся на месте.

**`LoginField`** (`shared/ui/login-field.tsx`, правила — `shared/lib/loginInput.ts`) — одно поле
«Телефон или e-mail» (IAM-02: есть «@» — e-mail, иначе телефон; решает сервер, R002). Цифры и «+» —
маска «+7 707 123 45 67» и цифровая клавиатура (`inputmode="tel"`); буква или «@» — без маски,
клавиатура с «@» (`inputmode="email"`, и у пустого поля). Первая «8» показывается как «+7», номер без
кода получает «+7» спереди, одиннадцатая цифра после «7» делает «7» кодом страны; другой код страны
(«+49…») — без маски. Цифры человека не теряются и не добавляются: `value` (то, что уйдёт серверу) —
набранное без пробелов маски; набрали «@» после цифр — они возвращаются как были. Курсор после правки в
середине номера остаётся у места правки. `type="text"` (у `type="email"` курсор не ставится),
`autocomplete="username"`. Контролируемое: `value`/`onChange(value)` — для `Controller`.
Почему маска своя (R021): готовое смотрели — динамическая маска imask «телефон или e-mail» из его же
примера (`+{7}(000)000-00-00` или `/^\S*@?\S*$/`) берёт первую «8» за цифру номера («8 707…» → «+7 (870)
7…»), а при переходе к e-mail набранные цифры уже переписаны маской; чтобы учесть «8», «+7» и
одиннадцатую цифру, нужен свой `dispatch`/`prepare` — та же логика плюс зависимость. Своё здесь —
только правила вида (`loginInput.ts`, покрыт `loginInput.test.ts`), поле — `Input` дизайн-системы.

**`PasswordField`** (`shared/ui/password-field.tsx`, IAM-05) — одно поле пароля на всех экранах
(вход, приглашение, сброс, смена в профиле, регистрация): пилюля с «глазом» (`aria-pressed`,
`aria-controls`, «Показать пароль / Скрыть пароль»). Режимы:
- `current` (по умолчанию, вход) — только поле, `autocomplete="current-password"`;
- `new` — под полем индикатор надёжности zxcvbn-ts в 4 уровня (`role="img"`, диктор читает
  «Надёжность пароля: хорошая, 3 из 4»; слово уровня — рядом) и чек-лист «8+ символов · есть цифра ·
  есть заглавная» (у каждого пункта для диктора «— выполнено / не выполнено») — подсказки по мере ввода,
  не проверка; `userInputs` — телефон, e-mail, имя: пароль из них индикатор не хвалит;
- `repeat` + `original` — «Повторите пароль»: не совпал — красный контур, `aria-invalid`, текст «Пароли
  не совпадают» (`FieldError`, связан `aria-describedby`). Пока набирают начало пароля — без ошибки;
  разошёлся с паролем или ушли из поля — сразу. Отправку форма блокирует сама по
  `passwordRepeatMismatch(password, repeat)` (`shared/lib/passwordStrength.ts`): IAM-05 требует сверку
  до отправки, повтор серверу не уходит.

Жёсткие правила пароля (8–128 символов, не из частых, не телефон и не e-mail) проверяет сервер
(`PasswordPolicy`, R002) — ошибку поля показывает `FieldError` под полем. Словари zxcvbn-ts
(`@zxcvbn-ts/core`, `language-common`, `language-en`, `language-ru`; английский — по рекомендации zxcvbn-ts:
без него пароль латиницей из английских слов, «Nightingale», — «высокая») — отдельные ленивые чанки
(английский ~1,2 MB, русский ~1 MB): грузятся при первом вводе в поле режима `new`, на вход и в
начальную загрузку не попадают (`chunkSizeWarningLimit` в vite.config.ts поднят до 1250 kB ради них). Не загрузились (нет сети) —
индикатор просто без уровня.

## Моки (MSW)

Моки повторяют контракт `api/openapi.json`: пути, коды ответов и схемы — фикстуры типизированы
сгенерированными `MeResponse`, `LoginRequest`, `SelectContextRequest`, `ProblemDetail` плюс
расширение прав `MeRightsExtension` (его пока присылают только моки). Данные вымышленные.
Сверку держит `mocks/contract.test.ts`: фикстуры и ответы-ошибки проходят сгенерированные
zod-схемы (`generated/zod/schemas`), мок входа проверяет тело запроса схемой `LoginRequest`.

Ответы-ошибки — `mocks/problems.ts`: `problem(status, code, detail, extra?, headers?)` и готовые
`problems.validation(errors[], 400 | 422)`, `unauthorized(code)`, `forbidden`, `notFound`,
`versionConflict({ modifiedAt, modifiedByName })` (409), `businessRule` (422),
`tooManyRequests(retryAfter)` (429 + `Retry-After`), `unavailable(retryAfter?)` (503), `internal()`
(500); у всех `application/problem+json` и вымышленный `traceId` (`MOCK_TRACE_ID`). В тестах:
`server.use(http.post(path, () => problems.validation([...])))`.

`src/shared/api/mocks/fixtures.ts` — персоны (cookie `dentify_mock_user`): `owner` — владелец со
всеми правами (по умолчанию), `assistant` — без «Клиники» и «Доступов», `multi` — врач двух клиник
с `context: null` (плюс отключённое членство), `plain` — ответ строго по спеке, без роли и прав
(как у живого бэкенда сейчас), `none` — 401. `handlers.ts`:
- `GET /api/v1/me` и `POST /api/v1/me/context` — `personaHandlers(persona | 'cookie')`; выбор
  клиники и филиала `multi` мок помнит в `sessionStorage` (вход и выход его сбрасывают). Выбор — как
  `AccountContexts.select`: нет членства или приглашение, чужой или недоступный филиал — 404, членство
  отключено — 403 `iam.membership.disabled`; без `branchId` — единственный филиал, иначе не выбран;
- CSRF как у сервера (`CsrfTests`): изменяющий запрос (вход, выход, выбор клиники) без `X-XSRF-TOKEN`
  или с чужим токеном — 403 `error.csrf`. `GET /api/v1/auth/csrf` — 204 и cookie `XSRF-TOKEN`: мок
  пишет её в `document.cookie` сам (cookie из ответов MSW держит в своём хранилище, мутатор её бы не
  увидел); вход меняет токен, как `AccountSignIn.signIn` (`mock-xsrf-token-<n>`: следующее изменение
  мутатор шлёт уже с новым, прежний мок отклонит), выход стирает;
- `POST /api/v1/auth/login` — как сервер: тело не по схеме `LoginRequest` (нет `rememberMe`) или
  пустые логин/пароль → 400 `error.validation` с `errors[]` (`validation.not-blank`,
  `validation.not-empty`, `validation.not-null`, `validation.size` — длиннее 320 / 1024); пароль
  `wrong` → 401 `iam.login.invalid`; логин со словом `limit` → 429 `error.too-many-login-attempts` c
  `Retry-After: 900`, `busy` → 429 `error.too-many-requests` (предел хэшей, `Retry-After: 1`),
  `disabled` → 403 `iam.login.access-disabled`, `boom` → 500 с `traceId`, `offline` — обрыв сети;
  иначе 200 и `MeResponse` персоны (`assistant` — ассистент, `multi` — врач двух клиник, у одной два
  филиала) и cookie персоны;
- `POST /api/v1/auth/logout` — 204, персона `none`.

Cookie из ответов моков MSW хранит в своём хранилище (localStorage `__msw-cookie-store__`), а не в
`document.cookie`, и они перекрывают cookie страницы — поэтому состояние выбора клиники не в cookie.
В браузере — `worker.start()` в `main.tsx` (только в режиме моков, чанк грузится лениво),
в Vitest — `server` из `mocks/node.ts` (поднимается в `src/test/setup.ts`, `onUnhandledRequest:
'error'`). Playwright задаёт персону cookie через `usePersona(context, 'assistant')`; Vitest —
`renderApp(path, 'assistant')` или готовым ответом `/me` (по схеме спеки или с правами).

## i18n (T-PLT-11: PLT-11, dentify R003)

Интерфейс MVP — только на русском, но готов к казахскому без правок компонентов: react-i18next 17 +
i18next 26, строки — ICU MessageFormat (i18next-icu), даты — date-fns. Включить казахский (PLT-34) =
положить словари `locales/kk/*.json` и включить флаг `i18n.kk`.

### Словарь: пространства и файлы

`src/shared/i18n/locales/<язык>/<пространство>.json`, пространство — по модулю (`NAMESPACES` в
`resources.ts`):

| Пространство | Что там |
|---|---|
| `common` | общее и дизайн-система: `app.*`, `languages.*`, `age` (возраст с ICU-склонением), `dates.*` (шаблоны date-fns), `user.*`, `titles.*` (заголовок вкладки), `states.*` (нет доступа, 404, нет связи, загрузка), `stub.*`, `ui.*` (закрыть, «Назад», «Вернуть», шаги, статусы записи…) |
| `shell` | каркас: `nav.*` (разделы), `tabs.<раздел>.*`, `actions.*` (жёлтые кнопки разделов), `header.*`, `search.*` (панель Ctrl K), `home.<раздел>` («К расписанию»), `more.*` (меню «Ещё») |
| `auth` | вход (`login.*`) и выбор клиники (`selectClinic.*`) |
| `errors` | тексты по коду ProblemDetail: ключ = код (`errors:iam.login.invalid`, `errors:error.not-found`); `ui.*` — тексты, которые фронт собирает сам (нет сети, «через 15 минут», код ошибки) |
| `schedule`, `patients`, `staff`, `inventory`, `clinic`, `access`, `profile` | содержимое экранов модуля (пока — заглушки, фильтры и страницы списка пациентов) |

Навигацию (названия разделов, вкладки, главные действия) держит `shell`: её рисует каркас до загрузки
экрана. Витрина `/dev/ui` — своё пространство `devUi` (`src/dev/ui/ru.json`, подключает сама витрина):
в production-словарь не попадает (сторожит `scripts/check-build.mjs`).

Русский словарь входит в сборку (`resources.ts`), словари других языков — лениво: `import.meta.glob`
по `locales/*/*.json` + `i18next-resources-to-backend`, каждый файл — свой чанк; язык «появляется»,
как только лежат его файлы.

В `errors` — только коды без параметров и только те, где нужен свой текст фронта или которые фронт
подставляет сам по HTTP-статусу (`codeForStatus`): остальные ошибки показывают текст сервера — он уже
на языке пользователя (`Accept-Language`) и берётся из `messages.properties` бэкенда по тому же коду.
Дублировать весь словарь сервера не нужно.

### Ключи

- **Ключ всегда полный — `пространство:путь`**: `t('shell:nav.schedule')`, `labelKey: 'shell:tabs.schedule.day'`.
  `useTranslation()` — без аргумента: `defaultNS` — все пространства, поэтому любой `t` типизированно
  принимает любой полный ключ. Ключ без префикса i18next ищет по порядку во всех пространствах и берёт
  первое совпадение (`nav.schedule` найдётся в `shell`, хотя ключ такой же может появиться и раньше —
  в `common`): это неоднозначно, поэтому в приложении ключ без префикса запрещён — его ловит
  `pnpm i18n:check`, а не i18next и не типы. Витрина пишет свои ключи без префикса:
  `useTranslation(['devUi', 'common'])` (там порядок задан явно).
- Путь — `camelCase` через точку, по смыслу, а не по тексту: `more.branch.change`, `states.notFound.title`.
  Коды ошибок — как у сервера (`errors:validation.not-blank`).
- Конфиги (разделы, вкладки, `staticData.titleKey`) держат тип `TKey` (`src/shared/i18n/keys.ts`) —
  только полный ключ, его проверяет `tsc`. Ключ из данных (`common:ui.status.${status}`,
  `patients:filters.${filter}`) — шаблонной строкой с полным префиксом: так её находит проверка словаря.
- В JSX — только `t(…)`. Вне React (заголовки вкладок в `head`, уведомления, тексты ошибок) —
  `i18n.t` из `@/shared/i18n`.
- Фразы из частей не склеиваются в коде: порядок и разделители — в словаре через подстановки
  (`common:titles.page` «{screen} — {app}», `common:user.roleAtClinic`, `shell:header.context`,
  `common:ui.steps.progressWithLabel`, `common:stub.withSoon`), чтобы перевод мог их переставить.
- Данные (ФИО, названия клиник, роль из `/me`) — не ключи: их язык — забота сервера. Двуязычные поля
  справочников (`LocalizedText { ru, kk }`) — `pickLocalized(text, язык)` / хук `useLocalized()` из
  `@/shared/i18n/localized`: нет казахского (`null`, пусто) — русское название.

### Строки: ICU MessageFormat

Подстановка — `{name}` (не `{{name}}` i18next). Склонение по числу — `plural` с формами `one`,
`few`, `many`, `other` (правила CLDR языка, `#` — само число с разрядами):

```json
"age": "{age, plural, one {# год} few {# года} many {# лет} other {# года}}"
```

`t('common:age', { age: 41 })` → «41 год», 52 → «52 года», 29 → «29 лет», 1,5 → «1,5 года». Выбор по
значению — `select` (`errors:ui.tooManyWait`: `{unit, select, minute {…} other {…}}`). Не собирайте
склонение в коде (`count === 1 ? … : …`) и не берите `Intl.NumberFormat` с единицей: «через 1 минута»
— неверный падеж, форма зависит от фразы. Номер (карты, документа) без разрядов — передавайте строкой.
Апостроф `'` перед `{`, `}` или `#` в ICU экранирует — в русском тексте используйте «ёлочки» и ’.
Типы i18next подстановки ICU не разбирают (`parseInterpolation: false`), поэтому их проверяют тесты.
Списков (массивов JSON) в словаре нет: `returnObjects` отдал бы плагину ICU массив целиком, мимо
разбора строк. Список — ключи по смыслу и массив id в коде:
`` DEMO_CHAIRS.map((id) => t(`overlays.chairs.${id}`)) `` (витрина, `src/dev/ui/lists.ts`); массив в
словаре ловит `pnpm i18n:check`.

### Даты: date-fns

- Шаблоны — в словаре (`common:dates.*`, токены date-fns): `formatDate(date, 'moment')` →
  «26 сентября, 14:05», `formatDayLabel(date)` → «Четверг, 24 сентября» (`src/shared/lib/format.ts`).
  Новый шаблон — ключ в `common:dates` и имя в `DatePattern`.
- Локаль и первый день недели — `src/shared/i18n/dates.ts`: `setDefaultOptions` date-fns ставит локаль
  языка интерфейса (русская — сразу, казахская — догружается при смене языка) и
  `CLINIC_WEEK_STARTS_ON` (понедельник; TODO: из настроек клиники). Поэтому `format`, `startOfWeek`
  и т. п. берут их сами — **если модуль дат уже загружен**: импортируйте даты через
  `@/shared/lib/format` или `@/shared/i18n/dates`. Компонентам с явной локалью (календарь
  react-day-picker) — `dateLocale()` и `weekStartsOn()`.
- date-fns в начальную загрузку не входит: его тянут экраны с датами (шапка каркаса, вход). Функции
  импортируйте по пути (`import { format } from 'date-fns/format'`), типы — из `date-fns`: общий вход
  тянет сотни модулей в тестах и dev-сервере.

### Флаг `i18n.kk` и переключатель «Рус | Қаз»

- `isFlagOn('i18n.kk')` (`src/shared/lib/flags.ts`) — переменная сборки `VITE_I18N_KK=true`, по
  умолчанию выключен (TODO(PLT-34): флаги с сервера, одним значением с `dentify.i18n.kk` бэкенда).
  Новый флаг — строка в `FLAG_ENV` (`VITE_<ИМЯ>`, точка → `_`) и тип в `vite-env.d.ts`; в тестах —
  `vi.stubEnv('VITE_I18N_KK', 'true')`.
- `uiLanguages()` — языки для переключателя: флаг выключен — только `ru`; включён — языки со словарём.
  Меньше двух — `LanguageSwitch` не рисуется, и строк «Язык» нет: на входе (в углу, как Vhod), в
  профиле (поле «Язык интерфейса», полные названия) и в меню «Ещё». Проверка — `canSwitchLanguage()`.
- `changeUiLanguage(язык)` догружает словарь и локаль дат, меняет язык, запоминает выбор на устройстве
  (`localStorage` `dentify-lang`; TODO(IAM-31): язык аккаунта из `/me` и сохранение через API). Язык
  недоступен (флаг выключен) — остаётся русский. i18next-browser-languagedetector не взят: язык
  браузера не нужен (казахский — только по флагу и выбору), а запомнить выбор — одна строка.
- Смена языка: компоненты с `useTranslation` перерисовываются сами; `<html lang>` = язык интерфейса;
  `Accept-Language` запросов — тоже (сервер отвечает на нём); `useLanguageRefresh` пересчитывает
  заголовки вкладки (`router.invalidate`) и перезапрашивает ответы сервера; ошибки сервера в форме
  переводит `useApiErrorForm` («Формы: ошибки сервера»). При запуске
  `i18nReady` (его ждёт `main.tsx`) восстанавливает сохранённый язык до первого экрана.

### Проверки

- **ESLint** (`eslint.config.js`, eslint-plugin-i18next 6 — совместим с ESLint 10: берёт
  `context.sourceCode`): `i18next/no-literal-string` в режиме `jsx-only` — строка с буквами где угодно
  внутри JSX (текст, выражения, любые атрибуты и свои пропсы экрана) — ошибка, шаблонная тоже
  (`should-validate-template`: `` {`Всего ${n}`} ``, `` title={`Пациентов: ${n}`} ``, `` {`Всего`} ``).
  Имя токена или ключа из частей в JSX не собирается — в функцию рядом (`readDoctorColorToken(color)`);
  шаблон в `t(…)` и в технических атрибутах (`className`) правило не трогает. Не ловятся технические
  атрибуты (`className`, `id`, `data-*`, `to`, `href`, `lang`, `value`, `aria-labelledby`…) и значения из
  объединения литералов по типам TypeScript (`variant="outline"`, `side="right"`, `aria-hidden="true"`):
  правило работает с типами (`parserOptions.projectService`). Что правило ловит, проверяет
  `src/shared/i18n/lint.test.ts` на настоящем конфиге.
- **`pnpm i18n:check`** (в verify; те же тесты входят в `pnpm test`) —
  `src/shared/i18n/dictionary.test.ts` читает исходники (`?raw`) и словари и падает, если: ключа из кода
  (строки `'пространство:путь'`, конфиги `TKey`, шаблоны `` `пространство:путь.${…}` ``) нет в ru; ключ
  в `t('…')` написан без пространства; строка словаря не нужна коду (удалите или сошлитесь); строка — не
  ICU (или старый `{{x}}`); шаблон `common:dates.*` не разбирается date-fns; у кода, который фронт
  подставляет по HTTP-статусу, нет текста; словарь другого языка (`locales/kk/`) не повторяет ru ключ в
  ключ с теми же аргументами ICU (полнота kk — PLT-34); в словаре есть список (массив); в коде
  приложения есть **русская строка мимо словаря** — литерал с кириллицей (строка, шаблон, текст JSX)
  где угодно, не только в JSX: `toast.add({ title: 'Готово' })`, `head`-заголовок, строка в хуке,
  `const label = 'Сохранить'` с показом `{label}` позже. Её ESLint не видит (он смотрит только в JSX), а
  интерфейс пока только русский — кириллица в коде почти наверняка текст для человека. Разбор — парсером
  TypeScript, комментарии не в счёт; можно: тесты и `src/test`, моки (`src/shared/api/mocks` — данные
  «бэкенда»), витрина (`src/dev`), сообщения разработчику — аргументы `console.*(…)` и `new Error(…)`.
  Логика — `integrity.ts`, её тест — `integrity.test.ts`.
  Почему не i18next-cli (`extract --ci`, `status --unused`): он видит ключи только в вызовах `t()`, а у
  нас половина ключей — в конфигах `TKey` (разделы, вкладки, заголовки), ICU-аргументы и полноту
  другого языка он не сверяет; свой разбор — ~200 строк на тех же исходниках через `?raw`.
- **Нет ключа во время работы** — dev-сборка и тесты пишут в консоль `i18n: нет ключа …`, сломанный ICU —
  `i18n: ошибка ICU …`; e2e `i18n.spec.ts` обходит все экраны и требует чистую консоль, `lang="ru"`,
  отсутствие сырых ключей и переключателя.
- Казахский без правок компонентов — `src/shared/i18n/i18n.test.tsx` с фикстурным словарём
  (`src/test/kkFixture.ts`): переключатель на входе, в профиле и «Ещё» только при флаге и словаре,
  смена языка меняет экран, заголовок вкладки, `lang`, даты, ICU-склонение и `Accept-Language`.

### Добавить строку, модуль, язык

1. Строка — в `locales/ru/<пространство>.json` модуля (или `shell`/`common`), в коде — полный ключ;
   `pnpm typecheck` проверит ключ, `pnpm i18n:check` — что строка нужна и она ICU.
2. Новый модуль — файл `locales/ru/<модуль>.json`, имя в `NAMESPACES` и импорт в `resources.ts`.
3. Новый язык (PLT-34) — `locales/<язык>/*.json` для всех пространств (ключ в ключ с ru — иначе
   `i18n:check` упадёт), язык в `UI_LANGUAGES`, локаль date-fns в `LAZY_LOCALES` (`dates.ts`), флаг.
   Длинные подписи нижней панели сначала проверить в `mobile.spec.ts` (подписи в ячейках на 360 px).

## Дизайн-система «Кресло» (T-PLT-06)

Источник значений — `wiki/projects/dentify/design-directions.md` (§ Токены), визуальный
эталон — макет `design/mockups/Sistema.dc.html`. Витрина всех токенов и компонентов —
**http://127.0.0.1:5173/dev/ui** (только `pnpm dev` / `dev:mock`): верх страницы повторяет
Sistema на настоящих компонентах, ниже — «Полный набор» (остальные токены, размеры,
состояния, панели, окна, уведомления).

### Токены (`src/styles/index.css`)

- `@theme { --color-*: initial }` — палитра Tailwind выключена: в разметке только цвета
  «Кресла» (`bg-red-500` не сгенерируется). Цвета — `@theme static` (все в `:root`, витрина
  показывает их значения).
- Цвета: `lagoon` (+ `lagoon-deep`, `lagoon-muted` из макетов), `lamp`, `lamp-deep`, `ink`,
  `graphite`, `slate`, `fog`, `mist`, `white`, `line`, `danger` / `danger-bg` /
  `danger-line`, `highlight` / `match`, `past`. Врачи: `doctor-<teal|coral|lilac|sky|sand|rose>`
  (фон), `-fg` (текст), `-strong` (аватар, точка; бирюза, коралл и песок чуть темнее макетов —
  ради 4.5:1 с белыми инициалами). Статусы: точки `status-planned|arrived|in-chair`
  и цвет слова `-fg` (из макета Sistema). Зубы: `tooth-<caries|filling|canals|crown|implant>`
  (+ `-line`), `tooth-removed-line`.
- Шрифты: `font-heading` (Rubik 600) и `font-sans` (Nunito Sans). Размеры: `text-h1` 30 ·
  `text-h2` 26 · `text-h3` 20 · `text-h4` 17 (Rubik) и `text-body-lg` 16 · `text-body` 15 ·
  `text-body-sm` 14 · `text-label` 13 · `text-caption` 12.5 (Nunito); по назначению из макетов —
  `text-nav` 11.5 (подпись нижней панели), `text-logo` 24 и `text-logo-lg` 48 (логотип),
  `text-tagline` 32 (слоган панели «О сервисе» экранов без входа — Vhod, Parol, Priglashenie).
  Стандартные `text-sm`, `text-[15px]` и радиусы `rounded-md` в `shared/ui`, каркасе и
  `modules` запрещены тестом `src/styles/usage.test.ts` (исключение с причиной — там же:
  инициалы крупного аватара 22 px); витрина `src/dev/**` повторяет Sistema и не проверяется.
- Радиусы: `rounded-pill` 999 (кнопки, чипы, поля) · `rounded-window` 28 (окна, нижний лист) ·
  `rounded-card` 24 (карточки, сайдбар, панель) · `rounded-block` 20 (внутренние блоки, список)
  · `rounded-tile` 18 (плитки дней) · `rounded-row` 16 (строки списков, плашки — из макетов) ·
  `rounded-item` 14 (записи, пункты меню) · `rounded-check` 8 (галочка 24 px, Vhod). Тени: `shadow-popover`, `shadow-window`,
  `shadow-raised`, `shadow-dock` (нижняя панель телефона), `shadow-lamp-glow` («+» панели).
- Брейкпоинты: `md` 768, `lg` 1024, `xl` 1280 (px) — см. «Телефон и адаптив».
- Отступы — стандартная шкала Tailwind (`--spacing: 0.25rem`). **Ритм** между блоками, поля
  страниц и карточек — шаги 1 · 2 · 3 · 4 · 6 · 8 (4 · 8 · 12 · 16 · 24 · 32 px). **Внутри
  компонентов** — шаги макетов 0.5 · 1.5 · 2.5 · 3.5 · 4.5 · 5 · 5.5 · 7 (2 · 6 · 10 · 14 · 18 ·
  20 · 22 · 28 px: промежуток иконки и текста, поле `px-4.5`, кнопка `px-5.5`, окно `p-7`).
  Прочее — резерв места под соседний элемент (`pr-9` под галочку пункта списка, `pr-14` под
  крестик окна, `pb-28` под нижнюю панель, `py-16` у пустого состояния) — только по списку
  `RESERVED_SPACING` с назначением; произвольное значение — только с `env(safe-area-inset-*)`,
  цель касания (`after:`) — не отступ. В `shared/ui`, каркасе и экранах (`modules`) это сторожит
  `usage.test.ts`: новое значение — сначала в макет и design-directions, потом в тест. Поля экранов без
  входа — геометрия макетов Vhod и VyborKliniki (панель «О сервисе» 44 · 48 · 36, карточка формы 40) — там
  же, в `RESERVED_SPACING` по файлам.
- Фокус (PLT-21): у всего — контур 3 px «лагуна» (`:focus-visible` в base, переменная
  `--focus-color`). На тёмной поверхности добавьте класс `surface-dark` (сайдбар, нижняя
  панель, уведомление) — контур станет белым. ring-классы shadcn из компонентов убраны:
  контур один на всё приложение. Поле ввода рисует контур на пилюле (`InputGroup`).
- `prefers-reduced-motion: reduce` — длительность всех переходов и анимаций ≈ 0 (Base UI
  ждёт конца перехода, поэтому 0.01 ms, а не `none`).
- Значения из макетов вне основной шкалы — тоже токены, а не `[…]` в классах: `shadow-field` (белое
  поле на «тумане», поиск шапки), `rounded-mark` (4 px, подсветка совпадения), `text-avatar-lg` (22 px,
  инициалы аватара 64 px). Кнопки `sm` (38 px) и `icon-xs` (28) — исключения вне системы 48/40,
  только внутри составных компонентов с расширенной целью касания (`after:` ≥ 44 px): «Скопировать
  код» в `FormAlert` и действие уведомления (`sm`), кнопка внутри поля `InputGroupButton` (`icon-xs`,
  крестик поиска); на экранах — `default`/`compact`. `icon-sm` (36) не используется — удалён.
- Контур фокуса у вкладок-пилюль — с отступом 1 px (`pillTabVariants`): полоса вкладок прокручивается
  (`overflow`), и контур 3 px + 2 px обрезался бы по краю контейнера с отступом 4 px.
- Tailwind ищет классы только в `src/` (`@import "tailwindcss" source("../")` в index.css): посторонний
  файл в папке frontend (отчёт проверки, .txt) иначе перезагружал бы все открытые страницы dev-сервера.
- Новый токен-размер, радиус или тень `cn` узнаёт сам: его таблицы собираются из `@theme` index.css
  (`cn build`, см. ниже); `src/styles/tokens.test.ts` проверяет каждое имя из темы (`text-<имя>` не
  спорит с цветом, `rounded-<имя>` вытесняет `rounded-lg`). Новый текстовый цвет — пару в
  `src/styles/contrast.test.ts`.

**`cn`** — из `@/shared/lib/utils` (официальный пакет shadcn `cn`): `createCn` из `cn/engine` на таблицах,
собранных заранее под тему «Кресла» — `src/shared/lib/cn-tables.ts` (генерирует `cn build`, не править
руками). Их пишет официальный плагин пакета (`cn/vite` в vite.config.ts: `full` — все группы классов
Tailwind, `css` — `src/styles/index.css`) при старте dev-сервера, сборки и тестов и при правке темы; файл
в репозитории — чтобы `pnpm typecheck` работал и без запуска Vite. Все группы, а не только найденные в
исходниках, — таблицы зависят лишь от темы и не отстают от новых классов. Зачем: компилятор конфигурации
`cn/config` (~5 kB gzip) не едет в начальную загрузку (PLT-22); поведение то же — сверено на 358 тыс. пар
классов проекта. Импорт пакета `cn` напрямую и общего входа `@base-ui/react` запрещён ESLint
(`no-restricted-imports`).

### Компоненты (`src/shared/ui/`)

Сначала готовое (R021): компонент берётся из shadcn (`pnpm dlx shadcn@latest add <имя>`,
стиль `base-nova` на Base UI) и перекрашивается токенами; своё — только составное поверх них.
После `shadcn add`: заменить `import { cn } from "cn"` на `@/shared/lib/utils` (подскажет
lint), убрать `dark:`- и ring-классы, перевести размеры и цвета на токены, строки — в словарь.
**Не перезаписывайте** уже настроенные файлы (`shadcn add … --overwrite` затрёт «Кресло»).

| Компонент | Файл | Основа | Главное |
|---|---|---|---|
| `Button` | button.tsx | shadcn Button / Base UI | `default` — жёлтая, **одна на область** (e2e считает `data-variant="default"`); `outline`, `selected` (ink), `soft`, `ghost`, `danger` (красный текст), `destructive` (красная заливка — финальное подтверждение опасного), `link`. Размеры 48 (`default`, `icon`) и 40 (`compact`, `icon-compact`); у кнопок меньше 44 px цель касания расширена псевдоэлементом |
| `Input`, `Textarea`, `InputGroup` | input.tsx, textarea.tsx, input-group.tsx | shadcn | поле-пилюля 48 px (`density="compact"` — 40, на телефоне 44), на телефоне шрифт 16 px, контур 2 px `line`, в фокусе — `ink` + контур фокуса; `InputGroup surface="page"` — белая пилюля без контура на «тумане». Любое поле на любом экране (и в заглушках) — только отсюда: самодельный `<input className="… outline-none">` гасит общий контур фокуса |
| `Label`, `Field*` | label.tsx, field.tsx | shadcn | метка 13/800 `slate`; `FieldError` — `role="alert"`, связать с полем через `aria-describedby` |
| `SearchField` | search-field.tsx | InputGroup | поиск для растущих списков (dentify R001): лупа, плейсхолдер **перечисляет поля** («ФИО, телефон или ИИН»), крестик очищает и возвращает фокус, `shortcut` — подсказка клавиши (с 768 px). Само не фильтрует: дебаунс и запрос к API — экран списка |
| `Combo` | combo.tsx (+ combobox.tsx) | shadcn Combobox / Base UI | выбор с поиском по вводу (R011): подсветка совпадения, первое совпадение активно (Enter выбирает), обрезанный текст — в `title` у пункта и у поля; список в портале, у края окна переворачивается вверх, высота — по месту в окне (R022). Короткий справочник — фильтр Base UI; растущий — `filter={null}` + `onInputValueChange` → запрос к API, `items` — ответ сервера |
| `Tabs` | tabs.tsx (+ tabs-variants.ts) | shadcn Tabs / Base UI | вкладки-пилюли внутри экрана; вкладки-маршруты (`PillTabs` каркаса) — на тех же `pillTabsListVariants` / `pillTabVariants`. Виды — в tabs-variants.ts без Base UI: каркас берёт только их, `Tabs` грузится с экранами |
| `Kbd` | kbd.tsx | shadcn Kbd | подсказка клавиши («Ctrl K»); лёгкий модуль для шапки, внутри поля — `InputGroupKbd` (та же подсказка) |
| `Segmented` | segmented.tsx | ToggleGroup | переключатель «Кресла / Врачи»: ровно один вариант выбран, повторное нажатие не снимает |
| `Chip`, `ChipGroup`, `chipVariants` | chip.tsx | Toggle / ToggleGroup | чипы выбора (`aria-pressed`), выбранный — ink, `showCheck` — галочка. Фильтр-ссылка (состояние в URL) — `<Link className={chipVariants({ surface: 'page' })} aria-current=…>`: тот же чип, выбранный — `aria-current="page"`; `surface="page"` — белый без контура на «тумане» (макет Pacienty) |
| `StatusBadge` | status-badge.tsx | Badge | статус записи **точкой и словом**: `planned` · `arrived` · `inChair` · `done` (галочка) · `noShow` (красный контур) · `cancelled` (зачёркнуто); `surface="color"` — белая пилюля на цветной записи |
| `Tag` | tag.tsx | Badge | метка в цвете палитры (`color`: цвет врача или `neutral`) |
| `Avatar` | avatar.tsx | shadcn Avatar / Base UI | инициалы из API; `solid` — врач (белые инициалы на насыщенном цвете, ≥ 4.5:1 у всех шести цветов), `soft` — пациент. Скрыт от диктора (рядом всегда ФИО); одиночному — `label` |
| `AlertStrip`, `Alert` | alert-strip.tsx, alert.tsx | shadcn Alert | красная плашка аллергии (`role="note"`: не объявляется при каждом открытии); `size="compact"` — строкой под именем |
| `FormAlert` | form-alert.tsx | Alert | общая ошибка формы (`errors.root` от `applyApiErrorToForm`): красная плашка над кнопкой отправки, `role="alert"`; у сбоев 5xx — «Код ошибки: …» и «Скопировать код» |
| `toast`, `toastUndo`, `toastApiError`, `Toaster` | toast.tsx | shadcn Toast / Base UI | тёмная пилюля с жёлтым действием, в живой области. `toastApiError(error)` — ошибка API вне формы: текст по коду, у 5xx — код ошибки и «Скопировать код». **Вместо «Вы уверены?»** — выполнить сразу и `toastUndo({ title, onUndo })`. `Toaster` подключён в App лениво (вне бюджета начальной загрузки); вызывать можно откуда угодно и когда угодно — вызовы до загрузки `Toaster` менеджер копит и показывает, как только он появится |
| `Panel` | panel.tsx | Base UI Dialog + Drawer | боковая панель: ≥ 1024 px с `inlineContainer` — колонка рядом с контентом, иначе — справа поверх, < 768 px — нижний лист (смахивание вниз). `step` + `onBack` — мастер (R023), `footer` — действия (своя жёлтая кнопка; в листе — на всю ширину) |
| `Dialog*` | dialog.tsx | shadcn Dialog / Base UI | окно 28, затемнение ink; для шагов — `stepAwareOpenChange` + `StepBackButton` |
| `stepAwareOpenChange`, `StepBackButton`, `StepProgress` | steps.tsx | — | R023: в шаге Escape, клик мимо и системная «Назад» — шаг назад, крестик и смахивание — закрыть всё; «← Назад» в подвале; «Шаг 2 из 3 · …». Кнопку «Далее / Записать» держите одним элементом — фокус не теряется при смене шага |
| `Tooltip` | tooltip.tsx | shadcn Tooltip / Base UI | подсказка ink; открывается и с клавиатуры |
| `Table*` | table.tsx | shadcn Table | по макету Pacienty: без линий, шапка 12.5/800 «графит», строка — скругление 16, наведение `mist`, выбранная (`data-state=selected`) — `highlight`; шире контейнера — прокрутка внутри него |
| `ResponsiveTable`, `rowActionClass` | responsive-table.tsx | Table | **таблицы → карточки** (PLT-18): с 768 px — `Table`, на телефоне — карточки (колонка `primary` — заголовок, остальные — «подпись: значение», `hideOnPhone` — пропустить). Ссылка/кнопка строки — в главной ячейке с `rowActionClass`: растянута на всю строку, контур фокуса — вокруг строки. Данные и порядок — с сервера |
| `PhoneInput`, `IinInput` | input.tsx | Input | телефон `type="tel"`, ИИН `inputMode="numeric"` и 12 знаков: цифровая клавиатура на телефоне |
| `LoginField` | login-field.tsx | Input | «Телефон или e-mail»: маска «+7 707 123 45 67» и `inputmode` по вводу, серверу — набранное (см. «Вход и выход») |
| `PasswordField` | password-field.tsx | InputGroup | пароль с «глазом»; `mode="new"` — индикатор zxcvbn-ts (лениво) и чек-лист, `mode="repeat"` — сверка с `original` (см. «Вход и выход») |
| `Checkbox` | checkbox.tsx | shadcn Checkbox / Base UI | галочка 24 px, `rounded-check` 8, отмеченная — ink с галочкой «лампой»; подпись — `<label>` вокруг галочки и текста (`min-h-11` — цель касания) |
| `DropdownMenu*` | dropdown-menu.tsx | shadcn DropdownMenu / Base UI Menu | меню по кнопке в портале (R022): пункты 44 px, `variant="danger"` — выход и удаление, `DropdownMenuLinkItem render={<Link />}` — переход роутером; меню пользователя в сайдбаре |
| `LanguageSwitch`, `canSwitchLanguage` | language-switch.tsx | ToggleGroup | «Рус \| Қаз» (сегменты 44 px, `lang`; `labels="short"` — полное название в `title`, `"full"` — «Русский \| Қазақша» для профиля; `surface="page"` — белый на «тумане», выбранный — ink, как на Vhod); языки — `uiLanguages()`: скрыт флагом `i18n.kk` и без словаря второго языка |
| `tokens.ts` | — | — | `DOCTOR_COLORS`, `PALETTE_CLASSES`, `APPOINTMENT_STATUSES`, `TOOTH_STATES` (+ классы) — классы строками целиком, чтобы их увидел Tailwind |

Правила вида: жёлтая кнопка — одна на область (страница; открытая панель или окно — своя;
«+» нижней панели — навигация); второстепенное — `outline`; выбранное — ink; отмена и
удаление — `danger`; статус — цвет и слово; всплывающее — только через компоненты выше
(портал, R022), никаких `position: absolute` под полем.

Почему свои составные, а не только готовые: `Combo` — это shadcn Combobox с настройками R011
(подсветка, `title`, активное первое совпадение) в одном месте; `Panel` меняет примитив по
ширине (Dialog-колонка ↔ Drawer-лист) — готового «адаптивного листа» в shadcn нет;
`SearchField`, `StatusBadge`, `Tag`, `AlertStrip`, `Segmented`, `Chip` — тонкие обёртки над
shadcn InputGroup / Badge / Alert / ToggleGroup с правилами «Кресла».

### Шрифты

Rubik (500–700) и Nunito Sans (500–800) — пакеты `@fontsource/*` (лицензия OFL, файлы
woff2 из Google Fonts), подключены в `src/styles/fonts.ts`. Vite кладёт woff2 в сборку —
шрифты отдаёт наш сервер, сторонних доменов нет (`docs/data-residency.md`: «нет Google
Fonts»); `@font-face` с `font-display: swap`, по `unicode-range` браузер берёт только нужные
подмножества. Казахские ә ғ қ ң ө ұ ү һ і — в `cyrillic-ext` (і — в `cyrillic`); что они
рисуются **своими** шрифтами, а не запасными, проверяет e2e (`dev-ui.spec.ts`): замер ширины
глифов на canvas и шрифты, которыми Chromium отрисовал текст (CDP `CSS.getPlatformFontsForNode`).
Новый шрифт — сначала такой замер.

## Тесты

- Пределы времени — с запасом на параллельные прогоны (сборка, e2e, соседние агенты): Vitest
  `testTimeout` 20 с, `hookTimeout` 30 с (vite.config.ts), ожидания Testing Library (`findBy*`,
  `waitFor`) — 10 с (`asyncUtilTimeout`, src/test/setup.ts); `lint.test.ts` один раз прогревает ESLint
  с типами TS (`beforeAll`, до 180 с), дальше кусочки — по 60 с. Маршруты в юнит-тестах не делятся
  на чанки (см. «Маршруты»).
- Playwright, параллельные прогоны (несколько агентов, `pnpm e2e` рядом с `pnpm verify`) — у каждого
  прогона всё своё (playwright.config.ts): **dev-сервер** — свободный порт из 5300–5799 (ниже
  динамического диапазона ОС; проба портов — синхронный `node -e` в главном процессе, старт поиска от
  pid; занятые, закрытые ОС и известные чужие порты пропускаются), `--strictPort`,
  `reuseExistingServer` выключен: чужой сервер не подхватывается (раньше второй прогон брал сервер
  первого на общем 5174 и получал ERR_CONNECTION_REFUSED, когда первый его гасил). Переиспользовать
  уже запущенный — только явно: `DENTIFY_E2E_REUSE=1` (порт — `DENTIFY_E2E_PORT`, по умолчанию 5174;
  сервер — `pnpm dev:mock --port 5174`). **Артефакты** — `test-results/run-<id>`, **HTML-отчёт** —
  `playwright-report/run-<id>` (открыть: `pnpm exec playwright show-report playwright-report/run-<id>`),
  папки старше суток удаляются при старте. Порт и id выбирает главный процесс, исполнители наследуют
  через окружение (`DENTIFY_E2E_PORT`, `DENTIFY_E2E_RUN`). Проект `warmup` (`e2e/warmup.setup.ts`) по
  очереди открывает все адреса, пока их собирает свежий dev-сервер; `desktop` и `mobile` зависят от
  него — первые тесты больше не падают на пустой странице под нагрузкой.
- **Живой стек** (T-PLT-16: PLT-04, PLT-17, PLT-18, PLT-21, PLT-27) — Playwright против уже поднятых бэкенда и `vite
  preview` сборки без моков; поднимает, гоняет и гасит всё `../verify.ps1 -E2E` (bash — `../verify.sh --e2e`, T-PLT-15):
  jar с профилем dev и preview с прокси `/api` на него, каждый на свободном порту. Режим выбирает
  `DENTIFY_STACK_URL` (адрес фронта, `http://localhost:<порт>` — не `127.0.0.1`: туда браузер не шлёт Secure-cookie
  сессии): задан — `playwright.config.ts` объявляет только проекты `stack-*` и не поднимает сервер моков; не задан —
  прежние `warmup`/`desktop`/`mobile` на моках, спеки `e2e/stack/` им не видны (`testIgnore`). Прямой адрес API —
  `DENTIFY_STACK_API_URL`, в режиме стека обязателен, как и адрес фронта (страница туда ходить не должна — это тоже
  проверяется, и без адреса проверка молча не делалась бы). В спецификации T-PLT-16 переменные названы
  `DENTIFY_E2E_BASE_URL` и `DENTIFY_E2E_API`; имена `DENTIFY_STACK_*` — из уже действующего договора с verify (T-PLT-15),
  а приставку `DENTIFY_E2E_*` занимает режим моков (порт, id прогона). Вручную:

  ```bash
  cd backend && java -jar target/dentify-backend-*.jar --spring.profiles.active=dev   # 8080/8081, пароли — ../deploy/.env
  cd frontend && pnpm build && DENTIFY_API_TARGET=http://127.0.0.1:8080 pnpm preview --port 4173 --strictPort
  DENTIFY_STACK_URL=http://localhost:4173 DENTIFY_STACK_API_URL=http://127.0.0.1:8080 pnpm e2e --project='stack*'
  ```

  Проекты (verify берёт их маской `--project=stack*`, имя без приставки из прогона выпало бы молча):

  | Проект | Что делает |
  |---|---|
  | `stack-setup` (`owner.setup.ts`) | вход dev-владельцем через API, как SPA (CSRF-токен → `POST /auth/login`), проверка сида (ФИО, клиника и филиал выбраны сразу) и storageState — cookie `DENTIFY_SESSION` и `XSRF-TOKEN` — в `<папка артефактов>/.auth/owner.json` (у параллельных прогонов своя) |
  | `stack-desktop` (1440×900), `stack-mobile` (390×844, Pixel 7) | спеки `e2e/stack/*.spec.ts`; трейс и снимок — у каждого теста (`trace: 'on'`, `screenshot: 'on'`) |
  | `stack-teardown` (`owner.teardown.ts`) | после всех: выход сессией `stack-setup`, та же cookie — 401 `error.session-expired`, файл storageState удаляется |

  Спеки: `demo.spec.ts` — **демо этапа 0** на обоих вьюпортах: вход по макету Vhod (axe, «Данные хранятся в Казахстане» —
  на обоих: на десктопе на панели «О сервисе», на телефоне под формой) → пустой кабинет (шапка с филиалом, сайдбар или
  нижняя панель и «Ещё», все 6 разделов с пустыми состояниями — переходами, как человек) → сессию завершает «другое
  устройство» (сессия `stack-setup` — свой клиент API того же аккаунта, без лишнего входа:
  `DELETE /api/v1/account/sessions/{id}` текущей сессии браузера — её id из списка сессий с `current`) → перезапрос `/me`
  при возврате во вкладку — 401 → «Сеанс истёк» с `returnTo` → снова вход на тот же экран → выход → `/login`, «Назад»
  кабинет не показывает; плюс все запросы — на свой origin, без ошибок в консоли; снимки шагов (`snap`) — вложениями
  HTML-отчёта (`…/stack/report`, открыть — `pnpm exec playwright show-report <папка>`) и кадрами трейса. «Завершить все
  сессии аккаунта» (PLT-04) демо не делает: сессии владельца делят параллельные спеки и прогоны, — это держит бэкенд
  (`AccountSessionsTests`). `cabinet.spec.ts` — с сессией `stack-setup`: каркас по `/me` (6 разделов, ФИО и инициалы с
  сервера, филиал в шапке), каждый раздел и «Мой профиль» — пустое состояние заглушки этапа 0, axe 0 serious/critical, без прокрутки
  вбок, на телефоне — цели касания ≥ 44; «Ещё». `security.spec.ts` — вход без «Запомнить»: cookie сессии HttpOnly, Secure,
  SameSite=Lax, без срока, в `document.cookie` её нет; в localStorage/sessionStorage нет токенов (ни значений cookie, ни
  JWT, ни ключей «token», «session», «auth»); изменение без `X-XSRF-TOKEN` — 403 `error.csrf`, с ним — 200; выход — та же
  cookie с другого клиента 401 `error.session-expired`; каждое изменение страницы несёт `X-XSRF-TOKEN`, равный cookie
  этого же запроса; ни одного запроса на сторонние домены (PLT-27). Помощники — `e2e/stack/stack.ts`: `login(page)` (UI)
  и `apiLogin(request)`, `logout`, `accountSessions`, `terminateSession`, `recordRequests` + `expectSameOrigin`,
  `expectCsrfOnChanges`, `expectHttpOnlySession`, `expectNoTokensInWebStorage`; типы ответов — из клиента orval.

  **Кто входит — dev-сид, а не свой профиль бэкенда** (решение 2026-09-27): `owner@demo.dentify.kz` из `DemoOwnerSeed`
  воспроизводим (идемпотентный сид, клиника «Dentify Demo» с одним филиалом «Абая, 150» выбирается при входе), а
  кабинет этапа 0 пуст по определению — данных в модулях ещё нет. Профиль `test-e2e` дал бы вторую копию сида и свою базу
  ради того же. Пароль — `DENTIFY_DEV_OWNER_PASSWORD` из окружения или `deploy/.env` (его же читает бэкенд). Когда
  у модулей появятся данные (T-CLN, T-PAT) и dev-база перестанет быть пустой, стеку нужна своя клиника на прогон:
  регистрация через `POST /api/v1/public/registrations` (T-CLN-05) в `stack-setup` — новая клиника и владелец, пустые
  на каждый прогон, без правок бэкенда. Пока пустые состояния разделов — заглушки этапа 0: в API они не ходят (с сервера
  только каркас по `/me`), и пустота от данных базы не зависит; когда разделы начнут запрашивать данные (T-PAT, T-SCH),
  пустоту проверять по ответу сервера (`page.waitForResponse`). Неверных паролей спеки живого стека не шлют: пятая
  неверная попытка закрыла бы вход dev-владельцу на 15 минут.

  **Потолок параллельных прогонов — лимит входов.** Прогон стека входит 7 раз с 127.0.0.1: `stack-setup` 1, `demo.spec.ts`
  2 × 2 (вход и снова вход после «Сеанс истёк»), `security.spec.ts` 1 × 2. Бэкенд пропускает с одного IP 30 входов в
  минуту (`dentify.security.rate-limit.login-ip`, сглаженная корзина: всплеск 30, дальше 1 в 2 с): одновременно — до
  четырёх прогонов `-E2E`, пятый получит 429 на входе. Новая спека со своим входом уменьшает потолок — вход без
  сценария берёт сессию `stack-setup` (фикстура `test` из `stack.ts`). В трейсах есть запрос входа с паролем dev-владельца — артефакты локальные
  (`deploy/test-results`, `frontend/test-results` — в `.gitignore`), наружу их не отдавать.
- Общие помощники e2e (`e2e/helpers.ts`, и для моков, и для стека): `assertNoHorizontalScroll(page)` (`scrollWidth ≤
  clientWidth` с ожиданием), `seriousA11yViolations(page)` (axe WCAG 2.2 AA, serious и critical — строкой «правило:
  элементы»), `smallTapTargets(page | locator)` (видимые кнопки, ссылки, поля и вкладки меньше 44×44 с учётом `::after`),
  `comboRows(page, within?)` (строки открытого `Combo` — список в портале, в `within` его нет), `isDesktop()` (`desktop` и
  `stack-desktop`).
- Vitest: `renderApp(path, persona | MeResponse | MeWithRights)` из `src/test/renderApp.tsx`
  поднимает роутер с memory-history и MSW; проверяем адаптер `/me` (ответ строго по спеке — без
  прав, `context = null`), гарды, меню по правам, выбор клиники, вход через сгенерированный клиент,
  перезапрос `/me` по фокусу и после 403, 401 → вход с «Сеанс истёк» (и без него при первом
  заходе), шапку закрытого раздела, search-параметры,
  `returnTo`, Ctrl K в раскладке ЙЦУКЕН, закрытие панели (`usePanel`), 404 и заголовки
  (`document.title`). Фокус вкладки в jsdom — `focusManager.setFocused(false → true)`.
- Клиент API (`src/shared/api/*.test.*`): `http.test.ts` — мутатор через сгенерированные функции
  (`credentials: include`, `Accept-Language`, CSRF из cookie, запрос токена и повтор после 403
  `error.csrf`, смена токена при входе — следующее изменение несёт новый, разбор ProblemDetail,
  `Retry-After`, не наш ответ → код по статусу, сеть, событие 401 и его отсутствие на `/auth/**`); `problem.test.ts` — текст ошибки (словарь → сервер → статус,
  конфликт версии, 429, сеть); `forms.test.tsx` — форма на Field + Controller: 422 `errors[]` под
  полями, фокус в порядке формы, вложенный путь, `fieldMap`/`fields`, несколько ошибок поля,
  409/429/500/сеть в `FormAlert`, копирование кода, `general: 'toast'`, сброс ошибок;
  `query.test.tsx` — повторы (матрица и настоящий QueryClient), `live` (фейковые таймеры, фокус),
  `invalidateApi`, `mutationInvalidates`; `mocks/contract.test.ts` — моки по zod-схемам спеки.
- Вход (T-IAM-03): `src/shared/ui/auth-fields.test.tsx` — `LoginField` (маска «+7 707 123 45 67» и
  `inputmode` по вводу, e-mail без маски, цифры до «@» не теряются, вставка и правка в середине — курсор
  на месте, сброс формы снаружи) и `PasswordField` (вход — только «глаз» с `aria-pressed`/`aria-controls`;
  `new` — индикатор zxcvbn-ts для диктора и чек-лист по мере ввода, пароль из e-mail/телефона и
  английское слово латиницей не хвалит; `repeat` — «Пароли не совпадают» не на лету, а когда разошёлся
  или ушли из поля; `passwordRepeatMismatch`); `src/shared/lib/loginInput.test.ts` — правила маски;
  `src/shared/lib/safeReturnTo.test.ts` — `returnTo`: только путь приложения и не сам вход;
  `src/app/routing.test.tsx` — вход через сгенерированный клиент с CSRF, выбор клиники, `returnTo`
  (и `/login` в нём — в кабинет), выход из меню пользователя и «Ещё».
- Playwright: проекты `desktop` (1440×900) и `mobile` (390×844, Pixel 7: сенсорный ввод); спеки в
  `e2e/` (`layout.spec.ts` — раскладка при 1440/1024/768 с открытой панелью; `session.spec.ts` —
  выбор клиники, перезапрос `/me` по фокусу (`visibilitychange`), 401 → вход с «Сеанс истёк»,
  чистая консоль на «нет доступа»; `auth.spec.ts` — вход по макету Vhod на обоих проектах: по телефону
  (маска, `inputmode`, CSRF-токен в заголовке) и e-mail, «глаз», «Неверный телефон, e-mail или пароль», 429
  «через 15 минут» из `Retry-After`, нет связи, глубокая ссылка → `returnTo`, выбор филиала при нескольких
  членствах, заглушки «Забыли пароль?» и «Зарегистрировать», раскладка (панель «О сервисе» с 1024 px, отметка «Данные
  хранятся в Казахстане» — при любой ширине, видимая одна: на панели или под формой; «Рус | Қаз» скрыт флагом), «Сеанс истёк» (плашка одна, повторные 401 не уводят), выход из меню
  пользователя (сайдбар) и «Ещё» (телефон) — «Назад» кабинет не показывает; меню пользователя своим
  чанком: нажатие и фокус, пока чанк грузится, доигрываются (Enter — с фокусом на первом пункте); общий
  компонент пароля на витрине; эталон
  экрана входа (`login.png`, сверен с `Vhod.dc.html`);
  `api-errors.spec.ts` — ошибки API на входе: 400 `errors[]` под
  полями с фокусом, контуром и `aria-describedby`, 401, 429 «через 15 минут», 500 с копированием
  кода в буфер, «Сеанс истёк» посреди работы и возврат на тот же адрес; `sidebar-collapsed.spec.ts` — иконки с подсказками на 768–1023 px;
  `sidebar-short.spec.ts` — сайдбар в низком окне 844×390, 915×412, 800×450, 1024×450: карточка в
  окне, каждый пункт и «Мой профиль» достижимы прокруткой, колесом, Tab и пальцем, активный виден
  сразу, вбок не прокручивается; обход пунктов начинается, когда карточка стала кнопкой меню —
  `data-user-menu="menu"`: смена кнопки посреди `scrollIntoViewIfNeeded` открепляет найденный элемент). Каждая UI-правка — новая/дополненная спека и зелёный `pnpm e2e` (R018).
- Телефон: `responsive.spec.ts` — **все маршруты** из `routeTree.gen.ts` (+ закрытый раздел,
  открытая панель, фильтры) на 390 и 360 px: `scrollWidth ≤ clientWidth`, поля ≥ 16 px, у всех
  кнопок, ссылок и полей площадь касания ≥ 44×44 (с расширением `::after` — `hitArea` в
  `e2e/helpers.ts`); `mobile.spec.ts` — нижняя панель по геометрии макета Telefon, пункты по
  правам, «+» → новая запись, меню «Ещё» (разделы по правам, филиал, «Выйти»), safe-area (CDP
  `Emulation.setSafeAreaInsetsOverride` — `emulateSafeArea`), панель прячется при фокусе в поле,
  нижний лист: свайп вниз (сенсорные события CDP — `swipe`; мышью Base UI Drawer не смахивается),
  короткий свайп не закрывает, крестик, фокус внутри; подписи панели умещаются в ячейки на
  360/375/390 (`labelBoxes`), на `/schedule/new` один `aria-current`; вкладки вбок: активная
  видна, полосу прокрутили пальцем (`swipe` по горизонтали) — смена ширины пилюль её не
  отбрасывает, не трогали — доводит выбранную; на 768/1024/1440 панели нет. Вкладки экрана
  (`TabsList`, 6 штук на витрине) — `dev-ui.spec.ts`: на телефоне вбок без полосы, выбранная видна,
  на десктопе влезают.
  axe (`a11y.spec.ts`) — и на телефоне: каркас, «Ещё», новая запись, нижний лист.
- Дизайн-система: `src/styles/tokens.test.ts` (значения токенов = design-directions, `cn`
  знает все размеры/радиусы/тени), `src/styles/contrast.test.ts` (пары текст/фон ≥ 4.5:1, в том
  числе инициалы врачей и полупрозрачные заливки сайдбара; графика и контур фокуса ≥ 3:1 —
  значения читаются из самого CSS), `src/styles/usage.test.ts` (в `shared/ui`, каркасе и
  `modules` — только токены размеров и радиусов, отступы — только шкала и шаги макетов, резерв — по
  файлам с назначением),
  `src/shared/ui/design-system.test.tsx` (Combo, SearchField, Segmented, StatusBadge, R023),
  `src/shared/ui/toast.test.tsx` (уведомление, вызванное до загрузки `Toaster`, не теряется),
  `src/shared/ui/mobile.test.tsx` (таблица → карточки по ширине, поля 16 px и цифровая клавиатура,
  переключатель языка: флаг `i18n.kk`, словарь kk, `<html lang>`), `src/styles/breakpoints.test.ts` (брейкпоинты CSS = JS, нет
  `sm:`/`2xl:` и медиазапросов мимо `MEDIA`), `src/app/shell/mobile.test.tsx` (нижняя панель и
  «Ещё» по правам, «+» → новая запись без `aria-current`, выход),
  `src/shared/lib/keepSelectedInView.test.ts` (доводка выбранной вкладки и остановка после
  прокрутки пользователем — на заглушках геометрии).
- i18n (см. «i18n → Проверки»): `src/shared/i18n/dictionary.test.ts` (`pnpm i18n:check`: словарь ↔ код,
  ICU, другие языки), `integrity.test.ts` (сама проверка ловит ошибки), `lint.test.ts` (ESLint падает на
  строке в JSX, в том числе шаблонной), `i18n.test.tsx` (ICU-склонения «1 год / 2 года / 5 лет», date-fns и неделя с
  понедельника, `LocalizedText`, флаг `i18n.kk` на входе/в профиле/«Ещё», смена языка с фикстурным
  kk без правок компонентов — и ошибок сервера в форме входа); e2e `i18n.spec.ts` — все экраны: `lang=ru`, консоль без «нет ключа» и
  ошибок ICU, нет сырых ключей и переключателя; возраст на витрине и дата шапки.
  Список пар в `contrast.test.ts` ведётся руками, поэтому настоящие пары меряет axe
  `color-contrast` без исключений: `dev-ui.spec.ts` (витрина) и `a11y.spec.ts` (расписание,
  пациенты, врачи, профиль, вход, выбор клиники + контур фокуса 3 px у полей входа и карточек
  клиник).
  e2e `dev-ui.spec.ts` — эталонные скриншоты витрины (десктоп и телефон, `deviceScaleFactor: 1`)
  и списка с поиском, казахские глифы, шрифты с нашего домена и `swap`, список у края окна и в
  прокручиваемой панели (открывается вверх, не обрезан), шаги панели и окна (R023), «Вернуть»
  в уведомлении, контур фокуса, reduced motion, axe (0 serious/critical); `one-lamp.spec.ts` —
  не больше одной жёлтой кнопки на область на экранах каркаса (`data-variant="default"`; «+» панели —
  навигация, в области не считается), в шапке и нижней панели вместе — не больше одного жёлтого по
  вычисленному фону (`--color-lamp`), на телефоне это «+», и у главного действия шапки — отклик
  (переход или уведомление «в разработке»); `focus.spec.ts` — фокус не падает на `<body>` (кнопка
  входа во время запроса, плашка ошибки, «назад» пагинации); `mobile.spec.ts` — главное действие
  раздела белым кругом 48 px рядом с поиском, значок «лагуной». `src/app/startup.test.ts` — `/me` при запуске: один запрос, отдаётся
  гарду один раз, при 401 без повтора. Эталоны — в
  `e2e/__screenshots__` (с ОС в имени: на Linux-CI понадобятся свои). Поменяли вид намеренно —
  `pnpm e2e --update-snapshots`, посмотреть разницу и закоммитить эталоны вместе с правкой.
- Бюджет (`.size-limit.js`): gzip начальной загрузки ≤ 225 kB (держит LCP, см. «Производительность»)
  — точка входа **вместе со всеми
  статическими импортами** (общие чанки, которые `index.html` подгружает через `modulepreload`)
  и их CSS. Список строится из `dist/.vite/manifest.json` рекурсивно по `imports`; ленивые
  маршруты и моки (`dynamicImports`) не входят, шрифты (woff2) — тоже. Если `dist/index.html`
  грузит файл, которого нет в списке, проверка падает. Смотреть состав: `vite build
  --sourcemap` + `source-map-explorer`.
- Начальная загрузка — **один чанк** (`codeSplitting.groups: [{ name: 'app', tags: ['$initial'] }]` в
  vite.config.ts, Rolldown): точка входа и все её статические импорты. Раньше Rolldown выносил общие с
  ленивыми маршрутами модули в два десятка мелких файлов — каждый лишний запрос в цепочке первого
  экрана. Предупреждение Vite о чанке > 500 kB без сжатия поднято до 800: бюджет — gzip, его держит
  size-limit.

## Производительность (PLT-22)

Бюджеты: начальная загрузка ≤ 218,5 kB gzip и чанки каркаса первого экрана ≤ 21 kB (`pnpm size`, в
`verify`), LCP ≤ 2.5 с и CLS ≤ 0.1 (Lighthouse mobile, `pnpm lcp`). LCP первого экрана `/` → `/schedule`
(сборка без моков, заглушка `/me`, медиана 3 прогонов):

| Когда | Начальная загрузка | Чанки каркаса | HTTP/2 + TLS (как прод: Caddy, `pnpm lcp`) | HTTP/1.1 (`pnpm lcp --http1`, справочно) |
|---|---|---|---|---|
| до оптимизации | 243,7 kB | — | 2,80 с | 4,13 с |
| 2026-09-26, до T-IAM-03: `zod/mini`, один начальный чанк | 219,1 kB | ~19 kB | 2,47–2,49 с (FCP 2,29) | 3,40–3,54 с |
| T-IAM-03 до правки ревью: меню пользователя в каркасе | 221,0 kB | 39,3 kB | **2,63 с** | — |
| T-IAM-03 после: меню своим чанком, `cn build` | **216,2 kB** | **19,7 kB** | **2,49 с** (2,48–2,50, FCP 2,29–2,30, CLS 0) | 3,40 с |

**Как растёт LCP.** Lighthouse считает LCP моделью (Lantern): медленный 4G, байты идут круговыми задержками
по ~30 kB, и по HTTP/2 цепочка первого экрана — документ → CSS → шрифты → начальный JS → чанки каркаса и
`/me` — одна очередь. Поэтому LCP растёт не плавно, а ступенями по ~50 мс: до ступени +2,5–3 kB к
начальной загрузке или к чанкам каркаса (замер 2026-09-26 подменой размеров в сохранённом прогоне
Lighthouse), следующая — 2,54 с. Так T-IAM-03 и перешагнул бюджет: сперва меню пользователя (Base UI Menu,
+20 kB в цепочке каркаса, 2,63 с), потом и без него — словарь входа, маршруты, стили (+1,9 kB к
начальной загрузке, 2,53 с). Отсюда оба бюджета size-limit — вплотную к текущим размерам (~2,5 kB до
ступени): рост валит `pnpm verify` раньше, чем LCP. Поднимать предел — только вместе с замером. Любая
правка стартового маршрута (каркас, `/` → первый раздел, `index.html`, шрифты первого экрана, новый
запрос до первого экрана) — `pnpm verify:perf` (build → size → lcp).

**LCP 2,5 с держится только на HTTP/2** и с запасом ~10 мс (2,49 с — тот же уровень, что до T-IAM-03);
отдача фронта без HTTP/2 (прокси или CDN, понижающие до HTTP/1.1) критерий нарушит; `pnpm lcp --http1` —
только справочно (код выхода по CLS). Меньше 2,49 с — только через ~30 kB меньше в цепочке первого экрана
(следующая ступень: 2,40 с).

Прод отдаёт фронт через Caddy (HTTP/2, `encode zstd gzip` — deploy/caddy), поэтому бюджет меряем
по HTTP/2. HTTP/1.1 в лаборатории хуже структурно: 6 соединений на хост, и волна чанков после
точки входа идёт в несколько заходов. Без собственных шрифтов LCP был бы 2,3 с — это пол текущего
стека (React + роутер + i18n + Base UI в начальном чанке); шрифты «Кресла» стоят ~0,17 с.

Что сделано против водопада (было 4 последовательных шага: HTML → JS → `/me` → чанки → шрифты):

- `/me` уходит сразу при запуске точки входа, параллельно со стартом моков и языка, а чанки
  стартового маршрута (каркас и экран; для `/` — первый раздел меню) — тут же, `router.loadRouteChunk`
  (`warmUpStartRoute`, app/startup.ts). Гард `_app` берёт уже летящий `/me` (`takeStartupSession`).
- Шрифты первого экрана — `<link rel="preload" as="font">` в index.html (Rubik 600 и Nunito Sans
  600/800, кириллица; путь — тот же файл, что в `@font-face`, Vite выпускает его один раз).
  Предзагрузка остальных (latin-подмножества, 700) замер не улучшила — они делят канал с JS.
- То, чего на первом экране не видно, — после него (`whenIdle`: шрифты загружены, кадр отрисован,
  браузер свободен): место уведомлений (`Toaster`), боковая панель (`SidePanel`, Base UI Drawer и поле
  поиска). Уведомление с кнопки главного действия грузит модуль по нажатию.
- Шапка не тянет тяжёлое: `Kbd` вместо `InputGroupKbd` (без Base UI Input), виды вкладок —
  `tabs-variants.ts` (без Base UI Tabs). Меню пользователя сайдбара — своим чанком после первого экрана
  (`UserMenuDropdown`, см. «Каркас»): Base UI Menu в чанке каркаса стоил 0,14 с LCP.
- `cn` — на таблицах, собранных заранее (`cn build`, см. «Дизайн-система»): без компилятора конфигурации
  `cn/config` начальная загрузка на 5 kB меньше.

Что осталось и куда смотреть дальше: экрана расписания ещё нет (критерий приёмки PLT-22 — по нему,
T-SCH): когда появится, `pnpm lcp` — в `verify` (TODO в scripts/lcp.mjs) и замер уже по нему; до тех пор —
`pnpm verify:perf` вручную. Резервы:
предкомпиляция ICU-строк (formatjs `compile --ast`, минус разборщик ~5 kB); `cn build` без `full` (таблицы
только под найденные в исходниках классы, ещё −2 kB, но их надо пересобирать при каждом новом классе);
стили витрины `/dev/ui` в production-CSS (−0,65 kB, нужен свой CSS витрины); подсказка из текущего экрана
в начальном чанке — `TooltipProvider` в App (Base UI Tooltip + floating-ui ~20 kB): перенос в каркас
проверен 2026-09-26 — начальная загрузка 191,5 kB, но LCP 2,63 с (те же байты уходят в чанки каркаса,
в той же очереди), не делать.

## Открытые вопросы (к владельцу)

- **Главное действие раздела на телефоне.** Сейчас — белый круг 48 px рядом с поиском (значок
  раздела «лагуной»; жёлтый на экране — только «+»), «+» нижней панели всегда ведёт в новую запись.
  Жёлтым круг в шапке не делаем без явного согласия владельца: два жёлтых «добавить» на одном
  экране (ревью 2026-09-26); согласие — записать в design-directions.md и поправить
  `one-lamp.spec.ts`. Альтернатива — «контекстный +»: «+» выполняет действие текущего раздела
  (пациент, документ склада), а в шапке телефона кнопки нет. Решить до экранов T-PAT-14 / T-INV-20.
- **Вкладки расписания на телефоне.** «День / Неделя / Архив» занимают 48 px под шапкой; в макете
  Telefon их нет — там полоса дней недели и чипы задач. Прятать ли вкладки на телефоне (Неделя и
  Архив — через меню) — решить с T-SCH.
- **Двойной контур фокуса у полей.** В фокусе поле рисует контур 2 px `ink` и общий контур фокуса
  3 px «лагуна» — вместе ~7 px, в Sistema у открытого поля только 2 px `ink`. Оставить одно кольцо
  (и какое) — решение по PLT-21 вместе с обновлением эталонов скриншотов витрины.
