export const meta = {
  name: 'dentify-phase1-backend-c1',
  description: 'Dentify этап 1, бэкенд, партия C1: REST-API модуля clinic (профиль, филиалы и календарь, кресла, прайс, справочники, настройки и правки задним числом, логотип), движок импорта Excel и импорт прайса, тесты изоляции и прав clinic, документация модуля — по задаче с проверкой, затем общее ревью',
  phases: [
    { title: 'Tasks', detail: 'impl → review → fix → commit по задаче', model: 'opus' },
    { title: 'Final review', detail: 'приёмка сквозняком + архитектура + безопасность', model: 'opus' },
    { title: 'Final fix', detail: 'исправления по общему ревью', model: 'opus' },
  ],
}


const APP = 'C:/Users/abyla/code/apps/dentify'
const BASE = `Ты работаешь над бэкендом проекта Dentify в ${APP}/backend (Windows; Git Bash и PowerShell; JDK 25: export JAVA_HOME="$HOME/.jdks/openjdk-25.0.2"; export PATH="$JAVA_HOME/bin:$PATH" — системная java 17 не годится; Docker Desktop запущен; окружение ${APP}/deploy/dev.ps1 up, пароли в ${APP}/deploy/.env). СНАЧАЛА прочитай ${APP}/CLAUDE.md и ${APP}/docs/architecture.md ЦЕЛИКОМ — обязательные соглашения: карта модулей и направления (clinic ← iam, staff, schedule, inventory; staff, patient ← schedule; medical → patient, schedule, clinic; onboarding → clinic, iam; все → platform; примитивы сессии в platform :: identity только для iam), раскладка пакетов модуля, «Как добавить таблицу арендатора» (имя миграции V<yyyy>_<MM>_<dd>_<HHmm>__<модуль>_<что>.sql, clinic_id, RLS ENABLE+FORCE+политика, uuidv7, тест каталога), «Как добавить эндпоинт» (record-DTO в web, сервис с правилами, @PreAuthorize("@perm.has(...)") со скоупами, коды ошибок → ProblemDetail в messages.properties, @Operation + обновление ${APP}/api/openapi.json сборкой, строка в реестре теста изоляции, интеграционный тест), «Права» (эффективные права на запрос с кэшем, @perm, PermissionContributor, CurrentMember, ScopeFilter), «Модули и события» (журнал публикаций JDBC, идемпотентные слушатели), «Аудит» (@Audited, подписи полей audit.field.*), «Логи», i18n (R003, LocalizedText {ru, kk}). Правила: dentify R001 — растущий список = серверный поиск/фильтры/сортировка/пагинация (PageResponse, size ≤ 100); R002 — вся логика на бэкенде, что разрешено — сообщает API (allowedActions, 409/422); R003 — только русский, строки только через коды/MessageSource; R021 — сначала официальное готовое; Spring Boot 4.1 / Security 7 / Modulith 2.1 / Hibernate 7 сверяй через MCP context7. Задачу и требования (описание + критерии приёмки) достань из ${APP}/docs/backlog.json командой из CLAUDE.md; план — C:/Users/abyla/code/wiki/projects/dentify/mvp-plan.md (Grep). Фронта в этой партии нет: делай ТОЛЬКО бэкенд-части, фронтовые подзадачи — в subtasksNotDone с пометкой «фронт». Уже готово (не переделывай, расширяй): этап 0 целиком; этап 1 партия A (события и почта, схема и домен clinic, каталог прав и @perm, валидаторы Iin/Bin/KzPhone, search_norm, сид клиники, защита входа, NotificationSender) и партия B (S3-хранилище, ExportService XLSX, регистрация клиники /api/v1/public/registrations, registerOwner, контекст клиники/филиала, аудит clinic, фабрика тестовых данных и демо «Ақ Тіс», автотест изоляции по OpenAPI). Ничего не ломай: весь набор тестов остаётся зелёным. Обновляй docs/architecture.md разделом по своей теме. Не коммить и не пушь сам — коммитит отдельный шаг после ревью. Данные — вымышленные. Порты для ручных проверок — 18080/18081, останавливай после. В конце: ./mvnw -B verify зелёный, api/openapi.json обновлён сборкой.`

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
  { id: 'T-CLN-07', title: 'API профиля и реквизитов клиники', spec: `Задача T-CLN-07 (требования CLN-06, CLN-07, CLN-09), подзадачи из backlog.json: GET/PUT /api/v1/clinic/profile (record-DTO, @Version → 409 с автором и временем), валидаторы ИИН/БИН из платформы, ИИК (KZ IBAN mod-97), КБе, телефон, e-mail; часовой пояс только из списка IANA; @perm clinic:view / clinic:profile:edit; OpenAPI; тесты: параметризованные юнит валидаторов, изоляция 404, права 403, конфликт 409; @Audited с подписями полей.` },
  { id: 'T-CLN-10', title: 'API филиалов, часов работы и праздников', spec: `Задача T-CLN-10 (требования CLN-10..13), подзадачи из backlog.json: CRUD /api/v1/clinic/branches (R001: поиск, фильтр архива, пагинация), архивировать/восстановить, уникальность названия, запрет архивации последнего активного; часы работы (валидация HH:MM, шаг 15, минимум 1 рабочий день, jsonb со схемой, сводка «Пн–Пт 09–20, Сб 10–16» — сводку строит сервер); праздники; BranchCalendar.isOpen в поясе клиники; предупреждения через SPI (число будущих смен/записей — реализации по умолчанию 0); @perm clinic:branches:edit; события; тесты: юнит BranchCalendar на границах, изоляция, права, 409.` },
  { id: 'T-CLN-12', title: 'API кресел и их порядка', spec: `Задача T-CLN-12 (требования CLN-14..16), подзадачи из backlog.json: CRUD /api/v1/clinic/branches/{id}/chairs, PATCH active с проверкой будущих записей/смен через SPI → 409 с числом, DELETE только без истории иначе 409, PUT /chairs/order (полный список id + версия порядка → 409 при гонке), скоуп филиалов в @perm clinic:chairs:edit, событие ChairsChanged, тесты изоляции, прав, скоупа, гонки порядка.` },
  { id: 'T-CLN-14', title: 'API прайса: категории и услуги', spec: `Задача T-CLN-14 (требования CLN-17..20), подзадачи из backlog.json: CRUD категорий (LocalizedText, порядок, удаление только пустой), CRUD услуг (код уникален без регистра, цена BigDecimal ≥ 0, длительность 5–480 кратно 5, специальность из активных, статусы ACTIVE/DRAFT/ARCHIVED, allowedActions в ответе), GET /services с поиском по коду, рус и каз названию (search_norm + pg_trgm), фильтром статуса, сортировкой, пагинацией; удаление неиспользованных / архив использованных (SPI ClinicReferenceUsage); «следующий код категории»; lookup активных услуг; @perm clinic:price:edit; событие ServiceChanged; тесты: поиск по каз названию, уникальность кода, архив, изоляция, права.` },
  { id: 'T-CLN-17', title: 'API справочников', spec: `Задача T-CLN-17 (требования CLN-22, CLN-23), подзадачи из backlog.json: общий контроллер /api/v1/clinic/dictionaries/{kind} поверх DictionaryItem + специфичные поля по виду; уникальность названия без регистра включая выключенные (код ошибки с подсказкой «включить»); PUT /order, переключение активности, удаление только при нулевом использовании (SPI и FK → 409); счётчики использования в списке; lookup активных для любой роли с кэшем в Valkey (ключ с clinic_id) и сбросом по DictionaryChanged; @perm clinic:dictionaries:edit со скоупом «все / складские»; тесты, параметризованные по 9 видам: изоляция, скоуп кладовщика, запрет удаления.` },
  { id: 'T-CLN-19', title: 'API настроек клиники и политика правок задним числом', spec: `Задача T-CLN-19 (требования CLN-24..26), подзадачи из backlog.json: GET/PUT /api/v1/clinic/settings с @Version и валидацией (шаг {10,15,20,30,60}, дни 0–365, язык — в MVP только ru (R003, флаг i18n.kk), день начала недели, регион телефона); BackdatePolicy.check(kind, eventDate, principal) в поясе клиники в clinic :: api, обход по праву backdate:edit с записью в аудит; ProblemDetail backdate-locked; предупреждение при выключении двойной записи через SPI; событие ClinicSettingsChanged, кэш настроек в Valkey; юнит-тесты политики (граница суток, пояс, 0 дней, выключенный лимит) и интеграционные права/изоляция.` },
  { id: 'T-CLN-09', title: 'Логотип клиники (бэкенд)', spec: `Бэкенд-часть задачи T-CLN-09 (требование CLN-08): POST/DELETE /api/v1/clinic/logo (multipart ≤ 2 МБ, сигнатура PNG/SVG, минимум 64×64), очистка SVG готовым санитайзером (проверь через context7 доступные Java-библиотеки; иначе — отказ SVG со script/foreignObject/on*/внешними ссылками), хранение через FileStorage платформы под ключом clinic/<id>/logo с удалением старого, отдача только своей клинике (404 чужим) с кэш-заголовками, тесты: SVG со скриптом, подмена расширения, изоляция. UI — фронт, не делать.` },
  { id: 'T-PLT-21', title: 'Движок импорта из Excel (бэкенд) + импорт прайса (T-CLN-16 бэкенд)', spec: `Бэкенд-части задач T-PLT-21 (требование PLT-31) и T-CLN-16 (CLN-21): каркас ImportJob в платформе (загрузка XLSX/CSV через FileStorage, потоковый разбор — fastexcel-reader или POI streaming, статус и прогресс, отчёт ошибок в XLSX через ExportService), API: создать задание, GET колонок и предпросмотра, PUT сопоставления колонок (автоматически по заголовкам, включая заголовки выгрузки macdent — см. wiki macdent-analysis, вручную), шаг проверки (счётчики готово/предупреждения/ошибки, стратегия для дублей пропустить/обновить), выполнение в фоне через события/планировщик с итогом и одним событием аудита; SPI ImportAdapter — модуль регистрирует свой импортёр; первый адаптер — прайс (категории и услуги: код, названия рус/каз, цена, длительность, специальность; дедуп по коду; обновление по стратегии) в модуле clinic, шаблон файла отдаётся эндпоинтом; импортёр пациентов — этап 2 (модуля patient ещё нет), не делать; тесты: 870 строк прайса импортируются, повтор без дублей, отчёт ошибок, изоляция, права clinic:price:edit. Мастер на фронте — не делать.` },
  { id: 'T-CLN-23', title: 'Тесты изоляции и прав API clinic', spec: `Задача T-CLN-23 (требования CLN-05, CLN-27): параметризованный тест по всем эндпоинтам clinic из OpenAPI (пользователь A → ресурсы B = 404) на базе реестра T-PLT-32; матрица ролей (Владелец, Администратор, Врач, Ассистент, Кладовщик) × эндпоинт → 200/403 с ожиданиями из шаблонов ролей; скоуп филиала для кресел и часов, скоуп «складские» для справочников; перехватчик логов модуля — нет ПД; ApplicationModules.verify. Подзадачу «шаг в CI» замени на запуск в ./mvnw verify.` },
  { id: 'T-CLN-25', title: 'Документация модуля clinic', spec: `Задача T-CLN-25 (требования CLN-27, CLN-21): документация модуля clinic (${APP}/docs/modules/clinic.md или раздел architecture.md — сверь, где по соглашению лежит документация модулей рядом с тем, что генерирует Documenter): публичный API ClinicDirectory, BranchCalendar, BackdatePolicy с примерами; события и контракт SPI ClinicReferenceUsage для STF, PAT, SCH, INV; коды прав, скоупы и шаблоны ролей по умолчанию; значения сида и формат шаблона импорта прайса. Сверить каждый пункт с кодом.` },
]

const COMMIT = { type: 'object', properties: { committed: { type: 'boolean' }, sha: { type: 'string' }, pushed: { type: 'boolean' }, note: { type: 'string' } }, required: ['committed', 'sha', 'pushed', 'note'] }
const results = []
phase('Tasks')
for (const t of TASKS) {
  const done = results.map(r => `${r.id}: ${r.impl?.summary?.slice(0, 500) ?? 'нет отчёта'}`).join('\n\n')
  const impl = await agent(`${BASE}

Уже сделано в этой партии (кратко):
${done || '— (первая задача партии)'}

ТЕКУЩАЯ ЗАДАЧА ${t.id} — ${t.title}.
${t.spec}

Верни отчёт.`, { label: `impl:${t.id}`, phase: 'Tasks', schema: IMPL, model: 'opus' })
  const fixes = []
  let rv = null
  for (let round = 0; round < 3; round++) {
    rv = await agent(`${BASE}

Ты — независимый строгий проверяющий задачи ${t.id} (${t.title}). Спецификация:
${t.spec}

Отчёт исполнителя: ${JSON.stringify(impl ?? {}).slice(0, 6000)}
${round ? 'Это повторная проверка после исправлений: сообщай только оставшиеся реальные проблемы.' : ''}

Проверь ИСПОЛНЕНИЕМ, а не на глаз: 1) ./mvnw -B verify (JDK 25) — зелёный? сколько тестов? 2) Каждая подзадача (бэкенд-часть) и критерий приёмки связанных требований — есть ли код И тест, который это доказывает; прочитай тесты. 3) Безопасность и корректность: утечки между клиниками (RLS, кэш, S3-пути, подписанные ссылки), права (@perm, скоупы), публичные эндпоинты (лимиты, идемпотентность, фиксация сессии), секреты/ПД в логах и ответах, гонки, транзакции, неверное использование API (context7). 4) Соглашения architecture.md: имя миграции, RLS, реестр теста изоляции, коды ошибок, openapi.json, направления модулей (ModularityTests), architecture.md дополнен. Для ручной проверки — порты 18080/18081, остановить после.
Верни buildGreen, evidence и findings: blocker — красная сборка/тесты, уязвимость, потеря данных; major — подзадача/критерий не выполнены или выполнены неверно, нет теста на ключевое поведение, нарушено соглашение; minor — мелочи.`, { label: `review:${t.id}#${round + 1}`, phase: 'Tasks', schema: REVIEW, model: 'opus', effort: 'xhigh' })
    const blocking = (rv?.findings ?? []).filter(f => f.severity !== 'minor')
    if (rv && rv.buildGreen && !blocking.length) break
    if (round === 2) break
    const fx = await agent(`${BASE}

Исправь находки проверки задачи ${t.id} (${t.title}). Спецификация задачи:
${t.spec}

Обязательные (blocker/major):
${JSON.stringify(blocking, null, 1)}
${rv && !rv.buildGreen ? 'Сборка КРАСНАЯ. Evidence: ' + rv.evidence : ''}
Мелкие — исправь, если дёшево и безопасно, иначе в followUps:
${JSON.stringify((rv?.findings ?? []).filter(f => f.severity === 'minor'), null, 1)}
Если находка ошибочна — не исправляй, обоснуй в deviations (со ссылкой на документацию). В конце ./mvnw -B verify зелёный.`, { label: `fix:${t.id}#${round + 1}`, phase: 'Tasks', schema: IMPL, model: 'opus' })
    fixes.push(fx)
  }
  const remaining = (rv?.findings ?? [])
  log(`${t.id}: сборка ${rv?.buildGreen ? 'зелёная' : 'КРАСНАЯ'}, осталось blocker/major ${remaining.filter(f => f.severity !== 'minor').length}, minor ${remaining.filter(f => f.severity === 'minor').length}`)
  const accepted = rv && rv.buildGreen && !(rv.findings ?? []).some(f => f.severity !== 'minor')
  if (accepted) {
    await agent(`В репозитории C:/Users/abyla/code (ветка master) закоммить и запушить результат принятой задачи ${t.id} — ${t.title}. Правило dentify R004. Шаги: 1) cd C:/Users/abyla/code; git add apps/dentify (ТОЛЬКО этот каталог: вне него лежат незакоммиченные правки других проектов — их не трогать); 2) git status --short --cached — убедись, что нет секретов (deploy/.env, *.mmdb, ключи) и мусора (target/, node_modules/, dist/, playwright-report/, test-results/); лишнее — git reset <путь>; если индекс пуст — сообщи и закончи; 3) git commit с сообщением Conventional Commits: заголовок «<type>(dentify): <суть задачи по-английски, не длиннее 72 символов>» (type: feat/fix/test/docs/chore), пустая строка, тело: id задачи ${t.id}, что сделано 3–6 строк, число тестов; последняя строка ровно: Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>; 4) bash C:/Users/abyla/code/bin/git-sync-push.sh — он делает fetch, rebase поверх origin/master с автостэшем чужих правок и push; код 2 или 3 — сообщи в note и не пытайся чинить руками; 5) git log --oneline -1 и git status --short --branch. Отчёт исполнителя задачи для сообщения коммита: ${JSON.stringify((fixes.at(-1) ?? impl ?? {}).summary ?? '').slice(0, 1500)}`, { label: `commit:${t.id}`, phase: 'Tasks', schema: COMMIT, model: 'sonnet', effort: 'low' })
  } else log(`${t.id}: не принята — коммита нет`)
  results.push({ id: t.id, impl, fixes, lastReview: rv })
}

phase('Final review')
const digest = results.map(r => `${r.id}: ${(r.fixes.at(-1)?.summary ?? r.impl?.summary ?? '').slice(0, 700)}`).join('\n\n')
const LENSES = [
  { key: 'acceptance', prompt: `Ты — финальный приёмщик партии C1 бэкенда этапа 1 (${TASKS.map(t => t.id).join(', ')}). Сводка исполнителей:
${digest}
Проверь ИСПОЛНЕНИЕМ: deploy/dev.ps1 check; ./mvnw -B clean verify — зелёный, число тестов; запусти jar с профилем dev на 18080/18081 и пройди через curl под dev-владельцем: профиль клиники (PUT с неверным БИН → 422 с полем; конфликт версии → 409), филиал + часы + праздник, кресла и их порядок (гонка → 409), категория и услуги прайса (поиск по казахскому названию, архив используемой), справочники 9 видов (удаление используемого → 409), настройки (шаг 25 → 422; BackdatePolicy), логотип (SVG со script → отказ), импорт прайса из XLSX на 50 строк (предпросмотр → сопоставление → выполнение → повтор без дублей), проверь RLS новых таблиц и записи аудита с подписями полей. Пройди критерии приёмки требований всех задач партии (бэкенд-часть): выполнен / частично / нет — таблицей. Останови приложение.` },
  { key: 'arch', prompt: `Ты — ревьюер архитектуры (Spring Boot 4.1, Modulith 2.1, Hibernate 7). Прочитай весь ${APP}/backend/src и ${APP}/docs/architecture.md. Ищи: нарушения направлений модулей и named interfaces, права вне @perm/скоупов, логику в контроллерах (R002), строки мимо MessageSource (R003), списки без серверной пагинации/поиска (R001), N+1 в списках (счётчики использования, lookup), кэш без clinic_id, слушатели без журнала/идемпотентности, дублирование валидаторов, самописное вместо готового (R021), конфиг, который сломает этапы 2–6, пробелы architecture.md и документации модуля. Сборку не запускай.` },
  { key: 'sec', prompt: `Ты — пентестер. Код ${APP}/backend/src; собери jar (без mvn clean) и запусти с профилем dev на 28080/28081 для black-box проверок curl, затем останови. Проверь: IDOR по всем новым эндпоинтам clinic (чужой clinic_id/branch_id/chair_id/service_id/dictionary id → 404), скоупы филиалов и «складские», SVG-логотип (XSS через SVG, внешние ссылки, подмена типа, размер), импорт (XXE и зип-бомба в XLSX, формулы =HYPERLINK/DDE в отчёте ошибок, чужой файл по id, гонки заданий), обход BackdatePolicy, ПД/секреты в логах, лимиты на тяжёлых эндпоинтах (импорт, поиск). Каждую находку подтверди воспроизведением (команда + ответ) в evidence.` },
]
const lensRuns = (await parallel(LENSES.map(l => () => agent(`${BASE}\n\n${l.prompt}\nВерни buildGreen (true, если сборку не проверял), evidence и findings с severity.`, { label: `final:${l.key}`, phase: 'Final review', schema: REVIEW, model: 'opus', effort: 'xhigh' })))).filter(Boolean)

let allReviews = lensRuns
const finalFixes = []
for (let round = 0; round < 2; round++) {
  const blocking = allReviews.flatMap(r => r.findings).filter(f => f.severity !== 'minor')
  const red = allReviews.some(r => r.buildGreen === false)
  log(`Финал, круг ${round + 1}: blocker/major ${blocking.length}, сборка ${red ? 'КРАСНАЯ' : 'зелёная'}`)
  if (!blocking.length && !red) break
  phase('Final fix')
  const fx = await agent(`${BASE}

Исправь находки финального ревью партии C1 бэкенда этапа 1. Обязательные:
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
Проверь ИСПОЛНЕНИЕМ: ./mvnw -B clean verify зелёный; каждая находка закрыта (воспроизведи прежний сценарий); ничего не сломано. Верни buildGreen, evidence и findings (только незакрытое и новое).`, { label: `final:recheck#${round + 1}`, phase: 'Final fix', schema: REVIEW, model: 'opus', effort: 'xhigh' })
  allReviews = [re].filter(Boolean)
}

const finalOk = allReviews.length && allReviews.every(r => r.buildGreen !== false && !r.findings.some(f => f.severity !== 'minor'))
if (finalFixes.length) {
  await agent(`В репозитории C:/Users/abyla/code (ветка master) закоммить и запушить исправления по финальному ревью партии (${meta.name}). Правило dentify R004. Шаги: cd C:/Users/abyla/code; git add apps/dentify (только этот каталог); проверь индекс на секреты и мусор; если индекс пуст — сообщи и закончи; git commit: заголовок «fix(dentify): address final review of ${meta.name}», тело — список закрытых находок (кратко) и число тестов, последняя строка ровно: Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>; затем bash C:/Users/abyla/code/bin/git-sync-push.sh; git log --oneline -1. Итог ревью: ${finalOk ? 'все обязательные находки закрыты' : 'ВНИМАНИЕ: остались незакрытые находки — всё равно коммить текущее состояние, оно собирается'}. Отчёт исправлений: ${JSON.stringify(finalFixes.at(-1)?.summary ?? '').slice(0, 1500)}`, { label: 'commit:final', phase: 'Final fix', schema: COMMIT, model: 'sonnet', effort: 'low' })
}
return {
  tasks: results.map(r => ({ id: r.id, impl: r.impl, fixes: r.fixes, lastReview: r.lastReview })),
  lenses: lensRuns, finalFixes, finalState: allReviews,
}
