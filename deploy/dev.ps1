<#
.SYNOPSIS
  Локальное окружение Dentify: PostgreSQL 18, Valkey, Mailpit, MinIO; Caddy — профилем proxy; с -App — всё приложение
  в контейнерах.

.DESCRIPTION
  up        поднять и дождаться healthy, создать приватный бакет MinIO. Первый запуск создаёт deploy/.env
            со случайными паролями; новые ключи шаблона любая команда дописывает в существующий .env.
            -Proxy — вместе с Caddy (https://localhost:8443).
            -App — плюс всё приложение в контейнерах: собрать образы бэкенда и фронта (сервисы app и web) и открыть
            http://localhost:8088 (порт — DENTIFY_WEB_PORT в deploy/.env). Первая сборка — несколько минут.
  down      остановить и удалить контейнеры; данные в томах сохраняются.
  reset-db  удалить том БД dentify-pgdata и поднять чистую базу. Только с -Force.
  logs      журналы всех сервисов или одного (logs postgres); -Follow — следить.
  psql      psql в базе dentify: -As owner|app|postgres (по умолчанию owner); -Sql "..." — выполнить и выйти.
  ps        состояние контейнеров.
  check     проверить окружение: порты, вход ролей по паролю, расширения, гранты, Valkey, Mailpit, MinIO.

.EXAMPLE
  ./deploy/dev.ps1 up
.EXAMPLE
  ./deploy/dev.ps1 up -App
.EXAMPLE
  ./deploy/dev.ps1 check
.EXAMPLE
  ./deploy/dev.ps1 psql -As app -Sql "select current_user"
.EXAMPLE
  ./deploy/dev.ps1 reset-db -Force
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('up', 'down', 'reset-db', 'logs', 'psql', 'ps', 'check', 'help')]
    [string]$Command = 'help',

    # сервис для logs
    [Parameter(Position = 1)]
    [string]$Service,

    [ValidateSet('owner', 'app', 'postgres')]
    [string]$As = 'owner',

    [string]$Sql,
    [switch]$Proxy,
    [switch]$App,
    [switch]$Follow,
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$Deploy = $PSScriptRoot
$ComposeFile = Join-Path $Deploy 'compose.dev.yml'
$EnvFile = Join-Path $Deploy '.env'
$EnvExample = Join-Path $Deploy '.env.example'
$DbVolume = 'dentify-pgdata'
$LongRunning = @('postgres', 'valkey', 'mailpit', 'minio')
$Roles = @{ owner = 'dentify_owner'; app = 'dentify_app'; postgres = 'postgres' }

# В Windows PowerShell 5.1 stderr нативной команды, если его перенаправить (2>&1, *> файл, Tee),
# при ErrorActionPreference=Stop превращается в исключение. docker пишет прогресс в stderr,
# поэтому вокруг нативных вызовов — Continue, а успех определяем по коду выхода.
function Invoke-Compose([string[]]$Arguments) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & docker compose --env-file $EnvFile -f $ComposeFile @Arguments }
    finally { $ErrorActionPreference = $old }
    if ($LASTEXITCODE -ne 0) { throw "docker compose $($Arguments -join ' '): код $LASTEXITCODE" }
}

function Invoke-Captured([string]$Exe, [string[]]$Arguments) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $out = @(& $Exe @Arguments 2>&1 | ForEach-Object { "$_" }) }
    finally { $ErrorActionPreference = $old }
    if ($LASTEXITCODE -ne 0) { throw (($out | Select-Object -Last 3) -join ' | ') }
    $out
}

function Invoke-ComposeCaptured([string[]]$Arguments) {
    Invoke-Captured 'docker' (@('compose', '--env-file', $EnvFile, '-f', $ComposeFile) + $Arguments)
}

function Assert-Docker {
    try { $null = Invoke-Captured 'docker' @('info', '--format', '{{.ServerVersion}}') }
    catch { throw 'Docker не отвечает: запустите Docker Desktop и повторите.' }
}

# Случайный пароль из [A-Za-z0-9]: такие значения одинаково читают Compose и бэкенд (.env как .properties).
function New-DevSecret {
    $bytes = New-Object byte[] 24
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    ([Convert]::ToBase64String($bytes) -replace '[^A-Za-z0-9]', '').Substring(0, 24)
}

# Первый запуск: .env из шаблона, вместо каждого «…change-me» — свой случайный пароль этой машины.
# .env уже есть: ключи, которые появились в шаблоне позже (DENTIFY_DEV_OWNER_PASSWORD и т. п.), дописываются в конец
# вместе с комментарием над ними, «…change-me» — тоже случайным паролем. Существующие значения не меняются.
function Initialize-EnvFile {
    if (-not (Test-Path -LiteralPath $EnvFile)) {
        $lines = foreach ($line in Get-Content -LiteralPath $EnvExample -Encoding UTF8) {
            if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=.*change-me\s*$') { "$($Matches[1])=$(New-DevSecret)" } else { $line }
        }
        [IO.File]::WriteAllText($EnvFile, (($lines -join "`n") + "`n"), (New-Object System.Text.UTF8Encoding $false))
        Write-Host 'Создан deploy/.env из .env.example со случайными паролями (только для этой машины).' -ForegroundColor Yellow
        return
    }
    $present = Read-DotEnv
    $added = @()
    $comments = @()
    foreach ($line in Get-Content -LiteralPath $EnvExample -Encoding UTF8) {
        if ($line -match '^\s*#') { $comments += $line; continue }
        if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$' -and -not $present.ContainsKey($Matches[1])) {
            $key = $Matches[1]
            $value = if ($Matches[2] -match 'change-me\s*$') { New-DevSecret } else { $Matches[2] }
            $added += $comments + @("$key=$value")
        }
        $comments = @()
    }
    if ($added.Count -gt 0) {
        $text = [IO.File]::ReadAllText($EnvFile)
        if ($text.Length -gt 0 -and -not $text.EndsWith("`n")) { $text += "`n" }
        $text += "`n" + ($added -join "`n") + "`n"
        [IO.File]::WriteAllText($EnvFile, $text, (New-Object System.Text.UTF8Encoding $false))
        $keys = @($added | Where-Object { $_ -match '^[A-Za-z_]' } | ForEach-Object { ($_ -split '=', 2)[0] })
        Write-Host "В deploy/.env дописаны новые ключи из .env.example: $($keys -join ', ')." -ForegroundColor Yellow
    }
}

function Read-DotEnv {
    if (-not (Test-Path -LiteralPath $EnvFile)) { throw 'Нет deploy/.env: сначала ./deploy/dev.ps1 up' }
    $vars = @{}
    foreach ($line in Get-Content -LiteralPath $EnvFile -Encoding UTF8) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            $vars[$Matches[1]] = $Matches[2].Trim([char[]]@('"', "'"))
        }
    }
    $vars
}

function Show-Endpoints([hashtable]$EnvVars) {
    Write-Host ''
    Write-Host 'Окружение поднято:' -ForegroundColor Green
    Write-Host '  PostgreSQL  127.0.0.1:5433   база dentify, роли dentify_owner (Flyway) и dentify_app'
    Write-Host '  Valkey      127.0.0.1:6379'
    Write-Host '  Mailpit     http://127.0.0.1:8025   (SMTP 127.0.0.1:1025)'
    Write-Host "  MinIO       http://127.0.0.1:9001   (S3 API 127.0.0.1:9000, бакет $($EnvVars['S3_BUCKET']))"
    if ($Proxy) { Write-Host '  Caddy       https://localhost:8443  (/api -> :8080, остальное -> :5173)' }
    if ($App) {
        $port = if ($EnvVars['DENTIFY_WEB_PORT']) { $EnvVars['DENTIFY_WEB_PORT'] } else { '8088' }
        Write-Host "  Приложение  http://localhost:$port   (контейнеры app и web; вход owner@demo.dentify.kz, пароль — DENTIFY_DEV_OWNER_PASSWORD)"
    }
    Write-Host 'Пароли и ключи — deploy/.env. Проверка: ./deploy/dev.ps1 check'
}

function Invoke-Up {
    $services = $LongRunning
    $profileArgs = @()
    if ($Proxy) { $profileArgs = @('--profile', 'proxy'); $services += 'caddy' }
    $upArgs = @('up', '--detach', '--wait', '--wait-timeout', '180')
    # -App: образы пересобираются из текущих исходников (слои с зависимостями — из кэша); ждём дольше — старт бэкенда
    # с миграциями.
    if ($App) { $services += @('app', 'web'); $upArgs = @('up', '--build', '--detach', '--wait', '--wait-timeout', '300') }
    # minio-init — разовый сервис: --wait считает его выход (даже с кодом 0) ошибкой,
    # поэтому сначала ждём долгоживущие сервисы, затем прогоняем init отдельно.
    Invoke-Compose ($profileArgs + $upArgs + $services)
    Invoke-Compose @('run', '--rm', 'minio-init')
    Show-Endpoints (Read-DotEnv)
}

function Invoke-ResetDb {
    if (-not $Force) {
        Write-Host "reset-db удалит том $DbVolume со всеми данными локальной БД. Повторите с -Force." -ForegroundColor Yellow
        exit 2
    }
    Invoke-Compose @('rm', '--stop', '--force', 'postgres')
    $volumes = Invoke-Captured 'docker' @('volume', 'ls', '--quiet', '--filter', "name=^$DbVolume$")
    if ($volumes) { $null = Invoke-Captured 'docker' @('volume', 'rm', $DbVolume) }
    Write-Host "Том $DbVolume удалён, база создаётся заново." -ForegroundColor Yellow
    Invoke-Compose @('up', '--detach', '--wait', '--wait-timeout', '180', 'postgres')
}

function Invoke-Psql {
    $arguments = @('exec')
    if ($Sql) { $arguments += '-T' }
    $arguments += @('postgres', 'psql', '-U', $Roles[$As], '-d', 'dentify')
    if ($Sql) { $arguments += @('-v', 'ON_ERROR_STOP=1', '-c', $Sql) }
    Invoke-Compose $arguments
}

# ---------- check ----------

$script:Failures = 0

function Test-Step([string]$Name, [scriptblock]$Body) {
    try {
        $detail = @(& $Body)
        Write-Host ('  OK    ' + $Name) -ForegroundColor Green
        foreach ($d in $detail) { if ("$d") { Write-Host ('        ' + $d) } }
    }
    catch {
        $script:Failures++
        Write-Host ('  FAIL  ' + $Name + ': ' + $_.Exception.Message) -ForegroundColor Red
    }
}

function Test-TcpPort([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $client.Connect('127.0.0.1', $Port) } finally { $client.Close() }
}

function Get-HttpStatus([string]$Url) {
    try { return [int](Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 -MaximumRedirection 0).StatusCode }
    catch {
        $response = $_.Exception.Response
        if ($response) { return [int]$response.StatusCode }
        throw
    }
}

function ConvertTo-Resp([string[]]$Parts) {
    $crlf = "`r`n"
    $s = '*' + $Parts.Count + $crlf
    foreach ($p in $Parts) { $s += '$' + [Text.Encoding]::UTF8.GetByteCount($p) + $crlf + $p + $crlf }
    $s
}

# PING в Valkey через опубликованный порт хоста (RESP по TCP), с паролем.
function Test-ValkeyPing([string]$Password) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $client.Connect('127.0.0.1', 6379)
        $stream = $client.GetStream()
        $stream.ReadTimeout = 3000
        $bytes = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Resp @('AUTH', $Password)) + (ConvertTo-Resp @('PING')))
        $stream.Write($bytes, 0, $bytes.Length)
        $reader = New-Object System.IO.StreamReader($stream)
        $auth = $reader.ReadLine()
        $pong = $reader.ReadLine()
        if ($auth -ne '+OK' -or $pong -ne '+PONG') { throw "ответ Valkey: $auth / $pong" }
        "127.0.0.1:6379  AUTH -> $auth, PING -> $pong"
    }
    finally { $client.Close() }
}

# SQL по TCP с паролем (host=postgres внутри сети compose → pg_hba scram-sha-256, не trust).
function Invoke-Sql([string]$Role, [string]$Password, [string]$Query) {
    Invoke-ComposeCaptured @('exec', '-T', '-e', "PGPASSWORD=$Password", '-e', 'PGOPTIONS=--client-min-messages=warning', 'postgres',
        'psql', "host=postgres dbname=dentify user=$Role", '-v', 'ON_ERROR_STOP=1', '-X', '-q', '-tA', '-c', $Query)
}

function Invoke-Mc([string]$HostUrl, [string[]]$Arguments) {
    Invoke-ComposeCaptured (@('exec', '-T', '-e', "MC_HOST_t=$HostUrl", 'minio', 'mc') + $Arguments)
}

function Invoke-Check {
    $e = Read-DotEnv
    $owner = $e['DENTIFY_OWNER_PASSWORD']
    $app = $e['DENTIFY_APP_PASSWORD']
    $bucket = $e['S3_BUCKET']
    Write-Host 'Проверка локального окружения Dentify' -ForegroundColor Cyan

    Test-Step 'deploy/.env: пароли этой машины, а не значения шаблона' {
        $template = @($e.Keys | Where-Object { $e[$_] -match 'change-me' } | Sort-Object)
        if ($template) {
            throw ("значения шаблона у $($template -join ', '): down, удалить deploy/.env и тома " +
                'dentify-pgdata, dentify-valkey-data, dentify-minio-data, затем up')
        }
        'значений …change-me нет'
    }

    Test-Step 'контейнеры healthy, порты только на 127.0.0.1' {
        $rows = Invoke-ComposeCaptured @('ps', '--format', '{{.Service}}|{{.Status}}|{{.Ports}}')
        foreach ($svc in $LongRunning) {
            $row = $rows | Where-Object { $_ -like "$svc|*" }
            if (-not $row) { throw "$svc не запущен" }
            if ($row -notlike '*(healthy)*') { throw "$svc не healthy: $row" }
        }
        $open = $rows | Where-Object { $_ -match '0\.0\.0\.0:|\[::\]:|:::' }
        if ($open) { throw "порт открыт наружу: $open" }
        $rows
    }

    Test-Step 'PostgreSQL: порт 127.0.0.1:5433 доступен с хоста' { Test-TcpPort 5433 }

    Test-Step 'PostgreSQL: вход dentify_owner по паролю' {
        Invoke-Sql 'dentify_owner' $owner "select current_user || ' superuser=' || rolsuper || ' bypassrls=' || rolbypassrls || ' search_path=' || current_setting('search_path') || ' | ' || version() from pg_roles where rolname = current_user"
    }

    Test-Step 'PostgreSQL: вход dentify_app по паролю, NOBYPASSRLS, не владелец схемы' {
        $r = Invoke-Sql 'dentify_app' $app "select current_user || ' superuser=' || rolsuper || ' bypassrls=' || rolbypassrls || ' schema_owner=' || (select nspowner::regrole::text from pg_namespace where nspname = 'dentify') from pg_roles where rolname = current_user"
        if ("$r" -notmatch 'superuser=false bypassrls=false schema_owner=dentify_owner') { throw "неожиданно: $r" }
        $r
    }

    Test-Step 'PostgreSQL: расширения btree_gist и pg_trgm, uuidv7()' {
        $r = Invoke-Sql 'dentify_app' $app "select string_agg(extname || ' ' || extversion, ', ' order by extname) || ' | uuidv7 ' || uuidv7() from pg_extension where extname in ('btree_gist', 'pg_trgm')"
        if ("$r" -notmatch 'btree_gist .*pg_trgm ') { throw "нет расширений: $r" }
        $r
    }

    Test-Step 'PostgreSQL: таблица dentify_owner доступна dentify_app на DML, DDL запрещён' {
        $null = Invoke-Sql 'dentify_owner' $owner 'drop table if exists zz_grant_probe; create table zz_grant_probe (id uuid primary key default uuidv7(), note text not null)'
        try {
            $r = Invoke-Sql 'dentify_app' $app "insert into zz_grant_probe (note) values ('probe'); update zz_grant_probe set note = 'probe2'; select 'select/insert/update: ' || count(*) from zz_grant_probe; delete from zz_grant_probe"
            $ddl = $null
            try { $null = Invoke-Sql 'dentify_app' $app 'create table zz_forbidden (id int)'; $ddl = 'dentify_app смог создать таблицу' }
            catch { if ("$_" -notmatch 'permission denied') { throw } }
            if ($ddl) { throw $ddl }
            @($r) + 'delete: ok; create table от dentify_app: permission denied (как и должно быть)'
        }
        finally { $null = Invoke-Sql 'dentify_owner' $owner 'drop table if exists zz_grant_probe' }
    }

    Test-Step 'Valkey: AUTH + PING через 127.0.0.1:6379' { Test-ValkeyPing $e['VALKEY_PASSWORD'] }

    Test-Step 'Mailpit: UI http://127.0.0.1:8025 и SMTP 1025' {
        $ui = Get-HttpStatus 'http://127.0.0.1:8025/'
        $ready = Get-HttpStatus 'http://127.0.0.1:8025/readyz'
        if ($ui -ne 200 -or $ready -ne 200) { throw "UI $ui, readyz $ready" }
        Test-TcpPort 1025
        "GET / -> $ui, GET /readyz -> $ready, SMTP 1025 открыт"
    }

    Test-Step "MinIO: бакет $bucket существует и приватный" {
        $root = "http://$($e['MINIO_ROOT_USER']):$($e['MINIO_ROOT_PASSWORD'])@127.0.0.1:9000"
        $ready = Get-HttpStatus 'http://127.0.0.1:9000/minio/health/ready'
        $anon = Invoke-Mc $root @('anonymous', 'get', "t/$bucket")
        if ("$anon" -notmatch 'private') { throw "анонимный доступ: $anon" }
        $http = Get-HttpStatus "http://127.0.0.1:9000/$bucket/"
        if ($http -ne 403) { throw "анонимный GET бакета вернул $http, ждали 403" }
        "health/ready -> $ready; $anon; анонимный GET -> $http"
    }

    Test-Step "MinIO: ключ приложения пишет и читает только в $bucket" {
        $appUrl = "http://$($e['S3_ACCESS_KEY']):$($e['S3_SECRET_KEY'])@127.0.0.1:9000"
        $null = Invoke-ComposeCaptured @('exec', '-T', '-e', "MC_HOST_t=$appUrl", 'minio', 'sh', '-c',
            "echo probe | mc pipe t/$bucket/zz-probe.txt >/dev/null && mc cat t/$bucket/zz-probe.txt >/dev/null && mc rm t/$bucket/zz-probe.txt >/dev/null")
        $denied = $null
        try { $null = Invoke-Mc $appUrl @('mb', 't/zz-forbidden-bucket'); $denied = 'ключ приложения создал бакет' }
        catch { if ("$_" -notmatch 'Access Denied|AccessDenied|not allowed') { throw } }
        if ($denied) { throw $denied }
        "put/get/delete в $bucket — ok; создание другого бакета — Access Denied"
    }

    Write-Host ''
    if ($script:Failures -gt 0) {
        Write-Host "Проблем: $($script:Failures). Журналы: ./deploy/dev.ps1 logs <сервис>" -ForegroundColor Red
        exit 1
    }
    Write-Host 'Все проверки пройдены.' -ForegroundColor Green
}

# ---------- main ----------

if ($Command -eq 'help') { Get-Help $PSCommandPath -Detailed; exit 0 }
Initialize-EnvFile   # compose требует переменные из .env для любой команды, даже down
Assert-Docker

switch ($Command) {
    'up'       { Invoke-Up }
    'down'     { Invoke-Compose @('--profile', 'proxy', 'down', '--remove-orphans') }
    'reset-db' { Invoke-ResetDb }
    'logs' {
        $arguments = @('--profile', 'proxy', 'logs', '--tail', '200')
        if ($Follow) { $arguments += '--follow' }
        if ($Service) { $arguments += $Service }
        Invoke-Compose $arguments
    }
    'psql'     { Invoke-Psql }
    'ps'       { Invoke-Compose @('--profile', 'proxy', 'ps', '--all') }
    'check'    { Invoke-Check }
}
exit 0   # иначе код выхода — от последней нативной команды (check намеренно роняет пару)
