export const meta = {
  name: 'dentify-phase0-frontend',
  description: 'Dentify этап 0, фронт на opus: каркас SPA, дизайн-система «Кресло», мобильный вид, orval-клиент, i18n — по задаче с проверкой, затем приёмка + UI-критик + a11y/perf',
  phases: [
    { title: 'Tasks', detail: 'impl → review → fix по задаче', model: 'opus' },
    { title: 'Final review', detail: 'приёмка вживую, UI-критик по макетам, a11y/perf', model: 'opus' },
    { title: 'Final fix', detail: 'исправления по общему ревью', model: 'opus' },
  ],
}

const APP = 'C:/Users/abyla/code/apps/dentify'
const WIKI = 'C:/Users/abyla/code/wiki/projects/dentify'
const BASE = `Ты работаешь над фронтендом проекта Dentify в ${APP}/frontend (Windows; Git Bash и PowerShell; Node 23, pnpm 10). СНАЧАЛА прочитай ${APP}/CLAUDE.md целиком (правила dentify R001/R002/R003, R011, R018, R019, R021, R022, R023; стек), затем ${WIKI}/design-directions.md (токены дизайн-системы «Кресло», шрифты с казахским алфавитом, правила компонентов, UX-принципы) и ${WIKI}/screens.md (имена и состав экранов). Макеты — ${APP}/design/mockups/<Имя>.dc.html: открывай в браузере через MCP chrome-devtools (ToolSearch "chrome-devtools": new_page file:///…, take_screenshot) — это визуальный эталон; для каркаса важны Raspisanie-Den (сайдбар, шапка, вкладки), Sistema (витрина дизайн-системы), Telefon-Eshche и Telefon-Raspisanie (мобильная навигация), Vhod. Вкус владельца: крупный, цветной, незагруженный интерфейс; жёлтая кнопка — одна на область.

ПАРАЛЛЕЛЬНО другой воркфлоу пишет бэкенд: НЕ трогай ${APP}/backend, ${APP}/deploy, ${APP}/api, ${APP}/docs/architecture.md и корневой ${APP}/README.md (их редактируют другие агенты — конфликт). Твои файлы: только ${APP}/frontend/** и ${APP}/docs/frontend.md (соглашения фронта: структура, как добавить маршрут/экран/компонент, i18n, генерация клиента, тесты). Спека API — ${APP}/api/openapi.json, только чтение (может расти по ходу — перегенерация клиента дешёвая). Бэкенд для разработки каркаса не нужен: моки MSW (Mock Service Worker) по схемам из openapi.json для /api/v1/me, /api/v1/auth/*, плюс Vite-прокси /api → http://127.0.0.1:8080 для работы с живым бэкендом позже; переключение — переменная окружения VITE_API_MOCK. Ничего не запускай на портах 8080/8081/18080/28080.

Правила фронта: вся бизнес-логика на бэкенде (R002) — фронт показывает ответ API и шлёт намерения, что разрешено — говорит API; ни одной пользовательской строки в JSX (R003) — только ключи i18n; любая UI-правка проверяется вживую в Playwright (R018), не на глаз; выпадающее — в портале с коллизиями (R022); select/input — с поиском и tooltip (R011). Версии библиотек и API (Vite 8, React 19, TanStack Router v1 file-based, Tailwind 4 @theme, shadcn/ui 4.x CLI с Base UI, orval 8, react-i18next 17, Playwright 1.6x, Vitest 5, ESLint 10 flat config) сверяй с актуальной документацией через MCP context7 (ToolSearch "context7"), не по памяти. R021: сначала официальное готовое (компоненты shadcn/Base UI, плагины), потом комьюнити, свой код — последним. Скрипты package.json: dev, build, test (vitest), e2e (playwright), lint, typecheck, verify (всё вместе). Не коммить и не пушь. Данные в моках — вымышленные.`

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
  { id: 'T-PLT-05', title: 'Каркас SPA и навигация', spec: `Задача T-PLT-05 (требования PLT-17, PLT-22), все подзадачи из ${APP}/docs/backlog.json (достань командой из CLAUDE.md):
- Проект: Vite 8 + React 19 + TypeScript strict, ESLint 10 (flat), Vitest 5 + Testing Library, Playwright (проекты desktop 1440×900 и mobile 390×844 — базовая конфигурация, расширит T-PLT-10). Алиасы путей, структура по модулям: src/app (shell, router, providers), src/modules/<schedule|patients|staff|inventory|clinic|access|profile>, src/shared (ui, api, i18n, lib).
- TanStack Router file-based: типизированные search-параметры (zod), неверные значения → значения по умолчанию, ленивые маршруты, заголовок вкладки «<Экран> — Dentify», маршрут 404, маршруты-заглушки для 6 разделов (пустые состояния по макетам, без бизнес-логики).
- Лейаут по макету Raspisanie-Den: плавающий сайдбар «лагуна» на 6 разделов с активным «лампа», пользователь внизу → «Мой профиль»; шапка: заголовок, контекст (дата/филиал — заглушки), поле «Найти пациента Ctrl K» (пока открывает пустую панель), место под одну жёлтую кнопку; вкладки-пилюли внутри раздела. На 768–1023 px сайдбар сворачивается до иконок с подсказками (tooltip доступен с клавиатуры).
- Гарды: без сессии (401 от /api/v1/me) → /login?returnTo=…; без права — экран «нет доступа»; пункты меню по правам из /api/v1/me (структуру ответа возьми из openapi.json; если поля прав ещё нет — заложи адаптер с явным TODO и моком). Экран /login — пока минимальная заглушка (настоящий — T-IAM-03).
- MSW: моки /api/v1/me (владелец с полными правами и ассистент без «Клиники» и «Доступов» — переключение фикстурой), 401 для проверки гарда.
- Бюджет начального бандла: size-limit (или vite bundle budget) — ≤ 250 КБ gzip, проверка в скрипте verify.
- Тесты: Vitest на роутинг/гарды/search-параметры; Playwright: ссылка с параметрами открывает тот же вид, ассистент не видит «Клиника»/«Доступы», на 900 px сайдбар свёрнут и у иконок подсказки, «Назад» браузера закрывает открытую панель (заложи механику панели в URL уже сейчас).
Витрину дизайн-системы и токены делает T-PLT-06 — здесь минимальные стили, но структура тем и шрифтов должна быть готова к нему (Tailwind 4 подключён).` },
  { id: 'T-PLT-06', title: 'Дизайн-система «Кресло»: токены и компоненты', spec: `Задача T-PLT-06 (требования PLT-20, PLT-21), подзадачи из backlog.json:
- Tailwind 4 @theme: цвета (lagoon, lamp, lamp-deep, ink, graphite, slate, fog, mist, white, line, danger/danger-bg, highlight/match), 6 цветов врачей (bg/fg/strong), статусы записи, состояния зубов, радиусы 999/28/24/20/18/14, отступы 4–32, шрифты. Значения — из ${WIKI}/design-directions.md, не выдумывай.
- shadcn/ui CLI 4.x с Base UI (проверь актуальную инициализацию в документации), тема переопределена токенами «Кресла»; компоненты берутся из shadcn и стилизуются, не пишутся с нуля.
- Шрифты Rubik и Nunito Sans (woff2, лицензия OFL — скачай с Google Fonts или из репозиториев шрифтов) — на своём домене, @font-face с font-display: swap; тест-замер ширины глифов ә ғ қ ң ө ұ ү һ і в Playwright: отрисованы своими шрифтами, а не запасными.
- Базовые компоненты: Button (жёлтая — одна на область, контурная, выбранная ink, красная текстовая, красная заливка для опасного), поле-пилюля 48 px (компакт 40), PillTabs, Segmented, Chip, StatusBadge (точка + слово), Avatar-инициалы в цвете врача, AlertStrip (красная плашка аллергии), SearchField (dentify R001: пилюля с лупой, placeholder перечисляет поля), Combo/Select с поиском по вводу и tooltip полного текста, в портале с переворотом у края (R011, R022; на Base UI Popover/Select), Toast с «Вернуть» вместо окон подтверждения, Panel (боковая панель справа; на телефоне — нижний лист; «Назад» в шагах — R023).
- Витрина /dev/ui по макету Sistema (только в dev-сборке): все компоненты и токены; скриншот-тест Playwright на десктопе и телефоне (эталоны закоммитить в frontend/e2e/__screenshots__).
- Юнит-тест контраста всех пар текст/фон из токенов ≥ 4.5:1 (крупный ≥ 3:1); видимый фокус — контур 3 px «лагуна»; prefers-reduced-motion учитывается.
- Сравни витрину с макетом Sistema скриншотами через chrome-devtools рядом — расхождения устрани.` },
  { id: 'T-PLT-10', title: 'Мобильная навигация и адаптив', spec: `Задача T-PLT-10 (требования PLT-18, PLT-21), подзадачи из backlog.json:
- Брейкпоинты mobile-first: телефон < 768, планшет 768–1023, десктоп ≥ 1024.
- Нижняя панель «Расписание · Пациенты · + · Склад · Ещё» по макету Telefon-Raspisanie: «+» ведёт на маршрут нового приёма (пока заглушка), пункты по правам; safe-area iOS (env(safe-area-inset-bottom)).
- Меню «Ещё» по макету Telefon-Eshche: карточка пользователя, текущий филиал со сменой (заглушка), разделы с подписями (Врачи — профили и графики; Склад — остатки, приход, поставщики; Клиника — филиалы, кресла, прайс; Доступы — сотрудники, роли, приглашения; Мой профиль — телефон, пароль, сеансы), переключатель «Рус | Қаз» скрыт флагом i18n.kk (R003), «Выйти».
- Panel на телефоне — нижний лист (закрытие свайпом вниз и кнопкой), таблицы → карточки (общий паттерн/компонент), вкладки прокручиваются вбок; шрифт полей ≥ 16 px; inputmode для телефона и ИИН.
- Playwright проект 390×844: общая проверка по всем маршрутам — нет горизонтальной прокрутки (scrollWidth ≤ clientWidth), все элементы нижней панели и «Ещё» ≥ 44×44 px, axe-core (@axe-core/playwright) — 0 serious/critical на каркасе, витрине и «Ещё».
- Сравни с макетами Telefon-* скриншотами.` },
  { id: 'T-PLT-07-front', title: 'Клиент API: orval, QueryClient, ошибки форм', spec: `Фронт-часть задачи T-PLT-07 (требования PLT-07, PLT-23, PLT-22):
- orval 8 из ${APP}/api/openapi.json: хуки TanStack Query + zod-схемы, генерация в src/shared/api/generated (коммитится); скрипт api:generate и проверка api:check (перегенерация во временную папку и diff — падает, если закоммиченный клиент устарел) в verify.
- Кастомный мутатор (fetch): credentials: 'include', заголовок X-XSRF-TOKEN из cookie XSRF-TOKEN на изменяющих запросах, Accept-Language, разбор ProblemDetail (code, traceId, errors[]) в типизированную ошибку ApiError; 401 → событие для роутера (переход на /login с returnTo и сообщением «Сеанс истёк» по ключу i18n); 429 — Retry-After в ошибке.
- QueryClient: retry только для GET (не для 4xx), refetchOnWindowFocus, refetchInterval 30 с — через опцию «живого» экрана, инвалидация по ключам (хелпер).
- Хелпер форм: ApiError.errors[] → setError react-hook-form по полям (маппинг code → текст через i18n), фокус на первое ошибочное поле; общая ошибка формы — в AlertStrip/тост.
- MSW-моки для тестов хелперов (422 с errors[], 409 conflict.version, 401, 429). Vitest на мутатор, разбор ошибок, хелпер форм. Обнови docs/frontend.md.` },
  { id: 'T-PLT-11-front', title: 'i18n: ключи, русский словарь, флаг kk', spec: `Фронт-часть задачи T-PLT-11 (требование PLT-11, правило R003):
- react-i18next 17 + i18next 26: пространства имён по модулям (common, shell, auth, errors, schedule, patients, staff, inventory, clinic, access, profile), ICU-склонения (i18next-icu) — «1 год / 2 года / 5 лет», date-fns с локалью ru (kk — готовность), первый день недели — из настроек (пока константа).
- Полный русский словарь всего, что уже есть в каркасе, витрине, «Ещё», заглушках и ошибках (коды ProblemDetail → тексты: errors.<code>); формат ключей и правила записаны в docs/frontend.md.
- ESLint: правило против строк в JSX (eslint-plugin-i18next или аналог — проверь совместимость с ESLint 10) — verify падает на строке без ключа; проверка целостности словаря (скрипт: все ключи, использованные в коде, есть в ru).
- Флаг i18n.kk (VITE_I18N_KK, по умолчанию false) скрывает переключатель «Рус | Қаз» во входе, профиле и «Ещё»; атрибут lang = текущий язык; при включённом флаге и наличии словаря kk переключение работает без правок компонентов — тест с фикстурным kk-словарём.
- Заменить все временные строки в коде на ключи; verify зелёный.` },
]

const results = []
phase('Tasks')
for (const t of TASKS) {
  const done = results.map(r => `${r.id}: ${r.impl?.summary?.slice(0, 500) ?? 'нет отчёта'}`).join('\n\n')
  const impl = await agent(`${BASE}

Уже сделано в этой партии:
${done || '— (первая задача; frontend/ содержит только README-заглушку)'}

ТЕКУЩАЯ ЗАДАЧА ${t.id} — ${t.title}.
${t.spec}

В конце: pnpm verify (typecheck + lint + test + build + budget + e2e) зелёный; результат проверен вживую в Playwright и скриншотами. Верни отчёт.`, { label: `impl:${t.id}`, phase: 'Tasks', schema: IMPL, model: t.id === 'T-PLT-05' ? 'fable' : 'opus' })
  const fixes = []
  let rv = null
  for (let round = 0; round < 3; round++) {
    rv = await agent(`${BASE}

Ты — независимый строгий проверяющий задачи ${t.id} (${t.title}). Спецификация:
${t.spec}

Отчёт исполнителя: ${JSON.stringify(impl ?? {}).slice(0, 6000)}
${round ? 'Это повторная проверка после исправлений: сообщай только оставшиеся реальные проблемы.' : ''}

Проверь ИСПОЛНЕНИЕМ: 1) pnpm install --frozen-lockfile и pnpm verify — зелёно? число тестов Vitest и Playwright; 2) каждая подзадача и критерии приёмки связанных требований (для этапа 0; что явно про будущие модули — не требуй) — есть ли код И тест, который это доказывает; прочитай тесты — не пустые ли; 3) визуально: подними pnpm dev с моками и сравни через chrome-devtools скриншоты с макетами (${APP}/design/mockups) на 1440 и 390 px — расхождения в цветах/формах/размерах/составе, обрезки, горизонтальная прокрутка, больше одной жёлтой кнопки на область, строки без i18n, логика, которой место на бэкенде (R002), самописное вместо готового (R021), выпадающее не в портале (R022); 4) docs/frontend.md актуален. Останови dev-сервер после.
Верни verifyGreen, evidence и findings: blocker — красная проверка, сломанный каркас; major — подзадача/критерий не выполнены или выполнены неверно, заметное расхождение с макетом, нет теста на ключевое поведение; minor — мелочи.`, { label: `review:${t.id}#${round + 1}`, phase: 'Tasks', schema: REVIEW, model: 'opus', effort: 'xhigh' })
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
Ошибочные находки — не исправляй, обоснуй в deviations (со ссылкой на документацию/макет). В конце pnpm verify зелёный, правки проверены вживую (R018).`, { label: `fix:${t.id}#${round + 1}`, phase: 'Tasks', schema: IMPL, model: 'opus' })
    fixes.push(fx)
  }
  const rem = rv?.findings ?? []
  log(`${t.id}: verify ${rv?.verifyGreen ? 'зелёный' : 'КРАСНЫЙ'}, осталось blocker/major ${rem.filter(f => f.severity !== 'minor').length}, minor ${rem.filter(f => f.severity === 'minor').length}`)
  results.push({ id: t.id, impl, fixes, lastReview: rv })
}

phase('Final review')
const digest = results.map(r => `${r.id}: ${(r.fixes.at(-1)?.summary ?? r.impl?.summary ?? '').slice(0, 700)}`).join('\n\n')
const LENSES = [
  { key: 'acceptance', effort: 'xhigh', prompt: `Ты — финальный приёмщик фронт-каркаса этапа 0 (T-PLT-05, 06, 10, фронт-части 07 и 11). Сводка:
${digest}
Проверь ИСПОЛНЕНИЕМ: pnpm install --frozen-lockfile, pnpm verify — зелёный (числа тестов, размер начального бандла gzip против бюджета 250 КБ); pnpm dev с моками — пройди на 1440×900 и 390×844: все 6 разделов, «Ещё», витрина /dev/ui, гард 401 → /login?returnTo, ассистент без «Клиники»/«Доступов», свёрнутый сайдбар на 900 px, «Назад» закрывает панель; пройди критерии приёмки PLT-17, PLT-18, PLT-20, PLT-21, PLT-22 (фронт-часть), PLT-07/PLT-23 (клиент), PLT-11 (фронт) — для каждого: выполнен / частично (что осталось) / нет — таблицей в evidence. Останови dev-сервер.` },
  { key: 'ui-critic', effort: 'xhigh', prompt: `Ты — UI-критик (дизайн и реализация). Подними pnpm dev с моками и сравни КАЖДЫЙ экран каркаса и витрину с макетами ${APP}/design/mockups (Raspisanie-Den, Sistema, Telefon-Raspisanie, Telefon-Eshche, Vhod) скриншотами рядом через chrome-devtools на 1440 и 390 px. Ищи: расхождения токенов (цвета, радиусы, шрифты, размеры кнопок 48/40, отступы), не те шрифты (проверь computed font-family, казахские глифы), больше одной жёлтой кнопки на область, статус только цветом, обрезки, наложения, горизонтальная прокрутка, цели касания < 44 px, фокус невидим, tooltip/поиск отсутствуют в select (R011), выпадающее режется контейнером (R022), «Отмена» вместо «Назад» в шагах (R023), перегруженность против вкуса владельца (крупно, цветно, воздух). Также код: дублирование компонентов вместо shadcn, инлайн-стили вместо токенов, строки без i18n, бизнес-логика на фронте (R002). Останови dev-сервер. Каждую находку — с экраном и скриншотом-доказательством в evidence.` },
  { key: 'a11y-perf', effort: 'high', prompt: `Ты — ревьюер доступности и производительности. pnpm dev с моками; axe-core по всем маршрутам на 1440 и 390 (0 serious/critical), навигация с клавиатуры по сайдбару, вкладкам, «Ещё», панели и Combo (фокус внутри диалога и возврат после закрытия), prefers-reduced-motion, масштаб 200% без потерь, живые области у тостов/ошибок; pnpm build — размер чанков, начальный JS gzip ≤ 250 КБ, ленивые маршруты действительно отдельные чанки, шрифты preload и swap, Lighthouse mobile по главному маршруту (chrome-devtools lighthouse_audit) — LCP ≤ 2.5 с, CLS ≤ 0.1 на моках. Останови dev-сервер. Находки с доказательствами в evidence.` },
]
const lensRuns = (await parallel(LENSES.map(l => () => agent(`${BASE}\n\n${l.prompt}\nВерни verifyGreen, evidence и findings с severity.`, { label: `final:${l.key}`, phase: 'Final review', schema: REVIEW, model: 'opus', effort: l.effort })))).filter(Boolean)

let allReviews = lensRuns
const finalFixes = []
for (let round = 0; round < 2; round++) {
  const blocking = allReviews.flatMap(r => r.findings).filter(f => f.severity !== 'minor')
  const red = allReviews.some(r => r.verifyGreen === false)
  log(`Финал, круг ${round + 1}: blocker/major ${blocking.length}, verify ${red ? 'КРАСНЫЙ' : 'зелёный'}`)
  if (!blocking.length && !red) break
  phase('Final fix')
  const fx = await agent(`${BASE}

Исправь находки финального ревью фронт-каркаса этапа 0. Обязательные:
${JSON.stringify(blocking, null, 1)}
${red ? 'Проверка КРАСНАЯ: ' + allReviews.map(r => r.evidence).join('\n---\n').slice(0, 8000) : ''}
Мелкие — если дёшево и безопасно:
${JSON.stringify(allReviews.flatMap(r => r.findings).filter(f => f.severity === 'minor'), null, 1).slice(0, 12000)}
Ошибочные находки — обоснуй в deviations. В конце pnpm verify зелёный, правки проверены вживую.`, { label: `final:fix#${round + 1}`, phase: 'Final fix', schema: IMPL, model: 'opus' })
  finalFixes.push(fx)
  const re = await agent(`${BASE}

Перепроверь исправления финального ревью. Исходные обязательные находки:
${JSON.stringify(blocking, null, 1)}
Отчёт исправлений: ${JSON.stringify(fx ?? {}).slice(0, 6000)}
Проверь ИСПОЛНЕНИЕМ: pnpm verify зелёный; каждая находка закрыта (воспроизведи прежний сценарий и скриншот); ничего не сломано. Останови dev-сервер. Верни verifyGreen, evidence и findings (только незакрытое и новое).`, { label: `final:recheck#${round + 1}`, phase: 'Final fix', schema: REVIEW, model: 'opus', effort: 'xhigh' })
  allReviews = [re].filter(Boolean)
}

return {
  tasks: results.map(r => ({ id: r.id, impl: r.impl, fixes: r.fixes, lastReview: r.lastReview })),
  lenses: lensRuns, finalFixes, finalState: allReviews,
}
