export const meta = {
  name: 'dentify-phase1-backend-b',
  description: 'Dentify этап 1, бэкенд, партия B: файлы S3, выгрузка Excel, регистрация клиники, аккаунт владельца, контекст клиники/филиала, аудит clinic, фабрика тестовых данных и демо-клиника, автотест изоляции по всем эндпоинтам — по задаче с проверкой, затем общее ревью',
  phases: [
    { title: 'Tasks', detail: 'impl → review → fix по задаче', model: 'opus' },
    { title: 'Final review', detail: 'приёмка сквозняком + архитектура + безопасность', model: 'opus' },
    { title: 'Final fix', detail: 'исправления по общему ревью', model: 'opus' },
  ],
}

const APP = 'C:/Users/abyla/code/apps/dentify'
const BASE = `Ты работаешь над бэкендом проекта Dentify в ${APP}/backend (Windows; Git Bash и PowerShell; JDK 25: export JAVA_HOME="$HOME/.jdks/openjdk-25.0.2"; export PATH="$JAVA_HOME/bin:$PATH" — системная java 17 не годится; Docker Desktop запущен; окружение ${APP}/deploy/dev.ps1 up, пароли в ${APP}/deploy/.env). СНАЧАЛА прочитай ${APP}/CLAUDE.md и ${APP}/docs/architecture.md ЦЕЛИКОМ — обязательные соглашения: карта модулей и направления (clinic ← iam, staff, schedule, inventory; staff, patient ← schedule; medical → patient, schedule, clinic; все → platform; примитивы сессии в platform :: identity только для iam; onboarding — отдельный модуль-оркестратор, зависящий от clinic и iam), раскладка пакетов модуля, «Как добавить таблицу арендатора» (имя миграции V<yyyy>_<MM>_<dd>_<HHmm>__<модуль>_<что>.sql, clinic_id, RLS ENABLE+FORCE+политика, uuidv7, тест каталога), «Как добавить эндпоинт» (record-DTO, сервис с правилами, @PreAuthorize("@perm.has(...)"), коды ошибок → ProblemDetail, @Operation + обновление ${APP}/api/openapi.json сборкой, строка в реестре теста изоляции, интеграционный тест), «Права» (эффективные права на запрос с кэшем, @perm, PermissionContributor, CurrentMember, ScopeFilter), «Модули и события» (журнал публикаций JDBC, идемпотентные слушатели), «Логи», i18n (R003). Правила: dentify R002 — вся логика на бэкенде; R003 — только русский, строки только через коды/MessageSource; R021 — сначала официальное готовое; Spring Boot 4.1 / Security 7 / Modulith 2.1 / Hibernate 7 / AWS SDK v2 / fastexcel сверяй через MCP context7. Задачу и требования достань из ${APP}/docs/backlog.json командой из CLAUDE.md; план — C:/Users/abyla/code/wiki/projects/dentify/mvp-plan.md (Grep). Фронта в этой партии нет: делай ТОЛЬКО бэкенд-части, фронтовые подзадачи — в subtasksNotDone с пометкой «фронт». Уже готово (не переделывай, расширяй): этап 0 целиком и партия A этапа 1 (события и почта, схема и домен clinic, каталог и проверка прав @perm, валидаторы Iin/Bin/KzPhone, search_norm, сид клиники, защита входа, NotificationSender). Ничего не ломай: весь набор тестов остаётся зелёным. Обновляй docs/architecture.md разделом по своей теме. Не коммить и не пушь. Данные — вымышленные. Порты для ручных проверок — 18080/18081, останавливай после. В конце: ./mvnw -B verify зелёный, api/openapi.json обновлён сборкой.`

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
  { id: 'T-PLT-20', title: 'Файловое хранилище S3 (бэкенд)', spec: `Бэкенд-часть задачи T-PLT-20 (требование PLT-13), подзадачи из backlog.json: порт FileStorage в платформе + адаптер S3 (AWS SDK v2, MinIO в dev по ключам приложения из deploy/.env, бакет dentify-files, префикс пути — clinic_id), миграция file_object (таблица арендатора по шаблону), потоковая загрузка через бэкенд с определением типа по сигнатуре (Apache Tika) и лимитами по назначению (logo, avatar, patient-file, …), подписанные ссылки на 5 мин только после проверки права на объект-владелец (SPI владельцев: модуль регистрирует проверку доступа к своему типу владельца), скачивание медфайлов — в аудит, превью изображений в фоне (задача через события/планировщик из T-PLT-14, Thumbnailator или ImageIO), мягкое удаление с восстановлением, тесты на Testcontainers MinIO (подменённое расширение, превышение размера, истёкшая ссылка, чужая клиника). Эндпоинты — общие /api/v1/files/... с @perm по владельцу. Компонент FileDropzone — фронт, не делать.` },
  { id: 'T-PLT-18', title: 'Выгрузка в Excel (бэкенд)', spec: `Бэкенд-часть задачи T-PLT-18 (требование PLT-30): ExportService с потоковой генерацией XLSX (fastexcel) по описанию колонок и Stream строк; текущие фильтры и сортировка, лимит 100 000 строк, маски по правам (телефон/ИИН через тот же механизм, что @PatientPhone); шаблон эндпоинта с Content-Disposition «<раздел>_<период>.xlsx» и событием аудита «Выгрузка»; первый потребитель — выгрузка журнала аудита (эндпоинт в iam или platform, @perm audit:read); тест памяти: 100 000 строк укладываются в 256 МБ (запуск с -Xmx256m в отдельном тесте или проверка через MemoryMXBean). ExportButton — фронт, не делать.` },
  { id: 'T-CLN-04', title: 'API регистрации клиники', spec: `Задача T-CLN-04 (требования CLN-01, CLN-02, CLN-03), подзадачи из backlog.json: модуль onboarding (оркестратор, зависит от clinic и iam — заведи в карте модулей и ModularityTests), POST /api/v1/public/registrations (record-DTO трёх шагов: клиника, филиал с часами и 1–20 креслами, владелец; серверная валидация: libphonenumber, часы, согласие ПД — чекбокс обязателен), одна транзакция: клиника + настройки + филиал + кресла + сид (T-CLN-03) + владелец и членство с ролью «Владелец» через API iam (T-IAM-10 сделает registerOwner — здесь заложи вызов через порт iam :: api и минимальную реализацию, если её ещё нет; T-IAM-10 доведёт) + аудит; Idempotency-Key в Valkey на 24 ч; rate-limit 5 регистраций в час на IP → 429; автовход после регистрации (сессия + CSRF в ответе) с защитой от фиксации; GET /api/v1/public/cities?q=; публичный префикс /api/v1/public/** — по architecture.md (SecurityConfiguration.publicPaths, реестр изоляции allowlist, CSRF и общий лимит сохраняются); интеграционные тесты: успех, откат при сбое создания владельца, повтор с тем же ключом, занятый телефон → 409 с полем.` },
  { id: 'T-IAM-10', title: 'Аккаунт владельца при регистрации (бэкенд)', spec: `Бэкенд-часть задачи T-IAM-10 (требования IAM-09, IAM-05): сервис registerOwner (новый аккаунт или существующий с проверкой пароля) в транзакции регистрации через iam :: api, членство с ролью «Владелец» на все филиалы, автовход, инвариант «хотя бы один активный владелец» (сервис + тест), REST шага «Владелец» описан в OpenAPI (как часть registrations), интеграционный тест: вторая клиника на том же телефоне не создаёт второй аккаунт. OwnerStep — фронт, не делать.` },
  { id: 'T-PLT-17', title: 'Контекст клиники и филиала (бэкенд)', spec: `Бэкенд-часть задачи T-PLT-17 (требование PLT-03): контекст клиники и филиала в сессии уже частично есть (T-PLT-03/T-IAM-02: /api/v1/me/context POST) — доведи до требования: GET /api/v1/me/context и PUT для смены; при смене проверка членства и скоупа филиала роли (ScopeFilter из T-IAM-07): вне скоупа — 403; список доступных клиник и филиалов для экрана выбора в /api/v1/me (карточки: адрес, город, число кресел; число записей на сегодня — 0 до модуля schedule, заложи SPI); при единственном варианте контекст ставится при входе; смена клиники пересоздаёт тенант-контекст и версию кэша; интеграционные тесты. Экран VyborKliniki и переключатель — фронт, не делать.` },
  { id: 'T-CLN-21', title: 'Модуль clinic в механизме аудита', spec: `Задача T-CLN-21 (требование CLN-28), подзадачи из backlog.json: словарь русских подписей сущностей и полей clinic (audit.field.*/audit.value.* в messages.properties, тест AuditFieldLabels.missing пуст), типы событий (создан, изменён, удалён, архивирован, восстановлен, переставлен, импорт), перестановка и импорт — одной записью, фильтр раздела «Клиника» в журнале (контракт с iam), @Audited на сервисах clinic (те, что уже есть: настройки/справочники — если REST ещё нет, покрой сервисный слой и сид), интеграционный тест: правка даёт запись с diff, отклонённая валидацией — не пишется.` },
  { id: 'T-PLT-31', title: 'Фабрика тестовых данных и демо-клиника «Ақ Тіс»', spec: `Задача T-PLT-31 (требования PLT-26, PLT-01), подзадачи из backlog.json: Java-билдеры (клиника, филиал, кресло, аккаунт, членство, роль) для интеграционных тестов в src/test платформы с точкой расширения для модулей; профиль demo: сидер клиники «Ақ Тіс» (2 филиала, 6 кресел, системные роли, 5 сотрудников-аккаунтов по ролям с паролями из переменных) — данные вымышленные, как на макетах; две клиники A и B в фикстурах для тестов изоляции; эндпоинт сброса и сида БД только в профиле e2e (POST /api/v1/e2e/reset) + тест, что в prod/dev его нет; заготовка для globalSetup Playwright: документированный контракт (фронт подключит в T-PLT-16/T-CLN-26). Обнови ${APP}/deploy/dev.ps1: команда demo (запуск с профилем dev,demo).` },
  { id: 'T-PLT-32', title: 'Автотест изоляции арендаторов по всем эндпоинтам', spec: `Задача T-PLT-32 (требования PLT-01, PLT-02), подзадачи из backlog.json. Реестр TenantIsolationTests уже есть с этапа 0 — доведи: тест читает ${APP}/api/openapi.json и перечисляет все пути с {id}; реестр фикстур «как создать ресурс в клинике B» (билдеры T-PLT-31); запрос пользователя A к ресурсу B — 404, списки и поиск не содержат id из B; сборка падает, если новый эндпоинт не покрыт реестром (публичные — явный allowlist); 401 без сессии, 403 без CSRF на изменяющих; проверка префикса clinic_id в ключах Valkey и путях S3; тест каталога RLS для всех таблиц. Подзадачу «в CI на каждый PR» замени на запуск в ./mvnw verify (CI отложен).` },
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
  { key: 'acceptance', prompt: `Ты — финальный приёмщик партии B бэкенда этапа 1 (${TASKS.map(t => t.id).join(', ')}). Сводка исполнителей:
${digest}
Проверь ИСПОЛНЕНИЕМ: deploy/dev.ps1 check; ./mvnw -B clean verify — зелёный, число тестов; запусти jar с профилем dev на 18080/18081 и пройди сквозной сценарий через curl: регистрация клиники POST /api/v1/public/registrations (3 шага, вымышленные данные) с Idempotency-Key → автовход → /api/v1/me (владелец, права, контекст) → GET/PUT /api/v1/me/context → загрузка логотипа/файла в S3 и подписанная ссылка → выгрузка журнала аудита в XLSX → повтор регистрации с тем же ключом → тот же ответ; 6-я регистрация с одного IP за час → 429; проверь RLS для новых таблиц по каталогу; профиль demo поднимает «Ақ Тіс». Пройди критерии приёмки требований всех задач партии (бэкенд-часть): выполнен / частично / нет — таблицей. Останови приложение.` },
  { key: 'arch', prompt: `Ты — ревьюер архитектуры (Spring Boot 4.1, Modulith 2.1, Hibernate 7). Прочитай весь ${APP}/backend/src и ${APP}/docs/architecture.md. Ищи: нарушения направлений модулей и named interfaces (onboarding → clinic, iam; никто → onboarding), права вне @perm, логику в контроллерах (R002), строки мимо MessageSource (R003), слушатели без журнала/идемпотентности, транзакционные границы регистрации (частичная запись при сбое), кэш/S3 без clinic_id, самописное вместо готового (R021), дублирование, конфиг, который сломает этапы 2–6, пробелы architecture.md. Сборку не запускай.` },
  { key: 'sec', prompt: `Ты — пентестер. Код ${APP}/backend/src; собери jar (без mvn clean) и запусти с профилем dev на 28080/28081 для black-box проверок curl, затем останови. Проверь: публичная регистрация (спам-клиники, обход лимита через X-Forwarded-For, фиксация сессии при автовходе, регистрация на чужой существующий телефон/e-mail — перечисление аккаунтов), IDOR на файлы (подписанные ссылки, чужой clinic_id в пути, подмена типа файла, размер), выгрузка Excel чужой клиники/без права, смена контекста на чужую клинику/филиал вне скоупа, e2e-эндпоинт сброса в dev/prod, RLS через SQL под dentify_app, ПД/секреты в логах. Каждую находку подтверди воспроизведением (команда + ответ) в evidence.` },
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

Исправь находки финального ревью партии B бэкенда этапа 1. Обязательные:
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
