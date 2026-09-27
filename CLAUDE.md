# Dentify — инженерный бриф

SaaS для стоматологий Казахстана. Требования — `docs/requirements.md`, задачи — `docs/backlog.md`,
источник правды обоих — `docs/backlog.json` (правится только он, затем
`python docs/build_backlog.py`). План и решения — `../../wiki/projects/dentify/` (mvp-plan.md).
Макеты — холст https://claude.ai/artifact/XiPsn66wbKPwxqwiRpgBsM (55 артбордов) и исходники
`design/mockups/*.dc.html` (открывать в браузере), перечень экранов —
`../../wiki/projects/dentify/screens.md`, токены и правила UI — `design-directions.md` там же.

Найти задачу или требование: `python -c "import json;d=json.load(open('docs/backlog.json',encoding='utf-8'));print(json.dumps([x for m in d['modules'] for x in m['tasks']+m['requirements'] if x['id'] in ('T-PLT-02','PLT-24')],ensure_ascii=False,indent=1))"`
(с `PYTHONIOENCODING=utf-8`).

## Правила (обязательны)

- **dentify R002 — вся логика на бэкенде, бэкенд первым.** Правила, валидация, права, переходы
  статусов, расчёты, фильтры и сортировки — только на сервере. Фронт тонкий: показывает ответ
  API и шлёт намерения; что разрешено — сообщает API (`allowedActions`, 409/422). В fullstack-
  задаче сначала готов и покрыт тестами бэкенд.
- **dentify R003 — только русский, но готово к переводу.** Ни одной пользовательской строки в
  коде: UI — ключи i18n (RU-словарь), бэкенд — коды ошибок + `MessageSource` (`messages.properties`
  — RU, базовый файл; `messages_kk` позже), двуязычные поля — `LocalizedText {ru, kk}`.
  Переключатель «Қаз» скрыт флагом `i18n.kk`.
- **dentify R001** — любой растущий список имеет поиск (серверный).
- **R021** — сначала официальное готовое (Spring, Hibernate, springdoc, Testcontainers), потом
  авторитетное комьюнити, свой код — последним. Версии Spring Boot 4 / Security 7 / Modulith 2
  проверяй по документации (MCP context7), а не по памяти: в Boot 4 модульные стартеры
  (`spring-boot-starter-webmvc`, `-flyway`, …), Jackson 3 (`tools.jackson`), JSpecify.
- UI: R011 (поиск и tooltip в select), R018 (любую UI-правку прогнать вживую в Playwright),
  R019 (UI-идеи владельца — концепция, не ТЗ: готовое из shadcn/Base UI, минимум кликов, большое —
  мастер или вкладки), R022 (выпадающее — в портале), R023 («Назад» в шагах диалога), жёлтая
  кнопка — одна на область.
- Данные в тестах, сидах и примерах — вымышленные. Никаких реальных пациентов и сотрудников.
- Коммит и пуш — после каждой принятой задачи (rules dentify R004): `git add apps/dentify` (только этот каталог, секреты и мусор в .gitignore), Conventional Commits `<type>(dentify): …` с id задачи, затем `bash C:/Users/abyla/code/bin/git-sync-push.sh` (fetch → rebase → push). Чужие незакоммиченные правки вне apps/dentify не трогать.

## Окружение (Windows)

- **JDK 25**: `C:\Users\abyla\.jdks\openjdk-25.0.2`. Системная `java` — 17, ей не собирать.
  Bash: `export JAVA_HOME="$HOME/.jdks/openjdk-25.0.2"; export PATH="$JAVA_HOME/bin:$PATH"`;
  PowerShell: `$env:JAVA_HOME="$HOME\.jdks\openjdk-25.0.2"`. Сборка — `./mvnw` (Maven 3.9).
- Node 23 + pnpm 10. Docker Desktop (Compose v2) — нужен для dev-окружения и Testcontainers.
- Порты: PostgreSQL 18 — **5433** (5432 занят локальным PG 15), Valkey 6379, Mailpit
  1025/8025, MinIO 9000/9001, backend 8080 (actuator — 8081), frontend 5173.
- Разработка только локальная; облачного CI и стенда нет до T-PLT-25.
- Dev-окружение: `deploy/dev.ps1 up | check | psql [-As app] | logs [svc] | reset-db -Force | down`; `up -App` — ещё и
  всё приложение в контейнерах (сервисы `app` и `web` в `compose.dev.yml`, образы `backend/Dockerfile` и
  `frontend/Dockerfile`, http://localhost:8088)
  (bash — `deploy/dev.sh`), пароли — `deploy/.env` (шаблон `.env.example`, `.env` не коммитится).
  БД `dentify`, схема `dentify`: Flyway ходит `dentify_owner` (владелец), приложение — `dentify_app`
  (NOBYPASSRLS, только DML, гранты по умолчанию). S3 — бакет `dentify-files`, ключ приложения
  `S3_ACCESS_KEY` только на него. MinIO — образ `pgsty/minio` (официальные образы сняты).
  Реестр размещения данных — `docs/data-residency.md`, новый внешний сервис — сначала туда.

## Стек (зафиксирован)

Java 25 · Spring Boot 4.1.1 · Spring Modulith 2.1.1 · Spring Security 7 · Spring Session (Valkey)
· JPA/Hibernate 7 (`@TenantId`) · Flyway · PostgreSQL 18 (`uuidv7()`, btree_gist, pg_trgm, RLS) ·
springdoc-openapi 3.1 → orval · Testcontainers 2 (`@ServiceConnection`) · ArchUnit ·
React 19 · Vite 8 · TanStack Router/Query · shadcn/ui + Tailwind 4 · react-hook-form + zod ·
react-i18next · Playwright.

## Бэкенд — кратко (подробно — `docs/architecture.md`)

- Модули `kz.dentify.<модуль>`: `platform`, `iam`, `clinic`, `staff`, `patient`, `schedule`, `medical`, `inventory`.
  Направления: clinic ← iam, staff, schedule, inventory; staff, patient ← schedule; medical → patient, schedule, clinic;
  все → `platform :: api`; **только iam → `platform :: identity`** (клиника в сессии, вход, сессии); подписки на события —
  iam ← `staff :: events`, inventory ← `schedule :: events`; `platform :: *` не писать. Циклы — портами/SPI и событиями.
  Меняются только решением владельца, вместе: `package-info.java`, `ModularityTests.ALLOWED`, architecture.md «Модули и
  события» (там же карта, `platform :: api`, порты, события).
- Пошагово: модуль — «Раскладка пакетов модуля», таблица — «Новая таблица арендатора» (RLS в той же миграции), эндпоинт —
  «Новый эндпоинт» (DTO-record, сервис с правилами, `@PreAuthorize`, коды ошибок, `operationId`, реестр
  `TenantIsolationTests`, интеграционный тест, orval).
- Права — только коды через `platform.api`, в сессии не хранятся («Права и контекст»). Логи без ПД, деньги `BigDecimal`,
  время `timestamptz` и пояс клиники, тексты — коды i18n («Правила кода»). Решения с причинами — «ADR».
- Ошибки — `ProblemDetail` с `code`; объект чужой клиники — 404. `@ApplicationModuleListener` для критичных потоков —
  только после журнала публикаций (T-PLT-14).
- Тест — наследник `AbstractIntegrationTest` без `@Import`, `@MockitoBean` и своих свойств («Тестовая инфраструктура»).

## Фронтенд — кратко (подробно — `docs/frontend.md`)

- Тонкий клиент (R002): показывает ответ API и шлёт намерения; ручных `fetch` нет — клиент orval из `api/openapi.json`
  («API-клиент», «Добавить вызов API»).
- Экран — «Маршруты» → «Добавить экран», состояние экрана — в URL; телефон и адаптив — «Телефон и адаптив».
- Строки — только полные ключи i18n `пространство:путь` («i18n»); компоненты — shadcn/Base UI в токенах «Кресла»
  («Дизайн-система»).
- Проверки — «Тесты» (моки, живой стек); правка стартового маршрута — `pnpm verify:perf` («Производительность»).

## Готово = 

`./mvnw verify` зелёный (JDK 25, Docker запущен), новая логика покрыта тестами, критерии приёмки
связанных требований проверены, фронт (если есть) прогнан в Playwright. Всё сразу — `./verify.ps1` (bash — `verify.sh`):
окружение, бэкенд, спека, `pnpm verify`; `-E2E` — живой стек, `-Scan` — уязвимости и секреты (README, «Проверка перед
коммитом»). Статусы задач в канбане
(https://claude.ai/artifact/TCRR3jmsp8ynpwimq8qRsG) двигает основная сессия.
