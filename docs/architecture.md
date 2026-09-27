# Dentify — архитектура и соглашения бэкенда

Документ растёт по задачам. Сейчас в нём «Как добавить» (модуль, таблица, эндпоинт — чек-листы со ссылками на
разделы), «Миграции», «Модули и события», «Логи», «Выключенная автоконфигурация» и «Код» (T-PLT-02), «Ошибки API»,
«Соглашения API и OpenAPI» (T-PLT-07), «Сообщения и i18n» (T-PLT-11), «Тенант-контекст и изоляция клиник» (T-PLT-03),
«Безопасность и сессии» (T-PLT-04), «Журнал действий» (T-PLT-13), «Аккаунты и членство» (T-IAM-01), «Вход, выход и
/api/v1/me» (T-IAM-02) и «Права» (решение до T-IAM-06/07). ADR появятся в T-PLT-38. Граф модулей генерируется тестом
`ModuleDocumentationTests` в [modules/](modules/) (`components.puml`, `all-docs.adoc`), руками его не правят.

## Как добавить

Чек-листы — порядок шагов; правила и причины — в разделах по ссылкам. Всё, что ниже, ловят тесты: пропущенный шаг
роняет `./mvnw verify`, а не ревью.

### Модуль

1. Направление зависимостей — сначала решение (владелец) и строка в `CLAUDE.md`; раскладка пакетов и правила — «Модули и
   события».
2. `kz.dentify.<модуль>/package-info.java`: `@ApplicationModule(displayName = …, allowedDependencies = { "platform :: api",
   … })` и `@NullMarked`. `platform :: *` не писать: он открыл бы и `platform :: identity`, который только для iam.
3. Та же строка — в `ModularityTests.ALLOWED` (тест сверяет их с `package-info`).
4. Пакеты `api` и `events` — со своим `package-info.java`: `@NamedInterface("api")` / `@NamedInterface("events")` и
   `@NullMarked`; `web`, `internal` — только `@NullMarked` («Код»).
5. Каталог миграций `db/migration/<модуль>` («Миграции», «Файлы и версии»); `MigrationNamingTests` проверит, что модуль в
   пути есть среди `kz.dentify.<модуль>`.
6. Глобальная таблица в модуле (без `clinic_id`) — `RowLevelSecurityTests.GLOBAL_TABLES` и, если модуль новый для
   глобальных сущностей, `TenancyRulesTests.GLOBAL_ENTITY_MODULES`, с причиной («Таблица арендатора»).
7. Коды ошибок модуля — `<модуль>.<что>` в `messages.properties` («Ошибки API»), тексты журнала — `audit.*` («Журнал
   действий», «Тексты»).

### Таблица

1. Миграция `db/migration/<модуль>/V<yyyy>_<MM>_<dd>_<HHmm>__<модуль>_<что>.sql`, метка новее последней в master
   («Файлы и версии»).
2. Таблица клиники — по шаблону «Таблица арендатора»: колонки из «Колонки», `UNIQUE (clinic_id, id)`, составные FK,
   индексы с `clinic_id` первой колонкой, RLS ENABLE + FORCE и политика `tenant_isolation` в той же миграции.
3. Данные в миграции при FORCE RLS — по рецепту «Миграции с данными при FORCE RLS».
4. Сущность — от `TenantEntity` (глобальная — от `GlobalEntity`), id — `@UuidGenerator(style = VERSION_7)`, `@Version`
   («Сущности»); репозиторий Spring Data, нативный SQL — с `clinic_id` («Репозитории»).
5. Глобальная таблица — в `RowLevelSecurityTests.GLOBAL_TABLES` с причиной; вторая политика — только `FOR SELECT` в
   `READ_POLICIES` и в этом документе.

### Эндпоинт

1. Путь — `/api/v1/<ресурс>`; открытый без входа — только под `/api/v1/auth/**` (сессия и пароль) или
   `/api/v1/public/**` (регистрация, города, приглашение) («Соглашения API и OpenAPI»).
2. Контроллер в пакете `web` модуля — тонкий: параметры → сервис модуля → DTO-record. Репозитории, сущности JPA и
   `@Transactional` в `web` запрещены, ответ — record, `PageResponse` записей, список записей, `ResponseEntity` или ничего
   (ArchUnit `CodingRulesTests.webLayerIsThin`).
3. DTO — `record` в `web`, поля без `@Nullable` обязательны и проверяются `@NotNull`/`@NotBlank`, `clinicId` в DTO
   запрещён («Соглашения API и OpenAPI», «Тесты изоляции»); список — `PageQuery` + `PageResponse`, сортировка — `@SortBy`.
4. `@PreAuthorize` на методе или классе: `isAuthenticated()`, право — `hasAuthority(…)` (с T-IAM-07 — `@perm.has(…)`,
   «Права»), открытый — `permitAll()` («Безопасность и сессии»; ArchUnit `endpointsHavePreAuthorize`).
5. OpenAPI: `@Operation(operationId = "<глагол><Ресурс>", summary, description)` — явный и уникальный operationId (имя хука
   orval); свои ответы — `@ApiResponse`; ошибки 4xx/5xx и `Retry-After` у 429 дописывает платформа
   (`OpenApiContractTests.operationIdsAreExplicitAndUnique`).
6. Ошибки — исключения `platform.api` с кодами модуля, тексты кодов — в `messages.properties` и проверка ключей тестом
   модуля («Ошибки API», «Сообщения и i18n»).
7. Изоляция: ресурс клиники — строка в `TenantIsolationTests.RESOURCES`, эндпоинт без данных клиники — в `NOT_CLINIC_DATA`
   путём целиком с причиной, открытый эндпоинт `/api/v1/public/…` — тоже по одному («Тесты изоляции»).
8. Интеграционный тест модуля наследует `AbstractIntegrationTest` (без `@Import`, `@MockitoBean`, своих свойств),
   запросы — через его `http`, вход — `signIn` или вход iam (`CLAUDE.md`, «Код»).
9. Прогнать тесты и закоммитить изменившийся `api/openapi.json` вместе с кодом («Соглашения API и OpenAPI»).

## Миграции

Схема БД меняется только миграциями Flyway. Они лежат в
`backend/src/main/resources/db/migration/` и применяются при старте приложения (dev, test, prod).

### Роли и права

| Роль | Кто ею ходит | Права |
|---|---|---|
| `postgres` | администратор, init-скрипт `deploy/postgres/init/01-roles.sh` | суперпользователь; приложение им не ходит |
| `dentify_owner` | Flyway (`spring.flyway.user`) | владелец схемы `dentify` и всех таблиц: DDL. `NOBYPASSRLS`, поэтому при `FORCE ROW LEVEL SECURITY` политики RLS действуют и на неё |
| `dentify_app` | приложение (`spring.datasource.username`) | только DML (`SELECT/INSERT/UPDATE/DELETE`) через гранты по умолчанию; `NOBYPASSRLS`; таблицами не владеет |

- Всё, что создаёт `dentify_owner` в схеме `dentify`, сразу доступно `dentify_app` на DML: так
  настроены гранты по умолчанию (`ALTER DEFAULT PRIVILEGES`). Писать `GRANT` в каждой миграции не нужно.
- Если приложению нельзя менять таблицу (журнал аудита, история миграций), права отнимает сама
  миграция: `REVOKE UPDATE, DELETE ON … FROM dentify_app`. V1 так закрывает `flyway_schema_history`, миграция журнала —
  `audit_log` (раздел «Журнал действий»).
- DDL приложению недоступен, единственное исключение — функция `audit_log_create_partitions` (`SECURITY DEFINER`, владелец
  `dentify_owner`, закреплённый `search_path`): она создаёт только помесячные партиции `audit_log`, и приложение вызывает её
  при старте и раз в сутки. Других функций `SECURITY DEFINER` без записанного обоснования не заводить.
- Расширения (`btree_gist`, `pg_trgm`) ставит администратор: локально и в тестах это делает
  init-скрипт, в managed PostgreSQL — консоль облака. Миграции только фиксируют требование через
  `CREATE EXTENSION IF NOT EXISTS`.
- Одна и та же конфигурация ролей везде. Dev поднимается скриптом `deploy/dev.ps1 up`, тесты — через
  `DentifyPostgresContainer`, который в Testcontainers выполняет тот же init-скрипт. Прод повторяет
  эту схему: владелец схемы для Flyway и роль приложения без BYPASSRLS.
- **search_path роли приложения — `dentify, public`**: таблицы приложения лежат в `dentify`, расширения
  (`pg_trgm`, `btree_gist`) — в `public`. Локально и в тестах его задаёт init-скрипт
  (`ALTER DATABASE dentify SET search_path = dentify, public`). На проде это требование к настройке
  managed PostgreSQL: `ALTER ROLE dentify_app IN DATABASE dentify SET search_path = dentify, public`
  (записано и в `application-prod.yml`).
- Роль приложения проверяется дважды. Тест `DatabaseSetupTests` сверяет атрибуты и права
  `dentify_app`: не суперпользователь, `NOBYPASSRLS`, `NOCREATEDB`, `NOCREATEROLE`, `NOREPLICATION`, без
  `TEMP` и `CREATE` на базе и схемах `dentify` и `public`, схемой владеет `dentify_owner`. При старте
  `platform.database.ApplicationRoleCheck` роняет приложение, если роль подключения:
  - суперпользователь или имеет `BYPASSRLS`;
  - владеет схемой `dentify` или таблицами в ней (владелец снимает `FORCE ROW LEVEL SECURITY`);
  - прямо или транзитивно состоит в `dentify_owner`, во владельце схемы или таблиц либо в роли с
    `SUPERUSER`/`BYPASSRLS`. Проверка — `pg_has_role(роль, m, 'MEMBER')`: она видит и цепочки грантов, и
    членство без `INHERIT` (у `NOINHERIT`-роли права группы не действуют сами, но `SET ROLE` доступен);
  - имеет search_path не `dentify, public` (первой должна быть `dentify`, `public` — в списке).

  В managed PostgreSQL роли заводят руками, и ошибку в настройке надо увидеть до первого запроса.
  Запрещено: делать `dentify_app` членом `dentify_owner`, ролей администратора облака и любых ролей с
  правами выше DML (`GRANT <роль> TO dentify_app`); делать её владельцем объектов.

### Файлы и версии

- Путь: `db/migration/<модуль>/V<yyyy>_<MM>_<dd>_<HHmm>__<модуль>_<что_делаем>.sql`, например
  `db/migration/clinic/V2026_10_01_1200__clinic_create_branch_chair.sql`. Версия-метка времени (UTC)
  нужна, чтобы параллельные ветки и агенты не занимали один номер. Исключение — базовая
  `platform/V1__platform_baseline.sql`.
- Части метки разделены подчёркиванием: Flyway читает его как точку, и версия в журнале —
  `2026.10.01.1200`. Слитная метка (`V202610011200`) запрещена: 12 цифр подряд в логе Flyway маска ПД
  принимает за ИИН (раздел «Логи»). Порядок версий — по дате и времени, `V1` раньше всех.
- Имена проверяются дважды: `spring.flyway.validate-migration-naming: true` роняет старт на файле, который
  не по формату Flyway (без этого Flyway его молча пропускает), а тест `MigrationNamingTests` — формат
  метки, реальность даты и времени и совпадение модуля в каталоге и в имени с модулем `kz.dentify.<модуль>`.
- Порядок. Метка снимает коллизию номеров, но не порядок: если ветка с более ранней меткой вливается
  после ветки с более поздней, уже применённой к базе, `validate` падает с «Detected resolved migration
  not applied to database». Правило:
  - перед мёржем в master миграция ветки должна быть новее последней миграции в master; если нет —
    переименовать файл на свежую метку (в стенд и прод он ещё не попал, контрольную сумму никто не помнит);
  - в dev включён `spring.flyway.out-of-order: true` (`application-dev.yml`): локальная база копит
    миграции из разных веток и не должна падать. Переименованную миграцию, уже применённую локально под
    старым именем, лечит `deploy/dev.ps1 reset-db -Force`;
  - в test (база каждый раз свежая) и prod `out-of-order` выключен: в проде порядок применения всегда
    совпадает с порядком версий.
- Модуль в пути и в имени — владелец таблиц. Миграция меняет только таблицы своего модуля.
- Повторяемые объекты (функции, представления) хранятся в `R__<модуль>_<что>.sql` и
  перезаписываются через `CREATE OR REPLACE`.
- Применённую миграцию не правят: Flyway сверяет контрольные суммы. Ошибку исправляют новой
  миграцией. Локально, пока изменения никуда не ушли, можно поправить файл и пересоздать базу
  (`deploy/dev.ps1 reset-db -Force`).
- Миграции только вперёд: откатных скриптов нет. `flyway clean` выключен (`clean-disabled` по
  умолчанию).
- Каждая миграция выполняется в одной транзакции: в PostgreSQL DDL транзакционный. Операции, которые
  не работают внутри транзакции (`CREATE INDEX CONCURRENTLY`), выносятся в отдельную миграцию, и для
  неё транзакция отключается: файл конфигурации скрипта `<имя>.sql.conf` со строкой
  `executeInTransaction=false`.
- Данные в миграциях (справочники по умолчанию, шаблоны ролей) только вымышленные или системные.
  Реальных клиник, пациентов и сотрудников в них нет.

### Одна история миграций, а не модульные миграции Modulith

В Spring Modulith 2 есть свои миграции по модулям (`spring.modulith.runtime.flyway-enabled`), с той же
раскладкой `db/migration/<модуль>`, но с другой семантикой: корень переезжает в `db/migration/__root`,
у каждого модуля своя таблица `flyway_schema_history_<модуль>` и своя нумерация `V1, V2, …`, модули
применяются в порядке зависимостей, а `@ApplicationModuleTest` выполняет только миграции модулей теста.

Решение (2026-09-25): **не включаем**, остаёмся на одной истории `flyway_schema_history` с глобальным
порядком по метке времени. Почему:

- интеграционные тесты идут на полном приложении с одним кэшированным контекстом
  (`AbstractIntegrationTest`), а не срезами `@ApplicationModuleTest` с частичной схемой;
- одна история — одна таблица, которую надо закрыть от `dentify_app` (`REVOKE` в V1). С модульными
  миграциями каждая `flyway_schema_history_<модуль>` получила бы DML приложения через гранты по
  умолчанию и требовала бы своего `REVOKE`;
- порядок между модулями задают внешние ключи по разрешённым направлениям, а метка времени его и так
  сохраняет; нумерация внутри модуля снова дала бы коллизии версий у параллельных веток.

`spring-modulith-runtime` на classpath есть (приходит с `spring-modulith-starter-insight`), поэтому
свойство `spring.modulith.runtime.flyway-enabled` нигде не включать. Страховка — тест
`DatabaseSetupTests.singleMigrationHistory`: в схеме ровно одна таблица истории. Если решение
пересмотрим, переходить до пилота: V1 — в `db/migration/__root`, версии модулей — `V1, V2, …`, и
`REVOKE ALL … FROM dentify_app` на каждую `flyway_schema_history_<модуль>`.

### Имена

- Таблицы и колонки — `snake_case`, таблицы в единственном числе: `clinic`, `branch`, `chair`,
  `appointment`. Если имя неоднозначно, добавляется префикс модуля: `inv_item`, `inv_doc`.
- Внешний ключ — `<таблица>_id`: `patient_id`, `chair_id`.
- Ограничения и индексы: `pk_<таблица>`, `fk_<таблица>_<на_что>`, `ux_<таблица>_<колонки>`
  (уникальный), `ix_<таблица>_<колонки>`, `ck_<таблица>_<что>`, `ex_<таблица>_<что>` (EXCLUDE).

### Колонки

| Что | Тип и правило |
|---|---|
| Идентификатор | `id uuid NOT NULL DEFAULT uuidv7()`, первичный ключ. UUIDv7 встроен в PostgreSQL 18, сортируется по времени, расширение не нужно |
| Клиника (арендатор) | `clinic_id uuid NOT NULL` в каждой таблице арендатора; первая колонка в индексах списков |
| Создание | `created_at timestamptz NOT NULL DEFAULT now()`, `created_by uuid` (id аккаунта; FK на `account` нет — это служебная колонка) |
| Изменение | `updated_at timestamptz NOT NULL DEFAULT now()`, `updated_by uuid`. Заполняет приложение (JPA-аудит), триггеров нет |
| Версия | `version bigint NOT NULL DEFAULT 0`: оптимистическая блокировка (`@Version`); конфликт даёт 409 |
| Момент времени | только `timestamptz` (хранится в UTC, показывается в поясе клиники). `timestamp without time zone` не используется |
| Календарная дата | `date`: дата рождения, дата смены |
| Время суток по расписанию | `time`, пояс берётся у клиники |
| Деньги | `numeric(12,2)` в KZT, в Java — `BigDecimal`. `float`/`double` не используются |
| Строки | `text` и `CHECK (length(…) BETWEEN …)`, если нужен предел. Без `varchar(n)` |
| Перечисления | `text` и `CHECK (status IN (…))`. Тип PostgreSQL `enum` не используется: его трудно менять миграциями |
| Флаги | `boolean NOT NULL DEFAULT …` |
| Структуры | `jsonb`; двуязычные поля — `LocalizedText {ru, kk}` в `jsonb` |
| Мягкое удаление | `archived_at timestamptz`; строки не удаляются физически |

### Таблица арендатора

- В каждой таблице с данными клиники есть `clinic_id`, стандартные служебные колонки и RLS. RLS
  включается (`ENABLE` + `FORCE`) вместе с политикой в той же миграции, где создаётся таблица.
- Если на таблицу ссылаются другие таблицы арендатора, у неё есть `UNIQUE (clinic_id, id)`, а
  внешний ключ на неё составной: `(clinic_id, x_id) → x (clinic_id, id)`. Тогда БД не даст связать
  объекты разных клиник.
- Внешние ключи между таблицами разных модулей допустимы только по разрешённым направлениям
  модулей: iam → clinic (уже есть: `membership` → `clinic`, `membership_branch` → `branch`); staff, inventory → clinic;
  schedule → patient, staff, clinic; medical → patient, schedule, clinic.
- Глобальные таблицы без `clinic_id` (`account`, `auth_event`) — исключение. Их перечень и
  обоснование ведёт модуль-владелец.
- Сама клиника (`clinic`) — **корень арендатора**: своей колонки `clinic_id` у неё нет, её `id` и есть `clinic_id` всех
  её данных. RLS — ENABLE + FORCE и одна политика `tenant_isolation` с `id = current_clinic_id()` в `USING` и `WITH
  CHECK`: клиника видит и пишет только саму себя, списка всех клиник у приложения нет. Новую клинику вставляют в
  контексте её же id (id известен до вставки). Реестр корней — `RowLevelSecurityTests.TENANT_ROOT_TABLES`.

Шаблон (проверен на PostgreSQL 18.6: без контекста клиники строк не видно, чужая клиника строк не видит, вставка
с чужим `clinic_id` отклоняется политикой; по нему создана тестовая таблица `test_note`, на которой это проверяют
`RowLevelSecurityTests` и `TenantIsolationTests`):

```sql
-- db/migration/clinic/V2026_10_01_1200__clinic_create_branch_chair.sql (пример)
CREATE TABLE chair (
    id          uuid        NOT NULL DEFAULT uuidv7(),
    clinic_id   uuid        NOT NULL,
    branch_id   uuid        NOT NULL,
    name        text        NOT NULL CHECK (length(name) BETWEEN 1 AND 100),
    sort        integer     NOT NULL DEFAULT 0,
    archived_at timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid,
    version     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_chair PRIMARY KEY (id),
    CONSTRAINT ux_chair_clinic_id UNIQUE (clinic_id, id),
    CONSTRAINT fk_chair_branch FOREIGN KEY (clinic_id, branch_id) REFERENCES branch (clinic_id, id)
);
CREATE INDEX ix_chair_clinic_branch ON chair (clinic_id, branch_id);

ALTER TABLE chair ENABLE ROW LEVEL SECURITY;
ALTER TABLE chair FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON chair
    USING      (clinic_id = current_clinic_id())
    WITH CHECK (clinic_id = current_clinic_id());
```

- Политика — ровно такая: одна разрешающая политика на все команды для всех ролей, выражение
  `clinic_id = current_clinic_id()` в `USING` и в `WITH CHECK`. Другое выражение или вторая разрешающая политика
  (они складываются через OR) роняют тест каталога. Единственный путь для второй политики — реестр
  `RowLevelSecurityTests.READ_POLICIES` с причиной, и только `FOR SELECT`: для `UPDATE` и `DELETE` строка должна пройти и
  политики SELECT, и политики изменения (документация PostgreSQL, CREATE POLICY), поэтому такая политика расширяет только
  чтение. Сейчас в реестре одна — `membership.own_memberships` (раздел «Аккаунты и членство»).
- `current_clinic_id()` — функция платформы (`platform/V2026_09_25_1200__platform_tenant_rls.sql`):
  `nullif(current_setting('app.clinic_id', true), '')::uuid`. Она `LANGUAGE sql STABLE`, и планировщик встраивает её
  в запрос: в плане — само выражение, а не вызов функции на каждую строку (проверяет `RowLevelSecurityTests`), и
  индекс по `clinic_id` применим, как если бы выражение было написано в политике.
  `nullif` нужен потому, что после сброса настройки в сессии остаётся пустая строка, а не `NULL`. Без контекста
  клиники политика ничего не показывает и ничего не даёт записать.
- `app.clinic_id` ставит приложение при каждой выдаче соединения из пула (раздел «Тенант-контекст и изоляция
  клиник»), миграции и psql-сессия без неё видят 0 строк.
- Тест каталога `RowLevelSecurityTests` проверяет после всех миграций: у каждой таблицы с `clinic_id` включены
  RLS и FORCE и есть стандартная политика; таблица без `clinic_id` — только из реестра глобальных таблиц в том же
  тесте (`GLOBAL_TABLES`, с причиной).

### Миграции с данными при FORCE RLS

`dentify_owner` — владелец таблиц, но `FORCE ROW LEVEL SECURITY` распространяет политики и на него. Без
`app.clinic_id` политика не видит ни одной строки, поэтому `UPDATE` для заполнения новой колонки или
сид по клиникам **молча обработает 0 строк**, и миграция пройдёт «успешно», ничего не сделав.

Рецепт — снять FORCE на время изменения данных в той же миграции (она выполняется в одной транзакции,
поэтому другие сессии таблицу без FORCE не увидят):

```sql
-- db/migration/patient/V2026_11_05_1000__patient_fill_search_name.sql (пример)
ALTER TABLE patient NO FORCE ROW LEVEL SECURITY;   -- владелец снова обходит RLS
UPDATE patient SET search_name = lower(last_name || ' ' || first_name) WHERE search_name IS NULL;
ALTER TABLE patient FORCE ROW LEVEL SECURITY;      -- обязательно вернуть в той же миграции
```

Проверено на PostgreSQL 18.6 в dev-базе: при FORCE `UPDATE` от владельца без контекста меняет 0 строк,
после `NO FORCE` — все, после возврата `FORCE` — снова 0. Если данные нужно писать по клиникам
(сид справочников), вместо `NO FORCE` ставить контекст на каждую клинику:
`SELECT set_config('app.clinic_id', id::text, true)` в цикле `DO $$ … $$`. Тест каталога `RowLevelSecurityTests`
проверяет, что после всех миграций у каждой таблицы с `clinic_id` снова стоит FORCE (`pg_class.relforcerowsecurity`);
тестовая миграция `test_note` проходит этот рецепт.

## Тенант-контекст и изоляция клиник

Данные всех клиник — в одной базе, клиника видит только своё (PLT-01, PLT-02, PLT-08). Три рубежа, каждый
закрывает промах предыдущего:

1. **Контекст клиники** в приложении (`TenantContext`): без него репозиторий данных клиники бросает
   `MissingTenantContextException`, а не читает «всё».
2. **Hibernate `@TenantId`** (`TenantEntity`): клиника в INSERT и условие `clinic_id = …` во всех запросах, поиске по id
   и связях — объект другой клиники «не существует», сервис отвечает 404.
3. **RLS в PostgreSQL**: соединение приложения несёт `app.clinic_id`, политика таблицы пропускает только строки этой
   клиники — даже для нативного SQL и `JdbcTemplate`, где фильтра Hibernate нет.

### Контекст клиники

- API — `platform.api.TenantContext`: `current()`, `require()`, `clinicId()`, `branchId()` и `run(tenant, action)` /
  `call(tenant, supplier)`; `Tenant` — клиника и выбранный филиал (филиал на изоляцию не влияет).
- **HTTP-запрос** — только из сессии. Вход и выбор клиники (`iam.internal.AccountContexts`, T-IAM-02) после проверки
  членства пишут `TenantSession.select(session, tenant)` (атрибуты `dentify.tenant.clinicId`/`branchId`, только UUID).
  `TenantSession` — в named interface `platform :: identity`, открытом только iam: из другого модуля вызов роняет
  `ApplicationModules.verify()` («Модули и события»);
  `platform.tenant.TenantContextFilter` (порядок 0 — после Spring Session и Spring Security) ставит контекст на весь
  запрос, включая `@RestControllerAdvice`, и снимает в `finally`. Нет клиники в сессии — контекста нет, обращение к
  данным клиники даёт 401 `error.clinic-not-selected`.
- **Другие потоки** — Micrometer Context Propagation: `spring.task.execution.propagate-context: true` вешает
  `ContextPropagatingTaskDecorator` Spring на исполнитель и планировщик Spring Boot, а `TenantThreadLocalAccessor`
  (`META-INF/services/io.micrometer.context.ThreadLocalAccessor`) переносит клинику вместе с traceId. Поэтому
  `@Async`, асинхронные `@EventListener` и `@ApplicationModuleListener` работают в клинике потока, который поставил
  задачу или опубликовал событие; синхронные обработчики — в том же потоке. `@EnableAsync` включает платформа
  (`TenantConfiguration`). После задачи поток пула возвращается к прежнему (пустому) контексту.
- **События клиники**: событие-record с `clinicId` реализует `TenantEvent`. Обработчик такого события (любой
  `@EventListener`, в том числе `@TransactionalEventListener` и `@ApplicationModuleListener`, в классах `kz.dentify`)
  выполняется в клинике события (`TenantEventListenerInterceptor`, **до** транзакции обработчика):
  - контекст той же клиники — как есть; другой клиники — `IllegalStateException`;
  - контекста нет и транзакции в потоке нет (асинхронный обработчик, переотправка журнала публикаций после рестарта,
    синхронный обработчик события, опубликованного вне транзакции) — ставится клиника события;
  - контекста нет, но событие опубликовано **в транзакции без клиники**, а обработчик синхронный: `@EventListener`
    внутри этой транзакции или `@TransactionalEventListener` после коммита, когда её сессия Hibernate и соединение
    ещё привязаны к потоку (документация Spring, `TransactionSynchronization#afterCompletion`: данные там идут через
    прежнюю транзакцию, для своей работы — `REQUIRES_NEW`). Клиника события ставится, только если обработчик
    открывает свою транзакцию: `@ApplicationModuleListener` или `@Transactional(propagation = REQUIRES_NEW)` (либо
    `NOT_SUPPORTED`). Иначе — `IllegalStateException`: у `@EventListener` она уходит публикующему и откатывает его
    транзакцию, после коммита Spring пишет её в лог ERROR и не пробрасывает — обработчик не выполнится, публикация в
    журнале останется незавершённой и уйдёт на переотправку (T-PLT-14).
- **Задачи по расписанию** контекста не получают: задача сама выбирает клинику, транзакция — внутри `run`:

  ```java
  @Scheduled(cron = "0 0 3 * * *")
  void remindAll() {
      for (UUID clinicId : this.clinics.activeIds()) {                         // глобальный список клиник
          TenantContext.run(Tenant.of(clinicId), this.reminders::sendForToday); // @Transactional внутри
      }
  }
  ```

- Клиника соединения с БД и сессии Hibernate фиксируется, когда они открываются, поэтому сменить клинику нельзя, пока
  они привязаны к потоку: `TenantContext.run` бросит `IllegalStateException`, если открыта транзакция, идёт
  синхронизация транзакции Spring (область `@Transactional(propagation = SUPPORTS)` без настоящей транзакции, фаза
  после коммита) или к потоку привязан ресурс транзакции — `EntityManagerHolder`, `ConnectionHolder`
  (`TenantContextHolder.pinnedByTransaction`). Иначе контекст и MDC показывали бы одну клинику, а запросы шли бы через
  сессию другой (`TenantEntityTests`).
- В каждой строке JSON-лога внутри контекста — ключ MDC `clinicId` (идентификатор, не ПД).

### Сущности

```java
@Entity
@Table(name = "chair")
class Chair extends TenantEntity {        // id, clinic_id, created/updated at/by, version — из базового класса
    private String name;
    ...
}

Chair rename(UUID id, String name, long version) {                                // сервис, @Transactional
    Chair chair = this.chairs.findById(id).orElseThrow(NotFoundException::new);  // чужая клиника — 404
    chair.checkVersion(version);                                                  // версия клиента устарела — 409
    chair.rename(name);
    return chair;
}
```

- `TenantEntity` — данные клиники: `clinic_id` с `@TenantId`, без сеттера и `updatable = false`; клиника из тела
  запроса в сущность не попадает, перенести объект в другую клинику нельзя. `GlobalEntity` — глобальная таблица
  без `clinic_id` (аккаунт, МКБ-10, реестр клиник), это и есть явная пометка. Общие колонки — в `BaseEntity`.
- **id — UUIDv7 от Hibernate: `@UuidGenerator(style = VERSION_7)`** (решение 2026-09-25). Hibernate 7.4 генерирует
  v7 сам (монотонно внутри миллисекунды): id известен до INSERT, не нужен `RETURNING`, `equals` по id работает сразу
  после `persist`. У колонки остаётся `DEFAULT uuidv7()` — для вставок SQL в миграциях. Стиль `VERSION_7` в Hibernate
  7.4 помечен `@Incubating`; если его поменяют — запасной путь: `DEFAULT uuidv7()` в БД и `@Generated` на id.
- Аудит — Spring Data JPA (`@EnableJpaAuditing` в `platform.persistence`): время — `Instant` с точностью до
  микросекунд (как `timestamptz`), автор — id аккаунта из `CurrentAccountProvider` (`platform.api`; реализация —
  `platform.security.SecurityCurrentAccountProvider` по контексту Spring Security, раздел «Безопасность и сессии»; без
  входа и в фоновой задаче `created_by`/`updated_by` пустые).
- `@Version`: Hibernate сверяет версию только с той, что загрузил сам. Версию, которую видел клиент, сервис
  проверяет `checkVersion(expected)` до изменения.
- **Списки без N+1** (`spring.jpa.properties`): `hibernate.default_batch_fetch_size: 50` — ленивые связи и коллекции
  строк списка грузятся пачками до 50 id одним запросом, а не по одному; `hibernate.query.fail_on_pagination_over_collection_fetch:
  true` — страница с fetch коллекции (`join fetch`, `@EntityGraph` по коллекции) падает, а не листается в памяти по всем
  строкам с WARN HHH90003004. Страница с коллекцией — сначала страница id, потом сущности по ним (или пачки).
- **Пул соединений** — явно: `spring.datasource.hikari.maximum-pool-size: 10`, `connection-timeout: 5000` мс. Виртуальные
  потоки не ограничивают число одновременных запросов, предел работы с БД — сам пул; не дождались соединения — ошибка
  сразу. Размер — по замеру (T-PLT-24) и пределу соединений managed PostgreSQL. Всё, что держит соединение без работы с
  БД (хэш пароля, ожидание Valkey), выносится из транзакции («Пароли», «Кэш»).

### Hibernate `@TenantId`

- `ClinicTenantIdentifierResolver` (настройка `hibernate.tenant_identifier_resolver`) отдаёт клинику контекста, а без
  контекста — нулевой UUID `NO_CLINIC`: ни одна строка ему не соответствует. `null` вернуть нельзя: Hibernate не
  откроет сессию вовсе, в том числе для глобальных сущностей и проверки запросов при старте. Корневого арендатора,
  который видит все клиники, нет (`isRoot` — всегда false).
- Фильтр `_tenantId` применяется к JPQL/Criteria, `find`/`getReference` и связям, **но не к нативному SQL** —
  там работает только RLS, и ArchUnit требует `clinic_id` в тексте нативного запроса репозитория данных клиники.

### RLS и соединения

- Пул приложения обёрнут в `platform.persistence.ClinicConnectionDataSource` (бин `dataSource`, через
  `BeanPostProcessor`; JPA, `JdbcTemplate` и менеджер транзакций получают одну обёртку). При **каждой выдаче
  соединения** она выполняет `select set_config('app.clinic_id', <клиника или ''>, false)`.
- Почему при выдаче соединения, а не `set_config(…, true)` в начале транзакции (так было в плане): к данным ходят и
  без транзакции — методы запросов репозиториев Spring Data по умолчанию нетранзакционные (документация Spring Data
  JPA, «Transactional query methods»), `JdbcTemplate` вне `@Transactional`; транзакционная настройка при
  автокоммите исчезает сразу. Сессионная настройка перезаписывается при каждой выдаче, поэтому значение прежнего
  владельца соединения следующий не увидит, как бы тот ни завершил работу (приёмка PLT-02, проверяет
  `ClinicConnectionDataSourceTests` на пуле из одного соединения). Транзакция держит одно соединение, клиника в ней —
  на момент открытия. Цена — один запрос на выдачу соединения. Решение записано и в бэклоге (PLT-02, T-PLT-03).
- **Открыто (приёмка PLT-02):** «p95 ключевых эндпоинтов с RLS растёт не больше чем на 10%» в T-PLT-03 не измерено —
  ключевых эндпоинтов ещё нет. Нетранзакционный метод запроса репозитория берёт соединение на каждый вызов, то есть
  это +1 запрос `set_config` на вызов. Замер p95 с обёрткой и RLS и без них — T-PLT-24 на нагрузочном сценарии
  (расписание, пациенты); если бюджет не сойдётся — выносить чтения в транзакции сервиса, а не убирать обёртку.
- Вернувшееся в пул соединение хранит `app.clinic_id` последнего владельца (настройка сессионная; сброс при возврате
  стоил бы ещё одного запроса на каждое соединение). Поэтому соединения — **только через бин `DataSource`
  приложения**: ArchUnit-правило `TenancyRulesTests.connectionsOnlyThroughClinicWrapper` запрещает вне
  `platform.persistence` классы Hikari (`com.zaxxer.hikari..`), `DataSourceUnwrapper` Spring Boot,
  `DataSource.unwrap(…)` и `DelegatingDataSource.getTargetDataSource()`. Сторонняя библиотека (ShedLock, второй
  `JdbcTemplate`) получает бин `dataSource`, то есть обёртку. Метрики и health Spring Boot разворачивают пул только для
  чтения его настроек и соединений из него не берут.
- Flyway ходит своим источником (роль `dentify_owner`, URL пула) без обёртки и не из пула приложения — миграции идут
  без `app.clinic_id`, отсюда рецепт «Миграции с данными при FORCE RLS».

### Репозитории

- Репозиторий сущности `TenantEntity` без контекста клиники бросает `MissingTenantContextException` до транзакции
  (`TenantRepositoryGuard`, `RepositoryProxyPostProcessor` Spring Data). Так фоновая задача или обработчик, забывшие
  клинику, падают, а не получают молча пусто (приёмка PLT-01).
- В обход репозитория (`EntityManager`, `JdbcTemplate`) без контекста ошибки нет, но и данных клиник нет (фильтр
  `NO_CLINIC` и RLS). Данные клиники читают и пишут через репозитории.
- Нативный запрос (`@NativeQuery`, `@Query(nativeQuery = true)`) в репозитории данных клиники содержит условие по
  `clinic_id`, например `where clinic_id = current_clinic_id()`.

### Конфликт версии (PLT-08)

Устаревшая версия — 409 `error.version-conflict` (код платформы по соглашению `error.*` из T-PLT-07; бэклог
переведён на этот код, прежнее имя в плане — `conflict.version`) с расширениями ProblemDetail: `modifiedAt` — время последней правки, `modifiedBy` — id
аккаунта автора, `modifiedByName` — имя автора в клинике для плашки «Динара Алиева изменила эти данные в 11:38».
Их читает `LastModificationLookup` в отдельной транзакции (исходная уже откатилась) в контексте клиники запроса.
Имя даёт точка расширения `AccountNameProvider` (`platform.api`): цепочка реализаций по `@Order`, чего не знает первый,
спрашивается у следующего (`AccountNameProvider.resolve`), пачкой — `displayNames(ids)`. Сейчас реализация одна —
`iam.internal.AccountDisplayNames`: ФИО аккаунта с членством (любым, в том числе отключённым) в клинике контекста, одним
запросом на пачку; имя аккаунта без членства в клинике не отдаётся. Имя из карточки сотрудника (T-IAM-26) встанет
раньше неё. Имени нет — нет `modifiedByName`, без автора — нет и `modifiedBy`; ошибка чтения имени — 409 без имени, а не
500. Из параллельных обновлений одной строки успешно ровно одно
(`VersionConflictTests`). «Сохранить поверх» и событие аудита о перезаписи — задачи модулей и аудита.

### Кэш

- Spring Cache в Valkey (`spring.cache.type: redis`, `@EnableCaching` в `platform.cache`, время жизни по умолчанию
  `spring.cache.redis.time-to-live: 1h`, значения — сериализация Spring Data Redis по умолчанию, то есть
  `Serializable`).
- Ключ кэша данных клиники — `<clinicId>::<кэш>::<ключ>` (`TenantCacheKeyPrefix`): одинаковый ключ двух клиник —
  две записи, `@CacheEvict(allEntries = true)` чистит только свою клинику, без контекста —
  `MissingTenantContextException`. Глобальный кэш (справочник МКБ-10) — имя с приставкой `global.`:
  `@Cacheable("global.icd10")`, ключ `global.icd10::<ключ>`, контекст не нужен.
- Префикс безусловный: `TenantCacheConfiguration.tenantCacheKeys` (`RedisCacheManagerBuilderCustomizer` с
  `@Order(LOWEST_PRECEDENCE)`) ставит его конфигурации по умолчанию (кэши, создаваемые при первом обращении) и всем
  кэшам, настроенным к его запуску (`spring.cache.cache-names`, настройщики модулей). Порядок настройщиков это не
  гарантирует: у настройщика без `@Order` тот же порядок, и между ними решает порядок регистрации бинов. Гарантия —
  `TenantCacheManagerCheck` (`BeanPostProcessor`): после сборки менеджера кэшей он роняет старт, если у конфигурации
  по умолчанию или у любого кэша префикс выключен или не `TenantCacheKeyPrefix`, а также если `CacheManager` — не
  `RedisCacheManager` (свой менеджер в памяти хранил бы ключи клиник вместе). Проверяют `TenantCacheConfigurationTests`
  (настройщики модулей до и после платформенного) и `TenantCacheTests.everyCacheHasClinicPrefix`.
- Свой срок жизни кэша модуль задаёт от конфигурации по умолчанию — тогда префикс есть при любом порядке:

  ```java
  @Bean
  RedisCacheManagerBuilderCustomizer scheduleCaches() {
      return (builder) -> builder.withCacheConfiguration("schedule-slots",
              builder.cacheDefaults().entryTtl(Duration.ofMinutes(5)));
  }
  ```

  Конфигурация от `RedisCacheConfiguration.defaultCacheConfig()`, `disableKeyPrefix()`, `prefixCacheNameWith(…)` или
  замена `builder.cacheDefaults(…)` после платформы роняют старт. `spring.cache.redis.key-prefix` и `use-key-prefix`
  не действуют.
- Префикс вычисляется в потоке, который обращается к кэшу, поэтому `@Cacheable` у методов с `CompletableFuture`
  для данных клиники не используем.
- Spring Data Redis 4 с Lettuce пишет в кэш асинхронно: ключ появляется в Valkey не сразу после `put` (сам ключ с
  префиксом вычислен синхронно). Тесты, которые смотрят ключи в Valkey, ждут их (`TenantCacheTests.keys`); повторный
  вызов `@Cacheable`, который должен взять значение из кэша, — только после того, как ключ первого дошёл
  (`TenantCacheTests.awaitKey`), иначе метод изредка вычисляется второй раз.
- Таймауты Valkey — на всю платформу (кэш, сессии и отметки их отзыва, лимиты, окно просмотров журнала): `spring.data.redis.timeout` и
  `connect-timeout` — 3 с. По умолчанию Lettuce ждёт команду 60 с, и запрос с открытой транзакцией БД держал бы соединение
  пула минуту. Команды Valkey идут миллисекунды; не дождались — `QueryTimeoutException` (`DataAccessException`) сразу.

### Тесты изоляции

| Тест | Что проверяет |
|---|---|
| `platform.tenancy.TenantIsolationTests` | харнесс: все эндпоинты из `RequestMappingHandlerMapping`; каждый — в реестре `NOT_CLINIC_DATA` с причиной (путь эндпоинта целиком; с `/**` на конце — всё под ним, только для входа и проб тестов; путь аккаунта `/api/v1/me`, `/api/v1/account/sessions` — только целиком, новый эндпоинт под ним в тест попадает сам) или проверен ресурсом клиники из `RESOURCES`: коллекция — только GET (список) и POST (создание), путь с переменными — по id объектов клиники B, остальное под ресурсом (массовые действия с id в теле или параметрах) — только если описано в `notById` с местом проверки, иначе тест падает; каждой переменной пути — свой объект клиники B (родитель, потомок, связанный), нет значения — тест падает; владелец по GET видит объект (2xx), клиника A по любому методу — 404, объект не изменился; списки без чужих объектов; `clinicId` в теле игнорируется |
| `platform.tenancy.RowLevelSecurityTests` | каталог `pg_class`/`pg_policies`: RLS, FORCE и стандартная политика у каждой таблицы с `clinic_id`; реестр глобальных таблиц `GLOBAL_TABLES`; корень арендатора `clinic` (`TENANT_ROOT_TABLES`) — RLS, FORCE и одна политика по `id`; политики сверх стандартной — только из `READ_POLICIES` и только `FOR SELECT`; роль приложения без `app.clinic_id` — 0 строк; встраивание `current_clinic_id()` |
| `clinic.ClinicTablesTests` | клиника видит только себя и свои филиалы, без контекста — ничего; чужую клинику и филиал не изменить и не вставить (42501); филиал без клиники — 23503 |
| `platform.persistence.ClinicConnectionDataSourceTests` | клиника не переходит через пул: разные клиники подряд в одном соединении |
| `platform.tenancy.TenantPropagationTests` | `@Async`, `@EventListener`, `@ApplicationModuleListener`, `TenantEvent` (в том числе синхронные обработчики после коммита транзакции без клиники: со своей транзакцией — клиника события, без неё — не выполняется), планировщик; без контекста — ошибка, в том числе у обработчиков событий без `TenantEvent`, которые читают данные клиники |
| `platform.tenancy.TenantEntityTests`, `VersionConflictTests`, `TenantCacheTests` | UUIDv7, аудит, фильтр Hibernate и RLS для запросов без транзакции; смена клиники запрещена и в области `SUPPORTS` без транзакции; 409 с автором; ключи кэша, префикс клиники у всех кэшей |
| `platform.cache.TenantCacheConfigurationTests` | автоконфигурация кэша Spring Boot без Valkey: префикс клиники при любом порядке настройщиков, старт падает на кэше без префикса и на чужом `CacheManager` |
| `TenancyRulesTests` (ArchUnit) | сущность — `TenantEntity` или `GlobalEntity`, глобальные — только в platform, iam, clinic, medical; в DTO-records веб-слоя нет `clinicId`/`clinic_id`; нативный SQL репозитория данных клиники фильтрует по `clinic_id`; пул Hikari не разворачивается в обход обёртки. Что правила ловят нарушения — на фикстурах `archfixtures.tenancy` |

Новый ресурс модуля — строка в `TenantIsolationTests.RESOURCES`: путь коллекции (у вложенного — с переменными
родителя: `/patients/{patientId}/visits`), переменная id объекта и как создать через API объект и всё, что нужно его
путям, в клинике сессии, с id для каждой переменной путей под ресурсом. Эндпоинт под ресурсом без переменных пути —
в `notById` с местом проверки. Чужой id в теле запроса (запись с пациентом клиники B) харнесс не подставляет: такой
DTO проверяет интеграционный тест модуля — 404 на ссылку на объект другой клиники. Эндпоинт без данных клиники — в
`NOT_CLINIC_DATA` путём целиком; `/**` (всё под путём) — только там, где данных клиники не бывает вовсе (`/api/v1/auth/**`,
пробы тестов). Новая глобальная таблица —
`RowLevelSecurityTests.GLOBAL_TABLES` и, если модуль новый, `TenancyRulesTests.GLOBAL_ENTITY_MODULES`, с причиной.
Вторая политика на таблицу клиники — только `FOR SELECT`, в `RowLevelSecurityTests.READ_POLICIES` с причиной и в этом
документе.

Запрещено:

- брать клинику из тела, параметров или заголовков запроса; поле `clinicId`/`clinic_id` в DTO веб-слоя. Единственное
  исключение — выбор клиники сессии `POST /api/v1/me/context` (`iam.web.SelectContextRequest`): клиника проверяется по
  своим активным членствам аккаунта; реестр таких DTO — `TenancyRulesTests.CLINIC_SELECTION_DTOS` с причиной;
- сеттер `clinic_id`, свой `@TenantId` вне `TenantEntity`, отключать фильтр `_tenantId` (`Session#disableFilter`);
- сущность данных клиники от `GlobalEntity`; таблицу с `clinic_id` без ENABLE + FORCE RLS и стандартной политики
  в той же миграции; вторую разрешающую политику не `FOR SELECT` или вне `READ_POLICIES`;
- нативный SQL к таблице клиники без условия по `clinic_id`; работать с данными клиники в обход репозиториев без
  нужды;
- менять клинику внутри открытой транзакции или области `SUPPORTS`; трогать `platform.tenant`/`platform.persistence`
  из модулей (Modulith не даст) — только `TenantContext`;
- синхронный обработчик события клиники без своей транзакции, если событие публикуют в транзакции без клиники;
- брать соединения из пула Hikari в обход бина `DataSource` (`unwrap`, `DataSourceUnwrapper`, `HikariDataSource`);
- роль приложения с `BYPASSRLS`, «корневой» арендатор. Межклиничные задачи (ретеншн, сверки) — отдельной ролью БД с
  записанным обоснованием (PLT-02, по мере появления);
- кэш данных клиники с именем `global.*`; конфигурация кэша не от `builder.cacheDefaults()`, выключенный или свой
  префикс ключа, свой `CacheManager`.

## Безопасность и сессии

Вход — только серверная сессия в cookie (PLT-04), пароли, одноразовые ссылки и лимиты (PLT-05), заголовки безопасности
(PLT-06). JWT и токенов в `localStorage` нет. Код — внутренний пакет `platform.security`; всем модулям открыты из
`platform.api` `PasswordPolicy`, `CurrentAccountProvider`, `IpAddresses` и бин Spring Security `PasswordEncoder`, только
iam — из `platform.identity` `AccountSignIn`, `AccountSessions`, `RateLimits` и `TenantSession` («Модули и события»).
REST активных сессий — `iam.web.AccountSessionController` (правила — `iam.internal.AccountSessionCommands`), токены
ссылок — `iam.internal.OneTimeTokens`.

### Цепочки Spring Security

| Цепочка | Что берёт | Правило |
|---|---|---|
| `managementSecurity` (порядок 0) | actuator на порту управления 8081 (`EndpointRequest.toAnyEndpoint()`) | без входа: порт внутренний, наружу не публикуется. Boot вешает ту же `springSecurityFilterChain` и на сервер управления, поэтому своя цепочка обязательна |
| `apiSecurity` (порядок 100, последняя) | всё остальное, то есть порт 8080 | без входа — только `/api/v1/auth/**` (вход, выход, CSRF-токен, сброс пароля), `/api/v1/public/**` (регистрация клиники, города, приглашение по ссылке; «Соглашения API и OpenAPI»), `/error` и, где springdoc включён (dev, test), `/v3/api-docs/**` и Swagger UI; всё прочее — `authenticated()` |

- Отказы — ProblemDetail без редиректов, без `WWW-Authenticate`, без форм входа, HTTP Basic, стандартного `/logout` и
  сохранения запроса до входа (`NullRequestCache`): `ProblemSecurityHandler` отдаёт исключение платформы в
  `HandlerExceptionResolver`, его обрабатывает общий `@RestControllerAdvice`.

  | Ответ | Когда | `code` |
  |---|---|---|
  | 401 | нет входа | `error.unauthorized` |
  | 401 | cookie сессии есть, а сессии нет: 12 ч бездействия, 30 дней от входа, выход, завершена со списка, деактивацией или сменой пароля | `error.session-expired` |
  | 403 | изменяющий запрос без заголовка `X-XSRF-TOKEN` или с чужим | `error.csrf` |
  | 403 | `@PreAuthorize` не пропустил | `error.forbidden` |
  | 429 | лимит API, входа или сброса пароля, заголовок `Retry-After` | `error.too-many-requests`, `error.too-many-login-attempts` |

- Что путь существует, знает только вошедший: неизвестный путь без входа — 401, с входом — 404 (под открытыми
  `/api/v1/auth/**` и `/api/v1/public/**` — 404 и без входа). 404 неизвестного пути в лог не пишется
  (`logging.level.org.springframework.web.servlet.PageNotFound: error`): иначе вошедший клиент засыпал бы лог WARN «No
  mapping for …» до предела общего лимита API.
- `@EnableMethodSecurity`: у каждого эндпоинта `@PreAuthorize` (`isAuthenticated()`, у открытых — `permitAll()`, права
  по клинике — T-IAM) на методе или на классе контроллера. Проверяет ArchUnit-правило
  `CodingRulesTests.endpointsHavePreAuthorize`: метод с `@RequestMapping`/`@GetMapping`/… в контроллере без
  `@PreAuthorize` роняет сборку; исключения — `CONTROLLERS_WITHOUT_PRE_AUTHORIZE` с причиной (сейчас только
  `ProblemErrorController`). Отказ в методе: без входа — 401, с входом — 403.
- Язык ответов из фильтров — `LocaleContextFilter` сразу после загрузки контекста безопасности, тем же
  `DentifyLocaleResolver`, что у DispatcherServlet (язык аккаунта известен только после входа).
- CORS выключен: SPA и API на одном origin (Caddy: `/api` → бэкенд, остальное → SPA). `@CrossOrigin` не использовать.

### Сессия

- Spring Session Data Redis, **индексированное** хранилище (`spring.session.data.redis.repository-type: indexed`,
  `RedisIndexedSessionRepository`), ключи `dentify:session:*`. Индекс — по имени вошедшего, то есть по id аккаунта: на
  нём список сессий и «завершить все». Для индекса Spring Session при старте включает в Valkey уведомления о ключах
  (`CONFIG SET notify-keyspace-events`); в managed Valkey `CONFIG` закрыт, поэтому в prod `configure-action: none`, а
  `notify-keyspace-events Egx` задаётся в настройках кластера (T-PLT-25).
- Cookie `DENTIFY_SESSION` (`server.servlet.session.cookie.*`): `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`; значение —
  id сессии в base64. **Secure — всегда**, и в dev за HTTP: браузеры считают `localhost` безопасным контекстом и
  принимают Secure-cookie на `http://localhost` (прокси Vite `localhost:5173` → 8080 — тот же host); прод-схему по
  HTTPS проверяет профиль Caddy `deploy/dev.ps1 up -Proxy` (https://localhost:8443). Флаги cookie проверяют тесты по
  заголовку `Set-Cookie` (`SessionSecurityTests`); вручную — в браузере на `localhost` или через Caddy: curl к
  `127.0.0.1:8080` Secure-cookie обратно не шлёт, ей нужно передавать `Cookie` явно.
- Срок сессии (`SessionLifetime`) — два предела: бездействие и срок от входа, который активность не продлевает.

  | Вход | Cookie | Бездействие | Срок от входа |
  |---|---|---|---|
  | без «Запомнить» | сессионная, до закрытия браузера | 12 ч (`server.servlet.session.timeout`) | 30 дней (`dentify.security.session-max-age`) |
  | с «Запомнить» | `Max-Age` 30 дней от входа (`SessionCookieSerializer`) | нет | 30 дней (`dentify.security.remember-me`) |

  Срок от входа продлевает только новый вход. Spring Session гасит сессию лишь по бездействию, поэтому срок от входа
  держит `SessionLifetimeFilter` в цепочке API (после записи заголовков и проверки отзыва): на каждом запросе с вошедшей
  сессией сравнивает `dentify.client.signedInAt` со сроком; истёк — `invalidate()`, контекст безопасности снимается,
  запрос идёт без входа: закрытый путь — 401 `error.session-expired`, вход и выдача CSRF-токена работают, ответ стирает
  cookie. Иначе держит бездействие сессии в Valkey не длиннее остатка до срока от входа, и ключ сессии истекает не позже
  него (сессия уходит из индекса, даже если с неё больше не придут). Без этого украденная cookie с «Запомнить» жила бы
  бессрочно. Предел без «Запомнить» — решение 2026-09-25: сессия без галочки не живёт дольше, чем с ней; ежедневный вход
  на общем компьютере регистратуры не навязываем. Cookie пишется только при входе (стандартный сериализатор Spring
  Session при «Запомнить» писал бы её навсегда).
- **Бездействие пишется не на каждом запросе** (`SessionLifetimeFilter.nextMaxInactive`). Каждая запись — изменение
  сессии, которое конец запроса отправляет в Valkey, в том числе поверх уже завершённой сессии (раздел «Активные
  сессии»). Записанное бездействие остаётся, пока оно не длиннее нужного (остатка до срока или 12 ч) и короче не больше
  чем на 2 минуты; иначе пишется нужное без минуты запаса. С «Запомнить» остаток убывает каждую секунду, а запись — раз
  в минуту; без «Запомнить» бездействие — 12 ч до последних 12 ч срока, и запись идёт только в них. В последнюю минуту —
  ровно остаток (`SessionLifetimeFilterTests`).
- Запросы без входа (401, выдача CSRF-токена) сессию в Valkey не создают.
- В сессии только классы JDK и Spring: контекст безопасности (`UsernamePasswordAuthenticationToken`, имя — id аккаунта,
  без пароля и без прав — «Права»), клиника (`dentify.tenant.*`, раздел «Тенант-контекст»), язык аккаунта
  (`dentify.account.language`, строка `ru`/`kk`, «Сообщения и i18n») и откуда вошли (`dentify.client.ip`, `userAgent`,
  `signedInAt`, `rememberMe`). Классы приложения в сессию не класть: сессии в Valkey переживают деплой.

Вход и выход — `AccountSignIn` (реализация `SessionAccountSignIn`). Пароль, блокировки, журнал входов и клинику сессии
проверяет iam — `POST /api/v1/auth/login` (`iam.internal.SignIns`, раздел «Вход, выход и /api/v1/me»), затем вызывает
`signIn`. `signIn` меняет id сессии (защита от фиксации), снимает клинику, выбранную в сессии до входа (иначе она досталась бы
новому аккаунту без проверки членства), — и с сессии, и с потока запроса входа: `TenantContextFilter` поставил её из
прежней сессии, и до конца запроса событие аудита или обработчик входа писали бы в ту клинику (внутри транзакции —
`IllegalStateException`, вход идёт вне транзакции), — меняет CSRF-токен (новая cookie `XSRF-TOKEN` в том же ответе), сохраняет
контекст безопасности в сессию и записывает клиента. `signOut` отзывает сессию (отметка в Valkey, «Активные сессии»),
уничтожает её, стирает cookie сессии и CSRF-токен.

### Активные сессии

- `AccountSessions` (`platform :: identity`, только iam): `list(accountId, currentSessionId)`, `terminate(accountId, id)`,
  `terminateAll(accountId)`, `terminateOthers(accountId, currentSessionId)`, `terminateInClinic(accountId, clinicId)`.
  Отозванную сессию и сессию с истёкшим сроком от входа `list` не показывает и удаляет из индекса. Завершение сразу: следующий запрос с cookie завершённой сессии с любого устройства —
  401 `error.session-expired`. Так деактивация и смена пароля (iam) гасят сессии аккаунта быстрее, чем за 5 секунд
  (`AccountSessionsTests`). Сессия текущего запроса завершается как выход, `invalidate()`, и ответ стирает её cookie.
- **Завершение необратимо** — выход, завершение одной, всех и всех, кроме текущей (`SessionRevocations`,
  `SessionRevocationFilter`). `deleteById` Spring Session (его вызывают и `invalidate()`, и завершение со списка) сессию
  из Valkey не удаляет: он ставит ей бездействие 0 и убирает её из индекса. Запрос этой сессии, который шёл в тот момент,
  в конце записывает свои изменения поверх: записанное бездействие оживило бы сессию, контекст безопасности (смена
  клиники с новыми правами) вернул бы её в индекс. Поэтому **до** удаления в Valkey пишется отметка отзыва:

  | Отметка | Ключ и значение | Когда | Живёт |
  |---|---|---|---|
  | сессии | `dentify:session-revoked:session:<SHA-256 id>` = `1` | выход, завершение одной; каждая сессия из индекса при завершении всех | до срока сессии от входа + 1 мин |
  | аккаунта | `dentify:session-revoked:account:<id аккаунта>` = `<T> [<SHA-256 оставленной>]` | завершение всех (T — момент вызова по часам Valkey) и всех, кроме текущей | самый долгий срок сессии (30 дней) + 1 мин |

  Отметка аккаунта гасит любую сессию аккаунта со входом не позже T, кроме оставленной, — и ту, которой в индексе нет:
  сессию, которую оживил запрос в полёте до этой защиты, и вход, шедший одновременно с завершением, **если его время
  входа не позже T** (вход позже T она не гасит — ниже, «Не закрыто платформой»). Отметки сессии нужны ещё и для сессий
  со входом позже T, найденных в индексе. Новое завершение всех заменяет отметку аккаунта. Сроки отметок достаточны:
  после срока от входа сессию гасит `SessionLifetimeFilter`, а время входа меняет только новый вход, который меняет и id.
- **Сессии одной клиники** — `terminateInClinic(accountId, clinicId)`: отключение членства в клинике (T-IAM-11). Гаснут
  сессии индекса аккаунта, в которых выбрана эта клиника (`dentify.tenant.clinicId`), — каждая отметкой отзыва и
  удалением, как `terminate`; работа того же человека в других клиниках и сессии без клиники остаются. Отметки аккаунта
  здесь нет, поэтому сессия, в которой клинику выбирают одновременно с отключением (выбор прочитал членство до коммита,
  а записал клинику после поиска), этим вызовом не гаснет. Это окно закрывает перепроверка активного членства на каждом
  запросе через кэш эффективных прав (T-IAM-07, «Права»): нет активного членства в клинике сессии — 401
  `error.clinic-not-selected`. До неё iam вызывает `terminateInClinic` после коммита отключения, как `terminateAll`
  (`AccountSessionsTests.serviceTerminatesOnlySessionsOfOneClinic`). Прямой `FindByIndexNameSessionRepository` и
  `deleteById` из iam запрещены: без отметки отзыва запрос в полёте оживит сессию, а `platform.security` — внутренний пакет.
- **Одни часы — Valkey.** T отметки аккаунта и время входа в сессию (`dentify.client.signedInAt`) берутся командой `TIME`
  Valkey (`SessionRevocations.now()`), а не с часов экземпляра: вход на одном экземпляре и завершение на другом
  сравниваются без расхождения их часов (`SessionRevocationTests.signInTimeAndAccountMarkUseValkeyClock`). Цена — одна
  команда Valkey на вход и на завершение всех. Срок от входа `SessionLifetimeFilter` считает часами экземпляра:
  расхождение в секунды для 12 часов и 30 дней не важно.
- **Не закрыто платформой: вход одновременно с деактивацией или сменой пароля — закрывает iam.** Пункт 2 ниже сделан во
  входе T-IAM-02 (`SignIns`, раздел «Вход, выход и /api/v1/me»), пункт 1 — обязанность задач деактивации, смены и сброса
  пароля (T-IAM-11, T-IAM-16, T-IAM-24). Вход, у
  которого пароль и статус аккаунта прочитаны до коммита деактивации или смены пароля, а время входа поставлено позже T
  (между ними — проверка Argon2, десятки миллисекунд), отметку аккаунта переживает, а в индекс попадает только в конце
  своего запроса — возможно, после поиска сессий в `terminateAll`. Платформа этого не видит: статус аккаунта и версия
  пароля — в БД iam. Поэтому iam обязан:
  1. вызывать `terminateAll`/`terminateOthers` **после коммита** деактивации, смены и сброса пароля
     (`@TransactionalEventListener(phase = AFTER_COMMIT)`, `@ApplicationModuleListener` или после выхода из транзакционного
     метода), а не внутри его транзакции: иначе вход, прочитавший ещё не изменённый аккаунт уже после T, получит время
     входа позже T;
  2. после `signIn` перечитать аккаунт **новым запросом к БД** (не из сессии Hibernate той же транзакции) — статус и
     версию учётных данных (`credentials_changed_at` или счётчик, который меняют смена и сброс пароля и деактивация).
     Изменились с тех, что проверил вход, — `signOut` и 401, как при неверном пароле. Сделано так: версия учётных
     данных — сам хэш пароля (смена и сброс пишут новый хэш с новой солью, отдельная колонка не нужна), плюс статус
     аккаунта и статусы членств (`Accounts.credentials`, `AccountMemberships.of` после `signIn`).

  Этого достаточно: если коммит был до перечитывания, вход его увидит и выйдет сам; если после — время входа (Valkey)
  раньше перечитывания, перечитывание раньше коммита, коммит раньше T (Valkey), и отметка аккаунта гасит вход. Проверка —
  `iam.SignInTests` (`deactivationCommittedDuringLoginSignsOut`, `passwordChangeCommittedDuringLoginSignsOut`,
  `membershipDisabledDuringLoginSignsOut`): деактивация, смена пароля и отключение членства коммитятся между проверкой
  пароля и `signIn` (тестовая обёртка `iam.SignInInterlude`).
- **Неполный hash сессии — сессии нет** (`SafeRedisSessionMapper`: бин `SessionRepositoryCustomizer<RedisIndexedSessionRepository>`
  в `SecurityConfiguration`, официальный рецепт Spring Session, reference «Configuring Redis Session Mapper»). После
  `deleteById` hash сессии живёт ещё 5 минут. Запрос в полёте, который закончился позже, пишет свои изменения командой
  `HSET` на пустое место: получается hash без `creationTime`, а контекст безопасности в изменениях возвращает id в индекс
  аккаунта. Стандартный `RedisSessionMapper` на таком hash бросает `IllegalStateException`: запрос с этой cookie, список
  сессий аккаунта, завершение всех и всех, кроме текущей (деактивация и смена пароля), отвечали бы 500, пока hash не
  истечёт, — с «Запомнить» до 30 дней. Обёртка при `IllegalStateException` удаляет hash, теневой ключ срока
  `sessions:expires:<id>` и id из индекса аккаунта (прямыми командами: `deleteById` читает сессию через этот же разбор) и
  возвращает null — сессии нет: запрос с cookie — 401 `error.session-expired`, поиск по индексу её пропускает, в лог INFO
  «Неполная сессия аккаунта … удалена». Живую сессию это не задевает: полный hash пишется одной командой при создании
  сессии, неполный бывает только у истёкшего или удалённого ключа. Следом Spring Session пишет WARN «Unable to publish
  SessionDestroyedEvent for session <id>» — это его лог, id в нём у уже мёртвой сессии. Отметки отзыва сессию гасят и без
  обёртки; обёртка держит работающим API (`SessionRevocationTests.requestInFlightAfterSessionKeyExpiredLeavesNoBrokenSession`).
- `SessionRevocationFilter` в цепочке API (после заголовков, раньше `SessionLifetimeFilter`) на каждом запросе с сессией
  читает обе отметки одной командой `MGET`. Отозвана — `invalidate()` (изменения этого запроса в Valkey не пишутся,
  сессия снова уходит из индекса), контекст безопасности снимается, 401 `error.session-expired`, ответ стирает cookie, в
  лог INFO «Запрос отозванной сессии аккаунта …». Цена — одна команда Valkey на запрос с сессией; Valkey недоступен —
  запрос падает, как и загрузка самой сессии (отказ закрыт). Проверяет `SessionRevocationTests`: запрос сессии держится,
  пока другой поток её завершает, — выход, завершение одной, всех, всех кроме текущей, с «Запомнить» и без.
- REST своего аккаунта (`iam.web`): `GET /api/v1/account/sessions` — список, сначала последние по активности;
  `DELETE /api/v1/account/sessions/{id}` — завершить одну (чужая или выдуманная — 404);
  `DELETE /api/v1/account/sessions` — все, кроме текущей, `?includeCurrent=true` — все. Список короткий, без страниц.
- Наружу не уходят ни id сессии (это пароль на время сессии), ни полный IP: у сессии открытый id — 16 байт SHA-256 от
  настоящего в base64url, IP — маской `95.56.•.•` (IPv6 — первые две группы).
- Браузер и ОС — uap-java (Java-порт правил ua-parser), по User-Agent входа: `Chrome 140`, `Windows 10`.
- Город — локальная база mmdb через GeoIP2 API MaxMind, без внешних вызовов: `dentify.security.geoip.database`.
  Базы нет — город `null`, старт не падает. Бесплатная база — **DB-IP IP to City Lite** (mmdb, лицензия CC BY 4.0,
  обновляется ежемесячно): её не коммитим, скачивание — `backend/README.md`, «GeoIP-база». Атрибуция «IP Geolocation by
  DB-IP» со ссылкой на https://db-ip.com обязательна на экране, где показан город (фронт). Название города — на языке
  запроса, если оно есть в базе, иначе русское, иначе английское (в DB-IP Lite названия английские).

### CSRF

- Встроенный режим Spring Security 7 для SPA — `csrf.spa()`: `CookieCsrfTokenRepository` с cookie `XSRF-TOKEN` (не
  HttpOnly — её читает SPA; `Secure`, `SameSite=Lax`, `Path=/`) и заголовок `X-XSRF-TOKEN`. Токен загружается на каждом
  запросе через цепочку API, поэтому cookie приходит в первом же ответе; явный запрос при старте SPA —
  `GET /api/v1/auth/csrf` (204). SPA на каждый POST, PUT, PATCH, DELETE шлёт значение cookie в заголовке.
- В заголовке — сырое значение cookie; маскированный (XOR, защита от BREACH) токен лежит только в атрибуте запроса.
- CSRF проверяется раньше входа: POST без токена — 403 и без сессии. Исключение — `/error` (только отдаёт ProblemDetail).
- Вход меняет токен, выход стирает; на 403 `error.csrf` SPA перечитывает cookie и повторяет запрос.

### Пароли

- Бин `PasswordEncoder` — `DelegatingPasswordEncoder` с Argon2id по умолчанию, параметры OWASP (Password Storage Cheat
  Sheet): память 19 МиБ (`m=19456`), 2 прохода, 1 поток, соль 16 байт, хэш 32 байта. В БД —
  `{argon2}$argon2id$v=19$m=19456,t=2,p=1$…`. Хэши bcrypt, pbkdf2 и Argon2 со старыми параметрами проверяются, и
  `upgradeEncoding` для них — true: вход перехэширует пароль. Argon2 Spring Security считает через BouncyCastle
  (`bcprov-jdk18on`, версия в `pom.xml`).
- `PasswordPolicy` (`CommonPasswordPolicy`): 8–128 символов Unicode, без требований к составу; отклоняются пароли из
  10 000 самых частых (`security/common-passwords.txt` — SecLists, MIT, атрибуция рядом в
  `common-passwords.NOTICE.txt`; без учёта регистра) и совпадающие с телефоном (по цифрам, в любой записи номера) или
  e-mail (целиком или часть до `@`). Коды полей: `password.too-short`, `password.too-long`, `password.common`,
  `password.matches-phone`, `password.matches-email`. Вызов — `passwordPolicy.check("password", password, phone, email)`:
  400 `error.validation` с `errors[]`.
- **Предел одновременных хэшей.** Argon2id берёт 19 МиБ памяти на каждое вычисление, а с виртуальными потоками
  (`spring.threads.virtual.enabled`) Tomcat не ограничивает число одновременных запросов пулом из 200 потоков — только
  `max-connections` (8192). Без предела тысяча одновременных входов, сбросов и смен пароля заняла бы 19 ГиБ. Поэтому бин
  `PasswordEncoder` — `BoundedPasswordEncoder` над `DelegatingPasswordEncoder`: `encode` и `matches` берут место в
  честном `Semaphore`, одновременно считается не больше `dentify.security.password-hashing.concurrency` хэшей (по
  умолчанию — число ядер: хэш занимает ядро целиком, больше одновременных быстрее не будет; память — ядра × 19 МиБ).
  Остальные ждут не дольше `dentify.security.password-hashing.max-wait` (3 с), не дождались — 429
  `error.too-many-requests` с `Retry-After: 1`. `upgradeEncoding` хэш не считает и места не ждёт. `@ConcurrencyLimit`
  Spring Framework 7 не подошёл: он либо ждёт без предела времени (`BLOCK`), либо отказывает сразу (`REJECT`), а нужно
  ограниченное ожидание (`PasswordHashingLimitTests`).
- **Хэш — вне транзакции БД.** Argon2id — десятки миллисекунд и до 3 с ожидания места, и открытая транзакция всё это
  время держала бы соединение пула (10). Новый аккаунт: `Accounts.prepare(NewAccount)` проверяет ввод и считает хэш без
  БД, `Accounts.create(PreparedAccount)` проверяет занятость и пишет; `create(NewAccount)` — оба шага, без своей
  транзакции (запись — транзакцией репозитория). Вызывающий со своей транзакцией (регистрация клиники, приглашение)
  зовёт `prepare` до неё (`AccountHashingTests`). Смена и сброс пароля (T-IAM-16, T-IAM-24) — так же: хэш до транзакции.
- Пароль не логируется и не попадает в сообщения исключений: там только коды.

### Одноразовые токены ссылок

- `iam.internal.OneTimeTokens` — сброс пароля (1 ч) и приглашение (7 дней), `TokenPurpose`: `issue` → значение для
  ссылки (256 бит `SecureRandom` в base64url, 43 символа, возвращается один раз), `peek` — проверить без использования,
  `consume` — использовать под блокировкой строки (из одновременных открытий одной ссылки срабатывает одно), `revokeAll`
  — погасить действующие токены цели (смена пароля: `revokeAll(PASSWORD_RESET, accountId)` вместе с
  `AccountSessions.terminateOthers`). Недействующий токен — 422 `iam.token.invalid`, «Ссылка устарела».
- Таблица `one_time_token` в модуле iam (`db/migration/iam`): токены выдаёт и гасит только iam, цель — аккаунт или
  приглашение iam, а платформе ссылаться на таблицы модулей нельзя (зависимости — только к platform). Глобальная, без
  `clinic_id` и RLS: ссылку открывают до входа (`RowLevelSecurityTests.GLOBAL_TABLES`). В ней только SHA-256 токена
  (`token_hash bytea`, 32 байта), срок, отметки использования и отзыва; внешнего ключа на цель нет: цель по `purpose` —
  аккаунт (`account`, T-IAM-01) или приглашение (T-IAM-13). Удаление старых строк — задачей по расписанию (открыто,
  T-PLT-14).

### Лимиты частоты

Всё в Valkey, общее для всех экземпляров (`RedisRateLimiter`), два механизма:

- **частота** — Bucket4j 8.20, официальная интеграция `bucket4j_jdk17-lettuce` (CAS-корзины): отдельное соединение того
  же клиента Lettuce, что у Spring Data Redis (адрес, пароль, TLS — из `spring.data.redis.*`). Модуль собран против
  Lettuce 6; с Lettuce 7.5 из Boot 4.1 проверен тестами `RateLimitTests`;
- **не больше N событий за любой период** — скользящее окно: журнал времён событий в sorted set, атомарно Lua-скриптом
  через `StringRedisTemplate`. Bucket4j здесь не подходит: у него токен-корзины, а `refillIntervally` считает окна от
  создания корзины — на стыке двух окон проходило вдвое больше лимита (одна неверная попытка в начале окна, четыре в
  конце, и через секунду снова пять), а блок длился до конца окна, а не 15 минут. Блокировки входа в Bucket4j нет.

| Лимит | По умолчанию (`dentify.security.rate-limit.*`) | Как считается | Где |
|---|---|---|---|
| неверные попытки входа на логин | 5 за 15 мин (`login-failures`), затем блок 15 мин (`login-lock`) | скользящее окно: пятая неверная попытка за любые 15 минут закрывает вход на 15 минут от неё, 429 даже с верным паролем; после блока счёт с нуля. Место попытки занимается до проверки пароля (ниже) | `RateLimits.checkLogin` / `recordLoginFailure` / `resetLogin` / `abandonLogin`; журнал отказов — `firstLoginRejection` |
| попытки входа с IP | 30 в минуту | Bucket4j, сглаженная корзина | `RateLimits.checkLogin` |
| сброс пароля на адрес | 3 в час | скользящее окно: за любой час не больше 3 писем, отклонённые не считаются | `RateLimits.checkPasswordReset` |
| общий API | 600 в минуту на сессию, без сессии — на IP | Bucket4j, сглаженная корзина: всплеск до 600, дальше 10 в секунду | фильтр `ApiRateLimitFilter` в цепочке до CSRF, только `/api/**` |

- **Одновременные попытки входа.** Проверка пароля (Argon2id) идёт десятки миллисекунд, и если бы неверная попытка
  засчитывалась только после неё, все параллельные попытки на логин прошли бы `checkLogin` раньше, чем записалась
  первая неудача: с одного IP — до 30 паролей вместо 5, с многих IP — без предела. Поэтому `checkLogin` одним
  Lua-скриптом (`RESERVE_LOGIN`) проверяет блокировку, чистит журнал от записей старше окна и, если в нём меньше 5
  записей, **занимает место попытки** (`p:<id>`) до проверки пароля; иначе — 429. Исход попытки:
  - `recordLoginFailure` — место одной идущей попытки становится неверной попыткой (`f:<id>`) с текущим временем; пятая
    неверная за окно ставит блокировку на 15 минут и очищает журнал. Возвращает `true`, если эта попытка закрыла вход:
    по нему журнал входов пишет «Блокировка после попыток». Идущие попытки в счёт блокировки не входят: из пяти
    одновременных блок ставит пятая неверная, а не первая, и успешная среди них не приводит к блоку;
  - `resetLogin` (успешный вход) — неверные попытки обнуляются, освобождается место этой попытки; места других идущих
    попыток остаются.

  Итог: пока нет успешного входа, пароль логина за любые 15 минут проверяется не больше 5 раз — подряд или разом, с
  любого числа IP (`RateLimitTests.concurrentAttemptsOnOneLoginPassCheckOnlyFiveTimes`: 30 одновременных — ровно 5
  проходят; по HTTP 20 одновременных неверных с 20 IP — пять 401 и пятнадцать 429). Шестая попытка, пока идут пять,
  получает 429 с `Retry-After` до выхода из окна самой старой записи (если пять окажутся неверными, блок — 15 минут от
  пятой). Каждая попытка после `checkLogin` обязана закончиться `recordLoginFailure` или `resetLogin`, а если пароль не
  проверен из-за предела одновременных хэшей (429 `error.too-many-requests` от `PasswordEncoder`) — `abandonLogin`: место
  одной идущей попытки освобождается, неверные остаются (скрипт `ABANDON_LOGIN`). Без этого под нагрузкой на хэширование
  пять таких повторов закрыли бы вход на 15 минут без единого неверного пароля; лишней проверки пароля освобождённое место
  не даёт — пароль не проверялся. Попытка, прерванная другим исключением, держит место как неверная до выхода из окна, но
  блок не ставит: пароль мог быть уже проверен.
- **Отказы в журнал входов — один раз за время отказа** (`firstLoginRejection`, скрипт `FIRST_REJECTION`): отказ
  `checkLogin` ничего не стоит нападающему, а поиск аккаунта и строка `auth_event` — стоят БД. Без отметки один IP писал бы
  сотни строк `LOCKED_OUT` в минуту в журнал чужого аккаунта (экран IAM-29), упираясь только в общий лимит API. Отметка
  на логин хранит конец времени отказа — `Retry-After` (при блокировке — до её конца), но не меньше минуты; первый отказ
  за это время — `true` и пишется, остальные — нет, из одновременных `true` получает один.
- Превышение — 429 ProblemDetail с `Retry-After` в секундах (с округлением вверх); на входе —
  `error.too-many-login-attempts` «Слишком много попыток, попробуйте через N мин».
- Ключи — `dentify:rate:<лимит>:<SHA-256>` (`login-ip`, `api` — корзины Bucket4j; `login-failures` — журнал неверных и
  идущих попыток, `password-resets` — журнал писем; `login-lock` — конец блокировки в мс; `login-rejected` — конец времени
  отказа, за которое отказ уже записан в журнал входов): логин, e-mail, IP и id
  сессии в Valkey в открытом виде не лежат; логин и e-mail — без учёта регистра и крайних пробелов. Ключ живёт, пока
  нужен лимиту (корзина — пока не наполнится, журнал — окно, блокировка — её срок). Время — часы приложения, у корзин и
  журналов одни; Valkey — одиночный (для кластера ключам журнала и блокировки понадобится общий hash tag).
- IP — `request.getRemoteAddr()`: за Caddy это клиент из `X-Forwarded-For` (`server.forward-headers-strategy: native`,
  `RemoteIpValve` Tomcat), иначе все клиенты считались бы одним IP. **И только от адреса прокси**:
  `server.tomcat.remoteip.internal-proxies` — сети CIDR через запятую (формат Tomcat 11; без `/` — регулярное выражение).
  В dev и test — loopback `127.0.0.0/8, ::1/128` (`application.yml`: тесты ходят с 127.0.0.1, Caddy профиля proxy в dev
  приходит через Docker Desktop с 127.0.0.1; на Linux без Docker Desktop — добавить адрес шлюза в `application-dev.yml`),
  в prod — точный адрес Caddy из `DENTIFY_TRUSTED_PROXIES`, например `10.128.0.5/32` (`application-prod.yml`, без
  переменной старт падает; T-PLT-25). Дефолт Tomcat и Boot — все частные сети и loopback: с ним любой источник из
  частной сети ротацией `X-Forwarded-For` обходил бы лимиты по IP (вход — 30 в минуту, API без сессии — 600 в минуту;
  блокировка логина — на аккаунт, и кросс-аккаунтный тормоз подбора — только IP) и подделывал бы IP в `auth_event` и
  `audit_log`. Поэтому `TrustedProxiesCheck` роняет старт, если список пуст (Tomcat поверил бы любому адресу) или
  пропускает адрес из глубины частных сетей, CGNAT, link-local, `fc00::/7` (`TrustedProxiesCheckTests`); что
  `X-Forwarded-For` с чужого адреса не принимается, проверяет `ForwardedHeadersTests` на копии настроек `RemoteIpValve`
  запущенного Tomcat.

### Заголовки

Ставит цепочка API на каждом ответе, в том числе на ошибках (`SecurityHeadersTests`):

| Заголовок | Значение |
|---|---|
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'` (`dentify.security.content-security-policy`; в dev для Swagger UI `style-src` с `'unsafe-inline'`) |
| `Strict-Transport-Security` | `max-age=31536000 ; includeSubDomains` — только на ответах по HTTPS (за Caddy — `X-Forwarded-Proto: https`) |
| `X-Frame-Options` | `DENY` (вместе с `frame-ancestors 'none'`) |
| `X-Content-Type-Options` | `nosniff` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `accelerometer=(), autoplay=(), camera=(), display-capture=(), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), usb=()` |
| `Cache-Control` | `no-cache, no-store, max-age=0, must-revalidate` (Spring Security по умолчанию) |

Страницы SPA отдаёт Caddy, и те же заголовки (CSP, HSTS, редирект с http на https) для них ставит он — прод-Caddyfile,
T-PLT-25.

### Тесты безопасности

| Тест | Что проверяет |
|---|---|
| `platform.security.SessionSecurityTests` | 401 без редиректа и `WWW-Authenticate`; 401 `error.session-expired` на неизвестную сессию и после выхода; 403 `@PreAuthorize`; cookie `DENTIFY_SESSION` HttpOnly, Secure, SameSite=Lax, без срока; 12 ч в Valkey; «Запомнить» — `Max-Age=2592000` и 30 дней; сроки со сдвигом времени в самой сессии в Valkey: 12 ч бездействия без «Запомнить» — 401, меньше 12 ч — проходит, с «Запомнить» 12 ч бездействия — проходит, 30 дней от входа при непрерывной активности — 401 с заголовками безопасности и стёртой cookie (с «Запомнить» и без), пути входа с такой cookie открыты, бездействие в Valkey подрезано до срока от входа, в списке сессий её нет; новый id сессии при входе (старый недействителен, клиника до входа снята и с сессии, и с потока запроса входа); новый CSRF-токен при входе; `/api/v1/public/**` без входа — 404 неизвестного пути, а не 401, POST без CSRF — 403; индекс по аккаунту; запросы без входа не создают сессию |
| `platform.security.CsrfTests` | cookie `XSRF-TOKEN` не HttpOnly, Secure, SameSite=Lax; POST без заголовка и с чужим — 403 `error.csrf`, с заголовком — проходит; PUT, PATCH, DELETE тоже; без cookie и без входа — 403 |
| `platform.security.SecurityHeadersTests` | CSP без unsafe-inline для скриптов, frame-ancestors, X-Frame-Options, nosniff, Referrer-Policy, Permissions-Policy, HSTS на год только по HTTPS, no-store, CORS выключен — на успешных ответах и ошибках |
| `platform.security.RateLimitTests` | 30 одновременных проверок входа на логин с разных IP — проходят ровно 5, остальные 429, пять неверных из них — блок 15 мин; идущие попытки держат места, успешный вход освобождает только своё, незаконченные — до выхода из окна; блок — только на пятой неверной, а не на пяти идущих; по HTTP 20 одновременных неверных с 20 IP — пять 401 и пятнадцать 429; после 5 неверных — блок 15 мин от пятой (`Retry-After`, число минут), через 15 мин — снова можно; стык окон: одна попытка в начале 15 минут и четыре в конце — блок ещё 15 минут; окно скользящее; логин без учёта регистра; обнуление неверных попыток; 30 с IP в минуту; сброс пароля 3 за любой час, на стыке часов не больше; ключи в Valkey — хэши; по HTTP (пробный вход) 6-я попытка с верным паролем — 429 `error.too-many-login-attempts` «через 15 мин» с `Retry-After`; 601-й запрос API за минуту на сессию и на IP — 429 ProblemDetail; попытка без проверки пароля (`abandonLogin`) освобождает своё место, неверные остаются; отказ в журнал — один раз за время отказа, не чаще раза в минуту, из 20 одновременных — один |
| `platform.security.PasswordTests` | `{argon2}$argon2id$v=19$m=19456,t=2,p=1$`; старые хэши проверяются и перехэшируются; правила пароля и коды ошибок: `12345678`, пароль-телефон и пароль-e-mail отклоняются; длина в символах Unicode; пароля нет в сообщениях |
| `platform.security.PasswordHashingLimitTests` | бин `PasswordEncoder` с пределом (по умолчанию по числу ядер, ожидание 3 с); при пределе 2 третий одновременный хэш ждёт места, а не дождавшись — 429 `error.too-many-requests` с `Retry-After` 1 с; ошибка хэширования освобождает место |
| `platform.security.SessionClientsTests` | маска IP, браузер и ОС по User-Agent, город из тестовой базы MaxMind (`src/test/resources/geoip`), без базы — `null` |
| `iam.AccountSessionsTests` | список своих сессий с браузером, ОС, маской IP и городом, без id сессий и полного IP; завершить одну, все кроме текущей, все; чужая — 404; после завершения — 401 с любого устройства быстрее 5 с |
| `platform.security.SessionRevocationTests` | запрос сессии держится (`SessionHoldProbeController`), пока другой поток её завершает — выход, завершение одной, всех, всех кроме текущей, с «Запомнить» и без: запрос в полёте переписывает бездействие и заново кладёт контекст безопасности, а следующий и дальнейшие запросы сессии — 401, в индексе её нет, второе устройство живо, если его не завершали; запрос ожившей сессии — 401 с заголовками безопасности и стёртой cookie; на открытых путях ожившая сессия не мешает: выдача CSRF-токена — 204 и стёртая cookie, вход — новая сессия, старая недействительна; список не показывает ожившую и убирает её из индекса; запрос в полёте дольше жизни ключа завершённой сессии (ключ удалён до конца запроса): неполный hash — не сессия, следующий запрос — 401, а не 500, список, «все, кроме текущей» и «завершить все» со второго устройства работают, hash, теневой ключ и id в индексе удалены, с «Запомнить» и без; сессия вне индекса гаснет от «завершить все» и «все, кроме текущей» (текущая остаётся); вход после «завершить все» работает; время входа и T отметки аккаунта — по часам Valkey; сроки отметок, id сессии в Valkey только хэшем |
| `platform.security.SessionLifetimeFilterTests` | без приложения: с «Запомнить» бездействие пишется раз в минуту, без — только в последние 12 ч срока; ключ сессии не переживает срок от входа; в последнюю минуту — ровно остаток; окно `nextMaxInactive` |
| `iam.internal.OneTimeTokensTests` | 256 бит, в БД только SHA-256, срок по назначению, одноразовость, в том числе при одновременном открытии; истёкший, отозванный сменой пароля, чужого назначения — `iam.token.invalid` |
| `CodingRulesTests.endpointsHavePreAuthorize` (ArchUnit) | у каждого эндпоинта контроллера `@PreAuthorize` на методе или классе; что правило ловит нарушение — на фикстурах `archfixtures.web` |
| `CodingRulesTests.webLayerIsThin` (ArchUnit) | в пакетах `web` нет репозиториев Spring Data, сущностей JPA и `@Transactional`; эндпоинт отдаёт record, `PageResponse` записей, список записей, `ResponseEntity` или ничего — не сущность, `Map`, `JsonNode`; ловит нарушения на фикстурах `archfixtures.web.FatController`, пропускает `ThinController` |
| `platform.ForwardedHeadersTests`, `platform.security.TrustedProxiesCheckTests` | `X-Forwarded-Proto` и `X-Forwarded-For` от прокси (127.0.0.1) принимаются; `X-Forwarded-For` с частных сетей, link-local, CGNAT, публичного адреса и `fd00::/8` — нет (копия настроек `RemoteIpValve` запущенного Tomcat); пустой, дефолтный Tomcat и Boot, весь `10.0.0.0/8` и `.*` — старт падает, loopback и точный адрес прокси (CIDR и регулярное выражение) — проходят |
| `iam.AccountSessionsTests.serviceTerminatesOnlySessionsOfOneClinic` | `terminateInClinic` гасит только сессии аккаунта с этой клиникой: сессия в другой клинике и сессии коллег живы |
| `iam.AccountHashingTests` | хэш пароля нового аккаунта — вне транзакции; `prepare` до транзакции вызывающего, `create(PreparedAccount)` — в ней; `PreparedAccount` без хэша и ПД в `toString` |

Пробные эндпоинты: `/test/**` в тестах идут через свою цепочку без входа и CSRF (`ProbeSecurityConfiguration`), вход —
`SignInProbeController` (`/api/v1/auth/test-sign-in`, через цепочку приложения, аккаунт без строки в `account`; там же
`test-login-attempt` — попытка входа по паролю с лимитами, как у входа T-IAM-02), проверки цепочки —
`SecuredProbeController` (`/secured-probe/**`), запрос в полёте — `SessionHoldProbeController` (`/secured-probe/hold/{key}`:
держит запрос сессии, пока тест не отпустит). Вошедшая сессия с CSRF-токеном в тесте — `AbstractIntegrationTest.signIn`.

Запрещено:

- JWT, токены в `localStorage` и `sessionStorage`; свой вход мимо `AccountSignIn` (без смены id сессии и CSRF-токена);
- открывать без входа пути вне `/api/v1/auth/**` и `/api/v1/public/**`; выключать CSRF для API; CORS и `@CrossOrigin`;
- широкий или пустой `server.tomcat.remoteip.internal-proxies` (дефолт Tomcat — все частные сети) при
  `forward-headers-strategy: native`; брать IP клиента из заголовков запроса в обход `getRemoteAddr()`;
- эндпоинт без `@PreAuthorize` (ловит ArchUnit); редиректы и HTML-страницы входа — ответ всегда ProblemDetail;
- продлевать сессию дольше срока от входа: менять `dentify.client.signedInAt` вне входа, ставить сессии бездействие
  больше остатка до срока от входа; писать бездействие сессии на каждом запросе — только через
  `SessionLifetimeFilter.nextMaxInactive`;
- завершать сессию мимо `AccountSignIn.signOut` и `AccountSessions` — голым `deleteById`, `invalidate()` или удалением
  ключей Valkey: без отметки отзыва запрос в полёте её оживит; считать `deleteById` удалением; убирать
  `SessionRevocationFilter` из цепочки или ставить его после `SessionLifetimeFilter`;
- убирать `SafeRedisSessionMapper` или ставить свой разбор hash сессии, который бросает исключение на неполном hash;
  чистить неполный hash через `deleteById` (он снова вызывает разбор);
- брать время входа в сессию или T отметки аккаунта с часов экземпляра — только `SessionRevocations.now()` (Valkey);
  вызывать `terminateAll`/`terminateOthers` внутри транзакции деактивации или смены пароля и входить без перечитывания
  аккаунта после `signIn` (T-IAM-02, «Активные сессии»);
- классы приложения в атрибутах сессии; отдавать наружу id сессии или полный IP;
- хэшировать пароль мимо бина `PasswordEncoder` или внутри транзакции БД; хранить токен ссылки в открытом виде; писать в
  лог пароль, токен ссылки, id сессии, CSRF-токен;
- свои счётчики лимитов в памяти приложения — только `RateLimits` и Valkey: экземпляров приложения может быть несколько;
- проверять пароль при входе без `checkLogin` перед ним; оставлять попытку без исхода — после `checkLogin` всегда
  `recordLoginFailure` или `resetLogin`, а `abandonLogin` — только когда пароль не проверен (429 предела хэшей); считать
  неудачу входа только после проверки пароля без места, занятого заранее; писать в журнал входов каждый отказ
  `checkLogin` — только при `firstLoginRejection`;
- свой `PasswordEncoder` или `Argon2PasswordEncoder` в обход бина (без предела одновременных хэшей); поднимать
  `password-hashing.concurrency` выше, чем позволяет память (каждое место — 19 МиБ);
- `refillIntervally` Bucket4j для правил «N за период» — на стыке окон он пропускает вдвое больше; такие правила —
  скользящим окном `RedisRateLimiter`;
- ослаблять `Secure`, `HttpOnly` или `SameSite=Lax` у cookie сессии.

## Журнал действий

Журнал значимых действий в клинике (PLT-09): кто, когда, с какого IP, с каким объектом что сделал и разница «было → стало».
Отдельно — просмотры медкарты, выгрузки и печать с ПД. Журнал только дописывается. Код: запись — `platform.api`
(`AuditService`, `@Audited`, `AuditEvent`, `AuditSnapshots`, `AuditSecret`, `AuditActions`) для всех модулей, чтение —
`platform.identity` (`AuditJournal`, `AuditQuery`, `AuditEntry`) только для iam, реализация — внутренний пакет
`platform.audit`, REST журнала — `iam.web.AuditEventController`.
Журнал входов (вход, смена и сброс пароля) — не он, а `auth_event` модуля iam (T-IAM).

Почему своя разница, а не готовое (R021). Hibernate Envers (официальный модуль Hibernate) хранит ревизии сущностей: на
каждую — таблица `<таблица>_AUD` с копией всех колонок строки и общая `REVINFO`. Разницы по полям в нём нет (её считали бы
при чтении), пароль и хэш попадают в копию строки, если не пометить каждое такое поле `@NotAudited`, а событий без
изменения сущности — просмотр, выгрузка, печать — нет вовсе. JaVers (комьюнити) — ещё одна зависимость со своей схемой
(`jv_commit`, `jv_snapshot`, `jv_global_id`, создаёт её сам, а у `dentify_app` нет DDL), без `clinic_id`, RLS и помесячных
партиций; снимки в нём — полные состояния объекта, маску контактов и тексты i18n всё равно писать самим. Поэтому своя
разница по снимкам-DTO (`AuditDiff`) и таблица по соглашениям платформы.

### Таблица `audit_log`

| Колонка | Что |
|---|---|
| `id`, `at` | UUIDv7 и время события (часы приложения, точность до микросекунд); первичный ключ `(id, at)` — в ключе партиционированной таблицы обязан быть ключ партиций |
| `clinic_id` | клиника; RLS ENABLE + FORCE и стандартная политика, как у всех таблиц клиники |
| `account_id`, `staff_id` | кто: аккаунт (`CurrentAccountProvider`) и сотрудник (`CurrentStaffProvider`, реализует iam; пока пусто); оба пусты — система |
| `ip` | `inet` клиента из `getRemoteAddr()` (за Caddy — из `X-Forwarded-For`); вне HTTP-запроса пусто. Наружу — только маской `95.56.•.•` |
| `entity_type`, `module` | тип объекта `<модуль>.<объект>` (`schedule.appointment`, `medical.card`); `module` — вычисляемая часть до точки, категория фильтра |
| `entity_id`, `entity_label` | объект и его понятное имя на момент события (ФИО пациента, номер документа, до 500 символов) |
| `action` | действие: `AuditActions` (`created`, `updated`, `archived`, `restored`, `status-changed`, `rescheduled`, `posted`, `granted`, `revoked`, `viewed`, `exported`, `printed`, `deleted`) или своё |
| `diff` | разница `[{field, type, from, to}]`; у секрета `type: secret` без значений |
| `args` | аргументы текста события (число строк выгрузки) |
| `search_text` | имя объекта и русский текст события в нижнем регистре — для поиска; ИИН, телефоны и e-mail — маской |

Служебных `updated_*` и `version` нет: событие не меняется. Неизменяемость — в два слоя: у `dentify_app` отняты `UPDATE` и
`DELETE` (остаются `SELECT` и `INSERT`), а триггер `audit_log_forbid_change` отклоняет правку, удаление и `TRUNCATE` любой ролью,
в том числе владельцем схемы (SQLSTATE 42501). Строчный триггер PostgreSQL копирует в партиции сам, `TRUNCATE`-триггер
ставится на каждую партицию отдельно: `TRUNCATE` партиции напрямую триггер родителя не вызывает (проверено на 18.6).

### Объём: партиции, индексы, пределы

- **Помесячные партиции** по `at` (`RANGE`, границы — полночь UTC первого числа), имена `audit_log_YYYY_MM`. Запрос журнала
  всегда с `clinic_id` и почти всегда с периодом: лишние партиции отсекаются, внутри — индексы партиции.
- **Индексы**: `(clinic_id, at)` — лента клиники за период; `(clinic_id, entity_type, entity_id, at)` — история объекта;
  `(clinic_id, account_id, at)` — действия сотрудника. Индекса для поиска по тексту нет: поиск идёт внутри клиники и
  периода не длиннее года (ниже); понадобится — `pg_trgm` уже в базе.
- **Партиции заранее**: прошлый месяц, текущий и `dentify.audit.partitions.months-ahead` (3) вперёд. Без партиции на момент
  события вставка падает, а с ней и изменение. Создаёт их функция `audit_log_create_partitions(months_ahead)`
  (`SECURITY DEFINER` от владельца схемы, `search_path` закреплён), её вызывают трижды с запасом:
  - миграция — при первом развёртывании;
  - старт приложения (`AuditPartitions`, `ApplicationRunner`) — после простоя и развёртывания без новых миграций;
  - задача по расписанию раз в сутки (`dentify.audit.partitions.cron`, по умолчанию 03:17 UTC) — приложение работает
    месяцами без рестарта. ShedLock не нужен: функция идемпотентна, одновременные вызовы нескольких экземпляров идут по
    очереди под advisory-lock.

  Почему приложение, а не только миграции: в проде миграции — разовый запуск при развёртывании, и без новых развёртываний
  партиции кончились бы. Почему не pg_partman: расширение ставит администратор, а его наличие в managed PostgreSQL в РК не
  проверено, и ему нужен ещё pg_cron или фоновый процесс; своя функция — 30 строк и те же гарантии. Партиция создаётся
  отдельной таблицей и присоединяется `ATTACH PARTITION`: он берёт на `audit_log` только `SHARE UPDATE EXCLUSIVE` и не
  блокирует вставки, в отличие от `CREATE TABLE … PARTITION OF` (`ACCESS EXCLUSIVE`); `lock_timeout` 5 с — не ждать долгих
  транзакций, повторит следующий запуск. Сбой не роняет приложение, в лог — ERROR (по нему алерт), запас — 3 месяца.
  DEFAULT-партиции нет: она спрятала бы сбой задачи, а партицию на её диапазон потом не создать без переноса строк.
- **Партиция — как таблица клиники**: RLS ENABLE + FORCE и та же политика (через родителя действует политика родителя, к
  партиции напрямую — её собственная), прав у `dentify_app` на неё нет — всё только через `audit_log`. Тест каталога
  `RowLevelSecurityTests` проверяет и партиции.
- **Хранение** — не меньше срока медданных (PLT-28: 10 лет, у детей 15): партиции не удаляются. Перенос старых партиций в
  архив (`DETACH PARTITION`) — отдельная задача вместе с ретеншном.
- **Пределы чтения**: страница — до 100 событий (`PageQuery`); выгрузка — не больше `dentify.audit.export-max-rows` (10 000)
  строк, больше — 422 `audit.export.too-many-rows` «Сузьте период или фильтры».
- **Период ленты** задаёт сервер (решение 2026-09-25, принято как есть). Без фильтра по объекту лента всегда за период:
  начало не задано — последние `dentify.audit.default-period` (30 дней) до конца периода или до сейчас; поиск `q` — за
  период не длиннее `dentify.audit.search-max-period` (366 дней), длиннее — 400 `audit.period.too-long` по полю `from`.
  **Фронт всегда шлёт явный период** `from` и `to` (по умолчанию в фильтре — последние 30 дней): что показано, видно в
  фильтре, а не выбрано сервером молча; значения сервера — страховка для других клиентов. У поиска нет индекса,
  а `count(*)` считается на каждой странице: без предела запрос с `q` просматривал бы все события клиники за 10+ лет. История
  объекта (`entityId`) — за всё время, её ведёт индекс объекта. Выгрузка — по тем же правилам.

### Запись изменений

Событие пишется в **той же транзакции**, что и изменение (JDBC на соединении транзакции JPA): откатилась транзакция — нет и
события. Разница считается по **снимкам** — неизменяемым record, обычно DTO объекта.

Аннотацией — для метода сервиса над одним объектом; снимки даёт бин `AuditSnapshots` типа объекта:

```java
@Component
class AppointmentSnapshots implements AuditSnapshots<AppointmentSnapshot> {   // schedule.internal
    public String entityType() { return "schedule.appointment"; }
    public Optional<AppointmentSnapshot> load(UUID id) { return this.appointments.findById(id).map(AppointmentSnapshot::of); }
    public String label(AppointmentSnapshot snapshot) { return snapshot.patientName(); }
}

@Transactional
@Audited(entity = "schedule.appointment", action = AuditActions.RESCHEDULED)            // id — аргумент id
Appointment reschedule(UUID id, LocalDate date, LocalTime time, long version) { … }

@Transactional
@Audited(entity = "schedule.appointment", action = AuditActions.CREATED, id = Audited.RESULT_ID)
Appointment create(AppointmentRequest request) { … }
```

Интерцептор открывает транзакцию по атрибуту `@Transactional` метода (так же, как сам `TransactionInterceptor`, поэтому
порядок советников не важен), читает снимок до, вызывает метод, читает снимок после — по изменённой, ещё не сохранённой
сущности — и пишет событие. `id` — SpEL по аргументам (`#id`, `#request.patientId`) или по результату (`#result.id`: снимка
до нет — создание). Снимка нет ни до, ни после — событие не пишется; оба есть и равны — тоже (сохранение без изменений).

Ошибка применения — `IllegalStateException`, а не тихо потерянное событие:

- переменные выражения `id` проверяются при первом вызове: только параметры метода по имени (`#appointmentId`), `#a0`/`#p0`
  и `#result`. `#id` по умолчанию у `reschedule(UUID appointmentId, …)` — ошибка (SpEL дал бы null, и событие пропало бы);
- `id`, вычисленный в null (аргумент null, результат без id), — ошибка, изменение откатывается. Необязательный id («создать
  или изменить») — запись вручную;
- снимок после читается в той же сессии Hibernate, поэтому объект меняется **только через управляемую сущность**. Массовый
  JPQL `UPDATE`/`DELETE` (`@Modifying`) и JDBC идут мимо сессии: снимок после вернёт прежнюю сущность из контекста, снимки
  совпадут, и события не будет. Такие изменения пишут в журнал вручную. `flush` и `refresh` перед снимком после интерцептор не
  делает: сущность он не знает, а `clear()` отцепил бы сущности вызывающего кода.

Вручную — когда снимки не по одному id или событие без разницы (выдача роли):

```java
AppointmentSnapshot before = AppointmentSnapshot.of(appointment);
appointment.reschedule(date, time);
this.audit.record(AuditEvent.of("schedule.appointment", AuditActions.RESCHEDULED)
    .entity(appointment.getId(), patientName)
    .change(before, AppointmentSnapshot.of(appointment)));
```

Разница (`AuditDiff`):

- поля — в порядке объявления record; вложенный record — по своим полям через точку (`name.ru`, `address.city`);
  коллекции, массивы и Map — одним полем целиком (ключи Map — по алфавиту); суммы — по значению (`12500.50` = `12500.5`);
- поля `id`, `version`, `createdAt/By`, `updatedAt/By` верхнего уровня не сравниваются;
- **секреты не попадают в журнал никогда**: поле с `@AuditSecret`; поле, в имени которого есть слово `password`, `passwd`,
  `pwd`, `secret`, `token`, `hash`, `salt`, `otp`, `pin`, `credential(s)` (`passwordHash`, `resetToken`); `byte[]` и `char[]`;
  значение вида хэша пароля (`{argon2}…`, `{bcrypt}…`, `$2a$…`) в любом поле. В разнице — только `{field, type: secret}`
  «изменился», для сравнения — SHA-256 значения в памяти;
- секреты ищутся **и внутри значений**: коллекция, массив, Map и record, объявленный не record-типом, обходятся
  рекурсивно. Компонент record по тем же правилам, значение Map с ключом-секретом (`smtpPassword`), `byte[]`/`char[]` и
  строка вида хэша заменяются на `••••`, остальное видно; изменились только скрытые секреты — поле `type: secret`;
- в снимке — только record, коллекции, массивы, Map и простые значения (строки, числа, да/нет, перечисления, UUID, дата
  и время). Сущность JPA, прокси Hibernate, другой класс, `Optional`, `JsonNode` — `IllegalArgumentException`: у них нет
  списка полей, по которому искать секреты. Изменение с таким снимком откатывается;
- снимок сразу раскладывается в JSON: изменение объекта после снимка «было» не портит.

### Просмотры, выгрузка, печать

- `recordView(entityType, id, label)` — просмотр, прежде всего медкарты (`medical.card`, id пациента): не чаще одного
  события на аккаунт и объект за `dentify.audit.view-dedup-window` (15 мин). Метка — ключ Valkey
  `dentify:audit:view:<клиника>:<аккаунт>:<тип>:<id>` с `SET NX` и временем жизни окна: из одновременных открытий
  событие пишет одно. Окно фиксированное — от записанного события. Откат транзакции снимает метку, следующий просмотр
  запишется; Valkey недоступен или не ответил за `spring.data.redis.timeout` (3 с) — событие пишется без проверки (полнота
  журнала важнее дублей), в лог WARN (`ViewWindowsTests`). Пока ждём Valkey, транзакция держит соединение пула, поэтому
  таймаут короткий, а не 60 с Lettuce по умолчанию.
- Допущение гонки: второй одновременный просмотр, увидевший метку первого, не пишется. Если транзакция первого потом
  откатится, метка снимется, но за это окно в журнале не останется ни одного просмотра — до следующего открытия карты. Окно
  гонки — длительность транзакции первого просмотра (миллисекунды у «открыть медкарту»); повторять занятие метки после
  отката чужой транзакции сложнее, чем стоит такой пропуск.
- `recordExport(entityType, label, rows)` — выгрузка с ПД, `recordPrint(entityType, id, label)` — печать с ПД.
- Эти три пишут в текущей транзакции записи, а без транзакции — в своей. В транзакции только для чтения —
  `IllegalStateException`: PostgreSQL не примет INSERT, а своя транзакция заняла бы второе соединение пула. Поэтому метод
  «открыть медкарту» — `@Transactional` без `readOnly`.

### Тексты (dentify R003)

Текст события, названия полей и значения — ключами `messages.properties` на языке запроса:

| Что | Ключ | Пример |
|---|---|---|
| текст события | `audit.<тип объекта>.<действие>`, иначе общий `audit.action.<действие>`; `{0}` — имя объекта, `{1}` — кто, `{2}…` — аргументы | `audit.schedule.appointment.rescheduled=Перенос записи: {0}` |
| поле | `audit.field.<тип объекта>.<поле>`, иначе `audit.field.<поле>`; путь с точкой — названия частей через «·» | `audit.field.schedule.appointment.date=Дата` |
| значение перечисления | `audit.value.<тип объекта>.<поле>.<ЗНАЧЕНИЕ>`, иначе `audit.value.<поле>.<ЗНАЧЕНИЕ>` | `audit.value.schedule.appointment.status.ARRIVED=Пришёл` |

Модуль, который пишет событие, заводит свои ключи в том же PR. Род глагола («перенесла») в текст не зашит: пола сотрудника
система не знает, поэтому общие тексты — существительным («Перенос записи»). Даты и время в разнице — по формату языка
(`24.09.2026`, `11:30`), да/нет — `audit.value.true/false`, момент времени — ISO 8601 (пояс клиники появится с модулем
clinic). Поиск `q` — по имени объекта и русскому тексту события (`search_text`); с казахским словарём (PLT-34) добавить
в `search_text` и казахский текст. ИИН, телефоны и e-mail в `search_text` — маской (`PiiMasker`), как их видит сотрудник
без `patient:phones`: иначе поиском по точному номеру он проверил бы, чей это номер. Номер находится по видимой части
маски (`4567`).

Ключи проверяются тестами. `MessagesTests`: у каждого действия `AuditActions` есть `audit.action.<действие>`, есть служебные
`audit.actor.system/unknown`, `audit.value.true/false` и коды ошибок журнала. Модуль заводит `audit.field.*` для всех полей
своих снимков и `audit.value.*` для значений полей-перечислений и проверяет это своим тестом через помощник тестовых
исходников `platform.audit.AuditFieldLabels`:

```java
assertThat(AuditFieldLabels.missing("schedule.appointment", AppointmentSnapshot.class)).isEmpty();
```

Без ключа журнал покажет сырой путь поля (`note`) или код значения (`ARRIVED`).

### Чтение: API для iam

- `AuditJournal` (`platform.identity`, открыт только iam): `search(AuditQuery, Pageable)` — страница, сначала новые; `find(id)`; `export(AuditQuery)`
  — для выгрузки в Excel (экран iam), с пределом строк, сама выгрузка — тоже событие (`platform.audit-log`, `exported`).
  Всегда только клиника контекста, без неё — 401 `error.clinic-not-selected`.
- Фильтры `AuditQuery` — вместе: период `[from, to)` (конец не позже начала — 400 `audit.period.invalid` по полю `to`),
  кто (`accountId`, `staffId`), категория (`module`), тип объекта, объект (`entityId` — история одного объекта),
  действие, текст. Сортировка — только по `at` (при равном времени — по id). Без `from` и `entityId` — последние 30 дней,
  `q` — за период не длиннее 366 дней (400 `audit.period.too-long` по полю `from`; раздел «Объём»).
- **Маска**: без права `patient:phones` ИИН, телефоны и e-mail в имени объекта, аргументах и значениях разницы — маской
  `PiiMasker` (`+7••••4567`, `••••0123`, `a•••@example.kz`); значение поля-контакта (`phone`, `phone2`, `mobile`, `iin`,
  `email`…), в котором маска ничего не распознала, скрывается целиком (`••••`). С правом — как есть.
- **IP — маской для всех ролей** (`95.56.•.•`, IPv6 — первые две группы). Решение 2026-09-25 для MVP: права `audit:ip` нет,
  полный IP в журнале хранится (`inet`), но через API не отдаётся никому, в том числе владельцу клиники, — общее правило
  «полный IP наружу не отдаём» (раздел «Безопасность и сессии»). PLT-09 перечисляет IP в содержимом журнала — маска его
  выполняет. Понадобится полный IP для расследований — отдельное право и отдельное решение владельца, до этого не
  заводить ни права, ни параметра запроса.
- Кто: имя — `AccountNameProvider` (цепочка по `@Order`, «Конфликт версии»), одним пакетом на страницу
  (`displayNames`), а не запросом на событие (`AuditLogTests.actorNamesAreResolvedOncePerPage`); без аккаунта —
  «Система», имя неизвестно — «Сотрудник».
- REST (iam): `GET /api/v1/audit-events` — `PageResponse` событий, параметры `from`, `to` (ISO 8601), `accountId`,
  `staffId`, `module`, `entityType`, `entityId`, `action` и `page`, `size`, `sort` (`at`, `-at`), `q`;
  `GET /api/v1/audit-events/{id}` — событие, чужой клиники — 404. `@PreAuthorize("hasAuthority('audit:read')")`: право
  появится в ролях iam (T-IAM-06), пока его нет ни у кого; с T-IAM-07 — `@perm.has('audit:read')` («Права»). Права
  текущей работы в коде — `CurrentPermissions.has(…)`, константы — `Permissions`.

### Тесты журнала

| Тест | Что проверяет |
|---|---|
| `platform.audit.AuditLogTests` | приёмка PLT-09: перенос записи — событие с разницей «Дата 24.09.2026 → 25.09.2026, Время 11:30 → 10:00», автором и IP, откат — события нет; `UPDATE`/`DELETE`/`TRUNCATE` и доступ к партиции от `dentify_app` — `permission denied`, от владельца — триггер; второй просмотр за 15 мин — не пишется, другой врач или карта — пишется, откат снимает метку; пароль, хэш, токен, PIN — без значений; телефон и ИИН без права — маской, поиском по точному номеру и ИИН событие не находится; `@Audited` на пробной записи (создание, изменение, без изменений, 409, удаление) и ошибки применения: нет транзакции, readOnly, REQUIRES_NEW, нет снимков, `#id` без такого параметра, id null по аргументу и по результату; фильтры, страницы, сортировка, 400/401/403/404; период ленты по умолчанию, история объекта за всё время, поиск не длиннее года; изоляция клиник и RLS на вставке; предел выгрузки и событие выгрузки; партиции заранее с RLS, триггером и без прав приложения |
| `platform.audit.AuditDiffTests`, `AuditTextsTests` | разница: порядок, вложенные record, виды значений, суммы, создание и удаление, копия снимка, секреты, в том числе внутри списков, Map и record в них; объект не record в снимке — ошибка; тексты: запасные ключи, форматы языка, маска контактов, проверка ключей снимка `AuditFieldLabels` |
| `platform.audit.ViewWindowsTests` | окно просмотров без Valkey: недоступен или не ответил — просмотр пишется, снимать нечего; сбой снятия метки — не ошибка |
| `platform.MessagesTests` | тексты всех `AuditActions` и служебные ключи журнала |
| `platform.tenancy.TenantIsolationTests` | ресурс `/api/v1/audit-events`: событие клиники B по id — 404 клинике A, список — без чужих событий |
| `platform.tenancy.RowLevelSecurityTests` | `audit_log` и его партиции — с RLS, FORCE и стандартной политикой |

Запрещено:

- писать в `audit_log` мимо `AuditService`/`@Audited`; менять или удалять события; давать `dentify_app` `UPDATE`,
  `DELETE`, `TRUNCATE` на журнал или права на партиции; DEFAULT-партиция;
- событие изменения вне транзакции изменения или в своей транзакции (`REQUIRES_NEW`) — откат изменения оставил бы событие;
  `@Audited` на методе без `@Transactional`, с `readOnly` или не `REQUIRED`; `@Audited` на методе, который меняет объект мимо
  управляемой сущности (`@Modifying`, JDBC), и с `id`, который бывает null;
- сущность JPA, прокси Hibernate и другие объекты не record в снимке;
- лента журнала без предела периода: период по умолчанию и предел поиска задаёт сервер (R002);
- класть в снимок пароль, токен или хэш без защиты: поле с другим именем помечать `@AuditSecret`; значения секретов в
  `label` и аргументах текста;
- текст события в коде — только ключи `audit.*`; ПД в логах журнала — только id;
- отдавать разницу без маски пользователю без `patient:phones`; полный IP наружу;
- удалять старые партиции до срока хранения медданных.

## Аккаунты и членство

Один аккаунт на человека для всех клиник и членство — его доступ к одной клинике (IAM-01). Код — `iam.internal`:
`Accounts`, `Memberships`, `AccountMemberships`, сущности `Account`, `Membership`, `MembershipBranch`; миграция
`iam/V2026_09_26_1010__iam_create_account_membership.sql`. Клиника и филиал, на которые ссылается членство, — пока
минимальные таблицы модуля clinic (`clinic/V2026_09_26_1000__clinic_create_clinic_branch.sql`: название и город), их
наполнит T-CLN-01.

### Таблицы

| Таблица | Вид | Что в ней |
|---|---|---|
| `account` | глобальная (`GlobalEntity`, `GLOBAL_TABLES`) | `phone` (E.164), `email` (нижний регистр, до 254), `full_name`, `password_hash` (`{argon2}…`), `language` (`ru`/`kk`), `photo_key`, `status` (`ACTIVE`/`DISABLED`). `CHECK` «хотя бы один контакт» и формат E.164; уникальные индексы `ux_account_phone (phone)` и `ux_account_email (lower(email))` |
| `membership` | клиники (`TenantEntity`, RLS) | `account_id` → `account`, `role_id` (FK и `NOT NULL` — T-IAM-06), `staff_id` (без FK: iam от staff не зависит; связь — T-IAM-26), `status` (`INVITED`/`ACTIVE`/`DISABLED`), `last_sign_in_at`; одно на аккаунт в клинике — `ux_membership_clinic_id_account_id` |
| `membership_branch` | клиники (`TenantEntity`, RLS) | филиалы доступа; нет строк — все филиалы. Составные FK `(clinic_id, membership_id)` → `membership` и `(clinic_id, branch_id)` → `branch`: филиал другой клиники не связать |
| `clinic` | корень арендатора, RLS по `id` | `name`, `city` (T-IAM-01 минимально) |
| `branch` | клиники, RLS | `name`, `city` (T-IAM-01 минимально) |

`@Version` — на всех. Филиалы — коллекция самого `Membership` (`@OneToMany` с `@JoinColumn`, а не `mappedBy`): Hibernate
меняет версию владельца только при изменении его собственной коллекции, у обратной стороны связи — нет (проверено,
`MembershipsTests.staleMembershipIsRejected`). Без этого одновременная правка филиалов двумя администраторами не дала бы
409.

### Телефон и e-mail

Значения-объекты в `platform.api` — ими пользуются и другие модули (телефон клиники, пациента, поставщика):

- `PhoneNumber` — E.164. `PhoneNumber.parse(ввод)` разбирает libphonenumber (Google, R021), номер без кода страны —
  казахстанский: «8 707 123 45 67», «+7 (707) 123-45-67», «7071234567», «87071234567» → `+77071234567`. Номера,
  которого нет по метаданным, с буквами или добавочным — нет (`Optional.empty()`, код поля `phone.invalid`).
  `isKazakhstanMobile()` — +7 7XX; у аккаунта принимается только такой номер (`iam.account.phone-not-kz-mobile`).
- `EmailAddress` — без крайних пробелов (любых, и неразрывного), в нижнем регистре (`Locale.ROOT`), до 254 символов,
  форма адреса: одна `@`, часть до неё до 64 символов, домен из частей через точку. Коды `email.invalid`,
  `email.too-long`. Кириллица в адресе допустима.
- Конструкторы принимают только уже приведённое значение (иначе `IllegalArgumentException`) — сущность и БД получают
  нормализованное; разбор ввода — только `parse`.

### Аккаунты: `Accounts`

- `create(NewAccount)` — телефон и e-mail в любой записи, ФИО, пароль. Все ошибки ввода — разом, 400 `error.validation`
  с `errors[]`: `phone`/`email` (`iam.account.contact-required`, если нет обоих), `fullName` (1–200 символов),
  `password` (`PasswordPolicy`). Пароль хэширует общий бин `PasswordEncoder` (Argon2id, раздел «Пароли») **вне транзакции
  БД**: `create(NewAccount)` = `prepare(NewAccount)` (проверка ввода и хэш, без БД) + `create(PreparedAccount)` (проверка
  занятости и запись). Вызывающий со своей транзакцией зовёт `prepare` до неё. Сам пароль не хранится и не логируется
  (`NewAccount.toString()` и `PreparedAccount.toString()` без значений).
- **Уникальность — 409 с полем** (IAM-01): сервис проверяет телефон и e-mail до записи — `iam.account.phone-taken` /
  `iam.account.email-taken`, в `errors[]` занятые поля. Индексы — страховка на гонку: одновременные регистрации с одним
  номером проходят проверку раньше, чем любая запишет; индекс пропускает одну, а сервис переводит нарушение
  `ux_account_phone`/`ux_account_email` (`DataIntegrity.isViolation`) в тот же ответ, а не в общий `error.duplicate`
  (`AccountsTests.concurrentCreatesWithOnePhoneGiveOneAccountAndSameConflict`). Внутри внешней транзакции (регистрация
  клиники, принятие приглашения) конфликт откатывает её целиком.
- Поиск: `findByPhone(PhoneNumber)`, `findByEmail(EmailAddress)` — по `lower(email)`, то есть по уникальному индексу.
  Проверка пароля — `passwordMatches(account, пароль)`; вход целиком, с фиктивной проверкой хэша и перехэшированием, —
  `SignIns` (раздел «Вход, выход и /api/v1/me»). Перехэширование — `upgradePasswordHash`: условная запись `where
  password_hash = <проверенный>` с ростом версии, свежие статус и хэш для перечитывания после входа — `credentials(id)`
  (JdbcTemplate, мимо сессии Hibernate).

### Членства: `Memberships` и `AccountMemberships`

- `Memberships` — только клиника контекста: `add(accountId, status, branchIds)` (второе в клинике — 409
  `iam.membership.exists`), `get(id)` (другой клиники — `NotFoundException`, 404), `ofAccount`, `list`,
  `limitToBranches`, `recordSignIn(accountId)` — время последнего входа в клинику запросом JPQL мимо сущности: вход не
  правка членства, версия не растёт, и администратор, правящий роли, не получит ложный 409. Без контекста —
  `MissingTenantContextException`. Филиалы загружаются вместе с членством
  (`@EntityGraph`): сервис отдаёт его за пределы транзакции.
- **Свои членства во всех клиниках — `AccountMemberships.of(accountId)`** (IAM-01: «входит одним паролем и видит обе
  клиники»). Вход и выбор клиники (T-IAM-02, T-IAM-08) узнают, куда можно войти, до выбора клиники, когда стандартная
  политика RLS не показывает ничего. Почему не обход RLS: роли с `BYPASSRLS` и корневого арендатора нет (запрещено), а
  функция `SECURITY DEFINER` не помогает — `FORCE` действует и на владельца таблицы. Поэтому вторая политика
  `own_memberships ON membership FOR SELECT USING (account_id = current_account_id())`:
  - `current_account_id()` — `app.account_id` или NULL (как `current_clinic_id()`); ставит её **только**
    `AccountMemberships`, транзакционно (`set_config(…, true)`), и снимает сразу после запроса — остаток транзакции
    вызывающего её уже не видит (`MembershipsTests.ownMembershipsReadLeavesNoAccountSettingInTransaction`);
  - политика только на чтение: изменить, удалить или вставить членство чужой клиники по ней нельзя, филиалов членства
    она не открывает (`ownMembershipsPolicyIsReadOnlyAndLimitedToOneAccount`);
  - отдаются только id членства, клиника и статус (отключённые тоже: вход покажет «Доступ отключён»). Название клиники,
    филиалы и прочее читаются уже в контексте каждой клиники — `TenantContext.run(Tenant.of(clinicId), …)`;
  - цена: у запросов к `membership` в плане — `OR` двух условий; таблица маленькая, индексы на `(clinic_id, …)` и
    `account_id` есть.

### Dev-сид

Только профиль `dev`: `iam.internal.DemoOwnerSeed` (`ApplicationRunner`) вызывает `clinic.api.DemoClinic.ensure()` —
клиника «Dentify Demo» и филиал «Абая, 150» с постоянными id (`DemoClinic.CLINIC_ID`, `BRANCH_ID`), вставка SQL в
контексте самой клиники (`clinic.internal.DemoClinicSeed`), — затем аккаунт `owner@demo.dentify.kz` с паролем из
`DENTIFY_DEV_OWNER_PASSWORD` и активное членство на все филиалы. Пароль генерирует `deploy/dev.ps1`/`dev.sh` (в старый
`.env` дописывается любой командой), в `application-dev.yml` — `dentify.dev.owner-password`. Сид идемпотентный: уже есть —
ничего не меняет, в том числе пароль (новый — после `reset-db -Force`); нет пароля или он не проходит правила — WARN и
пропуск, приложение стартует. Роль «Владелец» — T-IAM-06. В test и prod бинов сида нет.

### Тесты

| Тест | Что проверяет |
|---|---|
| `platform.api.PhoneNumberTests`, `EmailAddressTests` | приёмка IAM-01: «8 707 123 45 67», «+7(707)1234567», «7071234567» и ещё 7 записей — один номер; городской, российский, немецкий — не казахстанский мобильный; не номера; e-mail — пробелы любого вида, регистр, кириллица, punycode, 254 символа, 64 до `@`, неверные формы |
| `iam.internal.AccountsTests` | нормализация и хэш `{argon2}$argon2id$v=19$m=19456,t=2,p=1$`; поиск по любой записи номера и регистру e-mail; 409 с полем на занятый телефон и e-mail (и оба сразу); гонка 6 одновременных регистраций — один аккаунт и пять одинаковых 409; индексы и CHECK отклоняют запись в обход сервиса; все ошибки ввода разом; тексты всех кодов |
| `iam.internal.MembershipsTests` | членство в клинике контекста с филиалами; одно на аккаунт; приёмка: клиника A не видит членств клиники B (404, список, SQL под RLS); без контекста — отказ; приёмка: аккаунт с членствами в двух клиниках — один аккаунт и пароль, обе клиники видны; `own_memberships` только читает и только один аккаунт, настройка не остаётся в транзакции; филиалы: пусто — все, версия и время изменения меняются, устаревшая копия — конфликт версии, филиал другой клиники — 23503 |
| `iam.internal.DemoOwnerSeedTests`, `clinic.internal.DemoClinicSeedTests` | сид без пароля и со слабым паролем пропускается; повторный запуск не дублирует и не меняет пароль; демо-клиника и филиал создаются один раз |
| `platform.tenancy.RowLevelSecurityTests` | `account` — в реестре глобальных, `clinic` — корень, `own_memberships` — единственная политика сверх стандартной |

Запрещено:

- хранить телефон и e-mail не через `PhoneNumber`/`EmailAddress` (сырой ввод в сущность или запрос); искать e-mail без
  `lower(…)` — мимо индекса;
- свой кодировщик паролей и хэш мимо бина `PasswordEncoder`; пароль в логах, исключениях, `toString`;
- полагаться только на уникальный индекс: сервис проверяет занятость сам и отвечает своим кодом с полем;
- ставить `app.account_id` вне `AccountMemberships`; читать данные клиники поверх границы клиник иначе как через
  зарегистрированную политику `FOR SELECT`; отдавать из такого чтения больше, чем нужно выбору клиники;
- обратную сторону связи (`mappedBy`) для коллекций, по которым нужен конфликт версии владельца;
- сид с реальными данными; сид вне профиля `dev`; пароль сида в репозитории.

## Вход, выход и /api/v1/me

Вход по телефону или e-mail и паролю, выход, журнал входов (IAM-02, IAM-03), вошедший аккаунт и выбор клиники
(IAM-08). Код — `iam.internal`: `SignIns` (вход и выход), `AccountContexts` (что отдаёт `/me`, выбор клиники),
`AuthEvents` (журнал входов), `LoginName` (логин из одного поля); REST — `iam.web.AuthController`, `MeController`;
названия клиники и филиалов — `clinic.api.ClinicDirectory` (пока SQL, `clinic.internal.JdbcClinicDirectory`; сущности —
T-CLN-02). Миграция — `iam/V2026_09_26_1100__iam_auth_event.sql`. Сессия, cookie, CSRF и лимиты — платформа (раздел
«Безопасность и сессии»); здесь только то, что делает iam.

### API

| Запрос | Вход | Ответ |
|---|---|---|
| `GET /api/v1/auth/csrf` | нет | 204 и cookie `XSRF-TOKEN` (платформа). SPA вызывает при старте и шлёт значение в `X-XSRF-TOKEN` с каждым POST |
| `POST /api/v1/auth/login` `{login, password, rememberMe}` — все три обязательны | нет, CSRF нужен | 200 — тело как у `GET /me`, новые cookie `DENTIFY_SESSION` и `XSRF-TOKEN`; 400 `error.validation` (без `rememberMe` — `validation.not-null` по полю: он `Boolean` с `@NotNull`, а не `boolean`, иначе вышла бы ошибка формата значения); 401 `iam.login.invalid`; 403 `iam.login.access-disabled`; 429 `error.too-many-login-attempts`, а при пределе одновременных хэшей — `error.too-many-requests` |
| `POST /api/v1/auth/logout` | нет, CSRF нужен | 204, cookie сессии и CSRF-токена стёрты; без сессии — тоже 204 |
| `GET /api/v1/me` | да | `{account, memberships, context}` |
| `POST /api/v1/me/context` `{clinicId, branchId?}` | да, CSRF нужен | 200 — как `GET /me`; 404 — нет членства, приглашение или филиал недоступен; 403 `iam.membership.disabled` |

`GET /me`: `account` — `id`, `fullName`, `initials` (первые буквы двух первых слов ФИО, считает сервер), `language`,
`phone` (E.164), `email`; `memberships[]` — все членства аккаунта (в порядке создания, включая отключённые и приглашения):
`id`, `status` (`ACTIVE`/`DISABLED`/`INVITED`), `clinic {id, name, city}`, `branches[]` — филиалы, в которые можно войти
(у активного членства — доступные ему, иначе пусто); `context` — `{clinic, branch}` сессии или `null`. Роли и прав пока
нет (T-IAM-06, T-IAM-07): поля добавятся, не ломая ответ. Фото аккаунта (IAM-08, колонка `account.photo_key`) тоже пока
нет: ссылку на него даст файловое хранилище (T-PLT-20), загрузку — API профиля (T-IAM-24); поле `photoUrl` добавится
тогда же, а до него интерфейс показывает инициалы. `context: null` — интерфейс показывает выбор клиники.

**Цена `/me` (известная, принята для MVP).** `AccountContexts.overview` читает каждую клинику отдельно: на членство — две
транзакции в контексте его клиники (`ClinicDirectory.current` — клиника и филиалы, `Memberships.get` — членство с
филиалами), каждая со своей выдачей соединения и `set_config`, плюс аккаунт, свои членства и клиника контекста. У врача в
2–3 клиниках это 15–20 обращений к БД на `/me`, а `/me` интерфейс перезапрашивает при фокусе вкладки и после каждого 403
(IAM-08). Если замер (T-PLT-24) покажет, что это заметно, — читать клинику, её филиалы и филиалы членства одним проходом в
одной транзакции на клинику или кэшировать названия клиник.

### Вход

Порядок (`SignIns.login`), каждый шаг — по причине:

1. **Логин** (`LoginName`): есть `@` — e-mail, иначе телефон; нормализуется как у аккаунта (`EmailAddress`, `PhoneNumber`
   в E.164). По нормализованному значению считаются и лимиты: «8 707 123 45 67» и «+77071234567» — один счётчик.
2. **Лимиты до пароля** — `RateLimits.checkLogin`: 429 даже с верным паролем. В журнал (`LOCKED_OUT`) — первый отказ на
   логин за время отказа (`firstLoginRejection`, раздел «Лимиты частоты»). Остальные отказы не ищут аккаунт в БД и строк
   не пишут: отказ ничего не стоит нападающему, а строка журнала стоит БД.
3. **Пароль.** Аккаунт есть — `passwordMatches`. Нет (или ввод не телефон и не e-mail) — пароль всё равно проверяется
   против **хэша-пустышки**: хэш случайного пароля с текущими параметрами Argon2id, посчитанный при старте (иначе первый
   неизвестный логин отвечал бы вдвое дольше). Один и тот же ответ 401 `iam.login.invalid` «Неверный телефон, e-mail или
   пароль» и то же время (± 100 мс, приёмка IAM-02 и PLT-05). Предел одновременных хэшей ответил 429
   `error.too-many-requests` — пароль не проверен: `abandonLogin` освобождает место попытки, в журнал она не пишется (это
   отказ сервера, как 400 или 403 CSRF, а не попытка с исходом).
4. **Неверно** — `recordLoginFailure` (пятая за 15 минут закрывает вход), журнал `WRONG_PASSWORD`, при блокировке ещё
   `LOCKED_OUT`, 401.
5. **Верно** — `resetLogin`. Аккаунт `DISABLED` или все членства `DISABLED` — 403 `iam.login.access-disabled` «Доступ
   отключён. Обратитесь к владельцу клиники» и `ACCESS_DISABLED`. Только после верного пароля: иначе ответ выдал бы, что
   аккаунт есть. Без членств или с приглашением вход открыт — принять приглашение (T-IAM-13).
6. **Перехэширование** старого хэша (`upgradeEncoding`) — условной записью, не затирая пароль, сменённый в этот момент.
7. **Сессия** — `AccountSignIn.signIn`: новый id сессии, новый CSRF-токен, Запомнить. «Запомнить» — 30 дней **от входа**,
   продлевает только новый вход (PLT-04, раздел «Сессия»: иначе украденная cookie жила бы бессрочно). Текст IAM-03 прежде
   говорил «30 дней с продлением при активности»; 2026-09-26 он выровнен с PLT-04 в `backlog.json`.
8. **Перечитывание после входа** — новые запросы к БД, мимо сессии Hibernate: статус и хэш аккаунта (`credentials`),
   членства (`AccountMemberships.of`). Хэш не тот, что проверен, — `signOut`, `WRONG_PASSWORD`, 401; статус или все
   членства отключены — `signOut`, `ACCESS_DISABLED`, 403. Так вход, прочитавший аккаунт до коммита деактивации или смены
   пароля, не переживает её, даже если время входа позже `terminateAll` (раздел «Активные сессии»).
9. **Язык аккаунта** — в сессию (`dentify.account.language`, `iam.internal.AccountLanguage`): на нём API отвечает этому
   входу без запроса к БД («Сообщения и i18n»).
10. **Клиника сразу** — одно активное членство и один доступный филиал (`AccountContexts.autoSelect`): клиника и филиал в
   сессии, время последнего входа в членстве. Иначе `context: null`: несколько клиник, несколько филиалов, ни одного
   активного членства.
11. Журнал `SIGN_IN`, ответ — как `GET /me`.

Выход (`SignIns.logout`): `AccountSignIn.signOut` (отметка отзыва, сессия, cookie, CSRF-токен), затем `SIGN_OUT`. Без
сессии — 204 без события. Сервис входа нетранзакционный: сессия создаётся вне транзакции, перечитывание — отдельными
запросами, а клиника меняется только вне транзакции (`TenantContext.call`).

### Выбор клиники

`POST /api/v1/me/context` (`AccountContexts.select`) — единственное место, где клиника приходит от клиента, и она не
данные, а выбор: клиника ищется в своих членствах аккаунта (`AccountMemberships.of`, политика `own_memberships`).

- Нет членства или только приглашение — 404, как у чужого объекта; членство `DISABLED` — 403 `iam.membership.disabled`.
- Филиал — из доступных членству (`Membership.coversBranch`) филиалов этой клиники, иначе 404 (филиал другой клиники
  тоже). Без `branchId` — единственный доступный, если он один, иначе филиал не выбран (`context.branch: null`).
- Затем `TenantSession.select` и время последнего входа в членстве; дальше все запросы сессии идут в этой клинике
  (`TenantContextFilter`). Смена клиники — тот же запрос; id сессии не меняется (вход уже был).
- Всё, что читается о клинике (название, филиалы, членство), читается в контексте этой клиники
  (`TenantContext.call(Tenant.of(clinicId), …)`), то есть через RLS и `@TenantId`, а не в обход.
- DTO с `clinicId` в веб-слое запрещён ArchUnit-правилом; `SelectContextRequest` — единственная запись в реестре
  исключений `TenancyRulesTests.CLINIC_SELECTION_DTOS`. В ответах клиника — вложенным объектом `clinic {id, name, city}`.

### Журнал входов `auth_event`

**Глобальная таблица, без `clinic_id` и RLS** (`RowLevelSecurityTests.GLOBAL_TABLES`, решение 2026-09-26). Почему не по
клинике:

- попытка входа случается до выбора клиники, и у большинства событий клиники нет вовсе: неверный пароль, блокировка,
  вход аккаунта с членствами в нескольких клиниках (клиника выбирается позже, а может и не выбираться);
- у неизвестного логина нет даже аккаунта — строка без `account_id`; её не видит ни одна клиника, но IP и User-Agent нужны
  для разбора подбора;
- с RLS по клинике строку без клиники нельзя было бы вставить (политика `WITH CHECK`), а вторую политику на вставку
  соглашения запрещают.

Журнал клиники (экран «Входы и сессии», IAM-29) — события аккаунтов, у которых есть членство в клинике: запрос в
контексте клиники соединяет `auth_event` с `membership` под RLS, чужих людей он не видит. Если понадобится событие,
привязанное к клинике (сеанс завершён администратором клиники), способ хранения решает IAM-29; колонку `clinic_id` без
RLS в эту таблицу не добавлять (тест каталога требует RLS у любой таблицы с `clinic_id`).

| Колонка | Что |
|---|---|
| `id`, `at` | UUIDv7 и время (часы PostgreSQL) |
| `account_id` | аккаунт (FK) или NULL — неизвестный логин; введённый логин не хранится (это чей-то телефон или e-mail) |
| `kind` | `SIGN_IN`, `SIGN_OUT`, `WRONG_PASSWORD` (в том числе неизвестный логин и вход, у которого пароль сменили во время входа), `LOCKED_OUT` (попытка, закрывшая вход, и первая попытка на логин, отклонённая лимитом входа, за время отказа — не чаще раза в минуту), `ACCESS_DISABLED`. Смену и сброс пароля, принятие приглашения, завершение сеанса администратором добавят их задачи новой миграцией `CHECK` |
| `ip` | `inet` из `getRemoteAddr()` (за Caddy — клиент из `X-Forwarded-For`); наружу — только маской |
| `user_agent` | до 512 символов, для «браузер · ОС» на экране журнала |

- Запись — `AuthEvents.record` сразу, в своей транзакции (`REQUIRES_NEW`): событие неудачной попытки остаётся, хотя запрос
  заканчивается ошибкой, в том числе когда вызывающий — смена и сброс пароля, приглашение (T-IAM-13, 16, 24) — пишет
  его внутри своей транзакции и она откатывается (`AuthEventsTests`). В лог — только вид события и id аккаунта. IP —
  `IpAddresses.parse` (`platform.api`), тем же разбором, что у журнала действий; наружу — `IpAddresses.mask`.
- Только дописывается: у `dentify_app` отняты `UPDATE`, `DELETE`, `TRUNCATE`. Индекс `(account_id, at)` — журнал людей
  клиники и одного сотрудника. Хранение — не меньше года (IAM-29), удаления пока нет.
- Время — часы PostgreSQL: события одного аккаунта из разных запросов идут по `at`. На Windows часы контейнера (WSL2)
  иногда сдвигаются назад на доли секунды, поэтому тесты порядок событий разных запросов не сверяют.

### Тесты

| Тест | Что проверяет |
|---|---|
| `iam.SignInTests` | приёмка IAM-02: cookie HttpOnly, Secure, SameSite=Lax без срока, новый CSRF-токен, одна клиника с одним филиалом — клиника в сессии, событие входа с IP и User-Agent, время последнего входа без роста версии членства; телефон в любой записи; новый id сессии, прежняя сессия — 401, клиника, выбранная до входа, не переходит; неверный пароль, неизвестный e-mail, телефон и бессмысленный ввод — один ответ без сессии, в журнале — неверный пароль аккаунта и попытки без аккаунта; неизвестный логин отвечает за то же время (медианы, ± 100 мс) и проверяет пароль бином `PasswordEncoder` ровно один раз, как неверный пароль (без хэша-пустышки тест падает, замер времени — нет: хэш ~35 мс короче допуска); попытки, оборванные пределом хэшей, — 429 `error.too-many-requests`, места в лимите входа не держат и в журнал не пишутся; приёмка IAM-03: Запомнить — `Max-Age=2592000`, без — сессионная cookie; вход и выход без CSRF — 403; выход — 204, cookie стёрты, прежняя cookie — 401, сессии нет в Spring Session и в списке, второе устройство живо, событие выхода; выход без сессии — 204; отключённый аккаунт — 403 только с верным паролем; все членства отключены — 403, отключено и приглашение — вход без клиники; пятая неверная попытка (телефон в пяти записях) — блокировка в журнале, шестая с верным паролем — 429 «через 15 мин», следующие отказы в журнал не пишутся; несколько филиалов — клиника не выбрана, доступ к одному — выбран он; деактивация, смена пароля и отключение членства между проверкой пароля и `signIn` — выход, 403/401, в Valkey сессий нет; bcrypt перехэшируется в Argon2id с ростом версии; пустые поля — 400; без `rememberMe` — 400 `validation.not-null` по полю; журнал только дописывается; тексты кодов |
| `iam.MeTests` | без входа — 401; аккаунт (инициалы, язык, телефон, e-mail), членства с клиникой и доступными филиалами, без пароля и хэша; две клиники — выбор после входа, филиал явный, единственный или не выбран, время последнего входа; изоляция: чужая и выдуманная клиника, приглашение, филиал другой клиники и вне доступа — 404, отключённое членство — 403, клиника сессии прежняя, чужой клиники в членствах нет; записи клиники A не видны, пока выбрана B; выбор без CSRF — 403, без входа — 401 |
| `iam.internal.AuthEventsTests` | событие журнала входов остаётся после отката транзакции вызывающего |
| `iam.AccountPresentationTests` | 409 с `modifiedByName` — ФИО аккаунта с членством в клинике; аккаунту без членства имя не отдаётся; язык аккаунта `kk` — ответ на казахском вопреки cookie и `Accept-Language: ru`, `ru` — на русском вопреки `kk` браузера |
| `iam.internal.LoginNameTests` | телефон в любой записи — один логин и ключ лимитов, e-mail без пробелов и регистра, бессмысленный ввод — не контакт, `toString` без логина; инициалы |
| `clinic.ClinicDirectoryTests` | клиника контекста с филиалами по названию, чужого филиала нет, без контекста — отказ |
| `platform.security.RateLimitTests` | `recordLoginFailure` сообщает о блокировке ровно на пятой неверной попытке |
| `TenancyRulesTests.clinicSelectionDtosAreWebRecordsWithClinicId` | реестр исключений — только records веб-слоя, в которых клиника действительно есть |

Тестовая обёртка `iam.SignInInterlude` (`BeanPostProcessor` в тестовых исходниках, без `@MockitoBean`) выполняет действие
теста перед `signIn` одного аккаунта — так воспроизводится коммит между проверкой пароля и созданием сессии. Так же
`iam.PasswordChecks` оборачивает бин `PasswordEncoder`: для паролей, отмеченных тестом, считает `matches` или отвечает 429,
как предел одновременных хэшей; счёт — по паролю, а не по потоку (запросы теста сервер обслуживает в своих потоках). Люди и
членства тестов — `iam.IamFixtures`, шаги входа — `iam.LoginIntegrationTest`.

Проверено вручную (профиль dev, порты 18080/18081, Compose; 2026-09-26): миграция применилась к dev-базе, у
`dentify_app` на `auth_event` только `INSERT` и `SELECT`. Вход dev-владельцем `owner@demo.dentify.kz` curl-ом с cookie jar
по `http://localhost:18080` (curl, как и браузеры, шлёт Secure-cookie на `localhost`, но не на `127.0.0.1`) и CSRF-токеном
из `GET /api/v1/auth/csrf` — 200, cookie `DENTIFY_SESSION` HttpOnly, Secure, SameSite=Lax и новый `XSRF-TOKEN`, клиника
«Dentify Demo» и филиал «Абая, 150» выбраны сразу; `GET /api/v1/me` — 200; `POST /api/v1/me/context` — своя клиника 200,
выдуманная 404; выход без CSRF — 403, с ним — 204 и стёртые cookie; прежняя cookie после выхода — 401
`error.session-expired`; неизвестный логин — 401 `iam.login.invalid`. В `auth_event` — `SIGN_IN`, `SIGN_OUT` и
`WRONG_PASSWORD` без аккаунта, с IP и User-Agent; у членства — время последнего входа, версия 0. После правок проверки
(2026-09-26, так же): вход без `rememberMe` — 400 `validation.not-null` по полю «Заполните поле»; вход, `/me`, выход — как
выше; 9 неверных попыток на один логин — пять 401 и четыре 429, в `auth_event` — пять `WRONG_PASSWORD` и два `LOCKED_OUT`
(блокировка и первый отказ после неё), а не пять.

Проверить самому (Git Bash, приложение на 8080, пароль — `DENTIFY_DEV_OWNER_PASSWORD` из `deploy/.env`):

```bash
J=/tmp/jar; B=http://localhost:8080
curl -s -c $J -b $J $B/api/v1/auth/csrf
X=$(awk '$6=="XSRF-TOKEN"{print $7}' $J)
curl -s -c $J -b $J -H "X-XSRF-TOKEN: $X" -H 'Content-Type: application/json' \
  -d '{"login":"owner@demo.dentify.kz","password":"<пароль>","rememberMe":false}' $B/api/v1/auth/login
curl -s -b $J $B/api/v1/me
X=$(awk '$6=="XSRF-TOKEN"{print $7}' $J)   # вход сменил токен
curl -s -c $J -b $J -H "X-XSRF-TOKEN: $X" -X POST $B/api/v1/auth/logout
```

Запрещено:

- свой вход мимо `SignIns` (без лимитов до пароля, хэша-пустышки, перечитывания после `signIn` и журнала); разные ответы
  или время для неизвестного логина и неверного пароля; «Доступ отключён» до проверки пароля;
- проверять пароль неизвестного логина иначе, чем тем же `PasswordEncoder` против хэша с текущими параметрами, и считать
  хэш-пустышку лениво;
- ключ лимитов входа не из нормализованного логина (разные записи телефона — разные счётчики);
- класть клинику в сессию мимо `AccountContexts` (без проверки активного членства и доступного филиала) — другим
  модулям это не даст Modulith (`TenantSession` в `platform :: identity`, только iam); DTO с `clinicId` вне реестра
  `CLINIC_SELECTION_DTOS`;
- писать время последнего входа через сущность `Membership` (растёт версия — ложный 409 у администратора);
- менять или удалять строки `auth_event`, давать `dentify_app` `UPDATE`/`DELETE`; хранить введённый логин или пароль в
  журнале и логах; добавлять в `auth_event` колонку `clinic_id` без RLS.

## Права

Решение до T-IAM-06/07 (2026-09-26, по финальному ревью этапа 0): как модули проверяют права по клинике, не завися от
iam и не храня права в сессии. Сейчас из этого есть только `CurrentPermissions` и `Permissions` (`platform.api`) и
`hasAuthority('audit:read')` на журнале; `CurrentPermissions` пока читает authorities контекста Spring Security, их
выдают только тесты (`TestAuthorities`). Остальное делает T-IAM-07 по этому разделу.

- **Контракты — в `platform.api`, реализация — iam.** От iam не зависит ни один модуль (`ModularityTests.ALLOWED`),
  поэтому модули видят права только через платформу: `CurrentPermissions.has(код)` (есть), `CurrentMember` — членство
  и сотрудник текущей работы, `ScopeFilter` — скоуп «свои/все», филиалы, склады для запросов списков. Реализации — бины
  iam, SPI платформы; модуль импортирует только `platform.api`.
- **На эндпоинте — бин платформы для SpEL**: `@PreAuthorize("@perm.has('patient:view')")`. Бин `perm` объявляет
  платформа и делегирует SPI прав, который реализует iam. Ссылка `@perm` в SpEL — зависимость на этапе выполнения,
  Modulith её не видит, поэтому бин и должен быть в платформе, а не в iam. `hasAuthority(…)` на эндпоинтах T-IAM-07
  заменяет на `@perm`.
- **Права не хранятся в `Authentication` сессии.** Вход кладёт в сессию аккаунт без прав
  (`SessionAccountSignIn`): сессия живёт до 30 дней, и права в ней пережили бы смену роли администратором до нового
  входа, а при смене клиники остались бы правами прежней клиники. Эффективные права (роль ∩ филиалы членства)
  считаются **на запрос** по клинике контекста (`TenantContext`) и членству аккаунта в ней, с кэшем в Valkey: ключ —
  `clinicId`, `membershipId` и версия роли; сброс — по событиям `RolePermissionsChanged` и `MembershipChanged` (после
  коммита, через журнал публикаций T-PLT-14). Ключ кэша клиники — как у всего кэша клиники («Кэш»).
- **Перепроверка членства на каждом запросе** — тем же кэшем: нет активного членства в клинике сессии (отключено,
  удалено) — 401 `error.clinic-not-selected`, интерфейс показывает выбор клиники. Это закрывает окно, которое оставляет
  `AccountSessions.terminateInClinic` при выборе клиники одновременно с отключением членства («Активные сессии»).
- **Коды прав модуль объявляет сам** — бином SPI платформы (`PermissionContributor`: коды, подписи i18n, типы скоупа),
  а iam собирает каталог из всех бинов (T-IAM-06). Импорт iam или «регистрация в реестре iam» из модуля — зависимость
  модуль → iam, она запрещена (T-CLN-02 и следующие).
- **Тесты других модулей** получают права через платформу (тестовая реализация SPI прав, как сейчас `TestAuthorities`), а
  не аннотацией iam: тесты модулей не зависят от тестовых исходников iam.

Запрещено: права в `Authentication` сессии и в любых атрибутах сессии; бин прав для SpEL в модуле iam; импорт iam из
других модулей ради прав; `hasRole` и роли в коде — только коды прав.

## Модули и события

Модуль — прямой подпакет `kz.dentify.<модуль>`: `platform`, `iam`, `clinic`, `staff`, `patient`, `schedule`, `medical`,
`inventory`. Границы проверяет Spring Modulith: `ApplicationModules.verify()` в `ModularityTests` — нет циклов и обращений
во внутренние пакеты чужих модулей.

### Раскладка пакетов модуля

| Пакет | Что в нём | `package-info.java` |
|---|---|---|
| `kz.dentify.<модуль>` | только `package-info.java`: `@ApplicationModule(displayName, allowedDependencies)` | `@ApplicationModule`, `@NullMarked` |
| `api` | типы и сервисы, которые вызывают другие модули (`clinic.api.ClinicDirectory`); порты и SPI | `@NamedInterface("api")`, `@NullMarked` |
| `events` | события модуля для подписчиков: неизменяемые record с id, без ПД | `@NamedInterface("events")`, `@NullMarked` |
| `web` | контроллеры и DTO-records запросов и ответов; контроллер тонкий — параметры в сервис, ответ — record | `@NullMarked` |
| `internal` | сущности, репозитории, сервисы с правилами (`@Service`), dev-сиды | `@NullMarked` |

В каждом пакете — свой `package-info.java` с `@NullMarked`: аннотация пакета на подпакеты не распространяется («Код»).
`web` и `internal` другим модулям закрыты. Правила — в сервисе `internal`, контроллер их не повторяет (dentify R002):
ArchUnit `CodingRulesTests.webLayerIsThin` не даёт `web` ходить в репозитории и сущности, открывать транзакции и отдавать
не records.

### Направления зависимостей

Направления — здесь, в `package-info.java` каждого модуля (`allowedDependencies`) и в `ModularityTests.ALLOWED`; меняются
вместе, сначала решение владельца:

- clinic ← iam, staff, schedule, inventory; staff, patient ← schedule; medical → patient, schedule, clinic;
- все → platform: `platform :: api` — общий API платформы (исключения, `TenantContext`, `PageQuery`, `AuditService`,
  `PasswordPolicy`, `CurrentPermissions` и т. д.); platform — общий модуль (`sharedModules`);
- **только iam → `platform :: identity`**: клиника в сессии (`TenantSession`), вход и выход (`AccountSignIn`), активные
  сессии (`AccountSessions`), лимиты входа (`RateLimits`) и чтение журнала действий (`AuditJournal`, `AuditQuery`,
  `AuditEntry`). Это инварианты изоляции клиник: модуль, который сам положит клинику в сессию, отдаст ей чужую клинику
  без проверки членства. Поэтому они не в `api`, а в отдельном named interface, и `platform :: *` не пишется ни у кого:
  звёздочка открыла бы и его. Обращение из другого модуля роняет `verify()` («Module 'clinic' depends on named
  interface(s) 'platform :: identity'», проверено), список модулей с `identity` фиксирует
  `ModularityTests.identityPrimitivesAreOpenOnlyToIam`;
- циклы рвутся портами (SPI в `api` модуля или платформы, реализация — в модуле-потребителе) и событиями.

Другим модулям открыты только named interfaces:

- `api` — типы и сервисы, которые модуль-потребитель вызывает (`clinic :: api`, `patient :: api`, …);
- `events` — события модуля-издателя (`staff :: events`, `schedule :: events`), пакет
  `kz.dentify.<модуль>.events` с `@NamedInterface("events")`.

Событие убирает только обратную зависимость: издатель ничего не знает о подписчиках. Но слушатель
`@ApplicationModuleListener void on(StaffDeactivated event)` ссылается на тип события, и Modulith считает
это зависимостью подписчика от издателя. Поэтому подписка — это разрешённая зависимость на
`<издатель> :: events`, иначе `ApplicationModules.verify()` упадёт («Module 'iam' depends on named
interface(s) 'staff :: events'», проверено). Сейчас разрешены:

| Подписчик | Событие | Зависимость |
|---|---|---|
| iam | `StaffDeactivated` → гасит сессии сотрудника (PLT-24) | `staff :: events` |
| inventory | `AppointmentCompleted` → списание по нормам услуги | `schedule :: events` |

Циклов нет: staff и schedule не зависят ни от iam, ни от inventory. Новая подписка — сначала решение и
направление в `CLAUDE.md`, потом `package-info.java` и `ModularityTests.ALLOWED`.

Событие — неизменяемый `record` с идентификаторами (`clinicId`, `staffId`, …) без ПД: оно сериализуется
в журнал публикаций в БД.

### Журнал публикаций (PLT-24, T-PLT-14) — план, ещё не подключено

**Сейчас журнала публикаций нет**: в `pom.xml` только `spring-modulith-events-api` (аннотации), без
`spring-modulith-starter-jdbc` и таблицы `event_publication`. Асинхронный `@ApplicationModuleListener` без журнала при
исключении обработчика или рестарте приложения **теряет событие молча**: клиника остаётся без ролей, владелец — без прав.
Поэтому до T-PLT-14:

- `@ApplicationModuleListener` и асинхронные обработчики для критичных потоков (сид ролей и справочников новой клиники,
  сброс кэша прав, отключение доступа, списание склада) — **запрещены**. Задача с таким слушателем или событием зависит
  от T-PLT-14 (`dependsOn` в `backlog.json`: T-IAM-06, T-IAM-07, T-IAM-26, T-CLN-02, T-STF-09, T-STF-19, T-SCH-04,
  T-SCH-06, T-SCH-28, T-PAT-11, T-PAT-21, T-INV-06, T-MED-03 и всё, что после них);
- синхронная работа в той же транзакции — прямым вызовом порта, а не событием.

Решение для T-PLT-14 (при подключении `spring-modulith-starter-jdbc`):

- `spring.modulith.events.jdbc.schema-initialization.enabled: false` — по умолчанию Modulith создаёт
  таблицу сам, а у `dentify_app` нет прав на DDL, и приложение не стартует;
- таблица `event_publication` в структуре Modulith 2 (не legacy, `use-legacy-structure` не включаем)
  создаётся Flyway-миграцией модуля platform; `spring.modulith.events.jdbc.schema: dentify`;
- таблица глобальная: без `clinic_id` и RLS (её пишет инфраструктура Modulith вне контекста клиники).
  Клиника передаётся в самом событии, обработчик ставит контекст клиники из него;
- незавершённые публикации переотправляет задача по расписанию через `IncompleteEventPublications` под
  ShedLock (одним экземпляром), а не флагом `republish-outstanding-events-on-restart`: документация
  Modulith не рекомендует его при нескольких экземплярах;
- обработчики идемпотентны: повторная доставка не создаёт дублей.

## Логи

Правила для кода (PLT-10):

- В логи не пишем ПД: ни ФИО, ни ИИН, ни телефон, ни e-mail, ни адрес. Только идентификаторы
  (`clinicId`, `accountId`, `patientId`) и коды. Тела запросов и сущности или DTO пациента целиком
  (`log.info("{}", patient)`) не логируем.
- Маскирование — защитная сетка, а не разрешение. `platform.logging.PiiMaskingJsonMembersCustomizer`
  прогоняет каждое значение JSON-лога (сообщение, исключение, MDC, key-value любого типа) через
  `PiiMasker`: ИИН (12 цифр и 4-4-4), e-mail (включая кириллические), телефоны — `+7`/`7`/`8` и 10 цифр
  или местный номер из 10 цифр на 7 с любой группировкой (`+77071234567`, `8 707 123 45 67`,
  `8 (7172) 55-66-77`, `7071234567`), в том числе URL-кодированные (`%2B7%20707…`, `8+707+123…`).
  Точка и подчёркивание номер не разделяют, поэтому даты, суммы и версии миграций (`2026.10.01.1200`) целы.
- Ключ MDC или key-value, в имени которого есть `name`, `phone`, `iin`, `email`, `address`, `birth`,
  `patronymic` или `fio` (без учёта регистра, в любом месте: `patientFullName`, `doctorLastName`,
  `patientBirthDate`, `contact_phone`, `ipAddress`), маскируется целиком, как бы ни выглядело значение.
  Исключение — собственные поля форматов (`service.name`, `process.thread.name`, `logger_name`,
  `thread_name`, `_process_thread_name`, `_level_name`). Поэтому технические значения пишем под ключами
  без этих слов: `module`, `file`, `bucket`, а не `moduleName`, `fileName`, `bucketName`.
- ФИО в свободном тексте регулярным выражением не найти, поэтому маски для него нет. ФИО в логах —
  нарушение правила выше, а не пробел маски.
- Ошибки PostgreSQL приходят без значений строки: у драйвера `logServerErrorDetail: false` (раздел «Ошибки API»).
  Без этого Hibernate писал бы в лог WARN `Failing row contains (…)` со всеми полями строки, включая ФИО.
- Писать только через SLF4J. `System.out`, `System.err` и `printStackTrace()` идут мимо маски — их
  запрещает ArchUnit-тест `CodingRulesTests`.
- Маска работает только в структурированном формате. Он задан для консоли и для файла
  (`logging.structured.format.console/file: ecs`), JSON всегда в UTF-8 (`logging.charset.console`).

## Выключенная автоконфигурация

Инфраструктурные `@Enable…` включает платформа, каждый — рядом с тем, кому он нужен; в модулях их не повторять:

| Аннотация | Где | Зачем |
|---|---|---|
| `@EnableAsync` | `platform.tenant.TenantConfiguration` | `@Async` и `@ApplicationModuleListener`; перенос клиники в поток задачи |
| `@EnableScheduling` | `platform.audit.AuditConfiguration` | задачи по расписанию (партиции журнала, T-PLT-13); `TaskScheduler` создаёт Boot |
| `@EnableCaching` | `platform.cache` | Spring Cache в Valkey с префиксом клиники |
| `@EnableJpaAuditing` | `platform.persistence` | `created_by`/`updated_by` и время |
| `@EnableMethodSecurity` | `platform.security.SecurityConfiguration` | `@PreAuthorize` на эндпоинтах |

Что есть на classpath, но в MVP не нужно, выключено в `application.yml` (проверяет `InfrastructureConfigTests`):

- `spring.modulith.moments.enabled: false` — Moments Modulith (события `DayHasPassed`, `WeekHasPassed`, …)
  приходят со `spring-modulith-starter-core`. Их автоконфигурация включает `@EnableScheduling` без
  `TaskScheduler`, и в логе каждого старта было «No TaskScheduler/ScheduledExecutorService bean found».
  Свойства нет в метаданных Modulith 2.1.1 (IDE подсветит его), но его читает `@ConditionalOnProperty` у
  `MomentsAutoConfiguration`. Планировщик включён явно: `@EnableScheduling` в `platform.audit.AuditConfiguration`
  (T-PLT-13, партиции журнала действий), тогда Spring Boot сам создаёт `TaskScheduler` на виртуальных потоках. Задачи,
  которые должны идти одним экземпляром (переотправка публикаций, T-PLT-14), — под ShedLock.
- `spring.data.redis.repositories.enabled: false` — Valkey хранит только сессии, счётчики лимитов и кэш;
  репозитории Spring Data Redis (`@RedisHash`) не используем, данные — в PostgreSQL.
- `spring.autoconfigure.exclude: UserDetailsServiceAutoConfiguration` — Spring Boot не создаёт пользователя в памяти со
  сгенерированным паролем и не пишет этот пароль в лог WARN. Аккаунты — в БД (iam), вход — `AccountSignIn` (раздел
  «Безопасность и сессии»).

## Ошибки API

Любая ошибка API — `ProblemDetail` по RFC 9457 с типом `application/problem+json` (PLT-07):

```json
{
  "status": 400, "title": "Некорректный запрос", "detail": "Проверьте правильность заполнения полей.",
  "instance": "/api/v1/patients", "code": "error.validation", "traceId": "6ab649e81eab0751a95cfa72057a10af",
  "errors": [ { "field": "iin", "code": "patient.iin.checksum", "message": "Неверная контрольная сумма ИИН." } ]
}
```

- `code` — машинный код и одновременно ключ текста в `messages.properties`. `detail`, `title` и
  `errors[].message` — тексты из `MessageSource` на языке запроса, в коде их нет. `traceId` — тот же, что в
  JSON-логах этого запроса: пользователь сообщает его поддержке. `errors[]` есть, только если ошибка про поля.
  Поле `type` Spring не пишет, когда оно `about:blank`; по RFC 9457 отсутствие поля и означает `about:blank`.
- Как устроено (`platform.web`): `ProblemDetailsExceptionHandler` (`@RestControllerAdvice`, наследник
  `ResponseEntityExceptionHandler` Spring) ловит исключения контроллеров; `ProblemErrorController` заменяет
  `BasicErrorController` Boot и так же отвечает на всё, что Boot пересылает на `/error` (исключение или
  `sendError` в фильтре). Тело собирает `ProblemFactory`.
- `instance` — путь запроса, и у ошибок из фильтров тоже: исходный путь, а не `/error`. `errors[].field` — путь поля
  (`iin`, `tags[1]`, `title.ru`) или `null`, если ошибка про всю форму (ограничение класса).
- ERROR в лог пишется только с исключением. `sendError` с 5xx без исключения — WARN. Прямой запрос на `/error` (без
  пересылки ошибки) — 404 без записи в лог, иначе любой клиент засыпал бы лог и поднял алерты. Неизвестный путь — тоже
  404 без записи: WARN Spring MVC `PageNotFound` «No mapping for …» выключен
  (`logging.level.org.springframework.web.servlet.PageNotFound: error`, `ProblemDetailsTests.notFoundForUnknownPath`).

| Статус | Откуда | `code` |
|---|---|---|
| 400 | Bean Validation тела (`@Valid`), параметров (`@RequestParam @Max(100)`) и методов сервисов (`@Validated`, поле — путь внутри объекта или имя параметра), нет параметра, неверный тип, нечитаемый JSON, `InvalidRequestException` | `error.validation` с `errors[]`; `error.malformed-body` |
| 401 | нет входа (цепочка Spring Security, `@PreAuthorize` без входа, `UnauthorizedException`); cookie сессии есть, а сессии нет — истекла или завершена; обращение к данным клиники без клиники в сессии (`MissingTenantContextException`) | `error.unauthorized`, `error.session-expired`, `error.clinic-not-selected` |
| 403 | `ForbiddenException`, отказ `@PreAuthorize` с входом; изменяющий запрос без CSRF-токена или с чужим | `error.forbidden`, `error.csrf` |
| 404 | `NotFoundException`; `EntityNotFoundException` JPA и `ObjectRetrievalFailureException` Spring (ссылка на запись, которой нет или которую скрыла RLS); неизвестный путь; прямой запрос на `/error` | `error.not-found` |
| 405, 406, 413, 415 | стандартные исключения Spring MVC; заголовки `Allow` и `Accept` сохраняются; тело — ProblemDetail, даже если клиент просил не JSON | `error.method-not-allowed` и т. д. |
| 409 | `ConflictException`; конфликт `@Version` (Hibernate отклонил устаревшую версию или `checkVersion` в сервисе) — с полями `modifiedAt`, `modifiedBy`, `modifiedByName`; в БД — уникальность (SQLSTATE 23505), внешний ключ (23503), EXCLUDE (23P01) | `error.conflict`, `error.version-conflict`, `error.duplicate` (уникальность) |
| 422 | `BusinessRuleException` | код правила модуля |
| 429 | `TooManyRequestsException`, заголовок `Retry-After`: общий лимит API, лимиты входа и сброса пароля | `error.too-many-requests`, `error.too-many-login-attempts` |
| 500 | всё остальное: в ответе только общий текст и `traceId`, исключение — в лог ERROR с тем же `traceId`. Сюда же NOT NULL, CHECK, длина значения и прочие ограничения БД: значит, сервис пропустил проверку входных данных, это дефект, а не конфликт | `error.internal` |

Ошибки БД и ПД: в тексте ошибки PostgreSQL есть значения строки (`Key (iin)=(…) already exists`, `Failing row contains
(…)`), а Hibernate пишет этот текст в лог WARN. Поэтому у драйвера выключены подробности сервера
(`spring.datasource.hikari.data-source-properties.logServerErrorDetail: false`, настройка PgJDBC): в исключениях и логах
остаются только сообщение, SQLSTATE и имя ограничения. Обработчик тоже пишет в лог только SQLSTATE и имя ограничения
или колонки. Проверено на настоящих нарушениях через JPA (`DatabaseProblemsTests`).

Как пользоваться в модуле:

- Бросать исключения платформы из `platform.api` с кодом модуля:
  `throw new BusinessRuleException("schedule.backdated-edit", days)`, `throw new NotFoundException()`. Код и
  текст — в `messages.properties`, аргументы — `{0}`, `{1}`. Иерархия запечатана (`sealed`): статус задаёт класс,
  свои исключения модуля наследуют один из них (`PatientNotFoundException extends NotFoundException`).
- Ошибку поля, которую нашёл сервис, передавать нарушением:
  `throw new InvalidRequestException(FieldViolation.of("iin", "patient.iin.checksum"))`. Конфликт, привязанный к полю
  (телефон уже занят), — `ConflictException.ofFields("iam.account.phone-taken", List.of(FieldViolation.of("phone",
  …)), cause)`: 409 с `errors[]`.
- Код ошибки поля Bean Validation: своё сообщение-ключ (`@Pattern(message = "{patient.iin.format}")` или шаблон из
  валидатора) — код равен ключу; стандартное ограничение — `validation.<аннотация-через-дефис>`
  (`validation.not-blank`, `validation.size`), текст — по ключу Jakarta
  `jakarta.validation.constraints.NotBlank.message` с плейсхолдерами Hibernate Validator `{min}`, `{max}`,
  `{value}`. Текст подставляет Spring Boot из `MessageSource` на языке запроса.
- Общие коды — `ErrorCodes`, коды модулей — `<модуль>.<что>`, например `patient.iin.checksum`.
- Уникальность сервис проверяет сам и отвечает своим кодом с полем, ограничение БД — страховка на гонку. Сработавшее
  ограничение переводится в тот же ответ по имени — `DataIntegrity.isViolation(ex, "ux_account_phone")`
  (`platform.api`), а не своим разбором цепочки причин. Отпечатки SHA-256 (токены, ключи Valkey) — `Hashes`, IP —
  `IpAddresses` (`platform.api`); свои копии не писать.

Запрещено:

- текст ошибки в коде: `ResponseStatusException(…, "Пациент не найден")`, `ResponseEntity.badRequest().body("…")`,
  своё тело ошибки вместо ProblemDetail;
- ловить исключение в контроллере, чтобы вернуть текст или 200 с ошибкой;
- класть ПД в аргументы сообщений: сообщение исключения — только код, но текст с аргументами уходит пользователю;
- отвечать 403 на объект чужой клиники — только 404, чтобы не выдать, что объект есть;
- полагаться на NOT NULL и CHECK в БД вместо проверки входных данных: такое нарушение — 500 и ERROR. Проверять в DTO
  (Bean Validation) или в сервисе (`InvalidRequestException`, `BusinessRuleException`). Уникальность и пересечения
  сервис тоже проверяет сам со своим кодом, ограничение БД — страховка на гонку;
- бросать исключения платформы из фильтров там, где можно ответить: Tomcat пишет их в лог ERROR. Фильтр отдаёт ошибку
  через `HandlerExceptionResolver` (`resolver.resolveException(request, response, null, ex)`), тогда её обработает
  тот же `@RestControllerAdvice`.

Отказы Spring Security — тот же ProblemDetail: фильтры отдают их через `HandlerExceptionResolver`
(`platform.security.ProblemSecurityHandler` — `AuthenticationEntryPoint` и `AccessDeniedHandler`), а отказ
`@PreAuthorize` в методе ловит `ProblemDetailsExceptionHandler` (`AccessDeniedException` — 401 без входа, 403 с входом;
`AuthenticationException` — 401). Язык ответов из фильтров ставит `LocaleContextFilter` в начале цепочки тем же
`DentifyLocaleResolver`, что у DispatcherServlet (раздел «Безопасность и сессии»).

## Соглашения API и OpenAPI

Все модули работают через REST `/api/v1` (PLT-23):

- **Путь — всегда `/api/v1/<ресурс>`**: `/api/v1/patients/{id}`, `/api/v1/clinic/settings`, `/api/v1/me`. Путь без
  `/v1` не попадёт в группу springdoc `v1` (`pathsToMatch("/api/v1/**")`), а значит ни в `api/openapi.json`, ни в клиент
  orval. Пути в `backlog.json` выровнены 2026-09-26.
- **Открытые без входа пути — только два префикса**: `/api/v1/auth/**` — механика сессии (вход, выход, CSRF-токен, сброс
  пароля); `/api/v1/public/**` — открытые ресурсы до входа: регистрация клиники (`POST /api/v1/public/registrations`),
  города её формы (`GET /api/v1/public/cities`), приглашение по ссылке (`GET /api/v1/public/invitations/{token}`,
  `POST …/accept`). Оба — в `SecurityConfiguration.publicPaths`, с CSRF (изменяющий запрос без `X-XSRF-TOKEN` — 403) и
  общим лимитом API по IP; эндпоинт — `@PreAuthorize("permitAll()")`. В `TenantIsolationTests.NOT_CLINIC_DATA` открытый
  эндпоинт записывается **путём целиком, по одному, с причиной**: данные клиники под `/api/v1/public` бывают (приглашение
  по токену), и `/**` там не годится; изоляцию по токену проверяет тест модуля. Другие открытые префиксы не заводить.
- **operationId — явный** в `@Operation(operationId = "<глагол><Ресурс>")`: `listAuditEvents`, `getAuditEvent`,
  `terminateAccountSession`. Это имя хука и функции в клиенте orval; без него springdoc берёт имя метода и при совпадении
  дописывает `_1`, `_2` по порядку контроллеров — имена в клиенте менялись бы от нового контроллера
  (`OpenApiContractTests.operationIdsAreExplicitAndUnique`: явный у каждого эндпоинта `/api/v1`, уникальный, без `_N`).

- DTO запросов и ответов — `record` в пакете `web` модуля. Пакеты `@NullMarked`, и схему OpenAPI по `@Nullable`
  уточняет `RecordRequiredPropertiesConverter`:
  - компонент без `@Nullable` всегда есть в JSON и не null: в схеме он обязательный. Входящие поля без `@Nullable`
    проверяются `@NotNull` или `@NotBlank`. Обязательный флаг или число запроса — обёрткой с `@NotNull` (`@NotNull Boolean
    rememberMe`), а не примитивом: без поля Jackson 3 не заполнит примитив, и ответ был бы `validation.type-mismatch`
    «Неверный формат значения» вместо `validation.not-null` «Заполните поле»;
  - компонент с `@Nullable` Jackson пишет как `null` (`{"ru": "…", "kk": null}`, `"field": null`): в схеме он
    необязательный и допускает null — в OpenAPI 3.1 `type: [string, "null"]`, у ссылки на схему
    `oneOf: [{$ref}, {type: "null"}]`. orval даёт `kk?: string | null`, zod-схема принимает и null, и отсутствие поля.
    Проверяет `OpenApiContractTests.nullableRecordComponentsAllowNull`.
- id — UUIDv7 строкой; даты и время — ISO 8601 (`OffsetDateTime` с поясом, `Instant` в UTC, `LocalDate`); деньги и
  все `BigDecimal` — десятичной строкой `"12500.50"` (`JsonConventions`, в запросе принимается и число). Двуязычные
  поля — `LocalizedText {ru, kk}`.
- Список — `PageResponse<T> {items, page, size, total}`, параметры — `PageQuery`:

  ```java
  @GetMapping
  PageResponse<PatientRow> list(@SortBy(value = { "lastName", "createdAt" }, defaultSort = "lastName") PageQuery query) {
      return PageResponse.of(this.patients.search(query.q(), query.pageable()), PatientRow::from);
  }
  ```

  `page` — с 0, по умолчанию 0, смещение `page × size` не больше 2 147 483 647 (JPA принимает его как `int`, иначе
  Spring Data бросил бы исключение и был бы 500) — дальше 400 по полю `page`; `size` — от 1, по умолчанию 25
  (PLT-22), больше 100 — вернётся 100, это не ошибка; `sort=lastName,-createdAt` или повтором параметра, минус — по убыванию, только поля из `@SortBy` (у них
  должен быть индекс); `q` — строка поиска без крайних пробелов, пустая — `null`, длиннее 100 символов — 400. Все
  ошибки параметров — разом, одним 400 с `errors[]`. Разбирает `PageQueryArgumentResolver`. Фильтры — отдельные
  параметры эндпоинта.

Спецификация OpenAPI 3.1 (springdoc 3.1.1, собран против Boot 4.1):

- группа `v1` — пути `/api/v1/**`, адрес `/v3/api-docs/v1`. Спецификация включена в dev и test, в prod выключена;
- Swagger UI (`http://127.0.0.1:8080/swagger-ui.html`) — только локально. В основной зависимости только
  `springdoc-openapi-starter-webmvc-api`, UI (`-webmvc-ui`) подключает Maven-профиль `dev`: запуск
  `./mvnw -Pdev spring-boot:run`, в IDE — отметить профиль `dev` в Maven. Обычная сборка, тесты и jar для стенда и
  прода UI не содержат. Страховка на случай UI на classpath: вне dev `springdoc.swagger-ui.enabled: false`, а
  `spring.web.resources.add-mappings: false` выключает раздачу статики Boot (`/webjars/**`, `/**` из classpath) во
  всех профилях — бэкенд отдаёт только API, SPA раздаёт Caddy. Swagger UI в dev отдаёт свой обработчик springdoc на
  `/swagger-ui/**`;
- `OpenApiConfiguration` дописывает соглашения: у каждой операции ответы 4xx/5xx — `application/problem+json` со
  схемой `ProblemDetail` (всем — 400, 401, 403, 429, 500; с параметром пути — 404; POST, PUT, PATCH, DELETE — 409 и
  422), у 429 — заголовок `Retry-After` (целые секунды, `OpenApiContractTests.tooManyRequestsDocumentsRetryAfter`), `PageQuery` раскрыт в `page`, `size`, `sort` (значения из `@SortBy`) и `q`, `BigDecimal` — строка `decimal`;
- тест `OpenApiContractTests` выгружает спецификацию в [../api/openapi.json](../api/openapi.json) (коммитится, LF;
  по ней orval генерирует клиент фронта) и проверяет, что все ответы 4xx/5xx — ProblemDetail. Пока настоящих
  эндпоинтов нет, то же проверяется на пробной группе `probes` из тестовых исходников. Поменяли контроллер или DTO —
  прогоните тесты и закоммитьте изменившийся `openapi.json` вместе с кодом. `../verify.ps1` (T-PLT-15) это сторожит: шаг
  `api` красный, если сборка переписала спеку, а `pnpm api:check` — если клиент orval от неё отстал.

Запрещено: пути API без `/v1`; открытые без входа пути вне `/api/v1/auth/**` и `/api/v1/public/**`; эндпоинт без явного
`operationId`; `Map`, `JsonNode` и сущности JPA в ответах вместо record (ловит ArchUnit); свой формат страницы или параметры
`pageNumber`/`limit`; сортировка по полю не из `@SortBy`; фильтрация, сортировка и пагинация на фронте (R002);
глобальный `springdoc.paths-to-match` — в springdoc он перекрывает пути всех групп; `springdoc-openapi-starter-webmvc-ui`
в основных зависимостях и включение `spring.web.resources.add-mappings`; поле, которое бывает null, без `@Nullable` —
схема скажет «всегда есть», и zod отвергнет ответ.

## Сообщения и i18n

Интерфейс MVP — только русский, но бэкенд готов к казахскому без правок кода (PLT-11, dentify R003).

- Тексты — `MessageSource` Spring Boot. Русский — базовый файл `messages.properties`, а не
  `messages_ru.properties`: автоконфигурация `MessageSourceAutoConfiguration` включается, только если есть
  `<basename>.properties` (проверено по Boot 4.1.1). Казахский — `messages_kk.properties` с теми же ключами (PLT-34);
  нет ключа в kk — русский. `spring.messages.fallback-to-system-locale: false`: без перевода берётся базовый файл, а
  не язык ОС сервера. Тексты с аргументами идут через `MessageFormat`: апостроф в них пишется двумя.
- Язык запроса выбирает `platform.i18n.DentifyLocaleResolver` (бин `localeResolver`), первое поддерживаемое из
  цепочки: язык аккаунта (`AccountLocaleProvider` из `platform.api`; реализация — `iam.internal.AccountLanguage`: вход
  кладёт `account.language` в атрибут сессии `dentify.account.language`, и запрос не читает аккаунт из БД; смена языка в
  профиле, T-IAM-24, пишет его через `AccountLanguage.remember` в текущую сессию, другие сессии аккаунта получат новый
  язык при следующем входе) → cookie `dentify-lang` →
  `Accept-Language` (`kk-KZ` даёт `kk`) → `ru`. Поддерживаемые языки задаёт флаг `dentify.i18n.kk`: при `false`
  (сейчас) — только `ru`, звенья с казахским пропускаются. Язык запроса доступен через `LocaleContextHolder`: по нему
  выбираются тексты ProblemDetail и сообщения Bean Validation.
- Вне HTTP-запроса (фоновые задачи, письма, обработчики событий) `LocaleContextHolder` отдаёт язык по умолчанию. Его
  при старте ставит `I18nConfiguration` — русский, а не язык ОС сервера: иначе на машине с казахской локалью
  `LocalizedText.text()` отдал бы казахский и при `dentify.i18n.kk: false` (`I18nConfigurationTests`). Письмо на языке
  получателя — `text(locale)` и `MessageSource` с явным языком аккаунта.
- Включить казахский = положить `messages_kk.properties` в `src/main/resources` и поставить `dentify.i18n.kk: true`.
  Это доказывает `LocalizationTests`: в профиле test лежит тестовый словарь kk и включён флаг, и тот же эндпоинт
  отвечает на казахском по `Accept-Language: kk` или cookie.
- Двуязычные значения справочников — `LocalizedText {ru, kk}` из `platform.api`: `ru` обязателен, пустой `kk`
  хранится как `null`, `text(locale)` и `text()` отдают казахский, если он есть и язык казахский, иначе русский. В
  сущности — одна колонка `jsonb` через JSON-агрегат Hibernate 7:

  ```java
  @Embedded
  @JdbcTypeCode(SqlTypes.JSON)
  private LocalizedText name;   // name jsonb NOT NULL; в JPQL: where s.name.ru = :ru
  ```

  В DTO — тот же тип, `{"ru": "…", "kk": null}`, проверяется через `@Valid`. JSON-колонки Hibernate пишет Jackson 3
  (`hibernate.type.json_format_mapper: jackson3`): Jackson 2 на classpath только транзитивно, через springdoc.
- `MessagesTests` проверяет, что у каждого кода `ErrorCodes`, каждого статуса и каждого стандартного ограничения
  Jakarta есть русский текст, а в переводах нет ключей, которых нет в базовом файле.

Запрещено: пользовательские строки в Java-коде (ответы, исключения, письма); `messages_ru.properties`; язык из
`Locale.getDefault()`; отдельные колонки `name_ru`/`name_kk` вместо `LocalizedText`.

## Код

- `@NullMarked` (JSpecify) стоит в `package-info.java` каждого пакета: модуля, `api`, `events` и
  внутренних. Аннотация пакета на подпакеты не распространяется, поэтому новый пакет — сразу со своим
  `package-info.java`. `null` допустим только там, где явно стоит `@Nullable`. Проверку при сборке
  (NullAway) можно подключить позже.
- В Javadoc рабочих классов не использовать двойные кавычки и непарные скобки: Documenter Modulith
  2.1.1 разбирает собранный Javadoc простым JSON-парсером, и `ModuleDocumentationTests` падает.
- Пробные бины для тестов — top-level классами в тестовых исходниках (`platform.ProblemProbeController`,
  `ProblemProbeFilter`, `DataProbeController` с сущностью `ConstraintProbe`, `ValidatedProbeService`, группа springdoc
  `probes`; ресурс клиники `platform.tenancy.TestNoteController` с сущностью `TestNote`, пробный вход в клинику
  `TenantSessionProbeController` (с аккаунтом и правами — `platform.security.TestAuthorities`), снимки аудита пробной
  записи `TestNoteSnapshots`, имена вымышленных аккаунтов и счёт обращений за ними — `TestAccounts`; адрес клиента за прокси —
  `platform.SchemeProbeController`; `platform.security.SecuredProbeController` и запрос в полёте `SessionHoldProbeController`; перенос записи для журнала
  `platform.audit.AuditProbeController` с `AuditProbeAppointments`, неверное применение `@Audited` — `AuditMisuseProbe`;
  вымышленные клиники и филиалы в БД, пока у clinic нет API создания, — `clinic.TestClinics`),
  пути — вне `/api/**`, чтобы не попасть
  в `api/openapi.json`. Исключение — пробный вход `platform.security.SignInProbeController` на
  `/api/v1/auth/test-sign-in`: он идёт через цепочку приложения с CSRF, как будущий вход, и скрыт `@Hidden`; клинику потока
  сразу после входа он отдаёт заголовком `X-Test-Clinic-After-Sign-In`. Пути
  `/test/**` в тестах обслуживает своя цепочка Spring Security без входа и CSRF (`ProbeSecurityConfiguration`). Тестовые таблицы — миграциями
  `src/test/resources/db/test-migration` (подключены только в профиле test), тестовые тексты —
  `test-messages.properties`.
