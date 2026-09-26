export const meta = {
  name: 'dentify-phase0-close',
  description: 'Dentify закрытие этапа 0: экран входа (T-IAM-03), локальная проверка одной командой (T-PLT-15), Playwright и демо этапа 0 на живом стеке (T-PLT-16), документация архитектуры (T-PLT-38); приёмка демо',
  phases: [
    { title: 'Tasks', detail: 'impl → review → fix по задаче', model: 'opus' },
    { title: 'Final review', detail: 'демо этапа 0 вживую: вход → пустой кабинет на десктопе и телефоне → выход', model: 'opus' },
    { title: 'Final fix', detail: 'исправления по приёмке', model: 'opus' },
  ],
}

const APP = 'C:/Users/abyla/code/apps/dentify'
const WIKI = 'C:/Users/abyla/code/wiki/projects/dentify'
const BASE = `Ты работаешь над проектом Dentify в ${APP} (Windows; Git Bash и PowerShell; JDK 25: export JAVA_HOME="$HOME/.jdks/openjdk-25.0.2"; export PATH="$JAVA_HOME/bin:$PATH" — системная java 17 не годится; Node 23 + pnpm 10; Docker Desktop запущен, окружение поднимается ${APP}/deploy/dev.ps1 up, пароли в ${APP}/deploy/.env). СНАЧАЛА прочитай ${APP}/CLAUDE.md, ${APP}/docs/architecture.md (бэкенд) и ${APP}/docs/frontend.md (фронт) целиком. Правила: dentify R002 (вся логика на бэкенде, фронт тонкий), R003 (только русский, ни одной строки в JSX/коде — ключи i18n и коды MessageSource; «Қаз» скрыт флагом), R001 (поиск в списках), R011/R018/R019/R022/R023, R021 (сначала официальное готовое). Бэкенд этапа 0 готов: ${APP}/backend (Spring Boot 4.1.1; вход POST /api/v1/auth/login, logout, csrf, GET /api/v1/me, /api/v1/me/context, сессии в Valkey, лимиты, ProblemDetail; спека ${APP}/api/openapi.json; dev-сид: владелец owner@demo.dentify.kz с паролем DENTIFY_DEV_OWNER_PASSWORD из deploy/.env, клиника «Dentify Demo»). Фронт-каркас готов: ${APP}/frontend (Vite 8, React 19, TanStack Router file-based, Tailwind 4 + shadcn/Base UI с токенами «Кресла», orval-клиент из openapi.json, MSW-моки в режиме mock, react-i18next, Playwright с проектами desktop 1440×900 и mobile 390×844; скрипты dev/build/test/e2e/lint/typecheck/verify). Макеты — ${APP}/design/mockups/<Имя>.dc.html (открывать через MCP chrome-devtools: new_page file:///…, take_screenshot); нужные здесь: Vhod (вход), Raspisanie-Den, Telefon-Raspisanie, Sostoyaniya (пустые состояния). Токены и правила компонентов — ${WIKI}/design-directions.md. Задачу и требования с критериями приёмки достань из ${APP}/docs/backlog.json командой из CLAUDE.md. Версии и API библиотек сверяй через MCP context7, не по памяти. Не коммить и не пушь. Данные — вымышленные. Порты: бэкенд для ручных проверок и e2e запускай на 8080/8081 только если они свободны, иначе 18080/18081 и передай адрес фронту через переменную; останавливай процессы после себя. Любая UI-правка проверяется вживую в Playwright и скриншотами (R018). В конце каждой задачи ./mvnw -B verify (если трогал бэкенд) и pnpm verify зелёные.`

const IMPL = { type: 'object', properties: {
  summary: { type: 'string' },
  filesChanged: { type: 'array', items: { type: 'string' } },
  verifyGreen: { type: 'boolean' },
  subtasksDone: { type: 'array', items: { type: 'string' } },
  subtasksNotDone: { type: 'array', items: { type: 'string' } },
  deviations: { type: 'array', items: { type: 'string' } },
  followUps: { type: 'array', items: { type: 'string' } },
}, required: ['summary', 'filesChanged', 'verifyGreen', 'subtasksDone', 'subtasksNotDone', 'deviations', 'followUps'] }
const FINDING = { type: 'object', properties: {
  severity: { type: 'string', enum: ['blocker', 'major', 'minor'] },
  area: { type: 'string' }, file: { type: 'string' }, issue: { type: 'string' }, fix: { type: 'string' },
}, required: ['severity', 'area', 'file', 'issue', 'fix'] }
const REVIEW = { type: 'object', properties: {
  verifyGreen: { type: 'boolean' }, evidence: { type: 'string' },
  findings: { type: 'array', items: FINDING },
}, required: ['verifyGreen', 'evidence', 'findings'] }

const TASKS = [
  { id: 'T-IAM-03', title: 'Экран входа и общий компонент пароля', spec: `Задача T-IAM-03 (требования IAM-02, IAM-03, IAM-05, IAM-34), подзадачи из backlog.json:
- Перегенерировать orval-клиент из актуального ${APP}/api/openapi.json (auth, me, context); api:check зелёный.
- Маршрут /login по макету Vhod: панель «О сервисе» слева (скрыта на телефоне), форма — LoginField (телефон или e-mail: определение типа по вводу, маска телефона +7, inputmode), PasswordField (глаз показать/скрыть; индикатор силы zxcvbn-ts (@zxcvbn-ts/core + language-common и ru) и чек-лист правил показываются только в режиме «новый пароль» — здесь для входа выключены, но компонент общий и будет в смене пароля и приглашении), «Запомнить это устройство», одна жёлтая кнопка «Войти», ссылка «Забыли пароль?» (маршрут-заглушка), переключатель «Рус | Қаз» скрыт флагом i18n.kk. Ошибки: неверный логин/пароль — единый текст по коду ProblemDetail, 429 — «Слишком много попыток, попробуйте через N мин» из Retry-After, сеть — общая ошибка; всё через ключи i18n.
- returnTo: после входа переход на исходный URL (только относительные пути); перехват 401 в мутаторе → /login?returnTo с тостом/плашкой «Сеанс истёк» (ключ i18n) — без дублирования, если уже на /login.
- Вход: POST /api/v1/auth/login с CSRF cookie-to-header (сначала GET csrf, если cookie нет), затем /api/v1/me → в кабинет; если контекст клиники не выбран (несколько членств) — экран выбора (VyborKliniki — минимальная версия: карточки клиник/филиалов из /me, POST /me/context), иначе сразу в раздел.
- Выход из меню пользователя (сайдбар и «Ещё»): POST /api/v1/auth/logout, очистка кэша TanStack Query, переход на /login.
- MSW-моки обновить под реальные контракты (те же формы ответов, что у бэкенда: сверь с openapi.json и интеграционными тестами бэкенда).
- Playwright на моках: успешный вход, ошибка пароля, 429, возврат по returnTo, «Сеанс истёк», выбор клиники при нескольких членствах, выход — десктоп и 390 px; скриншоты рядом с макетом Vhod — расхождения устранить. Vitest на LoginField/PasswordField.` },
  { id: 'T-PLT-15', title: 'Локальная проверка одной командой', spec: `Задача T-PLT-15 (требования PLT-26, PLT-22, PLT-06; облачный CI отложен до T-PLT-25 — разработка локальная), подзадачи из backlog.json:
- ${APP}/verify.ps1 (основной, Windows PowerShell 5.1, UTF-8 с BOM) и ${APP}/verify.sh (bash): проверяют JDK 25 (JAVA_HOME или ~/.jdks/openjdk-25.0.2), Docker, поднятое окружение (dev.ps1 check — или поднимают); backend: ./mvnw -B verify; frontend: pnpm install --frozen-lockfile, pnpm verify (typecheck, lint, test, build, бюджет, e2e на моках); api:check (клиент orval совпадает со спекой; спека в репозитории совпадает с той, что генерирует сборка бэкенда).
- Режим -E2E / --e2e: собирает jar, запускает бэкенд с профилем dev на свободном порту, фронт — vite preview/dev с прокси на него, гоняет Playwright-проект «stack» (появится в T-PLT-16 — заложи хук: если проекта нет, шаг пропускается с сообщением), складывает трейсы и скриншоты в ${APP}/deploy/test-results, всё останавливает даже при ошибке (try/finally).
- Режим -Scan / --scan: OWASP dependency-check (Maven-плагин, кэш NVD в ~/.m2 или отдельной папке; если NVD API недоступен — понятное сообщение и ненулевой код только по флагу -Strict), pnpm audit --prod, gitleaks (бинарь через winget/scoop или скачивание релиза в ~/.dentify-tools — документируй) по apps/dentify.
- Итог — сводка по шагам с временем и кодом выхода; ненулевой код при любой красной проверке. Описать в README «Проверка перед коммитом». Прогнать оба скрипта целиком (без -Scan, если NVD недоступен) и приложить сводку.` },
  { id: 'T-PLT-16', title: 'Playwright на живом стеке и демо этапа 0', spec: `Задача T-PLT-16 (требования PLT-17, PLT-18, PLT-21, PLT-04, PLT-27), подзадачи из backlog.json:
- Playwright-проекты «stack-desktop» 1440×900 и «stack-mobile» 390×844, работающие против уже поднятого стека (переменные DENTIFY_E2E_BASE_URL фронта и DENTIFY_E2E_API); без моков (VITE_API_MOCK=false), фронт через vite preview с прокси /api на бэкенд.
- Фикстуры: сид тестовой клиники — через профиль бэкенда test-e2e или dev-сид (реши: нужен воспроизводимый владелец и пустой кабинет); вход через storageState (cookie сессии + XSRF), общий helper login(); отдельный проект setup.
- Хелперы: comboRows (строки выпадающего списка в портале), assertNoHorizontalScroll, axe-core (@axe-core/playwright) с порогом 0 serious/critical, tapTargets ≥ 44.
- Проверки безопасности с фронта: все запросы страницы идут на свой origin (route/request listener), в localStorage/sessionStorage нет токенов, cookie сессии HttpOnly (недоступна из document.cookie), CSRF-заголовок на POST.
- Спека демо этапа 0: вход владельцем → пустой кабинет (6 разделов с пустыми состояниями, сайдбар, шапка, филиал в контексте) на десктопе и телефоне (нижняя панель, «Ещё») → «Сеанс истёк» после завершения сессии с бэкенда (через API сессий) → выход → /login. Скриншоты в трейсе.
- Подключить проект «stack» в verify -E2E (T-PLT-15). Прогнать вживую: поднять бэкенд (jar, профиль dev) и фронт, выполнить, приложить результат; остановить всё.` },
  { id: 'T-PLT-38', title: 'Документация архитектуры и соглашений', spec: `Задача T-PLT-38 (требования PLT-23, PLT-01, PLT-09), подзадачи из backlog.json. Финальное ревью архитектуры этапа 0 отметило: новый разработчик по ${APP}/docs/architecture.md не соберёт модуль и эндпоинт — раскладка пакетов модуля описана только в CLAUDE.md, шаги «новый эндпоинт» разбросаны по пяти разделам. Сделай:
- Переструктурировать architecture.md: 1) карта модулей и разрешённые направления (clinic ← iam, staff, schedule, inventory; staff, patient ← schedule; medical → patient, schedule, clinic; все → platform), события в named interface events, порты/SPI вместо циклов, что лежит в platform :: api и кто вправе вызывать примитивы сессии (только iam) — как это обеспечено (named interface / ArchUnit); 2) раскладка пакетов модуля (api, events, web, internal, package-info с @ApplicationModule, @NamedInterface, @NullMarked); 3) пошагово «новая таблица арендатора» (шаблон миграции: имя V<yyyy>_<MM>_<dd>_<HHmm>__<модуль>_<что>.sql, колонки, RLS ENABLE+FORCE+политика, индексы, uuidv7, тест каталога); 4) пошагово «новый эндпоинт» в одном месте (record-DTO в web, сервис с правилами (R002), @PreAuthorize, коды ошибок в messages.properties и ProblemDetail, @Operation/OpenAPI + обновление api/openapi.json, строка в реестре теста изоляции, интеграционный тест, orval на фронте); 5) права и контекст: как права попадут в запрос на этапе 1 (не «замерзать» в сессии — вычислять на запросе по членству/роли с кэшем и инвалидацией), завершение сессий по клинике, публичные пути /api/v1/public/**; 6) правила: логи без ПД и маска по ключам, деньги BigDecimal/numeric, время timestamptz + пояс клиники, i18n (R003); 7) ADR-раздел: сессии в cookie вместо JWT, роли со скоупами, RLS с этапа 0, отдельный модуль medical, только русский в MVP, локальная разработка без CI до T-PLT-25 — каждый: контекст, решение, последствия.
- Сверить каждый пункт с реальным кодом (не описывать желаемое): пути классов, имена тестов, свойства.
- Сгенерировать актуальную документацию модулей (ModuleDocumentationTests) и сослаться на docs/modules/.
- CLAUDE.md проекта: убрать дубли, оставить краткие правила и ссылки на разделы architecture.md/frontend.md.
- Проверить: ./mvnw -B verify зелёный (документация модулей пересобралась).` },
]

const COMMIT = { type: 'object', properties: { committed: { type: 'boolean' }, sha: { type: 'string' }, pushed: { type: 'boolean' }, note: { type: 'string' } }, required: ['committed', 'sha', 'pushed', 'note'] }
const results = []
phase('Tasks')
for (const t of TASKS) {
  const done = results.map(r => `${r.id}: ${r.impl?.summary?.slice(0, 500) ?? 'нет отчёта'}`).join('\n\n')
  const impl = await agent(`${BASE}

Уже сделано в этой партии:
${done || '— (первая задача)'}

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

Проверь ИСПОЛНЕНИЕМ, не на глаз: сборки/проверки зелёные (числа тестов); каждая подзадача и критерий приёмки связанных требований — есть код И тест/прогон, который это доказывает; UI — сравнение с макетом скриншотами на 1440 и 390 px, строки без i18n, логика на фронте вместо бэкенда (R002), самописное вместо готового (R021); скрипты — запусти их сам; документация — сверь с кодом. Останови всё, что запускал.
Верни verifyGreen, evidence и findings: blocker — красная проверка, сломан вход/каркас; major — подзадача/критерий не выполнены или выполнены неверно, расхождение с макетом, нет теста на ключевое поведение; minor — мелочи.`, { label: `review:${t.id}#${round + 1}`, phase: 'Tasks', schema: REVIEW, model: 'opus', effort: 'xhigh' })
    const blocking = (rv?.findings ?? []).filter(f => f.severity !== 'minor')
    if (rv && rv.verifyGreen && !blocking.length) break
    if (round === 2) break
    const fx = await agent(`${BASE}

Исправь находки проверки задачи ${t.id} (${t.title}). Спецификация:
${t.spec}

Обязательные (blocker/major):
${JSON.stringify(blocking, null, 1)}
${rv && !rv.verifyGreen ? 'Проверка КРАСНАЯ. Evidence: ' + rv.evidence : ''}
Мелкие — исправь, если дёшево и безопасно, иначе в followUps:
${JSON.stringify((rv?.findings ?? []).filter(f => f.severity === 'minor'), null, 1)}
Ошибочные находки — не исправляй, обоснуй в deviations. В конце проверки зелёные, правки UI проверены вживую.`, { label: `fix:${t.id}#${round + 1}`, phase: 'Tasks', schema: IMPL, model: 'opus' })
    fixes.push(fx)
  }
  const rem = rv?.findings ?? []
  log(`${t.id}: verify ${rv?.verifyGreen ? 'зелёный' : 'КРАСНЫЙ'}, осталось blocker/major ${rem.filter(f => f.severity !== 'minor').length}, minor ${rem.filter(f => f.severity === 'minor').length}`)
  const accepted = rv && rv.verifyGreen && !(rv.findings ?? []).some(f => f.severity !== 'minor')
  if (accepted) {
    await agent(`В репозитории C:/Users/abyla/code (ветка master) закоммить и запушить результат принятой задачи ${t.id} — ${t.title}. Правило dentify R004. Шаги: 1) cd C:/Users/abyla/code; git add apps/dentify (ТОЛЬКО этот каталог: вне него лежат незакоммиченные правки других проектов — их не трогать); 2) git status --short --cached — убедись, что нет секретов (deploy/.env, *.mmdb, ключи) и мусора (target/, node_modules/, dist/, playwright-report/, test-results/); лишнее — git reset <путь>; если индекс пуст — сообщи и закончи; 3) git commit с сообщением Conventional Commits: заголовок «<type>(dentify): <суть задачи по-английски, не длиннее 72 символов>» (type: feat/fix/test/docs/chore), пустая строка, тело: id задачи ${t.id}, что сделано 3–6 строк, число тестов; последняя строка ровно: Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>; 4) bash C:/Users/abyla/code/bin/git-sync-push.sh — он делает fetch, rebase поверх origin/master с автостэшем чужих правок и push; код 2 или 3 — сообщи в note и не пытайся чинить руками; 5) git log --oneline -1 и git status --short --branch. Отчёт исполнителя задачи для сообщения коммита: ${JSON.stringify((fixes.at(-1) ?? impl ?? {}).summary ?? '').slice(0, 1500)}`, { label: `commit:${t.id}`, phase: 'Tasks', schema: COMMIT, model: 'sonnet', effort: 'low' })
  } else log(`${t.id}: не принята — коммита нет`)
  results.push({ id: t.id, impl, fixes, lastReview: rv })
}

phase('Final review')
const digest = results.map(r => `${r.id}: ${(r.fixes.at(-1)?.summary ?? r.impl?.summary ?? '').slice(0, 700)}`).join('\n\n')
const acc = await agent(`${BASE}

Ты — приёмщик этапа 0 целиком (демо по плану: «вход → пустой кабинет на телефоне и десктопе»). Сводка партии:
${digest}

Проверь ИСПОЛНЕНИЕМ с чистого листа: 1) deploy/dev.ps1 check; 2) ${APP}/verify.ps1 целиком и verify.ps1 -E2E — зелёные (числа тестов бэкенда, Vitest, Playwright mock и stack); 3) сам пройди демо в браузере через chrome-devtools на живом стеке: вход владельцем, кабинет на 1440 и 390 (скриншоты рядом с макетами Raspisanie-Den / Telefon-Raspisanie / Vhod), «Ещё», выход, «Сеанс истёк»; 4) критерии приёмки PLT-17, PLT-18, PLT-20, PLT-21, PLT-22, PLT-26, PLT-27, IAM-02, IAM-03, IAM-05, IAM-34, PLT-23 — таблицей: выполнен / частично (что осталось этапу 1) / нет; 5) README «Запуск за 10 минут» соответствует реальности — пройди по нему. Останови всё. Верни verifyGreen, evidence и findings.`, { label: 'final:acceptance', phase: 'Final review', schema: REVIEW, model: 'opus', effort: 'xhigh' })

let allReviews = [acc].filter(Boolean)
const finalFixes = []
for (let round = 0; round < 2; round++) {
  const blocking = allReviews.flatMap(r => r.findings).filter(f => f.severity !== 'minor')
  const red = allReviews.some(r => r.verifyGreen === false)
  log(`Финал, круг ${round + 1}: blocker/major ${blocking.length}, verify ${red ? 'КРАСНЫЙ' : 'зелёный'}`)
  if (!blocking.length && !red) break
  phase('Final fix')
  const fx = await agent(`${BASE}

Исправь находки приёмки этапа 0. Обязательные:
${JSON.stringify(blocking, null, 1)}
${red ? 'Проверка КРАСНАЯ: ' + allReviews.map(r => r.evidence).join('\n---\n').slice(0, 8000) : ''}
Мелкие — если дёшево и безопасно:
${JSON.stringify(allReviews.flatMap(r => r.findings).filter(f => f.severity === 'minor'), null, 1).slice(0, 12000)}
Ошибочные находки — обоснуй в deviations. В конце verify.ps1 и verify.ps1 -E2E зелёные.`, { label: `final:fix#${round + 1}`, phase: 'Final fix', schema: IMPL, model: 'opus' })
  finalFixes.push(fx)
  const re = await agent(`${BASE}

Перепроверь исправления приёмки этапа 0. Исходные обязательные находки:
${JSON.stringify(blocking, null, 1)}
Отчёт исправлений: ${JSON.stringify(fx ?? {}).slice(0, 6000)}
Проверь ИСПОЛНЕНИЕМ: verify.ps1 и verify.ps1 -E2E зелёные; каждая находка закрыта; ничего не сломано. Останови всё. Верни verifyGreen, evidence и findings (только незакрытое и новое).`, { label: `final:recheck#${round + 1}`, phase: 'Final fix', schema: REVIEW, model: 'opus', effort: 'xhigh' })
  allReviews = [re].filter(Boolean)
}

const finalOk = allReviews.length && allReviews.every(r => r.verifyGreen !== false && !r.findings.some(f => f.severity !== 'minor'))
if (finalFixes.length) {
  await agent(`В репозитории C:/Users/abyla/code (ветка master) закоммить и запушить исправления по финальному ревью партии (${meta.name}). Правило dentify R004. Шаги: cd C:/Users/abyla/code; git add apps/dentify (только этот каталог); проверь индекс на секреты и мусор; если индекс пуст — сообщи и закончи; git commit: заголовок «fix(dentify): address final review of ${meta.name}», тело — список закрытых находок (кратко) и число тестов, последняя строка ровно: Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>; затем bash C:/Users/abyla/code/bin/git-sync-push.sh; git log --oneline -1. Итог ревью: ${finalOk ? 'все обязательные находки закрыты' : 'ВНИМАНИЕ: остались незакрытые находки — всё равно коммить текущее состояние, оно собирается'}. Отчёт исправлений: ${JSON.stringify(finalFixes.at(-1)?.summary ?? '').slice(0, 1500)}`, { label: 'commit:final', phase: 'Final fix', schema: COMMIT, model: 'sonnet', effort: 'low' })
}
return {
  tasks: results.map(r => ({ id: r.id, impl: r.impl, fixes: r.fixes, lastReview: r.lastReview })),
  acceptance: acc, finalFixes, finalState: allReviews,
}
