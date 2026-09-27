# Dentify

Веб-приложение для стоматологических клиник Казахстана: расписание по креслам, карточка
пациента с зубной формулой, врачи и графики, склад, настройки клиники, роли и доступы.
Пилот — клиника владельца проекта (сейчас работает на macdent.kz).

## Запуск за 10 минут

Поднимается локальное окружение (база, кэш, почта, файловое хранилище) и приложение поверх него: целиком в
контейнерах (нужен только Docker) или бэкенд и фронт на хосте — для разработки.

**Что установить**

- Docker Desktop с Compose v2 (на Windows — с WSL2) и Git. Для окружения и приложения в контейнерах больше ничего не
  нужно.
- Для разработки бэкенда: JDK 25 (`JAVA_HOME` на него). Сборка идёт через `./mvnw`, отдельный Maven не нужен.
- Для разработки фронтенда: Node 23 и pnpm 10.

**Всё приложение в контейнерах** (PLT-26: на чистой машине нужен только Docker)

```powershell
./deploy/dev.ps1 up -App      # Windows; bash: bash deploy/dev.sh up --app
```

Первый запуск создаёт `deploy/.env` со случайными паролями, скачивает образы и собирает два своих: бэкенд
(`backend/Dockerfile`: jar собирается внутри, профиль dev) и фронт (`frontend/Dockerfile`: production-сборка SPA за
Caddy). Затем ждёт, пока всё станет healthy: бэкенд накатывает миграции и dev-сид. Открыть **http://localhost:8088**
(именно `localhost`, не `127.0.0.1`: на него браузер не отдаёт Secure-cookie сессии) и войти как владелец демо-клиники
`owner@demo.dentify.kz`, пароль — `DENTIFY_DEV_OWNER_PASSWORD` из `deploy/.env`. Замер 2026-09-27: сборка обоих образов
с пустыми кэшами Maven и pnpm — 2 мин 40 с (плюс на чистой машине скачивание образов: ~1,2 ГБ окружения и ~0,5 ГБ
базовых для сборки), старт бэкенда — ~7 с; повторный `up -App` без правок — ~30 с.

Без скрипта — одной командой Compose (нужен `deploy/.env`: его один раз создаёт `dev.ps1 up` или `dev.sh up`):

```bash
docker compose -f deploy/compose.dev.yml up --build -d --wait app web   # app, web и их зависимости: postgres, valkey
docker compose -f deploy/compose.dev.yml down                           # остановить; данные в томах остаются
```

`--wait` — только с именами сервисов: без них Compose ждёт и разовый `minio-init`, а его выход (с кодом 0) считает
ошибкой. Без `-d --wait` (`… up --build`) поднимается всё окружение с журналами в консоли, Ctrl+C — остановить.
`--build` пересобирает образы из текущих исходников (слои зависимостей берутся из кэша). Бэкенд в контейнере наружу не
публикуется: API — только через `web` (`/api` → `app:8080`), как в проде через Caddy; актуатор 8081 — только внутри
контейнера. Порт `web` другой — `DENTIFY_WEB_PORT` в `deploy/.env`. Журналы — `dev.ps1 logs app` / `logs web`.

**Для разработки — окружение**, а бэкенд и фронт на хосте:

```powershell
# Windows, PowerShell, из папки apps/dentify
./deploy/dev.ps1 up       # первый запуск: создаёт deploy/.env со случайными паролями, скачивает образы (~1,2 ГБ), ждёт healthy
./deploy/dev.ps1 check    # проверяет порты, роли БД, расширения, Valkey, Mailpit, MinIO
```

```bash
# Linux, macOS, Git Bash
bash deploy/dev.sh up
bash deploy/dev.sh check
```

Если PowerShell не даёт запускать скрипты, выполните
`powershell -ExecutionPolicy Bypass -File deploy/dev.ps1 up`.

**Бэкенд** (из папки `apps/dentify/backend`, окружение поднято):

```powershell
$env:JAVA_HOME = "<путь к JDK 25>"
./mvnw spring-boot:run    # профиль dev: Flyway накатывает миграции, API — 127.0.0.1:8080
./mvnw verify             # все тесты; нужен запущенный Docker (Testcontainers)
```

Готовность — http://127.0.0.1:8081/actuator/health/readiness (`UP`). Actuator слушает только
внутренний порт 8081, на 8080 его нет. Подробности — [backend/README.md](backend/README.md).

**Что поднимается.** Порты открыты только на 127.0.0.1. Пароли и ключи лежат в `deploy/.env`
(при первом `up` генерируются случайно), в репозитории есть только шаблон `deploy/.env.example`.
Значения в `.env` — только `[A-Za-z0-9_-]`: файл читают и Compose, и бэкенд, а кавычки, `$`, `\` и `#`
они понимают по-разному.

| Сервис | Адрес | Для чего |
|---|---|---|
| PostgreSQL 18 | `127.0.0.1:5433`, база `dentify`, схема `dentify` | `dentify_owner` — владелец схемы, им ходит Flyway; `dentify_app` — роль приложения: только DML, `NOBYPASSRLS`. Расширения `btree_gist`, `pg_trgm` |
| Valkey 8 | `127.0.0.1:6379`, с паролем | сессии, rate-limit, кэш |
| Mailpit | http://127.0.0.1:8025, SMTP `127.0.0.1:1025` | перехватывает всю почту, наружу ничего не уходит |
| MinIO | S3 API `127.0.0.1:9000`, консоль http://127.0.0.1:9001 | приватный бакет `dentify-files`; у приложения свой ключ с доступом только к этому бакету |
| Бэкенд | API `127.0.0.1:8080`, actuator `127.0.0.1:8081` | `./mvnw spring-boot:run` из `backend/`; пароли берёт из того же `deploy/.env` |
| Приложение в контейнерах | http://localhost:8088, только с `up -App` | сервисы `app` (бэкенд, профиль dev) и `web` (Caddy: сборка SPA и `/api` → `app`); с бэкендом на хосте не конфликтует — у `app` нет портов наружу |
| Caddy | https://localhost:8443, только с `up -Proxy` | прод-схема «один origin по HTTPS»: `/api` уходит на 8080, остальное на 5173. В обычной разработке хватает прокси Vite |

Порт 5433, а не стандартный 5432, потому что 5432 может быть занят локальным PostgreSQL.

**Команды** (`dev.ps1` / `dev.sh`)

| Команда | Что делает |
|---|---|
| `up` [`-Proxy` / `--proxy`] [`-App` / `--app`] | поднимает окружение, ждёт healthy, создаёт бакет MinIO; `-App` — плюс приложение в контейнерах (собирает образы) |
| `down` | останавливает окружение. Данные в томах сохраняются |
| `reset-db -Force` / `reset-db --force` | удаляет том БД `dentify-pgdata` и создаёт чистую базу. Без флага ничего не делает |
| `logs [сервис]` [`-Follow` / `--follow`] | показывает журналы |
| `psql` [`-As owner\|app\|postgres`] [`-Sql "…"`] | открывает psql в базе `dentify`. В bash: `--as app -c "…"` |
| `ps` | показывает состояние контейнеров |
| `check` | проверяет окружение от начала до конца |

Пароли БД применяются только при создании тома. Если поменяли их в `deploy/.env`, выполните
`reset-db -Force`. `check` падает, если в `.env` остались значения шаблона `…change-me`: тогда
`down`, удалить `deploy/.env` и тома `dentify-pgdata`, `dentify-valkey-data`, `dentify-minio-data`
(`docker volume rm …`), затем `up` сгенерирует новые пароли. Где какие данные хранятся и почему только в РК, описано в
[docs/data-residency.md](docs/data-residency.md).

## Проверка перед коммитом

Облачного CI до развёртывания нет (T-PLT-25), поэтому всё, что проверял бы конвейер (PLT-26), запускается локально одной
командой из папки `apps/dentify`:

```powershell
./verify.ps1                 # окружение, бэкенд, контракт API, фронтенд — перед каждым коммитом
./verify.ps1 -E2E            # плюс e2e на живом стеке: бэкенд + фронт + Playwright
./verify.ps1 -Scan           # отдельно: уязвимости зависимостей и секреты (нужен интернет)
```

```bash
bash verify.sh               # Linux, macOS, Git Bash; ключи --e2e, --scan, --strict, --skip
```

Скрипт идёт по шагам и не останавливается на первом красном: в конце — сводка со временем и кодом выхода каждого
шага, код выхода скрипта 1, если красный хоть один. Полный вывод каждого шага — в
`deploy/test-results/verify-<время>/` (не коммитится, папки старше 7 дней удаляются).

| Шаг | Что делает | Красный, если |
|---|---|---|
| env: JDK 25 | берёт `JAVA_HOME`, если там JDK 25+, иначе `~/.jdks/openjdk-25.0.2` (только для этого запуска) | JDK 25 нет ни там, ни там |
| env: Docker | `docker info`, Compose v2 | Docker Desktop не запущен |
| env: dev-окружение | `deploy/dev.ps1 check`; не прошёл — `up` и снова `check` (поднятое оставляет, остановить — `down`) | окружение не поднимается |
| env: Node и pnpm | Node 23+, pnpm 10+ | версии ниже |
| backend | `./mvnw -B verify`: JUnit, Testcontainers, `ApplicationModules.verify`, ArchUnit, тесты изоляции | любой тест или сборка |
| api | `api/openapi.json` не изменился после сборки: его выгружает `OpenApiContractTests` | спека отставала от кода — проверить `git diff`, `pnpm api:generate`, закоммитить вместе |
| frontend | `pnpm install --frozen-lockfile`, затем шаги `pnpm verify` по одному (список — из `package.json`): typecheck, lint, i18n:check, api:check (клиент orval = спека), test, build, size (бюджет), e2e на моках | любой шаг; size не мерится, если сборка упала |

**`-E2E` — живой стек.** Jar бэкенда (из шага backend или `mvnw -DskipTests package`) с профилем dev на свободном
порту, поверх dev-окружения; `vite preview` сборки без моков с прокси `/api` на этот бэкенд (`DENTIFY_API_TARGET`);
сквозной запрос `GET /api/v1/auth/csrf` через прокси (204 и cookie `XSRF-TOKEN`); затем Playwright-проекты живого стека
(`--project=stack*`, T-PLT-16): `stack-setup` входит dev-владельцем (`owner@demo.dentify.kz`, пароль из `deploy/.env`),
`stack-desktop` (1440×900) и `stack-mobile` (390×844) одним прогоном гоняют демо этапа 0 (вход → пустой кабинет → «Сеанс
истёк» → выход), пустой кабинет (axe, прокрутка вбок, цели касания) и безопасность сессии (HttpOnly, CSRF, свой origin,
токенов в хранилищах нет), `stack-teardown` выходит сессией `stack-setup`. Спеки — `frontend/e2e/stack/`, подробности —
`docs/frontend.md`, «Тесты». Адрес фронта — `DENTIFY_STACK_URL` (`http://localhost:<порт>`: браузер шлёт Secure-cookie на
`localhost`, но не на `127.0.0.1`), прямой адрес API — `DENTIFY_STACK_API_URL`; трейсы и скриншоты шагов демо —
`deploy/test-results/verify-<время>/stack/artifacts` (`--output`), HTML-отчёт — `…/stack/report`. В трейсах — запрос входа
с паролем dev-владельца: артефакты локальные и не коммитятся, наружу их не отдавать. Бэкенд и фронт
останавливаются всегда — и при красном шаге, и при Ctrl+C; журналы — `stack-backend.log`, `stack-frontend.log` там же.
Образы `app`/`web` из `docker compose` в `-E2E` не участвуют: jar и `vite preview` быстрее пересборки образов на каждый
прогон; сам `up -App` проверяется вручную (замер выше).

**`-Scan` — сканеры** (PLT-06), отдельной командой: им нужен интернет, база NVD весит ~250 МБ.

| Сканер | Порог | Исключения (allowlist) |
|---|---|---|
| OWASP dependency-check (Maven-плагин в `backend/pom.xml`, NVD + CISA KEV + RetireJS) | CVSS 7 и выше | `backend/dependency-check-suppressions.xml`: точечно, с причиной и сроком `until` |
| `pnpm audit --prod` | high и critical | `pnpm.auditConfig.ignoreGhsas` в `frontend/package.json` (pnpm 10; в pnpm 11 — `audit.ignore` в `pnpm-workspace.yaml`), с причиной в коммите |
| gitleaks по `apps/dentify`: история git этой папки — строгим `.gitleaks.toml`, рабочая копия (`dir`) — `.gitleaks-dir.toml` | любая находка | `.gitleaks.toml` (правило с причиной, действует на оба скана) или отпечаток находки в `.gitleaksignore` |

- База NVD — кэш в `~/.m2/repository/org/owasp/dependency-check-data` (другая папка — переменная `DENTIFY_NVD_DATA`).
  Сначала `dependency-check:update-only` (сеть; не чаще раза в 4 часа), затем `dependency-check:check` по кэшу. Без ключа
  NVD API база берётся из официальных JSON-фидов NVD (первая загрузка ~2 мин); ключ (бесплатно,
  https://nvd.nist.gov/developers/request-an-api-key) — переменная `NVD_API_KEY`; своё зеркало фидов —
  `DENTIFY_NVD_DATAFEED`. Отчёт — `backend/target/dependency-check-report.html`.
- NVD, реестр npm или GitHub недоступны — шаг жёлтый (WARN), проверка идёт по кэшу, если он есть; кэша NVD нет
  или он пуст (первое обновление не дошло до NVD) — dependency-check тоже жёлтый; с `-Strict` (`--strict`) — красный.
- gitleaks берётся из `PATH` (8.19+: `winget install Gitleaks.Gitleaks`, `scoop install gitleaks`, `brew install
  gitleaks`), иначе скрипт сам скачивает закреплённый релиз 8.30.1 с GitHub в `~/.dentify-tools` (другая папка —
  `DENTIFY_TOOLS_DIR`) и сверяет SHA-256 архива с зашитым в скрипт. Значения найденных секретов в логах и отчётах
  скрыты (`--redact`).
- Два конфига gitleaks: `.gitleaks-dir.toml` наследует `.gitleaks.toml` и исключает только то, чего нет в git
  (`node_modules`, сборки, отчёты, база GeoIP и локальный `deploy/.env` с паролями машины). История сканируется строгим
  `.gitleaks.toml` без этих исключений: `.env`, по ошибке закоммиченный в любую папку, там будет найден.

**Ключи.** `-Strict` / `--strict` — недоступный сканер красный. `-Skip env,backend,frontend` / `--skip …` — пропустить
группы, например `./verify.ps1 -E2E -Skip backend,frontend` — только живой стек. `-E2E -Scan` — всё сразу.
Справка — `Get-Help ./verify.ps1 -Detailed`, `bash verify.sh --help`.

## Репозитории

Разработка ведётся в монорепозитории `abylaykuat22/code` (каталог `apps/dentify/`, там же вики и
скрипты воркфлоу). Этот каталог зеркалится в отдельный репозиторий
**git@github.com:abylaykuat22/dentify.git** с историей только своих коммитов (`git subtree split`).
Синхронизация зеркала из монорепозитория (после партии задач или вехи):

```bash
git subtree push --prefix=apps/dentify dentify master
```

(remote `dentify` → `git@github.com:abylaykuat22/dentify.git`). В зеркало не входят `bin/git-sync-push.sh`
и вики монорепозитория — они нужны только рабочему процессу.

## Дизайн

- **Макеты всех экранов MVP** (стиль «Кресло», 55 артбордов по разделам):
  https://claude.ai/artifact/XiPsn66wbKPwxqwiRpgBsM — приватный холст, открывается под
  аккаунтом владельца; поделиться — меню Share на странице.
- Исходники макетов: [design/mockups/](design/mockups/) — по файлу `.dc.html` на экран +
  `canvas.json` (раскладка по страницам). Бриф для рисования новых экранов —
  [design/brief.md](design/brief.md); проверка формата — `python design/validate.py`;
  пересборка раскладки — `python design/build_canvas.py`.
- Токены дизайн-системы, правила компонентов и шрифтов — в вики:
  `wiki/projects/dentify/design-directions.md`; перечень экранов —
  `wiki/projects/dentify/screens.md`.

## Требования и задачи

- **Канбан**: https://claude.ai/artifact/TCRR3jmsp8ynpwimq8qRsG — 241 задача по этапам 0–6,
  фильтры по этапу и модулю, поиск, перетаскивание между колонками, подзадачи галочками.
  Статусы общие и хранятся в базе артефакта (коллекция `tasks`); исходник страницы —
  [kanban/index.html](kanban/index.html), данные задач — `kanban/tasks.json`.
- [docs/requirements.md](docs/requirements.md) — 250 требований MVP и 65 «позже» по 8 модулям,
  с критериями приёмки, экранами и ролями; в конце — замечания ревью.
- [docs/backlog.md](docs/backlog.md) — задачи и подзадачи по этапам с оценками и зависимостями.
- Источник правды — [docs/backlog.json](docs/backlog.json). Правите его → `python docs/build_backlog.py`
  пересобирает оба md и `kanban/tasks.json` и проверяет ссылки, зависимости и порядок фаз →
  канбан перепубликовать тем же артефактом.
- План MVP, стек, модель данных, юр-минимум РК — `wiki/projects/dentify/mvp-plan.md`.

## Стек (решено)

Java 25 · Spring Boot 4.1 · Spring Modulith · PostgreSQL 18 · Redis/Valkey · React 19 ·
Vite 8 · TanStack Router/Query · shadcn/ui · Tailwind 4 · Playwright. Хостинг — только в РК.

## Структура (будет расти)

```
apps/dentify/
├── README.md, CLAUDE.md   инструкция по запуску; инженерный бриф и правила
├── verify.ps1, verify.sh  проверка перед коммитом одной командой (-E2E, -Scan); .gitleaks*.toml — сканер секретов
├── design/      макеты и инструменты дизайна
├── docs/        требования, бэклог (backlog.json + генератор), data-residency.md,
│                architecture.md (соглашения), modules/ (граф модулей, генерируется тестом)
├── kanban/      страница канбана и tasks.json
├── backend/     Spring Boot 4.1, модульный монолит на Spring Modulith, Flyway; Dockerfile — образ app
├── frontend/    React SPA; Dockerfile — образ web (сборка SPA за Caddy)
└── deploy/      compose.dev.yml (окружение + app и web), dev.ps1 / dev.sh, .env.example, init-скрипты postgres/ и
                 minio/, caddy/ (Caddyfile.dev — профиль proxy, Caddyfile.app — сервис web)
```
