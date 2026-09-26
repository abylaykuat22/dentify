export const meta = {
  name: 'dentify-phase1-backend-c2',
  description: 'Dentify этап 1, бэкенд, партия C2: REST-API модуля iam (сотрудники, роли, сессии и журнал входов, сброс пароля, приглашения, профиль, журнал действий с XLSX), защита регистрации и SMS, тесты изоляции и прав iam, документация модели доступов — по задаче с проверкой, затем общее ревью',
  phases: [
    { title: 'Tasks', detail: 'impl → review → fix → commit по задаче', model: 'opus' },
    { title: 'Final review', detail: 'приёмка сквозняком + архитектура + безопасность', model: 'opus' },
    { title: 'Final fix', detail: 'исправления по общему ревью', model: 'opus' },
  ],
}


const APP = 'C:/Users/abyla/code/apps/dentify'
const BASE = `Ты работаешь над бэкендом проекта Dentify в ${APP}/backend (Windows; Git Bash и PowerShell; JDK 25: export JAVA_HOME="$HOME/.jdks/openjdk-25.0.2"; export PATH="$JAVA_HOME/bin:$PATH" — системная java 17 не годится; Docker Desktop запущен; окружение ${APP}/deploy/dev.ps1 up, пароли в ${APP}/deploy/.env). СНАЧАЛА прочитай ${APP}/CLAUDE.md и ${APP}/docs/architecture.md ЦЕЛИКОМ — обязательные соглашения: карта модулей и направления (clinic ← iam, staff, schedule, inventory; staff, patient ← schedule; medical → patient, schedule, clinic; onboarding → clinic, iam; все → platform; примитивы сессии в platform :: identity только для iam), раскладка пакетов модуля, «Как добавить таблицу арендатора» (имя миграции V<yyyy>_<MM>_<dd>_<HHmm>__<модуль>_<что>.sql, clinic_id, RLS ENABLE+FORCE+политика, uuidv7, тест каталога), «Как добавить эндпоинт» (record-DTO в web, сервис с правилами, @PreAuthorize("@perm.has(...)") со скоупами, коды ошибок → ProblemDetail в messages.properties, @Operation + обновление ${APP}/api/openapi.json сборкой, строка в реестре теста изоляции, интеграционный тест), «Права» (эффективные права на запрос с кэшем, @perm, PermissionContributor, CurrentMember, ScopeFilter), «Модули и события» (журнал публикаций JDBC, идемпотентные слушатели), «Аудит» (@Audited, подписи полей audit.field.*), «Логи», i18n (R003, LocalizedText {ru, kk}). Правила: dentify R001 — растущий список = серверный поиск/фильтры/сортировка/пагинация (PageResponse, size ≤ 100); R002 — вся логика на бэкенде, что разрешено — сообщает API (allowedActions, 409/422); R003 — только русский, строки только через коды/MessageSource; R021 — сначала официальное готовое; Spring Boot 4.1 / Security 7 / Modulith 2.1 / Hibernate 7 сверяй через MCP context7. Задачу и требования (описание + критерии приёмки) достань из ${APP}/docs/backlog.json командой из CLAUDE.md; план — C:/Users/abyla/code/wiki/projects/dentify/mvp-plan.md (Grep). Фронта в этой партии нет: делай ТОЛЬКО бэкенд-части, фронтовые подзадачи — в subtasksNotDone с пометкой «фронт». Уже готово (не переделывай, расширяй): этап 0 целиком; этап 1 партия A (события и почта, схема и домен clinic, каталог прав и @perm, валидаторы Iin/Bin/KzPhone, search_norm, сид клиники, защита входа, NotificationSender) и партия B (S3-хранилище, ExportService XLSX, регистрация клиники /api/v1/public/registrations, registerOwner, контекст клиники/филиала, аудит clinic, фабрика тестовых данных и демо «Ақ Тіс», автотест изоляции по OpenAPI) и партия C1 (REST-API clinic: профиль, филиалы, кресла, прайс, справочники, настройки и BackdatePolicy, логотип; движок импорта Excel и импорт прайса). Ничего не ломай: весь набор тестов остаётся зелёным. Обновляй docs/architecture.md разделом по своей теме. Не коммить и не пушь сам — коммитит отдельный шаг после ревью. Данные — вымышленные. Порты для ручных проверок — 18080/18081, останавливай после. В конце: ./mvnw -B verify зелёный, api/openapi.json обновлён сборкой.`

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
  { id: 'T-IAM-11', title: 'API сотрудников с доступом', spec: `Задача T-IAM-11 (требования IAM-10..13, IAM-28), подзадачи из backlog.json: GET /api/v1/access/members (R001: статус, поиск по ФИО и последним цифрам телефона через search_norm/pg_trgm, сортировка, пагинация, счётчики статусов), PATCH роли и филиалов с @Version → 409, отключение/включение (статус членства; после коммита AccountSessions.terminateInClinic — прямой доступ к Spring Session запрещён), инварианты (не себя; «Владельца» выдаёт только владелец; последний владелец; запрет повышения прав выше своих), «Сбросить пароль» и «Завершить все сеансы» от имени администратора (через порты iam), allowedActions в каждой строке, @Audited с diff, тесты изоляции и прав.` },
  { id: 'T-IAM-18', title: 'API ролей', spec: `Задача T-IAM-18 (требования IAM-20, IAM-22, IAM-23, IAM-28), подзадачи из backlog.json: GET ролей с числом людей и пометками (системная/своя), PUT прав роли с @Version (409 с автором и временем), применение зависимостей прав, валидация скоупов, филиалы роли; POST своей роли копией базовой; переименование с уникальностью без регистра; DELETE своей роли с переводом людей в выбранную; запреты для Владельца и системных ролей (allowedActions); событие RolePermissionsChanged → сброс кэша прав; аудит с diff по каждому праву; тесты.` },
  { id: 'T-IAM-22', title: 'API сессий и журнала входов', spec: `Задача T-IAM-22 (требования IAM-29, IAM-30, IAM-33), подзадачи из backlog.json: метаданные сессии при входе (разбор user-agent библиотекой по context7 → устройство, браузер, ОС; город по локальной GeoIP-базе, если файл есть; последняя активность), GET сессий клиники по контексту (для «Входы и сессии»), DELETE одной и «все, кроме моей», «Мои сеансы» по аккаунту, код 401 session.revoked с причиной для завершённых сессий, GET журнала входов (auth_event: фильтр ошибок, сотрудник, пагинация, видимость по членству и праву), аудит завершений, тесты.` },
  { id: 'T-IAM-16', title: 'Сброс пароля (бэкенд)', spec: `Задача T-IAM-16 (требования IAM-06, IAM-12), подзадачи из backlog.json: токены сброса через TokenService этапа 0 (hash, 30 мин, одноразовые) — отдельную таблицу заводи, только если TokenService не покрывает; POST /api/v1/public/password-reset (одинаковый ответ и время, кулдаун 60 с, 5 в сутки, e-mail или SMS через NotificationSender, отмена прежних, маска адреса в ответе); POST подтверждения (политика пароля, завершение всех сессий, автовход, снятие блокировки входа, auth_event с каналом); сброс по запросу администратора тем же механизмом; публичные пути — по соглашению /api/v1/public/**; интеграционные тесты: устаревшая и повторная ссылка, несуществующий логин.` },
  { id: 'T-IAM-13', title: 'Приглашения (бэкенд)', spec: `Задача T-IAM-13 (требования IAM-14..16, IAM-28), подзадачи из backlog.json: миграция invite (частичный уникальный индекс на активные), POST создания с проверками (уже активен/отключён/приглашён), лимит 50 в сутки, режим «скопировать ссылку», повторная отправка с ротацией токена и кулдауном 60 с, отзыв, статус «истекло»; публичные GET /api/v1/public/invitations/{token} и POST .../accept (новый аккаунт, существующий с паролем, уже вошедший) с CSRF, лимитом и записью в реестр изоляции; принятие: членство, auth_event, аудит, контекст сессии на клинику и филиал; письма/SMS через NotificationSender; интеграционные тесты всех веток и устаревших ссылок.` },
  { id: 'T-IAM-24', title: 'API профиля (бэкенд)', spec: `Бэкенд-часть задачи T-IAM-24 (требования IAM-31..33): GET и PATCH /api/v1/me/profile (ФИО; язык в MVP скрыт — хранится ru) с @Version; фото (сигнатура, ≤ 2 МБ, ресайз до 256 px в WebP — проверь наличие WebP-энкодера в Java через context7, иначе PNG; FileStorage платформы; удаление); смена пароля (проверка текущего с лимитом подбора, политика, завершение других сессий, auth_event); смена e-mail (ссылка на 24 ч) и телефона (SMS-код 6 цифр, 10 мин, 5 попыток) с уведомлением на старый адрес; тесты уникальности контактов и сценариев подтверждения.` },
  { id: 'T-IAM-20', title: 'API журнала действий и выгрузка в Excel', spec: `Задача T-IAM-20 (требования IAM-26..28), подзадачи из backlog.json: запрос по audit_log (период в поясе клиники, сотрудник, категория, поиск по фразе через search_norm/pg_trgm), keyset-пагинация, общее число и счётчики по дням, преобразование diff в «поле: было → стало» с подписями и ссылкой на объект с учётом прав, маска IP; потоковая выгрузка XLSX через ExportService (лимит 100 000, аудит выгрузки); право «Журналы и сессии» (@perm); тест «каждое изменяющее действие iam даёт ровно одну запись».` },
  { id: 'T-IAM-30', title: 'Защита регистрации и SMS от злоупотреблений', spec: `Задача T-IAM-30 (требование IAM-45), подзадачи из backlog.json: подтверждение телефона или e-mail владельца кодом до активации (статус клиники «не подтверждена» — ограничения по статусу задаются политикой в clinic :: api), лимиты в Valkey по IP, номеру/адресу и клинике для регистрации, кодов, сброса пароля и повтора приглашений (общий RateLimits платформы), суточный лимит SMS на клинику и общий бюджет с алертом (лог + событие), список разрешённых стран (+7 по умолчанию), фоновая очистка неподтверждённых клиник старше 7 дней (TenantAwareJob), тесты: 429 со временем повтора.` },
  { id: 'T-IAM-28', title: 'Тесты изоляции, прав и аудита iam (бэкенд)', spec: `Бэкенд-часть задачи T-IAM-28 (требования IAM-24, IAM-01, IAM-28, IAM-34): параметризованный тест по OpenAPI — каждый эндпоинт iam: без сессии 401, без права 403, чужая клиника 404; «одно изменение — одна запись аудита» для всех изменяющих эндпоинтов; перехватчик логов — нет ИИН, телефонов и e-mail; ApplicationModules.verify и Documenter модуля. Playwright на 360/390 px — фронт, не делать.` },
  { id: 'T-IAM-29', title: 'Документация модели доступов', spec: `Задача T-IAM-29 (требования IAM-20, IAM-21, IAM-24, IAM-25): документ модели доступов (там же, где документация clinic из C1): таблица каталога прав и стартовых наборов пяти ролей (генерировать из кода тестом, чтобы не расходилась), как модулю проверить право и скоуп (@perm, ScopeFilter, @PatientPhone, CurrentMember), жизненный цикл приглашения, сессий и сброса пароля, ADR «роли и скоупы вместо поштучной матрицы macdent». Сверить с кодом.` },
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
  { key: 'acceptance', prompt: `Ты — финальный приёмщик партии C2 бэкенда этапа 1 (${TASKS.map(t => t.id).join(', ')}). Сводка исполнителей:
${digest}
Проверь ИСПОЛНЕНИЕМ: deploy/dev.ps1 check; ./mvnw -B clean verify — зелёный, число тестов; запусти jar с профилем dev на 18080/18081 и пройди через curl сценарий: владелец приглашает администратора (письмо в Mailpit, ссылка) → принятие приглашения новым аккаунтом → вход администратором → список сотрудников с поиском → попытка выдать себе роль «Владелец» → 403 → владелец меняет роль и отключает сотрудника → его сессия завершена (401 session.revoked) → сброс пароля по e-mail (Mailpit) → новый пароль → «Мои сеансы» → журнал действий показывает каждое изменение, XLSX выгружается; лимиты: 6-я попытка сброса → 429. Пройди критерии приёмки требований всех задач партии (бэкенд-часть): выполнен / частично / нет — таблицей. Останови приложение.` },
  { key: 'arch', prompt: `Ты — ревьюер архитектуры (Spring Boot 4.1, Modulith 2.1, Hibernate 7). Прочитай весь ${APP}/backend/src и ${APP}/docs/architecture.md. Ищи: нарушения направлений модулей (iam зависит только от platform и clinic :: api), обход платформенных портов сессий, права вне @perm, логику в контроллерах (R002), строки мимо MessageSource (R003), списки без пагинации (R001), гонки инвариантов (последний владелец, повышение прав), кэш прав без инвалидации, дублирование токен-механизмов (сброс, приглашение, смена контакта — один TokenService), самописное вместо готового (R021), пробелы документации модели доступов. Сборку не запускай.` },
  { key: 'sec', prompt: `Ты — пентестер. Код ${APP}/backend/src; собери jar (без mvn clean) и запусти с профилем dev на 28080/28081 для black-box проверок curl, затем останови. Проверь: повышение привилегий (администратор выдаёт себе или другому «Владельца», правит системную роль, снимает последнего владельца), IDOR по членствам/ролям/сессиям/приглашениям чужой клиники, перечисление аккаунтов через сброс пароля и приглашения (текст и время ответа), перебор токенов и кодов (энтропия, лимиты, одноразовость), фиксация сессии при принятии приглашения и автовходе после сброса, отзыв сессии при отключении (запрос «в полёте»), утечка ПД в журнале действий без права, XLSX-инъекции (формулы) в выгрузке, лимиты SMS и обход через X-Forwarded-For. Каждую находку подтверди воспроизведением (команда + ответ) в evidence.` },
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

Исправь находки финального ревью партии C2 бэкенда этапа 1. Обязательные:
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
