#!/usr/bin/env bash
# Проверка Dentify перед коммитом одной командой (T-PLT-15). Bash-вариант verify.ps1: Linux, macOS, Git Bash.
#
#   bash verify.sh                  окружение, бэкенд, контракт API, фронтенд
#   bash verify.sh --e2e            плюс e2e на живом стеке (jar бэкенда + vite preview + Playwright-проекты stack-*)
#   bash verify.sh --scan           только сканеры: OWASP dependency-check, pnpm audit --prod, gitleaks (нужен интернет)
#   bash verify.sh --e2e --scan     всё сразу
#   --strict                        недоступный сканер (сеть, NVD, скачивание gitleaks) — красный шаг, а не предупреждение
#   --skip env,backend,frontend     пропустить группы (например --e2e --skip backend,frontend — только живой стек)
#
# Без ключей:
#   env       JDK 25 (JAVA_HOME или ~/.jdks/openjdk-25.0.2), Docker, dev-окружение (deploy/dev.sh check; не прошло —
#             dev.sh up и снова check), Node 23+ и pnpm 10+
#   backend   ./mvnw -B verify: JUnit, Testcontainers, ApplicationModules.verify, ArchUnit, тесты изоляции
#   api       спека api/openapi.json совпадает с той, что выгрузила сборка бэкенда (OpenApiContractTests)
#   frontend  pnpm install --frozen-lockfile и шаги pnpm verify по одному (список — из package.json): typecheck, lint,
#             i18n:check, api:check (клиент orval = спека), test, build, size, e2e на моках
# Итог — сводка по шагам со временем и кодом выхода; код 1, если красный хоть один шаг. Логи шагов, трейсы и отчёты —
# deploy/test-results/verify-<время>/ (старше 7 дней удаляются). Подробности — README.md, «Проверка перед коммитом».

# Справка (--help) — комментарий выше до первой пустой строки; служебные комментарии — ниже неё.
# Без set -u: пустые массивы под set -u роняют bash 3.2 (macOS).
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND="$ROOT/backend"
FRONTEND="$ROOT/frontend"
DEPLOY="$ROOT/deploy"
SPEC_FILE="$ROOT/api/openapi.json"
RESULTS="$DEPLOY/test-results"
RUN_DIR="$RESULTS/verify-$(date +%Y%m%d-%H%M%S)"

JDK25="$HOME/.jdks/openjdk-25.0.2"
NODE_MAJOR=23
PNPM_MAJOR=10
# Официальные JSON-фиды NVD 2.0: без ключа API (у API без ключа жёсткий лимит частоты). Зеркало — DENTIFY_NVD_DATAFEED;
# ключ API — NVD_API_KEY, тогда фиды не нужны.
NVD_FEED='https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz'
# gitleaks: закреплённая версия и SHA-256 архивов релиза (https://github.com/gitleaks/gitleaks/releases).
GITLEAKS_VERSION=8.30.1
gitleaks_sha256() {
  case $1 in
    linux_x64) echo 551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb ;;
    linux_arm64) echo e4a487ee7ccd7d3a7f7ec08657610aa3606637dab924210b3aee62570fb4b080 ;;
    darwin_x64) echo dfe101a4db2255fc85120ac7f3d25e4342c3c20cf749f2c20a18081af1952709 ;;
    darwin_arm64) echo b40ab0ae55c505963e365f271a8d3846efbc170aa17f2607f13df610a9aeb6a5 ;;
    windows_x64) echo d29144deff3a68aa93ced33dddf84b7fdc26070add4aa0f4513094c8332afc4e ;;
    windows_arm64) echo b95f5e4f5c425cedca7ee203d9afd29597e692c4924a12ed42f970537c72cc0f ;;
  esac
}
# Куда скачивается gitleaks, если его нет в PATH (другая папка — DENTIFY_TOOLS_DIR).
TOOLS_DIR="${DENTIFY_TOOLS_DIR:-$HOME/.dentify-tools}"

E2E=0 SCAN=0 STRICT=0 SKIP=""
while [[ $# -gt 0 ]]; do
  case $1 in
    --e2e) E2E=1 ;;
    --scan) SCAN=1 ;;
    --strict) STRICT=1 ;;
    --skip) SKIP="${2:-}"; shift ;;
    --skip=*) SKIP="${1#--skip=}" ;;
    -h | --help) awk 'NR == 1 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) printf 'неизвестный аргумент: %s (см. --help)\n' "$1" >&2; exit 2 ;;
  esac
  shift
done
SKIP=",${SKIP// /},"
for group in ${SKIP//,/ }; do
  case $group in env | backend | frontend) ;; *) printf -- '--skip: неизвестная группа %s; можно env, backend, frontend\n' "$group" >&2; exit 2 ;; esac
done
skipped() { [[ $SKIP == *",$1,"* ]]; }

RUN_BASE=0; { [[ $SCAN == 0 ]] || [[ $E2E == 1 ]]; } && RUN_BASE=1
DO_BACKEND=0; [[ $RUN_BASE == 1 ]] && ! skipped backend && DO_BACKEND=1
DO_FRONTEND=0; [[ $RUN_BASE == 1 ]] && ! skipped frontend && DO_FRONTEND=1
DO_ENV=0; [[ $RUN_BASE == 1 ]] && ! skipped env && DO_ENV=1
NEED_JDK=0; { [[ $DO_BACKEND == 1 ]] || [[ $E2E == 1 ]] || [[ $SCAN == 1 ]]; } && NEED_JDK=1
NEED_DOCKER=0; { [[ $DO_BACKEND == 1 ]] || [[ $DO_ENV == 1 ]] || [[ $E2E == 1 ]]; } && NEED_DOCKER=1
NEED_NODE=0; { [[ $DO_FRONTEND == 1 ]] || [[ $E2E == 1 ]] || [[ $SCAN == 1 ]]; } && NEED_NODE=1

IS_WINDOWS=0
case "$(uname -s)" in MINGW* | MSYS* | CYGWIN*) IS_WINDOWS=1 ;; esac
EXE=""; [[ $IS_WINDOWS == 1 ]] && EXE=".exe"

if [[ -t 1 ]]; then
  C_RESET=$'\033[0m' C_RED=$'\033[31m' C_GREEN=$'\033[32m' C_YELLOW=$'\033[33m' C_CYAN=$'\033[36m' C_GRAY=$'\033[90m'
else
  C_RESET='' C_RED='' C_GREEN='' C_YELLOW='' C_CYAN='' C_GRAY=''
fi

# Путь для Windows-программ (java.exe, node.exe, gitleaks.exe) в Git Bash — C:/…; в Linux и macOS — как есть.
native_path() { if [[ $IS_WINDOWS == 1 ]]; then cygpath -m "$1"; else printf '%s\n' "$1"; fi; }
relative() { local p=$1; printf '%s\n' "${p#"$ROOT"/}"; }

# ---------- шаги ----------

STEP_NAMES=() STEP_STATUS=() STEP_CODE=() STEP_NOTE=() STEP_SECONDS=()
R_STATUS=OK R_CODE=0 R_NOTE=""
CURRENT_LOG=/dev/null

ok() { R_STATUS=OK R_CODE=0 R_NOTE=${1:-}; }
fail() { R_STATUS=FAIL R_CODE=$1 R_NOTE=${2:-}; }
warn() { R_STATUS=WARN R_CODE=0 R_NOTE=${1:-}; }
skip() { R_STATUS=SKIP R_CODE=0 R_NOTE=${1:-}; }
# Сканер недоступен (сеть, NVD, скачивание): без --strict — предупреждение, с --strict — красный шаг.
unavailable() { if [[ $STRICT == 1 ]]; then fail 1 "$1"; else warn "$1 (красным — с --strict)"; fi; }

color_of() { case $1 in OK) printf '%s' "$C_GREEN" ;; FAIL) printf '%s' "$C_RED" ;; WARN) printf '%s' "$C_YELLOW" ;; *) printf '%s' "$C_GRAY" ;; esac; }
duration() { printf '%d:%02d' $(($1 / 60)) $(($1 % 60)); }

# run_step "Имя" функция [аргументы…]: функция ставит итог через ok/fail/warn/skip (ничего — OK).
run_step() {
  local name=$1; shift
  local number=$((${#STEP_NAMES[@]} + 1))
  local slug
  slug=$(printf '%s' "$name" | LC_ALL=C sed -e 's/[^A-Za-z0-9._-]\{1,\}/-/g' -e 's/^-//' -e 's/-$//')
  CURRENT_LOG="$RUN_DIR/$(printf '%02d' "$number")-$slug.log"
  printf '\n%s==> [%d] %s%s\n' "$C_CYAN" "$number" "$name" "$C_RESET"
  R_STATUS=OK R_CODE=0 R_NOTE=""
  local start=$SECONDS
  "$@"
  local elapsed=$((SECONDS - start))
  STEP_NAMES+=("$name") STEP_STATUS+=("$R_STATUS") STEP_CODE+=("$R_CODE") STEP_NOTE+=("$R_NOTE") STEP_SECONDS+=("$elapsed")
  printf '%s    %s  %s  код %s  %s%s\n' "$(color_of "$R_STATUS")" "$R_STATUS" "$(duration "$elapsed")" "$R_CODE" "$R_NOTE" "$C_RESET"
}

passed() {
  local i
  for ((i = ${#STEP_NAMES[@]} - 1; i >= 0; i--)); do
    if [[ ${STEP_NAMES[$i]} == "$1" ]]; then [[ ${STEP_STATUS[$i]} == OK ]]; return; fi
  done
  return 1
}

# run_logged <каталог> <команда> [аргументы…]: вывод — на экран (QUIET=1 — только в лог) и в лог шага; код — код команды.
run_logged() {
  local dir=$1; shift
  printf '%s    %s> %s%s\n' "$C_GRAY" "$dir" "$*" "$C_RESET"
  printf '# %s> %s\n' "$dir" "$*" >>"$CURRENT_LOG"
  local code
  if [[ ${QUIET:-0} == 1 ]]; then
    (cd "$dir" && "$@") >>"$CURRENT_LOG" 2>&1
    code=$?
  else
    (cd "$dir" && "$@") 2>&1 | tee -a "$CURRENT_LOG"
    code=${PIPESTATUS[0]}
  fi
  printf '# код выхода: %s\n' "$code" >>"$CURRENT_LOG"
  return "$code"
}

log_has() { grep -Eq -- "$1" "$CURRENT_LOG" 2>/dev/null; }
log_last() { grep -E -- "$1" "$CURRENT_LOG" 2>/dev/null | tail -n 1; }

sha256() { if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }

major() { local v=${1#v}; printf '%s\n' "${v%%[!0-9]*}"; }

# ---------- окружение ----------

jdk_version() {
  local home=$1
  [[ -n $home && -f $home/release && -x $home/bin/java$EXE ]] || return 1
  sed -n 's/^JAVA_VERSION="\([^"]*\)".*/\1/p' "$home/release" | head -n 1
}

step_jdk() {
  local current=${JAVA_HOME:-} candidate version
  # Git Bash: JAVA_HOME из Windows («C:\…») — в вид /c/…, иначе двоеточие диска разорвёт PATH.
  [[ $IS_WINDOWS == 1 && -n $current ]] && current=$(cygpath -u "$current")
  for candidate in "$current" "$JDK25"; do
    version=$(jdk_version "$candidate") || continue
    if [[ $(major "$version") -ge 25 ]]; then
      export JAVA_HOME=$candidate
      export PATH="$candidate/bin:$PATH"
      local note="$candidate ($version)"
      [[ -n $current && $candidate != "$current" ]] && note+="; JAVA_HOME был $current — взят JDK 25"
      ok "$note"
      return
    fi
  done
  fail 1 "нужен JDK 25: JAVA_HOME (${current:-не задан}) не 25+, в $JDK25 его нет"
}

step_docker() {
  command -v docker >/dev/null 2>&1 || { fail 1 'docker не найден: установите Docker Desktop или Docker Engine'; return; }
  local server compose
  server=$(docker info --format '{{.ServerVersion}}' 2>/dev/null) || { fail 1 'Docker не отвечает: запустите Docker'; return; }
  compose=$(docker compose version --short 2>/dev/null) || { fail 1 'нет Docker Compose v2'; return; }
  ok "Docker $server, Compose $compose"
}

step_devenv() {
  if QUIET=1 run_logged "$ROOT" bash "$DEPLOY/dev.sh" check; then ok 'dev.sh check — все проверки пройдены'; return; fi
  printf '%s    dev.sh check не прошёл — поднимаю окружение: dev.sh up%s\n' "$C_YELLOW" "$C_RESET"
  run_logged "$ROOT" bash "$DEPLOY/dev.sh" up || { fail $? 'dev.sh up не поднял окружение — см. лог шага'; return; }
  run_logged "$ROOT" bash "$DEPLOY/dev.sh" check || { fail $? 'окружение поднято, но dev.sh check не проходит — см. лог шага'; return; }
  ok 'было не поднято — подняли dev.sh up, check пройден (остановить: bash deploy/dev.sh down)'
}

step_node() {
  command -v node >/dev/null 2>&1 || { fail 1 "нет Node.js $NODE_MAJOR+"; return; }
  command -v pnpm >/dev/null 2>&1 || { fail 1 "нет pnpm $PNPM_MAJOR+"; return; }
  local node pnpm
  node=$(node --version) pnpm=$(pnpm --version)
  [[ $(major "$node") -ge $NODE_MAJOR ]] || { fail 1 "Node $node, нужен $NODE_MAJOR+"; return; }
  [[ $(major "$pnpm") -ge $PNPM_MAJOR ]] || { fail 1 "pnpm $pnpm, нужен $PNPM_MAJOR+"; return; }
  ok "Node $node, pnpm $pnpm"
}

# ---------- бэкенд и спека ----------

SPEC_BEFORE="" BACKEND_RAN=0
spec_hash() { if [[ -f $SPEC_FILE ]]; then sha256 "$SPEC_FILE"; fi; }

step_backend_verify() {
  SPEC_BEFORE=$(spec_hash) BACKEND_RAN=1
  touch "$RUN_DIR/.backend-started"
  local code
  run_logged "$BACKEND" ./mvnw -B -ntp verify
  code=$?
  if [[ $code -ne 0 ]]; then
    local errors
    errors=$(grep -E '^\[ERROR\] [^[:space:]]' "$CURRENT_LOG" | grep -E 'Tests run:|FAILURE|COMPILATION ERROR|Failed to execute goal' |
      head -n 2 | sed -e 's/^\[ERROR\] //' -e 's/^\(.\{150\}\).*/\1…/' | awk 'NR > 1 { printf " / " } { printf "%s", $0 }')
    fail "$code" "mvnw verify упал${errors:+: $errors}"
    return
  fi
  local tests
  tests=$(log_last '^\[(INFO|WARNING)\] Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$' | sed 's/^\[[A-Z]*\] //')
  ok "$tests"
}

step_spec() {
  [[ $BACKEND_RAN == 1 ]] || { skip 'бэкенд не запускался'; return; }
  local dirty=""
  [[ -n $(git -C "$ROOT" status --porcelain -- api/openapi.json 2>/dev/null) ]] && dirty='; api/openapi.json не закоммичен — коммитьте вместе с кодом'
  if [[ $(spec_hash) != "$SPEC_BEFORE" ]]; then
    fail 1 'сборка переписала api/openapi.json: спека отставала от контроллеров. Проверьте git diff api/openapi.json, затем cd frontend && pnpm api:generate && pnpm typecheck — и закоммитьте всё вместе'
    return
  fi
  if passed 'backend: ./mvnw -B verify'; then ok "спека = выгрузка OpenApiContractTests$dirty"; return; fi
  # Бэкенд красный: совпадению можно верить, только если тест контракта в этом прогоне прошёл.
  local report="$BACKEND/target/surefire-reports/TEST-kz.dentify.platform.OpenApiContractTests.xml"
  if [[ -f $report && $report -nt $RUN_DIR/.backend-started ]] &&
    head -n 2 "$report" | grep -q 'failures="0"' && head -n 2 "$report" | grep -q 'errors="0"'; then
    ok "спека = выгрузка OpenApiContractTests (бэкенд красный по другим тестам)$dirty"
    return
  fi
  skip 'бэкенд красный и OpenApiContractTests не прошёл — сверять не с чем'
}

# ---------- фронтенд ----------

step_pnpm_install() {
  run_logged "$FRONTEND" pnpm install --frozen-lockfile || fail $? 'lockfile не совпадает с package.json или реестр недоступен — см. лог шага'
}

step_frontend_script() {
  local script=$1 code
  if [[ $script == size ]] && ! passed 'frontend: pnpm build'; then skip 'сборка не прошла — бюджет мерить не по чему'; return; fi
  run_logged "$FRONTEND" pnpm "$script"
  code=$?
  if [[ $script == e2e ]]; then
    local counts
    counts=$(grep -E '^[[:space:]]+[0-9]+ (passed|failed|flaky|skipped|did not run)' "$CURRENT_LOG" | sed 's/^ *//' | paste -sd ',' - | sed 's/,/, /g')
    if [[ $code -ne 0 ]]; then fail "$code" "$counts; отчёт — frontend/playwright-report/run-<id> (путь — в конце лога шага)"; return; fi
    ok "$counts"
    return
  fi
  [[ $code -eq 0 ]] || { fail "$code" 'см. лог шага'; return; }
  if [[ $script == test ]]; then ok "$(log_last '^[[:space:]]+Tests[[:space:]]+[0-9]+' | tr -s ' ' | sed 's/^ //')"; fi
}

# ---------- живой стек ----------

BG_PIDS=() BG_NAMES=() BG_LOGS=()
JAR="" API_PORT="" WEB_PORT=""

start_bg() {
  local name=$1 dir=$2; shift 2
  local log="$RUN_DIR/stack-$name.log"
  printf '%s    %s> %s  (лог: %s)%s\n' "$C_GRAY" "$dir" "$*" "$(relative "$log")" "$C_RESET"
  (cd "$dir" && exec "$@") >"$log" 2>&1 &
  BG_PIDS+=("$!") BG_NAMES+=("$name") BG_LOGS+=("$log")
}

alive() { kill -0 "$1" 2>/dev/null; }

# Останавливает процессы стека всегда (и при Ctrl+C): в Git Bash — дерево процессов Windows (taskkill /T), иначе TERM,
# через 15 с — KILL.
stop_bg() {
  local i pid stopped=()
  for i in "${!BG_PIDS[@]}"; do
    pid=${BG_PIDS[$i]}
    alive "$pid" || continue
    if [[ -r /proc/$pid/winpid ]] && command -v taskkill >/dev/null 2>&1; then
      taskkill //PID "$(cat "/proc/$pid/winpid")" //T //F >/dev/null 2>&1
    else
      kill -TERM "$pid" 2>/dev/null
      local waited=0
      while alive "$pid" && [[ $waited -lt 30 ]]; do sleep 0.5; waited=$((waited + 1)); done
      alive "$pid" && kill -KILL "$pid" 2>/dev/null
    fi
    wait "$pid" 2>/dev/null
    stopped+=("${BG_NAMES[$i]} (pid $pid)")
  done
  BG_PIDS=() BG_NAMES=() BG_LOGS=()
  STOPPED_NOTE=""
  if [[ ${#stopped[@]} -gt 0 ]]; then STOPPED_NOTE=$(printf '%s, ' "${stopped[@]}"); STOPPED_NOTE=${STOPPED_NOTE%, }; fi
}

free_ports() {
  node -e "
    const net = require('node:net'); const servers = []; const ports = [];
    const next = () => {
      if (servers.length === Number(process.argv[1])) { console.log(ports.join(' ')); servers.forEach((s) => s.close()); return }
      const s = net.createServer(); servers.push(s)
      s.listen(0, '127.0.0.1', () => { ports.push(s.address().port); next() })
    }
    next()" "$1"
}

port_free() {
  node -e "const s = require('node:net').createServer(); s.once('error', () => process.exit(1)); s.listen(Number(process.argv[1]), '127.0.0.1', () => s.close())" "$1"
}

# wait_http <индекс процесса> <адрес> <секунды> <grep-шаблон ответа>
wait_http() {
  local index=$1 url=$2 seconds=$3 pattern=$4 deadline=$((SECONDS + $3))
  while [[ $SECONDS -lt $deadline ]]; do
    if ! alive "${BG_PIDS[$index]}"; then
      fail 1 "${BG_NAMES[$index]} завершился; лог $(relative "${BG_LOGS[$index]}"): $(tail -n 3 "${BG_LOGS[$index]}" | tr '\n' ' ' | cut -c1-300)"
      return 1
    fi
    if curl -fsS --max-time 3 "$url" 2>/dev/null | grep -Eq "$pattern"; then return 0; fi
    sleep 0.5
  done
  fail 1 "${BG_NAMES[$index]} не ответил за $seconds с: $url (лог $(relative "${BG_LOGS[$index]}"))"
  return 1
}

find_jar() {
  local jar
  jar=$(ls -t "$BACKEND"/target/dentify-backend-*.jar 2>/dev/null | grep -Ev '(sources|javadoc|plain)\.jar$' | head -n 1)
  [[ -n $jar ]] && printf '%s\n' "$jar"
}

step_stack_jar() {
  if passed 'backend: ./mvnw -B verify' && JAR=$(find_jar); then ok "из шага backend: $(relative "$JAR")"; return; fi
  run_logged "$BACKEND" ./mvnw -B -ntp -DskipTests package || { fail $? 'mvnw package упал — см. лог шага'; return; }
  JAR=$(find_jar) || { fail 1 'jar не найден в backend/target'; return; }
  ok "$(relative "$JAR")"
}

step_stack_dist() {
  if passed 'frontend: pnpm build' && [[ -f $FRONTEND/dist/index.html ]]; then ok 'из шага frontend: frontend/dist'; return; fi
  run_logged "$FRONTEND" pnpm build || { fail $? 'pnpm build упал — см. лог шага'; return; }
  ok 'frontend/dist'
}

step_stack_start() {
  [[ -n $JAR ]] || { skip 'нет jar бэкенда'; return; }
  passed 'e2e: сборка фронта (dist)' || { skip 'нет сборки фронта'; return; }
  local ports management
  ports=$(free_ports 3) || { fail 1 'не нашёл свободных портов'; return; }
  read -r API_PORT management WEB_PORT <<<"$ports"

  start_bg backend "$BACKEND" "$JAVA_HOME/bin/java$EXE" -jar "$(native_path "$JAR")" --spring.profiles.active=dev \
    "--server.port=$API_PORT" "--management.server.port=$management"
  wait_http $((${#BG_PIDS[@]} - 1)) "http://127.0.0.1:$management/actuator/health/readiness" 180 '"status" *: *"UP"' || return
  printf '%s    бэкенд готов: API 127.0.0.1:%s, actuator 127.0.0.1:%s%s\n' "$C_GRAY" "$API_PORT" "$management" "$C_RESET"

  # vite preview: сборка без моков, /api — прокси на этот бэкенд (vite.config.ts, DENTIFY_API_TARGET).
  DENTIFY_API_TARGET="http://127.0.0.1:$API_PORT" start_bg frontend "$FRONTEND" node node_modules/vite/bin/vite.js preview \
    --port "$WEB_PORT" --strictPort
  wait_http $((${#BG_PIDS[@]} - 1)) "http://127.0.0.1:$WEB_PORT/login" 60 '<div id="root"' || return
  # Сквозной запрос: страница → прокси Vite → бэкенд. 204 и cookie XSRF-TOKEN — стек собран правильно.
  local headers
  headers=$(curl -sS -D - -o /dev/null --max-time 10 "http://127.0.0.1:$WEB_PORT/api/v1/auth/csrf" 2>&1)
  if ! grep -q '^HTTP/[0-9.]* 204' <<<"$headers" || ! grep -qi '^set-cookie: XSRF-TOKEN=' <<<"$headers"; then
    fail 1 "GET /api/v1/auth/csrf через прокси: $(head -n 1 <<<"$headers" | tr -d '\r'), без cookie XSRF-TOKEN"
    return
  fi
  ok "фронт http://localhost:$WEB_PORT → API 127.0.0.1:$API_PORT (actuator $management); /api/v1/auth/csrf через прокси — 204"
}

# Проекты живого стека — по договору stack-<вьюпорт> (T-PLT-16: stack-desktop 1440×900, stack-mobile 390×844): берём все
# по маске, чтобы оба вьюпорта шли одним прогоном и новое имя не выпало из него молча. В кавычках — не шаблон файлов bash.
STACK_PROJECTS='--project=stack*'

step_stack_playwright() {
  { [[ -n $WEB_PORT ]] && passed 'e2e: запуск стека'; } || { skip 'стек не поднят'; return; }
  local code
  QUIET=1 run_logged "$FRONTEND" pnpm exec playwright test --list "$STACK_PROJECTS"
  code=$?
  if [[ $code -ne 0 ]]; then
    # Маска без совпадений — «No projects matched»; точное имя без проекта — «Project(s) "…" not found».
    if log_has 'No projects matched|Project\(s\) "stack[^"]*" not found'; then
      skip 'в playwright.config.ts нет проектов stack-* (stack-desktop и stack-mobile добавит T-PLT-16) — живой стек поднят и проверен запросом, UI-сценарии пропущены'
    else
      fail "$code" 'playwright test --list упал — см. лог шага'
    fi
    return
  fi
  local stack_dir="$RUN_DIR/stack"
  # Договор с проектами stack-* (T-PLT-16): адрес фронта на живом бэкенде и прямой адрес API — из окружения.
  DENTIFY_STACK_URL="http://localhost:$WEB_PORT" DENTIFY_STACK_API_URL="http://127.0.0.1:$API_PORT" \
    PLAYWRIGHT_HTML_OUTPUT_DIR="$(native_path "$stack_dir/report")" PLAYWRIGHT_HTML_OPEN=never \
    run_logged "$FRONTEND" pnpm exec playwright test "$STACK_PROJECTS" --output "$(native_path "$stack_dir/artifacts")"
  code=$?
  local summary
  summary=$(grep -E '^[[:space:]]+[0-9]+ (passed|failed|flaky)' "$CURRENT_LOG" | sed 's/^ *//' | paste -sd ',' - | sed 's/,/, /g')
  if [[ $code -ne 0 ]]; then fail "$code" "$summary; трейсы и скриншоты — $(relative "$stack_dir")"; return; fi
  ok "$summary; трейсы и скриншоты — $(relative "$stack_dir")"
}

step_stack_stop() {
  STOPPED_NOTE=""
  stop_bg
  local busy=() port
  for port in $API_PORT $WEB_PORT; do port_free "$port" || busy+=("$port"); done
  if [[ ${#busy[@]} -gt 0 ]]; then fail 1 "порты всё ещё заняты: ${busy[*]}"; return; fi
  if [[ -n $STOPPED_NOTE ]]; then ok "остановлены: $STOPPED_NOTE"; else ok 'нечего останавливать'; fi
}

# ---------- сканеры ----------

nvd_data_dir() { printf '%s\n' "${DENTIFY_NVD_DATA:-$HOME/.m2/repository/org/owasp/dependency-check-data}"; }
nvd_args() { if [[ -n ${DENTIFY_NVD_DATA:-} ]]; then printf '%s\n' "-DdataDirectory=$(native_path "$DENTIFY_NVD_DATA")"; fi; }

step_nvd_update() {
  local args=(-B -ntp dependency-check:update-only) source='NVD API с ключом NVD_API_KEY' extra
  while IFS= read -r extra; do [[ -n $extra ]] && args+=("$extra"); done < <(nvd_args)
  if [[ -z ${NVD_API_KEY:-} ]]; then
    local feed=${DENTIFY_NVD_DATAFEED:-$NVD_FEED}
    args+=("-DnvdDatafeedUrl=$feed")
    source="JSON-фиды NVD ($feed)"
  fi
  if run_logged "$BACKEND" ./mvnw "${args[@]}"; then
    # Плагин ходит в NVD не чаще раза в 4 часа (nvdValidForHours): свежая база — без сети, это не «обновлена».
    if log_has 'Skipping NVD API Cache check since last check was within'; then
      ok "база свежая (NVD проверялся меньше 4 ч назад) — обновление не нужно; кэш — $(nvd_data_dir)"
    else
      ok "база обновлена: $source; кэш — $(nvd_data_dir)"
    fi
    return
  fi
  local reason
  # Суть — в конце строки Maven: «… for more details. Unable to download …; nvd.invalid: Unknown host …».
  reason=$(log_last '^\[ERROR\] Failed to execute goal' | sed -e 's/ -> \[Help 1\]$//' -e 's/^.*for more details\. //' \
    -e 's/^\[ERROR\] Failed to execute goal [^:]*:[^:]*:[^:]*:[^ ]* ([^)]*) on project [^:]*: //' | cut -c1-200)
  unavailable "NVD недоступен ($source): ${reason:-см. лог шага}; проверка пойдёт по кэшу, если он есть"
}

step_dependency_check() {
  local db
  db=$(find "$(nvd_data_dir)" -name odc.mv.db 2>/dev/null | head -n 1)
  [[ -n $db ]] || { unavailable "нет локальной базы NVD в $(nvd_data_dir) — проверять не по чему"; return; }
  local args=(-B -ntp dependency-check:check -DautoUpdate=false) extra code
  while IFS= read -r extra; do [[ -n $extra ]] && args+=("$extra"); done < <(nvd_args)
  run_logged "$BACKEND" ./mvnw "${args[@]}"
  code=$?
  local report='backend/target/dependency-check-report.html'
  # Возраст базы по времени файла не показываем: odc.mv.db (H2) переписывается при каждой проверке, а не только при
  # обновлении. Свежесть — в шаге «NVD — обновление базы».
  if [[ $code -eq 0 ]]; then ok "уязвимостей с CVSS 7+ нет (по локальной базе NVD); отчёт — $report"; return; fi
  if log_has 'CVSS score greater than or equal to'; then
    local found
    found=$(grep -E '^\[ERROR\] [^ ]+\.jar \(.*\): CVE' "$CURRENT_LOG" | while IFS= read -r line; do
      local jar scores count max
      jar=$(sed -E 's/^\[ERROR\] ([^ ]+\.jar) .*/\1/' <<<"$line")
      scores=$(grep -oE '\([0-9]+([.,][0-9]+)?\)' <<<"${line##*: }" | tr -d '()' | tr ',' '.')
      count=$(wc -l <<<"$scores" | tr -d ' ')
      max=$(sort -g <<<"$scores" | tail -n 1)
      printf '%s — %s CVE, макс. %s; ' "$jar" "$count" "$max"
    done)
    fail "$code" "уязвимости с CVSS 7+: ${found%; }; отчёт — $report, исключения — backend/dependency-check-suppressions.xml"
    return
  fi
  # Первое обновление не дошло до NVD (новая машина, очищенный кэш, нет сети): плагин всё равно создал пустой
  # odc.mv.db, и проверка по нему падает с NoDataException. Это недоступность сканера, а не красная проверка.
  if log_has 'NoDataException|No documents exist'; then
    unavailable "база NVD в $(nvd_data_dir) пуста — ни разу не скачана, проверять не по чему"
    return
  fi
  fail "$code" 'dependency-check упал не из-за уязвимостей — см. лог шага'
}

step_pnpm_audit() {
  local code
  run_logged "$FRONTEND" pnpm audit --prod --audit-level high
  code=$?
  if [[ $code -eq 0 ]]; then
    local total
    total=$(log_last 'vulnerabilities found|No known vulnerabilities' | sed 's/^ *//')
    ok "${total:-high и critical нет} (порог — high)"
    return
  fi
  if log_has 'ERR_PNPM_AUDIT_BAD_RESPONSE|ERR_PNPM_META_FETCH_FAIL|ENOTFOUND|ETIMEDOUT|ECONNRESET|ECONNREFUSED|EAI_AGAIN|getaddrinfo'; then
    unavailable 'реестр npm недоступен для pnpm audit'
    return
  fi
  fail "$code" "уязвимости high/critical в prod-зависимостях: $(log_last '^Severity:'); исключения — pnpm.auditConfig.ignoreGhsas в frontend/package.json"
}

gitleaks_platform() {
  local os arch
  case "$(uname -s)" in Linux) os=linux ;; Darwin) os=darwin ;; *) os=windows ;; esac
  case "$(uname -m)" in aarch64 | arm64) arch=arm64 ;; *) arch=x64 ;; esac
  printf '%s_%s\n' "$os" "$arch"
}

GITLEAKS=""
step_gitleaks_binary() {
  local local_bin="$TOOLS_DIR/gitleaks-$GITLEAKS_VERSION/gitleaks$EXE" on_path version
  on_path=$(command -v gitleaks 2>/dev/null || true)
  if [[ -n $on_path ]]; then
    version=$("$on_path" version 2>/dev/null | tail -n 1)
    version=${version#v}
    local maj=${version%%.*} rest=${version#*.}
    if [[ $maj =~ ^[0-9]+$ ]] && { [[ $maj -gt 8 ]] || { [[ $maj -eq 8 ]] && [[ ${rest%%.*} -ge 19 ]]; }; }; then
      GITLEAKS=$on_path
      ok "$on_path ($version)"
      return
    fi
    printf '%s    gitleaks в PATH старый (%s), нужен 8.19+ (команда dir) — беру закреплённый %s%s\n' "$C_YELLOW" "$version" "$GITLEAKS_VERSION" "$C_RESET"
  fi
  if [[ -x $local_bin ]]; then GITLEAKS=$local_bin; ok "$local_bin ($GITLEAKS_VERSION)"; return; fi

  local platform ext name url archive
  platform=$(gitleaks_platform)
  ext=tar.gz; [[ $platform == windows_* ]] && ext=zip
  name="gitleaks_${GITLEAKS_VERSION}_$platform.$ext"
  url="https://github.com/gitleaks/gitleaks/releases/download/v$GITLEAKS_VERSION/$name"
  archive="$(mktemp -d)/$name"
  printf '%s    скачиваю %s%s\n' "$C_GRAY" "$url" "$C_RESET"
  if ! curl -fsSL --max-time 120 -o "$archive" "$url"; then
    unavailable "gitleaks не скачался ($url). Поставьте сами: brew install gitleaks, winget install Gitleaks.Gitleaks или релиз с GitHub"
    return
  fi
  local hash
  hash=$(sha256 "$archive")
  if [[ $hash != "$(gitleaks_sha256 "$platform")" ]]; then fail 1 "SHA-256 архива $name не совпал с закреплённым: $hash"; rm -f "$archive"; return; fi
  mkdir -p "$(dirname "$local_bin")"
  if [[ $ext == zip ]]; then unzip -oq "$archive" -d "$(dirname "$local_bin")"; else tar -xzf "$archive" -C "$(dirname "$local_bin")"; fi
  rm -f "$archive"
  [[ -x $local_bin ]] || chmod +x "$local_bin" 2>/dev/null
  [[ -f $local_bin ]] || { fail 1 "в архиве $name нет gitleaks"; return; }
  GITLEAKS=$local_bin
  ok "скачан $local_bin ($GITLEAKS_VERSION, SHA-256 сверен)"
}

gitleaks_result() {
  local code=$1 report=$2
  if [[ $code -eq 0 ]]; then ok 'утечек нет'; return; fi
  local leaks
  leaks=$(log_last 'leaks found: [0-9]+' | grep -oE 'leaks found: [0-9]+' | grep -oE '[0-9]+$')
  if [[ -n $leaks ]]; then
    fail "$code" "найдено секретов: $leaks; отчёт — $(relative "$report") (значения скрыты). Ложное — .gitleaks.toml (allowlist с причиной) или отпечаток в .gitleaksignore"
    return
  fi
  fail "$code" 'gitleaks упал — см. лог шага'
}

# Рабочая копия — .gitleaks-dir.toml (наследует .gitleaks.toml и исключает то, чего нет в git: deploy/.env, сборки,
# зависимости); запуск из $ROOT — путь [extend] gitleaks считает от текущей папки. История — строгим .gitleaks.toml.
step_gitleaks_dir() {
  [[ -n $GITLEAKS ]] || { skip 'нет gitleaks'; return; }
  local report="$RUN_DIR/gitleaks-dir.json" code
  run_logged "$ROOT" "$GITLEAKS" dir . --config .gitleaks-dir.toml --redact --no-banner --no-color --report-format json \
    --report-path "$(native_path "$report")"
  code=$?
  gitleaks_result "$code" "$report"
}

step_gitleaks_history() {
  [[ -n $GITLEAKS ]] || { skip 'нет gitleaks'; return; }
  local top prefix report="$RUN_DIR/gitleaks-history.json" code
  top=$(git -C "$ROOT" rev-parse --show-toplevel 2>/dev/null) || { skip 'не git-репозиторий — историю не проверить'; return; }
  prefix=$(git -C "$ROOT" rev-parse --show-prefix)
  prefix=${prefix%/}
  run_logged "$ROOT" "$GITLEAKS" git "$(native_path "$top")" "--log-opts=-- ${prefix:-.}" --config "$(native_path "$ROOT/.gitleaks.toml")" \
    --redact --no-banner --no-color --report-format json --report-path "$(native_path "$report")"
  code=$?
  gitleaks_result "$code" "$report"
  local commits
  commits=$(log_last '[0-9]+ commits scanned' | grep -oE '[0-9]+ commits' | grep -oE '[0-9]+')
  [[ $R_STATUS == OK && -n $commits ]] && R_NOTE="утечек нет (коммитов с ${prefix:-.}: $commits)"
}

# ---------- сводка ----------

show_summary() {
  local total=$1 mode=$2 i failed=() warned=0
  printf '\n%sСводка: verify.sh %s — %s, всего %s%s\n' "$C_CYAN" "$mode" "$(date '+%Y-%m-%d %H:%M')" "$(duration "$total")" "$C_RESET"
  printf '   #  Итог  Время   Код  Шаг\n'
  for i in "${!STEP_NAMES[@]}"; do
    local line
    line=$(printf '  %2d  %-4s  %5s  %4s  %s' "$((i + 1))" "${STEP_STATUS[$i]}" "$(duration "${STEP_SECONDS[$i]}")" "${STEP_CODE[$i]}" "${STEP_NAMES[$i]}")
    [[ -n ${STEP_NOTE[$i]} ]] && line+=" — ${STEP_NOTE[$i]}"
    printf '%s%s%s\n' "$(color_of "${STEP_STATUS[$i]}")" "$line" "$C_RESET"
    [[ ${STEP_STATUS[$i]} == FAIL ]] && failed+=("${STEP_NAMES[$i]}")
    [[ ${STEP_STATUS[$i]} == WARN ]] && warned=$((warned + 1))
  done
  echo
  if [[ ${#failed[@]} -gt 0 ]]; then
    local list
    list=$(printf '%s; ' "${failed[@]}")
    printf '%sИтог: КРАСНО — не прошли %d из %d: %s%s\n' "$C_RED" "${#failed[@]}" "${#STEP_NAMES[@]}" "${list%; }" "$C_RESET"
  else
    local extra=""
    [[ $warned -gt 0 ]] && extra=", предупреждений: $warned"
    printf '%sИтог: ЗЕЛЕНО — шагов: %d%s%s\n' "$C_GREEN" "${#STEP_NAMES[@]}" "$extra" "$C_RESET"
  fi
  printf 'Логи шагов: %s\n' "$(relative "$RUN_DIR")"
}

# ---------- main ----------

mkdir -p "$RUN_DIR"
find "$RESULTS" -maxdepth 1 -type d -name 'verify-*' -mtime +7 -exec rm -rf {} + 2>/dev/null

MODE=()
[[ $RUN_BASE == 1 ]] && MODE+=(verify)
[[ $E2E == 1 ]] && MODE+=(--e2e)
[[ $SCAN == 1 ]] && MODE+=(--scan)
[[ $STRICT == 1 ]] && MODE+=(--strict)
[[ $SKIP != ",," ]] && MODE+=("--skip ${SKIP:1:${#SKIP}-2}")
MODE_TEXT="${MODE[*]}"

# Ctrl+C и ошибки: фоновые процессы стека не остаются висеть.
trap 'stop_bg' EXIT
trap 'exit 130' INT TERM

# JVM Maven пишет вывод в UTF-8 (кириллица в логах цела и в Git Bash).
export MAVEN_OPTS="${MAVEN_OPTS:-} -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

printf '%sDentify verify (%s), логи — %s%s\n' "$C_CYAN" "$MODE_TEXT" "$(relative "$RUN_DIR")" "$C_RESET"

[[ $NEED_JDK == 1 ]] && run_step 'env: JDK 25' step_jdk
[[ $NEED_DOCKER == 1 ]] && run_step 'env: Docker' step_docker
if [[ $DO_ENV == 1 ]]; then
  if passed 'env: Docker'; then run_step 'env: dev-окружение (deploy/dev.sh check)' step_devenv
  else run_step 'env: dev-окружение (deploy/dev.sh check)' skip 'Docker не отвечает'; fi
fi
[[ $NEED_NODE == 1 ]] && run_step 'env: Node и pnpm' step_node

if [[ $DO_BACKEND == 1 ]]; then
  if passed 'env: JDK 25'; then run_step 'backend: ./mvnw -B verify' step_backend_verify
  else run_step 'backend: ./mvnw -B verify' skip 'нет JDK 25'; fi
  run_step 'api: спека = выгрузка сборки бэкенда' step_spec
fi

if [[ $DO_FRONTEND == 1 ]]; then
  if passed 'env: Node и pnpm'; then
    run_step 'frontend: pnpm install --frozen-lockfile' step_pnpm_install
    rest=$(cd "$FRONTEND" && node -p "require('./package.json').scripts.verify")
    while [[ -n $rest ]]; do
      part=${rest%%&&*}
      if [[ $rest == *'&&'* ]]; then rest=${rest#*&&}; else rest=""; fi
      part=$(printf '%s' "$part" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
      [[ -z $part ]] && continue
      if [[ $part =~ ^pnpm[[:space:]]+(run[[:space:]]+)?([^[:space:]]+)$ ]]; then
        name=${BASH_REMATCH[2]}
        run_step "frontend: pnpm $name" step_frontend_script "$name"
      else
        run_step "frontend: $part" fail 1 'шаг verify не вида «pnpm <скрипт>» — поправьте verify.sh'
      fi
    done
  else
    run_step 'frontend: pnpm verify' skip 'нет Node или pnpm'
  fi
fi

if [[ $E2E == 1 ]]; then
  if passed 'env: JDK 25'; then run_step 'e2e: jar бэкенда' step_stack_jar; else run_step 'e2e: jar бэкенда' skip 'нет JDK 25'; fi
  if passed 'env: Node и pnpm'; then run_step 'e2e: сборка фронта (dist)' step_stack_dist; else run_step 'e2e: сборка фронта (dist)' skip 'нет Node или pnpm'; fi
  run_step 'e2e: запуск стека' step_stack_start
  run_step 'e2e: Playwright stack' step_stack_playwright
  run_step 'e2e: остановка стека' step_stack_stop
fi

if [[ $SCAN == 1 ]]; then
  if passed 'env: JDK 25'; then
    run_step 'scan: NVD — обновление базы' step_nvd_update
    run_step 'scan: OWASP dependency-check (CVSS 7+)' step_dependency_check
  else
    run_step 'scan: OWASP dependency-check (CVSS 7+)' skip 'нет JDK 25'
  fi
  if passed 'env: Node и pnpm'; then run_step 'scan: pnpm audit --prod (high+)' step_pnpm_audit
  else run_step 'scan: pnpm audit --prod (high+)' skip 'нет Node или pnpm'; fi
  run_step 'scan: gitleaks — бинарь' step_gitleaks_binary
  run_step 'scan: gitleaks — рабочая копия apps/dentify' step_gitleaks_dir
  run_step 'scan: gitleaks — история git' step_gitleaks_history
fi

show_summary "$SECONDS" "$MODE_TEXT"
for status in "${STEP_STATUS[@]}"; do [[ $status == FAIL ]] && exit 1; done
exit 0
