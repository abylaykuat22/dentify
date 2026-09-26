export const meta = {
  name: 'dentify-phase0-backend',
  description: 'Dentify этап 0, бэкенд: хвосты ревью, ProblemDetail/OpenAPI/i18n, тенант+RLS, безопасность, аудит, аккаунты, вход — по задаче с проверкой, затем общее ревью',
  phases: [
    { title: 'Tasks', detail: 'реализация по задаче: impl → review → fix' },
    { title: 'Final review', detail: 'сквозная сборка + архитектура + безопасность вживую' },
    { title: 'Final fix', detail: 'исправления по общему ревью' },
  ],
}

const APP = 'C:/Users/abyla/code/apps/dentify'
const BASE = `Ты работаешь над проектом Dentify в ${APP} (Windows; Git Bash и PowerShell). СНАЧАЛА прочитай ${APP}/CLAUDE.md и ${APP}/docs/architecture.md целиком — правила (dentify R002 «вся логика на бэкенде», R003 «только русский, но готово к переводу: коды + MessageSource», R021 «сначала официальное готовое»), окружение (JDK 25: export JAVA_HOME="$HOME/.jdks/openjdk-25.0.2"; export PATH="$JAVA_HOME/bin:$PATH"; системная java 17 не годится), соглашения по миграциям, ролям БД, модулям, логам. Бэкенд — ${APP}/backend (Spring Boot 4.1.1, Modulith 2.1.1, Hibernate 7, Flyway, Testcontainers 2), окружение Compose поднимается ${APP}/deploy/dev.ps1 up (PostgreSQL 18 на 127.0.0.1:5433, Valkey 6379 с паролем, Mailpit, MinIO; пароли в ${APP}/deploy/.env). Docker Desktop запущен. Задачу и требования (описание + критерии приёмки) достань из ${APP}/docs/backlog.json командой из CLAUDE.md; план — C:/Users/abyla/code/wiki/projects/dentify/mvp-plan.md (Grep по нужному разделу). Spring Boot 4 / Security 7 / Session / Modulith 2 / Hibernate 7 / springdoc 3 / Bucket4j — сверяй с актуальной документацией через MCP context7 (ToolSearch "context7"), не по памяти. Фронтенда пока нет — делаешь ТОЛЬКО бэкенд-часть задачи; фронтовые подзадачи (orval, react-i18next, QueryClient, формы, eslint, Playwright) не трогай — их сделает этап фронта. Ничего не ломай из уже сделанного: весь набор тестов должен оставаться зелёным. Обновляй docs/architecture.md разделом по своей теме (кратко: как устроено, как пользоваться, что запрещено). Не коммить и не пушь. Данные — вымышленные. Не используй порты 8080/8081, если приложение уже запущено кем-то: для ручных проверок запускай на свободных (например --server.port=18080 --management.server.port=18081) и останавливай после.`

const IMPL = { type: 'object', properties: {
  summary: { type: 'string' },
  filesChanged: { type: 'array', items: { type: 'string' } },
  buildGreen: { type: 'boolean' },
  testsCount: { type: 'integer' },
  subtasksDone: { type: 'array', items: { type: 'string' } },
  subtasksNotDone: { type: 'array', items: { type: 'string' } },
  deviations: { type: 'array', items: { type: 'string' } },
  followUps: { type: 'array', items: { type: 'string' } },
}, required: ['summary', 'filesChanged', 'buildGreen', 'testsCount', 'subtasksDone', 'subtasksNotDone', 'deviations', 'followUps'] }
const FINDING = { type: 'object', properties: {
  severity: { type: 'string', enum: ['blocker', 'major', 'minor'] },
  area: { type: 'string' }, file: { type: 'string' }, issue: { type: 'string' }, fix: { type: 'string' },
}, required: ['severity', 'area', 'file', 'issue', 'fix'] }
const REVIEW = { type: 'object', properties: {
  buildGreen: { type: 'boolean' }, evidence: { type: 'string' },
  findings: { type: 'array', items: FINDING },
}, required: ['buildGreen', 'evidence', 'findings'] }

const TASKS = [
  { id: 'CARRY', title: 'Хвосты ревью фундамента', spec: `Исправь хвосты ревью T-PLT-01/02:
1) platform/database/ApplicationRoleCheck: кроме атрибутов роли и прямого владения схемой, проверяй членство — роль приложения не должна быть (прямо или транзитивно) членом dentify_owner, владельца схемы или любой роли с SUPERUSER/BYPASSRLS (pg_has_role / рекурсивный pg_auth_members). Тест в ApplicationRoleCheckTests на сценарий GRANT dentify_owner TO <тестовая роль>.
2) Flyway: spring.flyway.validate-migration-naming=true. Формат версий меняется на V<yyyy>_<MM>_<dd>_<HHmm>__<модуль>_<что>.sql (Flyway читает как 2026.10.01.1200) — чтобы в логах не было 12 цифр подряд (маска ИИН). Обнови architecture.md «Файлы и версии» и пример. V1__platform_baseline остаётся.
3) spring.modulith.moments.enabled=false (moments в MVP не нужны; убирает конфликт @EnableScheduling с автоконфигом TaskScheduler — проверь, что в логе тестов больше нет предупреждения о TaskScheduler).
4) spring.data.redis.repositories.enabled=false (Valkey только для сессий/лимитов/кэша).
5) PiiMasker: телефоны без разделителей и в других форматах (+77071234567, 87071234567, 8 707 123 45 67, 7071234567 в контексте, URL-кодированный %2B7 707…), составные ключи в JSON (имя ключа содержит name/fullname/lastname/surname/phone/iin/email/address/birth — без учёта регистра и camelCase: patientFullName, doctorLastName, patientBirthDate). Тесты на каждый формат и на отсутствие ложных срабатываний (UUID, traceId, суммы, даты, версии миграций 2026.10.01.1200).
6) application-prod.yml и architecture.md «Роли и права»: явно записать требование search_path = dentify, public для роли приложения на проде.
7) Dev-секреты: в deploy/.env остались публичные значения шаблона «…change-me». Пересоздай: deploy/dev.ps1 down, удали deploy/.env и тома dentify-pgdata, valkey и minio (docker volume ls | grep dentify), deploy/dev.ps1 up (сгенерирует случайные пароли), deploy/dev.ps1 check — всё OK. Данных там нет, это локальный dev.
Затем ./mvnw -B verify зелёный.` },
  { id: 'T-PLT-07+11', title: 'Контракт API и i18n (бэкенд-часть T-PLT-07 и T-PLT-11)', spec: `Бэкенд-части задач T-PLT-07 (требования PLT-07, PLT-23, PLT-22) и T-PLT-11 (PLT-11), подзадачи из backlog.json:
- @RestControllerAdvice в platform: ProblemDetail (RFC 9457) с полями code (ключ сообщения), traceId, errors[] {field, code, message} для 400/401/403/404/409/422/429/500; тексты — только из MessageSource по коду; 500 без стектрейса и деталей. Иерархия исключений платформы с кодами (NotFound→404, Conflict→409, BusinessRule→422 и т.п.) в named interface platform для других модулей. Ошибки валидации Bean Validation и разбора JSON → 400/422 с errors[].
- Соглашения /api/v1: DTO — record; PageResponse {items, page, size, total}; параметры page/size (size ≤ 100, по умолчанию 20)/sort/q с валидацией; общий резолвер/объект запроса страницы.
- springdoc 3.x (совместимый с Boot 4.1 — проверь): спека OpenAPI 3.1 выгружается при сборке (springdoc-openapi-maven-plugin или тест, пишущий файл) в ${APP}/api/openapi.json (коммитится; фронт потом генерирует orval), все 4xx/5xx описаны схемой ProblemDetail; Swagger UI только в профиле dev.
- i18n: MessageSource с базовым messages.properties на русском (UTF-8) + messages_ru при необходимости, spring.messages.fallback-to-system-locale=false; LocaleResolver с цепочкой «язык аккаунта (точка расширения, пока нет аккаунтов) → cookie → Accept-Language → ru», поддерживаемые локали из конфигурации: при i18n.kk=false только ru. Тип LocalizedText {ru, kk} (record, jsonb-колонка через Hibernate 7 JSON-маппинг, геттер text(locale) с фолбэком на ru) + тест. Тест: в тестовом профиле словарь messages_kk + i18n.kk=true переключают текст ProblemDetail для Accept-Language: kk без правок кода.
- Тестовый контроллер в src/test для проверки ошибок, как уже сделано LogProbeController. Интеграционные тесты на каждый код ответа.` },
  { id: 'T-PLT-03', title: 'Тенант-контекст, изоляция и RLS', spec: `Задача T-PLT-03 целиком (требования PLT-01, PLT-02, PLT-08), все подзадачи из backlog.json, включая RLS с этапа 0:
- TenantContext (clinicId, branchId) — источник: HTTP-сессия (атрибут, который потом выставит вход T-IAM-02 и выбор клиники), проброс в @Async/@EventListener/@ApplicationModuleListener/@Scheduled через TaskDecorator/ContextPropagation; без контекста — ошибка, а не «все клиники». CurrentTenantIdentifierResolver для @TenantId.
- Базовая сущность TenantEntity (@MappedSuperclass): id UUIDv7 — выбери официальный способ генерации v7 в Hibernate 7 (сверься с документацией: @UuidGenerator с поддержкой version 7, иначе DB default uuidv7() + @Generated) и запиши выбор в architecture.md; clinic_id с @TenantId; created/updated at/by (кто — из контекста безопасности, точка расширения до T-PLT-04), @Version. Глобальная базовая сущность без clinic_id — для account и справочников МКБ.
- RLS: set_config('app.clinic_id', …, true) в начале каждой транзакции (надёжно: и для @Transactional, и для Spring Data без явной транзакции; соединение из пула не должно унести чужой контекст). Шаблон миграции таблицы арендатора в architecture.md уже есть — проверь, что он работает с твоей реализацией, при необходимости функция dentify.current_clinic_id(). Тест по каталогу pg_class/pg_policies (relrowsecurity + relforcerowsecurity + политика для каждой таблицы с clinic_id).
- Для проверки заведи тестовые таблицу/сущность/контроллер ТОЛЬКО в src/test (например, test_note), миграцию для них — в src/test/resources/db/migration (подключи локацию в тестовом профиле), чтобы в прод-схеме не было мусора.
- Харнесс теста изоляции: перебор всех эндпоинтов из RequestMappingHandlerMapping; ресурсы клиники B из-под клиники A → 404 (GET/PUT/PATCH/DELETE по id); реестр исключений (публичные эндпоинты) явный. Прямой SQL под ролью приложения без app.clinic_id → 0 строк.
- 409 на OptimisticLockException: ProblemDetail code conflict.version с автором и временем последней правки (через advice из предыдущего шага); интеграционный тест параллельных обновлений.
- ArchUnit: сущности модулей арендатора наследуют TenantEntity; DTO-records не содержат clinicId/clinic_id; репозитории не используют нативные запросы без clinic_id… (что разумно проверить).
- Префикс clinic_id в ключах кэша Valkey (CacheManager/KeyGenerator), тест.` },
  { id: 'T-PLT-04', title: 'Сессии, CSRF, пароли, лимиты, заголовки', spec: `Задача T-PLT-04 (требования PLT-04, PLT-05, PLT-06), подзадачи из backlog.json (минимального сид-входа нет — вход делает T-IAM-02):
- SecurityFilterChain: stateful сессия; 401/403 — ProblemDetail (через общий advice/entry point), без редиректов и form login; публичные: /api/v1/auth/**, health (на 8081 отдельно), /v3/api-docs в dev.
- Spring Session Data Redis (Valkey) с индексом по principal (FindByIndexNameSessionRepository / indexed repository): cookie DENTIFY_SESSION HttpOnly, Secure (в dev за HTTP — объясни, как проверяется), SameSite=Lax; таймаут бездействия 12 ч; «Запомнить» = постоянная cookie 30 дней (механизм готов, включает вход T-IAM-02); смена id сессии при входе (защита от фиксации).
- API сессий для IAM (сервис + REST): список сессий текущего аккаунта (браузер/ОС из User-Agent, IP маской 95.56.•.•, город из локальной GeoIP-базы — если файла базы нет, город null; база не коммитится, способ получения — в README/architecture; бери бесплатную базу с разрешающей лицензией, например DB-IP Lite в формате mmdb, с атрибуцией), завершить одну / все / все кроме текущей; принудительное завершение всех сессий аккаунта за ≤ 5 с (сервисный метод для деактивации и смены пароля).
- CSRF: для SPA — CookieCsrfTokenRepository (XSRF-TOKEN, не HttpOnly) + заголовок X-XSRF-TOKEN; в Security 7 проверь встроенный режим для SPA (csrf.spa()).
- Пароли: DelegatingPasswordEncoder с Argon2id по умолчанию (параметры OWASP), PasswordPolicy (8–128, топ-10 000 из встроенного списка — файл в resources, не совпадает с телефоном/e-mail, коды ошибок), TokenService (256 бит, в БД только SHA-256, TTL, одноразовый, гасится при смене пароля) — таблица токенов в модуле iam или platform (реши по направлениям модулей).
- Лимиты: Bucket4j с хранилищем в Valkey (официальная интеграция bucket4j-redis через Lettuce — проверь совместимость с Boot 4.1): вход 5 неверных на логин за 15 мин + 30/мин на IP, сброс пароля 3/час на адрес, общий API 600/мин на сессию; 429 ProblemDetail + Retry-After.
- Заголовки: CSP (script-src без unsafe-inline), HSTS 1 год, frame-ancestors 'none', nosniff, Referrer-Policy strict-origin-when-cross-origin, Permissions-Policy; тесты MockMvc/RestTestClient на каждый.
- Тесты: POST без CSRF → 403, с заголовком проходит; cookie флаги; 429 после лимита; хэш формата {argon2}; политика паролей; токены одноразовые и хранятся хэшем.` },
  { id: 'T-PLT-13', title: 'Аудит', spec: `Задача T-PLT-13 (требование PLT-09), подзадачи из backlog.json: миграция audit_log (помесячные партиции, clinic_id, RLS как у таблиц арендатора, индексы (clinic_id, at) и по объекту, REVOKE UPDATE/DELETE у роли приложения + триггер запрета), создание партиций заранее (на N месяцев вперёд — задача по расписанию или при старте, объясни), AuditService + @Audited (запись в той же транзакции; «было → стало» по снимкам DTO; секреты — пароли, токены — никогда не попадают в diff; ИИН и телефон маскируются при чтении без права), события просмотра медкарты с дедупликацией 15 мин (Valkey) — API для будущего модуля medical, события выгрузки и печати, ключи i18n текстов событий в messages.properties, API чтения журнала (фильтры: период, сотрудник, объект, текст; пагинация PageResponse; @PreAuthorize — пока по authority audit:read, права появятся в IAM), интеграционные тесты: откат транзакции не оставляет записи, UPDATE/DELETE audit_log под ролью приложения запрещены, дедупликация работает, изоляция арендаторов. Учти замечание ревью: объём журнала — партиции и индексы, лимит строк при выгрузке.` },
  { id: 'CARRY2', title: 'Blocker сессий из T-PLT-04 и решения по аудиту', spec: `1) Закрыть blocker третьего круга ревью T-PLT-04 (круги кончились, не исправлен). Суть: завершённая сессия с «Запомнить» оживает, если у неё в этот момент идёт запрос. SessionLifetimeFilter на каждом запросе заново пишет maxInactiveInterval (остаток до 30 дней меняется каждую секунду) и это попадает в delta Spring Session; а RedisIndexedSessionRepository.deleteById (его вызывают RedisAccountSessions.delete, invalidate() при выходе и «завершить все») не удаляет hash сессии — ставит maxInactiveInterval=0 и убирает из индекса. Запрос «в полёте» после этого сохраняет delta и воскрешает сессию. Требование PLT-04: деактивация и смена пароля гасят сессии за 5 с и необратимо. Сделай отзыв необратимым: отметка отзыва в Valkey (ключ по id сессии с TTL до срока сессии) и/или «сессии аккаунта, созданные до момента T, недействительны» для terminateAll и terminateOthers; фильтр проверяет отметку на каждом запросе раньше SessionLifetimeFilter и инвалидирует без записи delta. И перестань переписывать maxInactiveInterval на каждом запросе: пиши только если срок изменился больше чем на минуту, либо храни абсолютный срок атрибутом сессии и сравнивай. Интеграционный тест сценария «в полёте» (запрос держится, пока другой поток завершает сессию) → следующий запрос этой сессии 401; покрыть logout, terminate одной, terminateAll, terminateOthers, с «Запомнить» и без.
2) Решения по T-PLT-13 (принять как есть и записать в architecture.md «Аудит»): IP в журнале аудита — маска для всех ролей в MVP, права audit:ip нет; период ленты по умолчанию 30 дней, при текстовом поиске не больше 366 дней, фронт всегда шлёт явный период.
3) ./mvnw -B verify зелёный.` },
  { id: 'T-IAM-01', title: 'Аккаунты и членство', spec: `Задача T-IAM-01 (требование IAM-01), подзадачи из backlog.json: миграции модуля iam (account — глобальная, без clinic_id; membership и membership_branch — таблицы арендатора с RLS по шаблону; uuidv7; уникальные phone и lower(email); CHECK «хотя бы один контакт»), JPA-сущности (account — глобальная базовая, membership — TenantEntity, @Version), нормализация телефона (libphonenumber, регион KZ, формат E.164) и e-mail в value-объектах record, пароль — общий PasswordEncoder из T-PLT-04, репозитории и интеграционные тесты (уникальность, нормализация, изоляция членств между клиниками), ApplicationModules.verify. Модуль clinic ещё пуст: для членства нужны клиника и филиал — заведи минимальные таблицы clinic и branch в модуле clinic (id, name, city; RLS для branch по clinic_id; clinic — корень арендатора, её RLS: id = current_clinic_id) — полноценно их наполнит этап 1 (T-CLN-*), не делай лишнего. Dev-сид (только профиль dev, отдельная локация миграций или ApplicationRunner): вымышленная клиника «Dentify Demo», филиал «Абая, 150», владелец с e-mail owner@demo.dentify.kz и паролем из переменной DENTIFY_DEV_OWNER_PASSWORD в deploy/.env (добавь в .env.example и генерацию dev.ps1/dev.sh).` },
  { id: 'T-IAM-02', title: 'Вход, выход, /api/me', spec: `Задача T-IAM-02 (требования IAM-02, IAM-03, IAM-08), подзадачи из backlog.json: POST /api/v1/auth/login (JSON: логин — телефон или e-mail, пароль, remember), POST /api/v1/auth/logout, смена id сессии при входе, «Запомнить устройство» (30 дней) против сессионной cookie (12 ч бездействия), CSRF cookie-to-header (GET /api/v1/auth/csrf или выдача при первом запросе — как принято для SPA в Security 7), таблица auth_event (вход, выход, неверный пароль, блокировка; user-agent, IP; глобальная или по клинике — реши и объясни) и запись событий, одинаковый ответ и время для неизвестного логина (фиктивная проверка Argon2-хэша), лимиты входа из T-PLT-04, GET /api/v1/me (аккаунт, членства, текущий контекст клиники/филиала; при одном членстве и одном филиале контекст выставляется при входе автоматически, иначе — пустой и POST /api/v1/me/context {clinicId, branchId} с проверкой членства — это ставит TenantContext в сессию из T-PLT-03), описание в OpenAPI (спека ${APP}/api/openapi.json обновлена), интеграционные тесты на Postgres и Valkey (вход/выход, неверный пароль, неизвестный логин — тот же ответ, remember, CSRF, 401 после logout, выбор контекста, изоляция). Проверь вручную: приложение с профилем dev, вход dev-владельцем curl-ом с cookie jar и CSRF, /api/v1/me, выход.` },
]

const CACHED = new Set(["impl:CARRY", "review:CARRY#1", "impl:T-PLT-07+11", "review:T-PLT-07+11#1", "fix:T-PLT-07+11#1", "review:T-PLT-07+11#2", "impl:T-PLT-03", "review:T-PLT-03#1", "fix:T-PLT-03#1", "review:T-PLT-03#2", "impl:T-PLT-04", "review:T-PLT-04#1", "fix:T-PLT-04#1", "review:T-PLT-04#2", "fix:T-PLT-04#2", "review:T-PLT-04#3", "impl:T-PLT-13", "review:T-PLT-13#1", "fix:T-PLT-13#1"])
const mo = (label, review) => CACHED.has(label) ? {} : (review ? { model: 'opus', effort: 'xhigh' } : { model: 'opus' })
const results = []
phase('Tasks')
for (const t of TASKS) {
  const done = results.map(r => `${r.id}: ${r.impl?.summary?.slice(0, 600) ?? 'нет отчёта'}`).join('\n\n')
  let impl = await agent(`${BASE}

Уже сделано в этой партии (кратко):
${done || '— (первая задача)'}

ТЕКУЩАЯ ЗАДАЧА ${t.id} — ${t.title}.
${t.spec}

В конце: ./mvnw -B verify зелёный (JDK 25). Верни отчёт.`, { label: `impl:${t.id}`, phase: 'Tasks', schema: IMPL, ...mo(`impl:${t.id}`) })
  const fixes = []
  let rv = null
  for (let round = 0; round < 3; round++) {
    rv = await agent(`${BASE}

Ты — независимый строгий проверяющий задачи ${t.id} (${t.title}). Спецификация:
${t.spec}

Отчёт исполнителя: ${JSON.stringify(impl ?? {}).slice(0, 6000)}
${round ? 'Это повторная проверка после исправлений: сообщай только оставшиеся реальные проблемы.' : ''}

Проверь ИСПОЛНЕНИЕМ, а не на глаз: 1) ./mvnw -B verify (JDK 25) — зелёный? сколько тестов? 2) Каждая подзадача и каждый критерий приёмки связанных требований (для этапа 0; то, что явно относится к фронту или будущим модулям, не требуй) — есть ли код И тест, который это доказывает; прочитай тесты — не пустые ли, проверяют ли суть. 3) Безопасность и корректность: утечки между клиниками, обход RLS, секреты и ПД в логах/ответах, гонки, транзакционные границы, неверное использование API Spring Boot 4 / Security 7 (сверься с документацией context7 при сомнении). 4) Соблюдение правил R002/R003 (строки пользователю — только через коды/MessageSource), направлений модулей (ModularityTests), architecture.md обновлён. Для ручной проверки можно запустить приложение на портах 18080/18081 и остановить после.
Верни buildGreen, evidence и findings: blocker — красная сборка/тесты, уязвимость, потеря данных; major — подзадача/критерий не выполнены или выполнены неверно, нет теста на ключевое поведение; minor — мелочи.`, { label: `review:${t.id}#${round + 1}`, phase: 'Tasks', schema: REVIEW, ...mo(`review:${t.id}#${round + 1}`, true) })
    const blocking = (rv?.findings ?? []).filter(f => f.severity !== 'minor')
    if (rv && rv.buildGreen && !blocking.length) break
    if (round === 2) break
    const minors = (rv?.findings ?? []).filter(f => f.severity === 'minor')
    const fx = await agent(`${BASE}

Исправь находки проверки задачи ${t.id} (${t.title}). Спецификация задачи:
${t.spec}

Обязательные (blocker/major):
${JSON.stringify(blocking, null, 1)}
${rv && !rv.buildGreen ? 'Сборка КРАСНАЯ. Evidence: ' + rv.evidence : ''}
Мелкие — исправь, если дёшево и безопасно, иначе в followUps:
${JSON.stringify(minors, null, 1)}
Если находка ошибочна — не исправляй, обоснуй в deviations (со ссылкой на документацию). В конце ./mvnw -B verify зелёный.`, { label: `fix:${t.id}#${round + 1}`, phase: 'Tasks', schema: IMPL, ...mo(`fix:${t.id}#${round + 1}`) })
    fixes.push(fx)
  }
  const remaining = (rv?.findings ?? [])
  log(`${t.id}: сборка ${rv?.buildGreen ? 'зелёная' : 'КРАСНАЯ'}, осталось blocker/major ${remaining.filter(f => f.severity !== 'minor').length}, minor ${remaining.filter(f => f.severity === 'minor').length}`)
  results.push({ id: t.id, impl, fixes, lastReview: rv })
}

phase('Final review')
const digest = results.map(r => `${r.id}: ${(r.fixes.at(-1)?.summary ?? r.impl?.summary ?? '').slice(0, 800)}`).join('\n\n')
const acc = await agent(`${BASE}

Ты — финальный приёмщик всей партии бэкенда этапа 0 (хвосты ревью, T-PLT-07/11 бэкенд, T-PLT-03, T-PLT-04, T-PLT-13, CARRY2, T-IAM-01, T-IAM-02). Сводка исполнителей:
${digest}

Проверь сквозняком ИСПОЛНЕНИЕМ: 1) deploy/dev.ps1 check; 2) ./mvnw -B clean verify (JDK 25) — зелёный, число тестов; 3) собери jar и запусти с профилем dev на 18080/18081; пройди сценарий демо этапа 0 со стороны API: GET csrf → login dev-владельцем (пароль DENTIFY_DEV_OWNER_PASSWORD из deploy/.env) → /api/v1/me с контекстом клиники → список своих сессий → logout → /api/v1/me = 401; неверный пароль → единый ответ, после 5 попыток 429 с Retry-After; POST без CSRF → 403; заголовки безопасности на ответах; ${APP}/api/openapi.json актуален и описывает эндпоинты; 4) пройди критерии приёмки требований PLT-01, PLT-02 (RLS), PLT-04, PLT-05, PLT-06, PLT-07, PLT-08, PLT-09, PLT-11 (бэкенд), PLT-23, IAM-01, IAM-02, IAM-03, IAM-08 — для каждого: выполнен / частично (что осталось фронту или будущему этапу) / нет. Останови приложение. Верни buildGreen, evidence (включая таблицу критериев) и findings.`, { label: 'final:acceptance', phase: 'Final review', schema: REVIEW, model: 'opus', effort: 'xhigh' })

const LENSES = [
  { key: 'arch', prompt: `Ты — ревьюер архитектуры (Spring Boot 4.1, Modulith 2.1, Hibernate 7). Прочитай весь ${APP}/backend/src и ${APP}/docs/architecture.md. Ищи: нарушения границ модулей и направлений (clinic ← iam…), утечки внутренних типов через api, логику в контроллерах вместо сервисов (R002), строки пользователю мимо MessageSource (R003), неверные транзакционные границы, N+1 и ленивые загрузки вне транзакции (open-in-view выключен?), самописное там, где есть официальное готовое (R021), конфиг, который сломает этапы 1–6, дублирование, пробелы в architecture.md (новый разработчик должен по нему добавить модуль/таблицу/эндпоинт). Сборку не запускай.` },
  { key: 'sec', prompt: `Ты — пентестер и ревьюер безопасности. Код ${APP}/backend/src. Приложение можно собрать jar из уже собранного target (не запускай mvn clean!) и запустить с профилем dev на 28080/28081 для black-box проверок curl, затем остановить. Проверь: обход аутентификации и CSRF (в т.ч. на logout, смене контекста, завершении сессий), фиксация сессии, перечисление аккаунтов по времени/тексту ответа, смена контекста на чужую клинику (POST /api/v1/me/context с чужим clinicId), IDOR на завершение чужой сессии, утечки между клиниками через RLS (попробуй через SQL под dentify_app без app.clinic_id и с чужим), ПД/секреты в логах и ответах (включая ProblemDetail 500 и детали валидации), заголовки безопасности, лимиты (обход через X-Forwarded-For — доверенные прокси!), хэши паролей и токенов, cookie-флаги, CORS. Каждую находку подтверди воспроизведением (команда + ответ) в evidence.` },
]
const lensRuns = (await parallel(LENSES.map(l => () => agent(`${BASE}\n\n${l.prompt}\nВерни buildGreen=true (если сборку не проверял), evidence и findings с severity.`, { label: `final:${l.key}`, phase: 'Final review', schema: REVIEW, model: 'opus', effort: 'xhigh' })))).filter(Boolean)
let allReviews = [acc, ...lensRuns].filter(Boolean)
const finalFixes = []
for (let round = 0; round < 2; round++) {
  const blocking = allReviews.flatMap(r => r.findings).filter(f => f.severity !== 'minor')
  const red = allReviews.some(r => r.buildGreen === false)
  log(`Финал, круг ${round + 1}: blocker/major ${blocking.length}, сборка ${red ? 'КРАСНАЯ' : 'зелёная'}`)
  if (!blocking.length && !red) break
  phase('Final fix')
  const fx = await agent(`${BASE}

Исправь находки финального ревью партии бэкенда этапа 0. Обязательные:
${JSON.stringify(blocking, null, 1)}
${red ? 'Сборка/приёмка КРАСНАЯ: ' + allReviews.map(r => r.evidence).join('\n---\n').slice(0, 8000) : ''}
Мелкие — если дёшево и безопасно:
${JSON.stringify(allReviews.flatMap(r => r.findings).filter(f => f.severity === 'minor'), null, 1).slice(0, 12000)}
Ошибочные находки — обоснуй в deviations. В конце ./mvnw -B clean verify зелёный.`, { label: `final:fix#${round + 1}`, phase: 'Final fix', schema: IMPL, model: 'opus' })
  finalFixes.push(fx)
  const re = await agent(`${BASE}

Перепроверь исправления финального ревью. Исходные обязательные находки:
${JSON.stringify(blocking, null, 1)}
Отчёт исправлений: ${JSON.stringify(fx ?? {}).slice(0, 6000)}
Проверь ИСПОЛНЕНИЕМ: ./mvnw -B clean verify зелёный; каждая находка действительно закрыта (воспроизведи прежний сценарий — теперь не срабатывает); ничего не сломано. Верни buildGreen, evidence и findings (только незакрытое и новое).`, { label: `final:recheck#${round + 1}`, phase: 'Final fix', schema: REVIEW, model: 'opus', effort: 'xhigh' })
  allReviews = [re].filter(Boolean)
}

return {
  tasks: results.map(r => ({ id: r.id, impl: r.impl, fixes: r.fixes, lastReview: r.lastReview })),
  acceptance: acc,
  lenses: lensRuns,
  finalFixes,
  finalState: allReviews,
}
