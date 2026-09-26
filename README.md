# Dentify

Веб-приложение для стоматологических клиник Казахстана: расписание по креслам, карточка
пациента с зубной формулой, врачи и графики, склад, настройки клиники, роли и доступы.
Пилот — клиника владельца проекта (сейчас работает на macdent.kz).

## Запуск за 10 минут

Поднимается локальное окружение (база, кэш, почта, файловое хранилище) и бэкенд поверх него.
Фронтенд (T-PLT-05) подключится, когда появится.

**Что установить**

- Docker Desktop с Compose v2 (на Windows — с WSL2) и Git. Для окружения больше ничего не нужно.
- Для бэкенда: JDK 25 (`JAVA_HOME` на него). Сборка идёт через `./mvnw`, отдельный Maven не нужен.
- Для фронтенда (с T-PLT-05): Node 23 и pnpm 10.

**Поднять**

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
| Caddy | https://localhost:8443, только с `up -Proxy` | прод-схема «один origin по HTTPS»: `/api` уходит на 8080, остальное на 5173. В обычной разработке хватает прокси Vite |

Порт 5433, а не стандартный 5432, потому что 5432 может быть занят локальным PostgreSQL.

**Команды** (`dev.ps1` / `dev.sh`)

| Команда | Что делает |
|---|---|
| `up` [`-Proxy` / `--proxy`] | поднимает окружение, ждёт healthy, создаёт бакет MinIO |
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
├── design/      макеты и инструменты дизайна
├── docs/        требования, бэклог (backlog.json + генератор), data-residency.md,
│                architecture.md (соглашения), modules/ (граф модулей, генерируется тестом)
├── kanban/      страница канбана и tasks.json
├── backend/     Spring Boot 4.1, модульный монолит на Spring Modulith, Flyway
├── frontend/    React SPA (скелет — T-PLT-05)
└── deploy/      compose.dev.yml, dev.ps1 / dev.sh, .env.example, init-скрипты postgres/ и minio/, caddy/
```
