# backend

Spring Boot 4.1.1, модульный монолит на Spring Modulith 2.1.1, Java 25, Maven (`./mvnw`).
Соглашения и правила — [../CLAUDE.md](../CLAUDE.md), миграции и колонки —
[../docs/architecture.md](../docs/architecture.md), граф модулей — [../docs/modules/](../docs/modules/).

## Запуск

Сначала поднимите окружение: `../deploy/dev.ps1 up` (подробности в [../README.md](../README.md)).

```powershell
$env:JAVA_HOME = "$HOME\.jdks\openjdk-25.0.2"   # JDK 25; с системной Java 17 сборка остановится на enforcer
./mvnw spring-boot:run                          # профиль dev
./mvnw -Pdev spring-boot:run                    # то же со Swagger UI (Maven-профиль dev)
./mvnw verify                                   # все тесты, нужен запущенный Docker
./mvnw test -Dtest=ModularityTests              # только границы модулей
```

```bash
export JAVA_HOME="$HOME/.jdks/openjdk-25.0.2"; export PATH="$JAVA_HOME/bin:$PATH"
./mvnw spring-boot:run
```

| Порт | Что |
|---|---|
| `127.0.0.1:8080` | API; в dev ещё спецификация `/v3/api-docs/v1`, а с `-Pdev` — Swagger UI `/swagger-ui.html` (в jar без `-Pdev` UI нет) |
| `127.0.0.1:8081` | actuator: `/actuator/health/liveness`, `/actuator/health/readiness` (БД + Valkey), `/actuator/prometheus` (с бакетами `http_server_requests` для p95), `/actuator/info` (версия и время сборки), `/actuator/modulith` (структура модулей) |

Actuator есть только на порту 8081 (`management.server.port`). Caddy и прод-прокси ведут на 8080,
поэтому снаружи метрик не видно.

**Демо-данные в dev.** При старте с профилем `dev` сид создаёт вымышленную клинику «Dentify Demo» с филиалом
«Абая, 150» (Алматы) и её владельца: логин `owner@demo.dentify.kz`, пароль — `DENTIFY_DEV_OWNER_PASSWORD` из
`../deploy/.env` (случайный для этой машины; в старый `.env` его дописывает любая команда `deploy/dev.ps1`). Сид
ничего не меняет, если всё уже есть; другой пароль из `.env` применится после `deploy/dev.ps1 reset-db -Force`.
Роли у владельца появятся с каталогом прав (T-IAM-06), вход по паролю — T-IAM-02.

## Профили

| Профиль | Где | Откуда настройки |
|---|---|---|
| `dev` | локально, `./mvnw spring-boot:run` (включён по умолчанию в `pom.xml`) | `application-dev.yml`: сервисы из `deploy/compose.dev.yml`, пароли из `../deploy/.env` (запускать из `backend/`), переменные окружения важнее |
| `test` | интеграционные тесты | `src/test/resources/application-test.yml`, контейнеры `AbstractIntegrationTest` |
| `prod` | стенд и прод | `application-prod.yml`, всё из переменных окружения: `DENTIFY_DB_URL`, `DENTIFY_APP_PASSWORD`, `DENTIFY_OWNER_PASSWORD`, `VALKEY_HOST`, `VALKEY_PORT`, `VALKEY_PASSWORD`, `DENTIFY_ENVIRONMENT`; необязательно — `DENTIFY_SECURITY_GEOIP_DATABASE` (путь к GeoIP-базе) |

## GeoIP-база

Город в списке активных сессий определяется по IP из локального файла mmdb, без внешних вызовов. Файла нет — город
пустой, остальное работает. Бесплатная база — DB-IP IP to City Lite (лицензия CC BY 4.0: на экране с городом
обязательна подпись «IP Geolocation by DB-IP» со ссылкой на https://db-ip.com). В репозиторий её не кладём
(`deploy/geoip/` в `.gitignore`), новая версия выходит в начале каждого месяца.

```bash
# из apps/dentify (Git Bash, Linux, macOS); в первые дни месяца, пока новой версии нет, — прошлый месяц
mkdir -p deploy/geoip
curl -fL "https://download.db-ip.com/free/dbip-city-lite-$(date +%Y-%m).mmdb.gz" | gunzip > deploy/geoip/dbip-city-lite.mmdb
```

Профиль dev ищет файл по пути `../deploy/geoip/dbip-city-lite.mmdb` (`dentify.security.geoip.database` в
`application-dev.yml`), на стенде и в проде путь задаёт `DENTIFY_SECURITY_GEOIP_DATABASE`, файл обновляется раз в месяц
(T-PLT-25). При старте в логе — `GeoIP-база DBIP-City-Lite от …` или сообщение, что базы нет.

## Устройство

- `kz.dentify.<модуль>` — модули `platform`, `clinic`, `iam`, `staff`, `patient`, `schedule`,
  `medical`, `inventory`. В `package-info.java` каждого модуля указаны `@ApplicationModule` и
  разрешённые зависимости. Другим модулям открыты только пакеты `api` (`@NamedInterface("api")`) и
  `events` у staff и schedule (`@NamedInterface("events")`, на них подписаны iam и inventory). platform —
  общий модуль (`@Modulithic(sharedModules = "platform")`). Бизнес-кода пока нет.
- БД: Flyway ходит ролью `dentify_owner`, приложение — `dentify_app` (только DML, RLS действует).
  Миграции лежат в `src/main/resources/db/migration/<модуль>/`. При старте
  `platform.database.ApplicationRoleCheck` не даёт приложению работать ролью, которая обходит RLS.
- Логи — JSON (ECS, UTF-8) в stdout с `traceId`/`spanId`. ИИН, телефоны и e-mail в любом значении
  (сообщение, исключение, MDC, key-value любого типа), а поля с ФИО по имени ключа маскирует
  `platform.logging.PiiMaskingJsonMembersCustomizer`, подключённый через `META-INF/spring.factories`,
  поэтому выключить его настройкой нельзя. Правила логов — [../docs/architecture.md](../docs/architecture.md), «Логи».
- API: ошибки — ProblemDetail (RFC 9457) с `code`, `traceId` и `errors[]`, тексты из `messages.properties`
  (`platform.web`); списки — `PageQuery`/`PageResponse`; язык ответа — `platform.i18n` (сейчас только русский,
  казахский — флаг `dentify.i18n.kk`). Спецификация OpenAPI 3.1 выгружается тестом в `../api/openapi.json`.
  Правила — [../docs/architecture.md](../docs/architecture.md), «Ошибки API», «Соглашения API и OpenAPI»,
  «Сообщения и i18n».
- Безопасность (`platform.security`): Spring Security с сессией в Valkey (Spring Session, cookie `DENTIFY_SESSION`),
  CSRF для SPA (`XSRF-TOKEN` + `X-XSRF-TOKEN`), Argon2id и правила пароля, лимиты Bucket4j, заголовки безопасности;
  активные сессии своего аккаунта — `GET/DELETE /api/v1/account/sessions` (iam). Без входа открыт только
  `/api/v1/auth/**`. Правила — [../docs/architecture.md](../docs/architecture.md), «Безопасность и сессии».
- Журнал действий (`platform.audit`): `AuditService` и `@Audited` пишут события в `audit_log` (помесячные партиции,
  только INSERT) в транзакции изменения, разница «было → стало» по снимкам без секретов, просмотры медкарты — не чаще
  раза в 15 минут (Valkey); чтение — `GET /api/v1/audit-events` (iam, право `audit:read`). Партиции на месяцы вперёд
  создаёт задача по расписанию. Правила — [../docs/architecture.md](../docs/architecture.md), «Журнал действий».
- Сборка: процессоры аннотаций заданы явно (JDK 23+ иначе их не запускает): `spring-modulith-apt` кладёт
  Javadoc в канвы модулей, `spring-boot-configuration-processor` — метаданные своих настроек.

## Тесты

| Тест | Что проверяет |
|---|---|
| `ModularityTests` | `ApplicationModules.verify()`; найдены все 8 модулей; разрешённые зависимости совпадают с направлениями из CLAUDE.md; каждый named interface из них (`api`, `events`) объявлен |
| `CodingRulesTests` | ArchUnit: нет `System.out`, `System.err` и `printStackTrace()` в рабочем коде; у каждого эндпоинта контроллера `@PreAuthorize` (фикстуры `archfixtures.web`) |
| `ModuleDocumentationTests` | пересобирает `../docs/modules/` (Documenter: C4-диаграммы PlantUML, канвы модулей) |
| `PiiMaskerTests`, `StructuredLogMaskingTests` | маскирование ИИН, телефона и e-mail (в том числе URL-кодированных и кириллических): сама функция и настоящий JSON-энкодер Boot, включая key-value не-строкового типа и поля с ФИО |
| `DatabaseSetupTests` | V1 применена от `dentify_owner`, история миграций одна; приложение ходит `dentify_app` без лишних атрибутов и прав, DDL и чтение истории Flyway ему запрещены; есть `uuidv7()` и расширения |
| `ApplicationRoleCheckTests` | проверка роли при старте: `dentify_app` проходит, суперпользователь и владелец схемы — нет |
| `ActuatorTests` | liveness/readiness/prometheus (с гистограммой запросов)/info/modulith на порту управления; на порту приложения `/actuator/**` — 404 |
| `ForwardedHeadersTests` | за прокси `X-Forwarded-Proto: https` от внутреннего адреса делает запрос защищённым (`server.forward-headers-strategy: native`) |
| `TraceIdLoggingTests` | лог внутри HTTP-запроса — JSON с traceId/spanId и без ПД (пробный `LogProbeController` из тестовых исходников) |
| `ProblemDetailsTests` | каждый код ответа 400/401/403/404/405/406/409/415/422/429/500 — ProblemDetail с `code`, `traceId`, текстом из `messages.properties` и `errors[]` (в том числе проверка сервиса `@Validated` и ошибка всей формы с `field: null`); 500 без стектрейса и SQL, тот же traceId в логе; ошибки фильтров через `/error` с исходным `instance`, прямой `/error` — 404 без ERROR (пробные `ProblemProbeController`, `ProblemProbeFilter`, `ValidatedProbeService`) |
| `DatabaseProblemsTests` | настоящие ошибки PostgreSQL через JPA: уникальность и устаревшая `@Version` — 409, NOT NULL и CHECK — 500 и ERROR, нет записи — 404; значения строки не попадают ни в ответ, ни в лог (пробные `DataProbeController`, `ConstraintProbe`) |
| `LocalizationTests`, `DentifyLocaleResolverTests`, `I18nConfigurationTests` | цепочка языка аккаунт → cookie → Accept-Language → ru; тестовый словарь kk и флаг `dentify.i18n.kk` переключают ProblemDetail без правок кода; при выключенном флаге — только ru; вне запроса — ru, а не язык ОС |
| `PageQueryTests`, `JsonConventionsTests` | `page` (смещение в пределах `int`)/`size` (больше 100 — 100)/`sort`/`q` и `PageResponse`; деньги — строкой, даты — ISO 8601 |
| `AuditLogTests`, `AuditDiffTests`, `AuditTextsTests` | журнал действий: событие с разницей, автором и IP в транзакции изменения, откат — без события; UPDATE/DELETE/TRUNCATE запрещены роли приложения и владельцу; окно просмотров 15 мин; секреты без значений; маска ИИН и телефона без права; фильтры и страницы API, изоляция клиник, предел выгрузки, партиции заранее |
| `OpenApiContractTests` | OpenAPI 3.1: выгрузка в `../api/openapi.json`, все ответы 4xx/5xx — схема ProblemDetail, параметры страницы, обязательные поля record и null у `@Nullable`; Swagger UI и его webjar вне dev нет |
| `LocalizedTextTests`, `LocalizedTextMappingTests` | `LocalizedText {ru, kk}` с фолбэком на ru; JSON-агрегат Hibernate в `jsonb` (таблица из тестовой миграции `db/test-migration`) |
| `MessagesTests` | русский текст у каждого кода платформы, статуса и стандартного ограничения; в переводах нет лишних ключей |
| `SessionSecurityTests`, `CsrfTests`, `SecurityHeadersTests` | 401 и 403 — ProblemDetail без редиректов; cookie сессии HttpOnly, Secure, SameSite=Lax; 401 после 12 ч бездействия и через 30 дней от входа (с «Запомнить» и без) при любой активности; новый id сессии и CSRF-токена при входе; выход; POST без `X-XSRF-TOKEN` — 403; CSP, HSTS, frame-ancestors, nosniff, Referrer-Policy, Permissions-Policy |
| `RateLimitTests`, `PasswordTests`, `SessionClientsTests` | лимиты входа (5 неверных за любые 15 мин — блок 15 мин от пятой), сброса пароля (3 за любой час) и API (429 с `Retry-After`), ключи в Valkey — хэши; `{argon2}` и правила пароля; маска IP, браузер, город по тестовой GeoIP-базе |
| `iam.AccountSessionsTests`, `iam.internal.OneTimeTokensTests` | список и завершение своих сессий, после завершения — 401 быстрее 5 с; токены ссылок: 256 бит, в БД только SHA-256, одноразовые, гаснут по сроку и при смене пароля |

Интеграционные тесты наследуют `AbstractIntegrationTest`. Контейнеры PostgreSQL 18.6 и Valkey 8.1.10
запускаются один раз на прогон и подключаются через `@ServiceConnection`, эндпоинты проверяются через
`RestTestClient` (поле `http`); вошедшая сессия с CSRF-токеном — `signIn(accountId)`. Конфигурация у всех наследников одна, поэтому Spring-контекст на прогон
тоже один. В PostgreSQL выполняется
тот же `deploy/postgres/init/01-roles.sh`, что и в dev, поэтому роли и права в тестах такие же.
