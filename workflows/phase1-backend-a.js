export const meta = {
  name: 'dentify-phase1-backend-a',
  description: 'Dentify этап 1, бэкенд, партия A: события/планировщик/почта, схема clinic, каталог и проверка прав, валидаторы, казахская нормализация, домен clinic, сид клиники, защита входа, почта/SMS — по задаче с проверкой, затем общее ревью',
  phases: [
    { title: 'Tasks', detail: 'impl → review → fix по задаче', model: 'opus' },
    { title: 'Final review', detail: 'приёмка сквозняком + архитектура + безопасность', model: 'opus' },
    { title: 'Final fix', detail: 'исправления по общему ревью', model: 'opus' },
  ],
}

const APP = 'C:/Users/abyla/code/apps/dentify'
const BASE = `Ты работаешь над бэкендом проекта Dentify в ${APP}/backend (Windows; Git Bash и PowerShell; JDK 25: export JAVA_HOME="$HOME/.jdks/openjdk-25.0.2"; export PATH="$JAVA_HOME/bin:$PATH" — системная java 17 не годится; Docker Desktop запущен; окружение ${APP}/deploy/dev.ps1 up, пароли в ${APP}/deploy/.env). СНАЧАЛА прочитай ${APP}/CLAUDE.md и ${APP}/docs/architecture.md ЦЕЛИКОМ — там обязательные соглашения этапа 0: карта модулей и направления (clinic ← iam, staff, schedule, inventory; staff, patient ← schedule; medical → patient, schedule, clinic; все → platform; примитивы сессии в platform :: identity только для iam), раскладка пакетов модуля (api, events, web, internal), «Как добавить таблицу арендатора» (имя миграции V<yyyy>_<MM>_<dd>_<HHmm>__<модуль>_<что>.sql, clinic_id, RLS ENABLE+FORCE+политика, uuidv7, тест каталога), «Как добавить эндпоинт» (record-DTO в web, сервис с правилами, @PreAuthorize, коды ошибок в messages.properties → ProblemDetail, @Operation + обновление ${APP}/api/openapi.json через сборку, строка в реестре теста изоляции, интеграционный тест), «Права» (эффективные права считаются на запрос с кэшем в Valkey, в сессии не хранятся; бин @perm в платформе, SPI реализует iam; коды прав модули объявляют через PermissionContributor), «Модули и события» (журнал публикаций JDBC обязателен до любых @ApplicationModuleListener), «Логи» (маска ПД по значению и по имени ключа), i18n (R003: коды + MessageSource, LocalizedText {ru, kk}). Правила проекта: dentify R002 — вся логика на бэкенде; R003 — только русский, ни одной строки пользователю в коде; R021 — сначала официальное готовое (Spring, Hibernate 7, Modulith 2.1, springdoc, Testcontainers 2), потом комьюнити, свой код — последним; Spring Boot 4.1 / Security 7 / Modulith 2.1 / Hibernate 7 сверяй с документацией через MCP context7 (ToolSearch "context7"), не по памяти. Задачу и требования (описание + критерии приёмки) достань из ${APP}/docs/backlog.json командой из CLAUDE.md; план — C:/Users/abyla/code/wiki/projects/dentify/mvp-plan.md (Grep по разделу). Фронта в этой партии нет: делай ТОЛЬКО бэкенд-части задачи, фронтовые подзадачи перечисли в subtasksNotDone с пометкой «фронт». Уже готово на этапе 0 (не переделывай, расширяй): тенант-контекст и RLS, сессии/CSRF/лимиты/пароли, ProblemDetail/OpenAPI/i18n, аудит (@Audited, AuditService, партиции), аккаунты и членство (iam), минимальные таблицы clinic и branch (расширяй их, не пересоздавай), dev-сид владельца. Ничего не ломай: весь набор тестов (сейчас 572) остаётся зелёным. Обновляй docs/architecture.md разделом по своей теме. Не коммить и не пушь. Данные — вымышленные. Порты для ручных проверок — 18080/18081, останавливай после. В конце: ./mvnw -B verify зелёный, api/openapi.json обновлён сборкой.`

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
  { id: 'T-PLT-14', title: 'События модулей, фоновые задачи, отправка писем', spec: `Задача T-PLT-14 (требование PLT-24), все подзадачи из backlog.json. Ключевое: spring-modulith-starter-jdbc, таблица event_publication миграцией platform в схеме dentify (schema-initialization выключен), переотправка незавершённых публикаций задачей под ShedLock (JDBC, таблица shedlock миграцией) через IncompleteEventPublications, а не republish-outstanding-events-on-restart; шаблон идемпотентного @ApplicationModuleListener + тесты Scenario API; планировщик включить явно (@EnableScheduling; Moments выключены); TenantAwareJob — задача по всем клиникам обходит их с явным тенант-контекстом на каждую; задачи очистки (истёкшие токены/приглашения, индекс сессий); Spring Mail с Mailpit в dev: шаблоны писем на русском (казахский — заготовка словаря, R003), очередь отправки с 5 повторами через реестр публикаций; тесты: письмо на языке пользователя, досылка после недоступности SMTP (Testcontainers Mailpit или GreenMail).` },
  { id: 'T-CLN-01', title: 'Схема БД модуля clinic', spec: `Задача T-CLN-01 (требования CLN-06, 07, 10, 12, 13, 14, 17, 19, 22, 23, 24, 25), подзадачи из backlog.json. Минимальные clinic и branch уже созданы в T-IAM-01 — расширяй их ALTER-ами в миграции модуля clinic (владелец таблиц clinic/branch — модуль clinic; проверь, где лежат их сущности сейчас, и перенеси в clinic, если они в iam, без циклов: iam → clinic :: api разрешён). Все таблицы арендатора — по шаблону architecture.md (RLS, uuidv7, индексы по clinic_id), глобальный справочник city с сидом городов РК (вымышленных данных не нужно — реальные города и пояса). Тест миграций + тест каталога RLS.` },
  { id: 'T-IAM-06', title: 'Каталог прав и системные роли', spec: `Задача T-IAM-06 (требования IAM-20, IAM-21, IAM-09), подзадачи из backlog.json. Дерево Permission в коде платформы (platform.api: тип права, раздел, действие, тип скоупа OWN_ALL/BRANCHES/WAREHOUSES, зависимости, i18n-ключи), SPI PermissionContributor в платформе — каждый модуль объявляет свои коды, каталог собирает iam (зависимость модулей → iam запрещена, см. ModularityTests.ALLOWED). Миграции role, role_permission (permission, scope jsonb), role_branch — таблицы арендатора по шаблону. Пять системных ролей (Владелец неизменяем, Администратор, Врач, Ассистент, Кладовщик) из шаблонов в ресурсах; слушатель ClinicRegistered (событие объявит T-CLN-02 — пока событие регистрации из onboarding/clinic может отсутствовать: сделай сид ролей вызываемым сервисом + слушателем на событие, которое заведи в clinic :: events сейчас минимально, если его нет); миграция новых прав по шаблонам; GET /api/v1/access/permissions с @PreAuthorize; юнит-тесты зависимостей прав; dev-сид владельца получает роль «Владелец».` },
  { id: 'T-IAM-07', title: 'Проверка прав, скоупы, маска телефонов', spec: `Задача T-IAM-07 (требования IAM-24, IAM-25, IAM-08), подзадачи из backlog.json и раздел «Права» architecture.md — реализуй ровно его: эффективные права на запрос (роль ∩ филиалы членства) с кэшем в Valkey по clinicId + membershipId + версии роли, инвалидация по событиям RolePermissionsChanged и MembershipChanged; в Authentication сессии права не хранятся; бин @perm в платформе делегирует SPI, реализация в iam; контракты CurrentMember и ScopeFilter в platform.api; нет активного членства в клинике сессии → 401 error.clinic-not-selected (закрывает окно гонки terminateInClinic); все существующие hasAuthority(...) на эндпоинтах (аудит, сессии) заменить на @perm с кодами прав; 403 ProblemDetail с кодом права; @PatientPhone + Jackson-сериализатор маски по праву patient:phones и шаблон эндпоинта «Показать» с аудитом; тестовая реализация SPI прав в платформе для тестов других модулей; /api/v1/me дополнить эффективными правами; тесты: 403, 404 чужой клиники, скоуп «Свои» на двух врачах (фикстуры минимальные, staff ещё нет — используй членства).` },
  { id: 'T-PLT-12', title: 'Валидаторы ИИН, БИН и телефона (бэкенд)', spec: `Бэкенд-часть задачи T-PLT-12 (требование PLT-12): общий JSON тест-векторов ИИН/БИН/телефонов в ${APP}/shared/test-vectors/ (его же будет читать Vitest), value-объекты Iin (контрольный разряд по алгоритму РК, дата рождения, пол), Bin, KzPhone (E.164 через libphonenumber, регион KZ) и аннотации @Iin, @Bin, @KzPhone с кодами ошибок в messages.properties; маска телефона и ИИН при сериализации по праву (согласовать с @PatientPhone из T-IAM-07 — один механизм); тест миграций: все колонки времени — timestamptz, деньги — numeric(12,2). Фронт-часть (zod, маски, форматтеры) — не делать.` },
  { id: 'T-PLT-35', title: 'Нормализация казахских букв для поиска (бэкенд)', spec: `Бэкенд-часть задачи T-PLT-35 (требование PLT-43): SQL-функция dentify.search_norm(text) IMMUTABLE (регистр, ё→е, ә→а, ғ→г, қ→к, ң→н, ө→о, ұ→у, ү→у, һ→х, і→и) миграцией platform; соглашение и шаблон в architecture.md: генерируемая колонка *_search + GIN pg_trgm; телефон — только цифры + индекс по последним 4; ICU-коллация для сортировки имён (проверь наличие ICU в образе postgres:18 и в Testcontainers); общие тест-векторы JSON в ${APP}/shared/test-vectors/; Java-утилита SearchNorm с той же таблицей (для ключей кэша и проверок) и тест равенства с SQL; замер поиска на 1000 сгенерированных строк (не пациентов — таблицы patient ещё нет; тестовая таблица в src/test). Фронт-часть — не делать.` },
  { id: 'T-CLN-02', title: 'Домен и публичный API модуля clinic', spec: `Задача T-CLN-02 (требования CLN-27, CLN-05), подзадачи из backlog.json: JPA-сущности с @TenantId и @Version, @MappedSuperclass DictionaryItem для 9 справочников, репозитории и lookup-проекции, named interface clinic :: api ClinicDirectory (настройки, пояс, BranchCalendar, кресла по порядку, активные услуги, значения справочников), SPI ClinicReferenceUsage (реализация по умолчанию 0 + агрегатор), события в clinic :: events (ClinicRegistered, BranchCreated/Updated/Archived, BranchCalendarChanged, ChairsChanged, ServiceChanged, DictionaryChanged, ClinicSettingsChanged) через журнал публикаций из T-PLT-14, PermissionContributor модуля clinic (clinic:view, clinic:edit, скоупы), ApplicationModules.verify и Scenario-тесты публикации. REST здесь нет (его делают T-CLN-07..19) — только домен, api и события.` },
  { id: 'T-CLN-03', title: 'Начальное наполнение новой клиники', spec: `Задача T-CLN-03 (требование CLN-04), подзадачи из backlog.json: ClinicSeeder с версионированным набором значений рус/каз в ресурсах (LocalizedText), специальности и категории прайса по направлениям, справочники по умолчанию, настройки по умолчанию (шаг 30, колонки «Кресла», двойная запись выкл., лимиты правок 3/1/0 дней, RU, неделя с понедельника, +7); вызывается из будущей регистрации (T-CLN-04) и из dev-сида; интеграционный тест: две клиники засеяны независимо, значения правятся и удаляются.` },
  { id: 'T-IAM-04', title: 'Защита входа от подбора пароля', spec: `Задача T-IAM-04 (требование IAM-04), подзадачи из backlog.json. Лимитер входа уже есть в T-PLT-04 (Bucket4j в Valkey: 5 на логин / 30 на IP за 15 мин) — не заводи второй: доведи до требования: 429 с Retry-After и ProblemDetail с оставшимися минутами, событие «Блокировка после попыток» в auth_event, сброс счётчика при успешном входе и при сбросе пароля (точка расширения для T-IAM-16), тесты порогов и снятия блокировки. Если что-то из этого уже сделано — подтверди тестами и не дублируй.` },
  { id: 'T-IAM-05', title: 'Отправка писем и SMS', spec: `Задача T-IAM-05 (требование IAM-17), подзадачи из backlog.json: порт NotificationSender в платформе (или iam — реши по направлениям), адаптеры SMTP (Spring Mail из T-PLT-14) и SMS (HTTP-клиент KZ-провайдера с интерфейсом; конкретный провайдер — конфигурируемый, реализуй один типовой REST-адаптер (например, по документации SMSC.kz или Mobizon — сверься) плюс заглушку), выбор по профилю; SMS-заглушка в Compose для dev/e2e (простой HTTP-сервис, который хранит последние сообщения и отдаёт их по адресу — для e2e-хелпера «последнее сообщение»), шаблоны на русском (казахский — заготовка), отправка после коммита через @ApplicationModuleListener с повторами по реестру публикаций, статус доставки обратно, маскирование контактов в логах, тест: сбой провайдера → досылка.` },
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

Проверь ИСПОЛНЕНИЕМ, а не на глаз: 1) ./mvnw -B verify (JDK 25) — зелёный? сколько тестов? 2) Каждая подзадача (бэкенд-часть) и критерий приёмки связанных требований — есть ли код И тест, который это доказывает; прочитай тесты. 3) Безопасность и корректность: утечки между клиниками (RLS, кэш, S3-пути), права (@perm, скоупы), секреты/ПД в логах и ответах, гонки, транзакции, идемпотентность слушателей, неверное использование API Spring Boot 4 / Modulith 2.1 / Hibernate 7 (context7). 4) Соглашения architecture.md: имя миграции, RLS на новых таблицах, реестр теста изоляции для новых эндпоинтов, ProblemDetail-коды в messages.properties, openapi.json обновлён, направления модулей (ModularityTests), architecture.md дополнен. Для ручной проверки — порты 18080/18081, остановить после.
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
  { key: 'acceptance', prompt: `Ты — финальный приёмщик партии A бэкенда этапа 1 (${TASKS.map(t => t.id).join(', ')}). Сводка исполнителей:
${digest}
Проверь ИСПОЛНЕНИЕМ: deploy/dev.ps1 check; ./mvnw -B clean verify — зелёный, число тестов; запусти jar с профилем dev на 18080/18081 и пройди: вход dev-владельцем → /api/v1/me содержит эффективные права роли «Владелец» → GET /api/v1/access/permissions → письмо из очереди доходит до Mailpit (вызови любой сценарий, который шлёт письмо, или тестовый эндпоинт dev-профиля) → аудит записал события; проверь Flyway-историю: все новые миграции с правильными именами, у всех новых таблиц арендатора RLS FORCE и политика (SQL по каталогу под dentify_owner). Пройди критерии приёмки требований всех задач партии (бэкенд-часть): выполнен / частично (что фронту или дальше) / нет — таблицей в evidence. Останови приложение.` },
  { key: 'arch', prompt: `Ты — ревьюер архитектуры (Spring Boot 4.1, Modulith 2.1, Hibernate 7). Прочитай весь ${APP}/backend/src и ${APP}/docs/architecture.md. Ищи: нарушения направлений модулей и named interfaces (в т.ч. обход через platform), права вне @perm, hasAuthority-остатки, логику в контроллерах (R002), строки пользователю мимо MessageSource (R003), слушатели событий без журнала публикаций/идемпотентности, транзакционные границы, N+1, кэш без clinic_id в ключе, самописное там, где есть официальное готовое (R021), дублирование, конфиг, который сломает этапы 2–6, пробелы architecture.md. Сборку не запускай.` },
  { key: 'sec', prompt: `Ты — пентестер. Код ${APP}/backend/src; собери jar (без mvn clean, из target) и запусти с профилем dev на 28080/28081 для black-box проверок curl, затем останови. Проверь: обход прав (@perm) и скоупов, смена клиники на чужую, доступ к ролям/правам другой клиники, утечки через RLS (SQL под dentify_app без и с чужим app.clinic_id), маска телефона без права, ПД/секреты в логах и письмах, повторная доставка писем (дубли), SSRF/инъекции в адаптерах SMS/SMTP, лимиты и обходы через заголовки, CSRF на новых эндпоинтах, идемпотентность. Каждую находку подтверди воспроизведением (команда + ответ) в evidence.` },
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

Исправь находки финального ревью партии A бэкенда этапа 1. Обязательные:
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
