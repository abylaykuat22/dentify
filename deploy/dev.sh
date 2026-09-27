#!/usr/bin/env bash
# Локальное окружение Dentify: PostgreSQL 18, Valkey, Mailpit, MinIO; Caddy — профилем proxy.
# Bash-вариант deploy/dev.ps1 (Linux, macOS, Git Bash). Команды:
#
#   up [--proxy] [--app]         поднять, дождаться healthy, создать приватный бакет MinIO;
#                                первый запуск создаёт deploy/.env из .env.example со случайными паролями;
#                                новые ключи шаблона любая команда дописывает в существующий .env;
#                                --app — плюс всё приложение в контейнерах (app и web), http://localhost:8088
#   down                         остановить и удалить контейнеры; данные в томах сохраняются
#   reset-db --force             удалить том БД dentify-pgdata и поднять чистую базу
#   logs [service] [--follow]    журналы
#   psql [--as owner|app|postgres] [-c "SQL"]   psql в базе dentify (по умолчанию owner)
#   ps                           состояние контейнеров
#   check                        проверить окружение (то же, что dev.ps1 check)
set -Eeuo pipefail

DEPLOY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Git Bash: docker.exe — Windows-программа. Свои пути отдаём ей как C:/..., а автоконверсию
# MSYS выключаем, чтобы она не портила аргументы вида /init/init.sh внутри контейнера.
if command -v cygpath >/dev/null 2>&1; then
  DEPLOY=$(cygpath -m "$DEPLOY")
  export MSYS_NO_PATHCONV=1
fi
COMPOSE_FILE="$DEPLOY/compose.dev.yml"
ENV_FILE="$DEPLOY/.env"
DB_VOLUME=dentify-pgdata
LONG_RUNNING=(postgres valkey mailpit minio)

red() { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[33m%s\033[0m\n' "$*"; }

compose() { docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; }

usage() { sed -n '2,14p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

# Случайный пароль из [A-Za-z0-9]: такие значения одинаково читают Compose и бэкенд (.env как .properties).
dev_secret() { head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | cut -c1-24; }

# Первый запуск: .env из шаблона, вместо каждого «…change-me» — свой случайный пароль этой машины.
# .env уже есть: ключи, которые появились в шаблоне позже (DENTIFY_DEV_OWNER_PASSWORD и т. п.), дописываются в конец
# вместе с комментарием над ними, «…change-me» — тоже случайным паролем. Существующие значения не меняются.
init_env() {
  local line
  if [[ ! -f "$ENV_FILE" ]]; then
    while IFS= read -r line || [[ -n $line ]]; do
      line=${line%$'\r'}
      if [[ $line =~ ^([A-Za-z_][A-Za-z0-9_]*)=.*change-me[[:space:]]*$ ]]; then
        printf '%s=%s\n' "${BASH_REMATCH[1]}" "$(dev_secret)"
      else
        printf '%s\n' "$line"
      fi
    done <"$DEPLOY/.env.example" >"$ENV_FILE"
    yellow "Создан deploy/.env из .env.example со случайными паролями (только для этой машины)."
    return 0
  fi
  local key value comments="" added="" keys=""
  while IFS= read -r line || [[ -n $line ]]; do
    line=${line%$'\r'}
    if [[ $line =~ ^[[:space:]]*# ]]; then
      comments+="$line"$'\n'
      continue
    fi
    if [[ $line =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
      key=${BASH_REMATCH[1]}
      value=${BASH_REMATCH[2]}
      if ! grep -Eq "^[[:space:]]*${key}[[:space:]]*=" "$ENV_FILE"; then
        if [[ $value =~ change-me[[:space:]]*$ ]]; then
          value=$(dev_secret)
        fi
        added+="$comments$key=$value"$'\n'
        keys+="${keys:+, }$key"
      fi
    fi
    comments=""
  done <"$DEPLOY/.env.example"
  if [[ -n $added ]]; then
    if [[ -s $ENV_FILE && -n $(tail -c1 "$ENV_FILE") ]]; then
      printf '\n' >>"$ENV_FILE"
    fi
    printf '\n%s' "$added" >>"$ENV_FILE"
    yellow "В deploy/.env дописаны новые ключи из .env.example: $keys."
  fi
  return 0
}

assert_docker() {
  docker info --format '{{.ServerVersion}}' >/dev/null 2>&1 || {
    red "Docker не отвечает: запустите Docker Desktop (или dockerd) и повторите."
    exit 1
  }
}

# Значение из deploy/.env без source (не исполняем файл как shell).
env_get() {
  local line
  line=$(grep -E "^[[:space:]]*$1[[:space:]]*=" "$ENV_FILE" | tail -n 1) || true
  line=${line#*=}
  line=${line%$'\r'}
  line=${line#\"}; line=${line%\"}; line=${line#\'}; line=${line%\'}
  printf '%s' "$line"
}

cmd_up() {
  local proxy=0 app=0 services=("${LONG_RUNNING[@]}") profile=() up=(up --detach --wait --wait-timeout 180)
  for a in "$@"; do
    case $a in --proxy) proxy=1 ;; --app) app=1 ;; esac
  done
  if ((proxy)); then profile=(--profile proxy); services+=(caddy); fi
  # --app: образы пересобираются из текущих исходников (слои с зависимостями — из кэша); ждём дольше — старт бэкенда
  # с миграциями.
  if ((app)); then services+=(app web); up=(up --build --detach --wait --wait-timeout 300); fi
  # minio-init — разовый сервис: --wait считает его выход (даже с кодом 0) ошибкой,
  # поэтому сначала ждём долгоживущие сервисы, затем прогоняем init отдельно.
  compose ${profile[@]+"${profile[@]}"} "${up[@]}" "${services[@]}"
  compose run --rm minio-init
  echo
  green "Окружение поднято:"
  echo "  PostgreSQL  127.0.0.1:5433   база dentify, роли dentify_owner (Flyway) и dentify_app"
  echo "  Valkey      127.0.0.1:6379"
  echo "  Mailpit     http://127.0.0.1:8025   (SMTP 127.0.0.1:1025)"
  echo "  MinIO       http://127.0.0.1:9001   (S3 API 127.0.0.1:9000, бакет $(env_get S3_BUCKET))"
  ((proxy)) && echo "  Caddy       https://localhost:8443  (/api -> :8080, остальное -> :5173)"
  ((app)) && echo "  Приложение  http://localhost:$(env_get DENTIFY_WEB_PORT | grep . || echo 8088)   (контейнеры app и web; вход owner@demo.dentify.kz, пароль — DENTIFY_DEV_OWNER_PASSWORD)"
  echo "Пароли и ключи — deploy/.env. Проверка: ./deploy/dev.sh check"
}

cmd_reset_db() {
  if [[ ${1:-} != --force ]]; then
    yellow "reset-db удалит том $DB_VOLUME со всеми данными локальной БД. Повторите с --force."
    exit 2
  fi
  compose rm --stop --force postgres
  if [[ -n $(docker volume ls --quiet --filter "name=^${DB_VOLUME}\$") ]]; then
    docker volume rm "$DB_VOLUME" >/dev/null
  fi
  yellow "Том $DB_VOLUME удалён, база создаётся заново."
  compose up --detach --wait --wait-timeout 180 postgres
}

cmd_logs() {
  local args=(--profile proxy logs --tail 200)
  for a in "$@"; do
    case $a in
      --follow | -f) args+=(--follow) ;;
      *) args+=("$a") ;;
    esac
  done
  compose "${args[@]}"
}

cmd_psql() {
  local role=dentify_owner sql=""
  while (($#)); do
    case $1 in
      --as)
        case ${2:-} in
          owner) role=dentify_owner ;; app) role=dentify_app ;; postgres) role=postgres ;;
          *) red "--as: owner | app | postgres"; exit 2 ;;
        esac
        shift 2 ;;
      -c) sql=${2:-}; shift 2 ;;
      *) red "неизвестный аргумент: $1"; exit 2 ;;
    esac
  done
  if [[ -n $sql ]]; then
    compose exec -T postgres psql -U "$role" -d dentify -v ON_ERROR_STOP=1 -c "$sql"
  else
    compose exec postgres psql -U "$role" -d dentify
  fi
}

# ---------- check ----------

FAILURES=0

# step "название" функция — печатает OK/FAIL и вывод функции.
step() {
  local name=$1 out
  shift
  if out=$("$@" 2>&1); then
    green "  OK    $name"
    [[ -n $out ]] && sed 's/^/        /' <<<"$out"
  else
    FAILURES=$((FAILURES + 1))
    red "  FAIL  $name: $(tail -n 3 <<<"$out" | tr '\n' ' ')"
  fi
  return 0
}

# SQL по TCP с паролем (host=postgres внутри сети compose → pg_hba scram-sha-256, не trust).
sql_as() {
  compose exec -T -e "PGPASSWORD=$2" -e PGOPTIONS=--client-min-messages=warning postgres \
    psql "host=postgres dbname=dentify user=$1" -v ON_ERROR_STOP=1 -X -q -tA -c "$3"
}

tcp_open() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

http_status() { curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$1"; }

check_containers() {
  local rows svc row
  rows=$(compose ps --format '{{.Service}}|{{.Status}}|{{.Ports}}')
  for svc in "${LONG_RUNNING[@]}"; do
    row=$(grep "^$svc|" <<<"$rows") || { echo "$svc не запущен"; return 1; }
    [[ $row == *"(healthy)"* ]] || { echo "$svc не healthy: $row"; return 1; }
  done
  if grep -Eq '0\.0\.0\.0:|\[::\]:|:::' <<<"$rows"; then echo "порт открыт наружу"; return 1; fi
  echo "$rows"
}

# Пароли в deploy/.env — случайные этой машины, а не публичные значения шаблона «…change-me».
check_env_secrets() {
  local keys
  keys=$(grep -E '^[[:space:]]*[A-Za-z_][A-Za-z0-9_]*[[:space:]]*=.*change-me' "$ENV_FILE" | cut -d= -f1 | tr -d ' ' | paste -sd, -) || true
  if [[ -n $keys ]]; then
    echo "значения шаблона у $keys: down, удалить deploy/.env и тома dentify-pgdata, dentify-valkey-data, dentify-minio-data, затем up"
    return 1
  fi
  echo "значений …change-me нет"
}

check_pg_port() { tcp_open 5433 || { echo "127.0.0.1:5433 закрыт"; return 1; }; }

check_owner() {
  sql_as dentify_owner "$(env_get DENTIFY_OWNER_PASSWORD)" \
    "select current_user || ' superuser=' || rolsuper || ' bypassrls=' || rolbypassrls || ' search_path=' || current_setting('search_path') || ' | ' || version() from pg_roles where rolname = current_user"
}

check_app() {
  local r
  r=$(sql_as dentify_app "$(env_get DENTIFY_APP_PASSWORD)" \
    "select current_user || ' superuser=' || rolsuper || ' bypassrls=' || rolbypassrls || ' schema_owner=' || (select nspowner::regrole::text from pg_namespace where nspname = 'dentify') from pg_roles where rolname = current_user")
  [[ $r == *"superuser=false bypassrls=false schema_owner=dentify_owner"* ]] || { echo "неожиданно: $r"; return 1; }
  echo "$r"
}

check_extensions() {
  local r
  r=$(sql_as dentify_app "$(env_get DENTIFY_APP_PASSWORD)" \
    "select string_agg(extname || ' ' || extversion, ', ' order by extname) || ' | uuidv7 ' || uuidv7() from pg_extension where extname in ('btree_gist', 'pg_trgm')")
  [[ $r == *btree_gist*pg_trgm* ]] || { echo "нет расширений: $r"; return 1; }
  echo "$r"
}

check_grants() {
  local owner app r ddl rc=0
  owner=$(env_get DENTIFY_OWNER_PASSWORD)
  app=$(env_get DENTIFY_APP_PASSWORD)
  sql_as dentify_owner "$owner" 'drop table if exists zz_grant_probe; create table zz_grant_probe (id uuid primary key default uuidv7(), note text not null)' >/dev/null
  if r=$(sql_as dentify_app "$app" "insert into zz_grant_probe (note) values ('probe'); update zz_grant_probe set note = 'probe2'; select 'select/insert/update: ' || count(*) from zz_grant_probe; delete from zz_grant_probe" 2>&1); then
    if ddl=$(sql_as dentify_app "$app" 'create table zz_forbidden (id int)' 2>&1); then
      echo "dentify_app смог создать таблицу"; rc=1
    elif [[ $ddl != *"permission denied"* ]]; then
      echo "$ddl"; rc=1
    else
      echo "$r"; echo "delete: ok; create table от dentify_app: permission denied (как и должно быть)"
    fi
  else
    echo "$r"; rc=1
  fi
  sql_as dentify_owner "$owner" 'drop table if exists zz_grant_probe' >/dev/null
  return $rc
}

# PING в Valkey через опубликованный порт хоста (RESP по TCP), с паролем.
check_valkey() {
  local pw auth pong
  pw=$(env_get VALKEY_PASSWORD)
  exec 3<>/dev/tcp/127.0.0.1/6379 || { echo "127.0.0.1:6379 закрыт"; return 1; }
  printf '*2\r\n$4\r\nAUTH\r\n$%d\r\n%s\r\n*1\r\n$4\r\nPING\r\n' "${#pw}" "$pw" >&3
  IFS= read -r -t 3 auth <&3 || true
  IFS= read -r -t 3 pong <&3 || true
  exec 3>&-
  auth=${auth%$'\r'}; pong=${pong%$'\r'}
  [[ $auth == +OK && $pong == +PONG ]] || { echo "ответ Valkey: $auth / $pong"; return 1; }
  echo "127.0.0.1:6379  AUTH -> $auth, PING -> $pong"
}

check_mailpit() {
  local ui ready
  ui=$(http_status http://127.0.0.1:8025/)
  ready=$(http_status http://127.0.0.1:8025/readyz)
  [[ $ui == 200 && $ready == 200 ]] || { echo "UI $ui, readyz $ready"; return 1; }
  tcp_open 1025 || { echo "SMTP 1025 закрыт"; return 1; }
  echo "GET / -> $ui, GET /readyz -> $ready, SMTP 1025 открыт"
}

check_minio_bucket() {
  local bucket root ready anon http
  bucket=$(env_get S3_BUCKET)
  root="http://$(env_get MINIO_ROOT_USER):$(env_get MINIO_ROOT_PASSWORD)@127.0.0.1:9000"
  ready=$(http_status http://127.0.0.1:9000/minio/health/ready)
  anon=$(compose exec -T -e "MC_HOST_t=$root" minio mc anonymous get "t/$bucket")
  [[ $anon == *private* ]] || { echo "анонимный доступ: $anon"; return 1; }
  http=$(http_status "http://127.0.0.1:9000/$bucket/")
  [[ $http == 403 ]] || { echo "анонимный GET бакета вернул $http, ждали 403"; return 1; }
  echo "health/ready -> $ready; $anon; анонимный GET -> $http"
}

check_minio_app() {
  local bucket app out
  bucket=$(env_get S3_BUCKET)
  app="http://$(env_get S3_ACCESS_KEY):$(env_get S3_SECRET_KEY)@127.0.0.1:9000"
  compose exec -T -e "MC_HOST_t=$app" minio sh -c \
    "echo probe | mc pipe t/$bucket/zz-probe.txt >/dev/null && mc cat t/$bucket/zz-probe.txt >/dev/null && mc rm t/$bucket/zz-probe.txt >/dev/null"
  if out=$(compose exec -T -e "MC_HOST_t=$app" minio mc mb t/zz-forbidden-bucket 2>&1); then
    echo "ключ приложения создал бакет"; return 1
  fi
  [[ $out == *"Access Denied"* || $out == *AccessDenied* || $out == *"not allowed"* ]] || { echo "$out"; return 1; }
  echo "put/get/delete в $bucket — ok; создание другого бакета — Access Denied"
}

cmd_check() {
  local bucket
  bucket=$(env_get S3_BUCKET)
  printf '\033[36m%s\033[0m\n' "Проверка локального окружения Dentify"
  step "deploy/.env: пароли этой машины, а не значения шаблона" check_env_secrets
  step "контейнеры healthy, порты только на 127.0.0.1" check_containers
  step "PostgreSQL: порт 127.0.0.1:5433 доступен с хоста" check_pg_port
  step "PostgreSQL: вход dentify_owner по паролю" check_owner
  step "PostgreSQL: вход dentify_app по паролю, NOBYPASSRLS, не владелец схемы" check_app
  step "PostgreSQL: расширения btree_gist и pg_trgm, uuidv7()" check_extensions
  step "PostgreSQL: таблица dentify_owner доступна dentify_app на DML, DDL запрещён" check_grants
  step "Valkey: AUTH + PING через 127.0.0.1:6379" check_valkey
  step "Mailpit: UI http://127.0.0.1:8025 и SMTP 1025" check_mailpit
  step "MinIO: бакет $bucket существует и приватный" check_minio_bucket
  step "MinIO: ключ приложения пишет и читает только в $bucket" check_minio_app
  echo
  if ((FAILURES > 0)); then
    red "Проблем: $FAILURES. Журналы: ./deploy/dev.sh logs <сервис>"
    exit 1
  fi
  green "Все проверки пройдены."
}

# ---------- main ----------

command=${1:-help}
(($#)) && shift
case $command in
  help | -h | --help) usage; exit 0 ;;
  up | down | reset-db | logs | psql | ps | check) ;;
  *) red "неизвестная команда: $command"; usage; exit 2 ;;
esac

init_env # compose требует переменные из .env для любой команды, даже down
assert_docker

case $command in
  up) cmd_up "$@" ;;
  down) compose --profile proxy down --remove-orphans ;;
  reset-db) cmd_reset_db "$@" ;;
  logs) cmd_logs "$@" ;;
  psql) cmd_psql "$@" ;;
  ps) compose --profile proxy ps --all ;;
  check) cmd_check ;;
esac
