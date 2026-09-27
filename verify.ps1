<#
.SYNOPSIS
  Проверка Dentify перед коммитом одной командой (T-PLT-15): окружение, бэкенд, контракт API, фронтенд;
  -E2E — ещё e2e на живом стеке, -Scan — сканеры уязвимостей и секретов.

.DESCRIPTION
  Без ключей:
    env       JDK 25 (JAVA_HOME или ~/.jdks/openjdk-25.0.2), Docker, dev-окружение (deploy/dev.ps1 check; не прошло —
              dev.ps1 up и снова check), Node 23+ и pnpm 10+.
    backend   ./mvnw -B verify: JUnit, Testcontainers, ApplicationModules.verify, ArchUnit, тесты изоляции.
    api       спека api/openapi.json совпадает с той, что выгрузила сборка бэкенда (OpenApiContractTests);
              клиент orval совпадает со спекой — шаг frontend api:check.
    frontend  pnpm install --frozen-lockfile, затем шаги pnpm verify по одному (список — из package.json): typecheck,
              lint, i18n:check, api:check, test, build, size (бюджет), e2e на моках.
  -E2E     плюс e2e на живом стеке: jar бэкенда с профилем dev на свободном порту, vite preview с прокси /api на него,
           Playwright-проекты stack-<вьюпорт> (--project=stack*: stack-desktop 1440×900 и stack-mobile 390×844 добавит
           T-PLT-16; нет ни одного — шаг пропускается с сообщением). Трейсы, скриншоты и отчёт —
           deploy/test-results/verify-<время>/stack/. Бэкенд и фронт останавливаются всегда, даже при ошибке или Ctrl+C.
  -Scan    только сканеры — отдельная команда, нужен интернет: OWASP dependency-check (CVSS 7 и выше), pnpm audit --prod
           (high и выше), gitleaks по apps/dentify (рабочая копия и история git). Вместе с -E2E — всё сразу.
  -Strict  недоступный сканер (нет сети, NVD не отвечает, gitleaks не скачался) — красный шаг, а не предупреждение.
  -Skip    пропустить группы: env (dev-окружение), backend, frontend. Например, -E2E -Skip backend,frontend — только
           живой стек.

  Итог — сводка по шагам со временем и кодом выхода; код выхода 1, если красный хоть один шаг. Полные логи шагов —
  deploy/test-results/verify-<время>/ (папки старше 7 дней удаляются при запуске). Подробности — README.md,
  «Проверка перед коммитом».

.EXAMPLE
  ./verify.ps1
.EXAMPLE
  ./verify.ps1 -E2E
.EXAMPLE
  ./verify.ps1 -Scan -Strict
#>
[CmdletBinding()]
param(
    [switch]$E2E,
    [switch]$Scan,
    [switch]$Strict,
    # env, backend, frontend — через запятую (из powershell -File список приходит одной строкой)
    [string[]]$Skip = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$Root = $PSScriptRoot
$Backend = Join-Path $Root 'backend'
$Frontend = Join-Path $Root 'frontend'
$Deploy = Join-Path $Root 'deploy'
$SpecFile = Join-Path $Root 'api\openapi.json'
$Results = Join-Path $Deploy 'test-results'
$RunDir = Join-Path $Results ('verify-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$Mvnw = Join-Path $Backend 'mvnw.cmd'

$Jdk25 = Join-Path $HOME '.jdks\openjdk-25.0.2'
$NodeMajor = 23
$PnpmMajor = 10
# Официальные JSON-фиды NVD 2.0: без ключа API (у API без ключа жёсткий лимит частоты). Другой адрес (зеркало) —
# DENTIFY_NVD_DATAFEED; ключ API — NVD_API_KEY, тогда фиды не нужны.
$NvdFeed = 'https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz'
# gitleaks: закреплённая версия и SHA-256 архивов релиза (https://github.com/gitleaks/gitleaks/releases).
$GitleaksVersion = '8.30.1'
$GitleaksSha256 = @{
    'windows_x64'   = 'd29144deff3a68aa93ced33dddf84b7fdc26070add4aa0f4513094c8332afc4e'
    'windows_arm64' = 'b95f5e4f5c425cedca7ee203d9afd29597e692c4924a12ed42f970537c72cc0f'
}
# Куда скачивается gitleaks, если его нет в PATH (другая папка — DENTIFY_TOOLS_DIR).
$ToolsDir = if ($env:DENTIFY_TOOLS_DIR) { $env:DENTIFY_TOOLS_DIR } else { Join-Path $HOME '.dentify-tools' }

# Кодировка консоли — до первого вывода: Windows PowerShell 5.1 создаёт поток вывода при первой записи и дальше
# кодировку не меняет. Поэтому кириллица цела и при запуске из Git Bash или с перенаправлением вывода (UTF-8), а вывод
# Maven и Node читаем как UTF-8 (JVM Maven пишет в UTF-8 по MAVEN_OPTS). В конце прежняя кодировка возвращается —
# ./verify.ps1 в открытой консоли её не меняет.
$savedOutputEncoding = $null
try { $savedOutputEncoding = [Console]::OutputEncoding; [Console]::OutputEncoding = New-Object Text.UTF8Encoding $false } catch { }
function Restore-ConsoleEncoding { if ($savedOutputEncoding) { try { [Console]::OutputEncoding = $savedOutputEncoding } catch { } } }

$Skip = @($Skip | ForEach-Object { $_ -split '[,\s]+' } | Where-Object { $_ })
$unknownSkip = @($Skip | Where-Object { @('env', 'backend', 'frontend') -notcontains $_ })
if ($unknownSkip) {
    Write-Host "-Skip: неизвестные группы $($unknownSkip -join ', '); можно env, backend, frontend" -ForegroundColor Red
    Restore-ConsoleEncoding
    exit 2
}

$RunBase = (-not $Scan) -or $E2E
$DoBackend = $RunBase -and ($Skip -notcontains 'backend')
$DoFrontend = $RunBase -and ($Skip -notcontains 'frontend')
$DoEnv = ($RunBase -or $E2E) -and ($Skip -notcontains 'env')
$NeedJdk = $DoBackend -or $E2E -or $Scan
$NeedDocker = $DoBackend -or $DoEnv -or $E2E
$NeedNode = $DoFrontend -or $E2E -or $Scan

$script:Steps = New-Object System.Collections.ArrayList
$script:Background = New-Object System.Collections.ArrayList
$script:OriginalEnv = @{}
$script:CurrentLog = Join-Path $RunDir 'verify.log'
# Результаты шагов для следующих шагов (StrictMode не даёт читать незаданные переменные).
$script:Jar = $null
$script:ApiPort = $null
$script:WebPort = $null
$script:Gitleaks = $null

# ---------- вывод и шаги ----------

function New-Result([string]$Status, [int]$Code, [string]$Note) { @{ Status = $Status; Code = $Code; Note = $Note } }
function Ok([string]$Note = '') { New-Result 'OK' 0 $Note }
function Fail([int]$Code, [string]$Note) { New-Result 'FAIL' $Code $Note }
function Warn([string]$Note) { New-Result 'WARN' 0 $Note }
function Skipped([string]$Note) { New-Result 'SKIP' 0 $Note }
# Сканер недоступен (сеть, NVD, скачивание): без -Strict — предупреждение, с -Strict — красный шаг.
function Unavailable([string]$Note) { if ($Strict) { New-Result 'FAIL' 1 $Note } else { New-Result 'WARN' 0 "$Note (красным — с -Strict)" } }

function Format-Duration([TimeSpan]$Span) { '{0}:{1:00}' -f [int][Math]::Floor($Span.TotalMinutes), $Span.Seconds }

function Get-StatusColor([string]$Status) {
    switch ($Status) { 'OK' { 'Green' } 'FAIL' { 'Red' } 'WARN' { 'Yellow' } default { 'DarkGray' } }
}

# Шаг: тело возвращает Ok/Fail/Warn/Skipped (или ничего — это OK); исключение — красный шаг с его текстом.
function Invoke-Step([string]$Name, [scriptblock]$Body) {
    $number = $script:Steps.Count + 1
    Write-Host ''
    Write-Host ("==> [{0}] {1}" -f $number, $Name) -ForegroundColor Cyan
    $script:CurrentLog = Join-Path $RunDir (('{0:00}-' -f $number) + (($Name -replace '[^\w.-]+', '-').Trim('-')) + '.log')
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $result = $null
    try {
        $output = @(& $Body)
        $result = $output | Where-Object { $_ -is [hashtable] -and $_.ContainsKey('Status') } | Select-Object -Last 1
        if (-not $result) { $result = Ok }
    }
    catch {
        $result = Fail 1 $_.Exception.Message
    }
    $watch.Stop()
    $step = [pscustomobject]@{
        Number = $number; Name = $Name; Status = $result.Status; Code = $result.Code; Note = $result.Note
        Duration = $watch.Elapsed; Log = $(if (Test-Path -LiteralPath $script:CurrentLog) { $script:CurrentLog } else { $null })
    }
    [void]$script:Steps.Add($step)
    $line = '    {0}  {1}  код {2}' -f $step.Status, (Format-Duration $step.Duration), $step.Code
    if ($step.Note) { $line += '  ' + $step.Note }
    Write-Host $line -ForegroundColor (Get-StatusColor $step.Status)
    $step.Status
}

function Test-StepPassed([string]$Name) {
    $step = $script:Steps | Where-Object { $_.Name -eq $Name } | Select-Object -Last 1
    [bool]($step -and $step.Status -eq 'OK')
}

# ---------- запуск программ ----------

function ConvertTo-CmdArg([string]$Value) {
    if ($Value -match '[\s&|<>^()"]') { '"' + ($Value -replace '"', '\"') + '"' } else { $Value }
}

# Запуск через cmd.exe: stderr сливается со stdout внутри cmd, поэтому Windows PowerShell 5.1 не превращает его строки
# в ошибки (как при 2>&1 в самом PowerShell). Вывод — на экран (без -Quiet) и в лог шага (UTF-8); результат — код выхода.
# Команда передаётся через переменную окружения: так cmd не разбирает кавычки аргументов второй раз.
function Invoke-Logged {
    param([string]$Dir, [string]$Exe, [string[]]$Arguments = @(), [switch]$Quiet, [string]$Log = $script:CurrentLog)
    $line = (@($Exe) + $Arguments | ForEach-Object { ConvertTo-CmdArg $_ }) -join ' '
    Write-Host ("    {0}> {1}" -f $Dir, $line) -ForegroundColor DarkGray
    $writer = New-Object IO.StreamWriter($Log, $true, (New-Object Text.UTF8Encoding $false))
    $writer.WriteLine("# $Dir> $line")
    $env:DENTIFY_VERIFY_CMD = $line
    $code = -1
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    Push-Location -LiteralPath $Dir
    try {
        & cmd.exe /d /c '%DENTIFY_VERIFY_CMD% 2>&1' | ForEach-Object {
            $writer.WriteLine($_)
            if (-not $Quiet) { Write-Host $_ }
        }
        $code = $LASTEXITCODE
    }
    finally {
        Pop-Location
        $ErrorActionPreference = $savedPreference
        $writer.WriteLine("# код выхода: $code")
        $writer.Dispose()
        Remove-Item Env:DENTIFY_VERIFY_CMD -ErrorAction SilentlyContinue
    }
    [int]$code
}

function Read-Log([string]$Log = $script:CurrentLog) {
    if (Test-Path -LiteralPath $Log) { Get-Content -LiteralPath $Log -Encoding UTF8 } else { @() }
}

# Переменная процесса на весь прогон (JAVA_HOME, PATH, MAVEN_OPTS): в конце скрипта возвращается прежнее значение —
# ./verify.ps1 в открытой консоли не меняет её окружение.
function Set-RunEnv([string]$Name, [string]$Value) {
    if (-not $script:OriginalEnv.ContainsKey($Name)) {
        $script:OriginalEnv[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process')
    }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}

function Restore-RunEnv {
    foreach ($name in @($script:OriginalEnv.Keys)) { [Environment]::SetEnvironmentVariable($name, $script:OriginalEnv[$name], 'Process') }
    $script:OriginalEnv.Clear()
}

# Переменные только на время тела (дочерние процессы наследуют их при запуске).
function Invoke-WithEnv([hashtable]$Vars, [scriptblock]$Body) {
    $saved = @{}
    foreach ($name in $Vars.Keys) {
        $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $Vars[$name], 'Process')
    }
    try { & $Body }
    finally { foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') } }
}

function Get-Relative([string]$Path) {
    $full = [IO.Path]::GetFullPath($Path)
    if ($full.StartsWith($Root, [StringComparison]::OrdinalIgnoreCase)) { $full.Substring($Root.Length).TrimStart('\') } else { $full }
}

# ---------- фоновые процессы стека ----------

function Start-Background([string]$Name, [string]$Exe, [string[]]$Arguments, [string]$Dir) {
    $out = Join-Path $RunDir "stack-$Name.log"
    $err = Join-Path $RunDir "stack-$Name.err.log"
    $argLine = ($Arguments | ForEach-Object { ConvertTo-CmdArg $_ }) -join ' '
    Write-Host ("    {0}> {1} {2}  (лог: {3})" -f $Dir, $Exe, $argLine, (Get-Relative $out)) -ForegroundColor DarkGray
    $process = Start-Process -FilePath $Exe -ArgumentList $argLine -WorkingDirectory $Dir -NoNewWindow -PassThru `
        -RedirectStandardOutput $out -RedirectStandardError $err
    [void]$script:Background.Add([pscustomobject]@{ Name = $Name; Process = $process; Log = $out })
    $process
}

function Stop-Background {
    $stopped = @()
    foreach ($item in @($script:Background)) {
        $process = $item.Process
        if (-not $process.HasExited) {
            $savedPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try { & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null } finally { $ErrorActionPreference = $savedPreference }
            [void]$process.WaitForExit(15000)
            $stopped += "$($item.Name) (pid $($process.Id))"
        }
    }
    $script:Background.Clear()
    $stopped
}

function Get-FreePorts([int]$Count) {
    $listeners = @()
    try {
        for ($i = 0; $i -lt $Count; $i++) {
            $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
            $listener.Start()
            $listeners += $listener
        }
        $listeners | ForEach-Object { $_.LocalEndpoint.Port }
    }
    finally { $listeners | ForEach-Object { $_.Stop() } }
}

# Тело ответа строкой: Windows PowerShell 5.1 отдаёт Content массивом байт, если тип не text/* (актуатор — vnd…+json).
function Get-Body($Response) {
    if ($Response.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($Response.Content) } else { "$($Response.Content)" }
}

function Test-PortFree([int]$Port) {
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $Port)
    try { $listener.Start(); $true } catch { $false } finally { try { $listener.Stop() } catch { } }
}

# Ждёт ответа, который принимает $Accept; процесс упал — сразу ошибка с хвостом его лога.
function Wait-Http([string]$Url, [int]$Seconds, $Item, [scriptblock]$Accept) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if ($Item.Process.HasExited) {
            $tail = (Get-Content -LiteralPath $Item.Log -Tail 5 -ErrorAction SilentlyContinue) -join ' | '
            throw "$($Item.Name) завершился (код $($Item.Process.ExitCode)); лог $(Get-Relative $Item.Log): $tail"
        }
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 -MaximumRedirection 0
            if (& $Accept $response) { return $response }
        }
        catch { }
        Start-Sleep -Milliseconds 500
    }
    throw "$($Item.Name) не ответил за $Seconds с: $Url (лог $(Get-Relative $Item.Log))"
}

# ---------- шаги: окружение ----------

function Get-JdkVersion([string]$JavaHome) {
    if (-not $JavaHome) { return $null }
    $release = Join-Path $JavaHome 'release'
    if (-not (Test-Path -LiteralPath $release) -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) { return $null }
    $match = Select-String -LiteralPath $release -Pattern '^JAVA_VERSION="([^"]+)"' | Select-Object -First 1
    if ($match) { $match.Matches[0].Groups[1].Value } else { $null }
}

function Get-Major([string]$Version) {
    if ($Version -match '^1\.(\d+)') { return [int]$Matches[1] }
    if ($Version -match '^v?(\d+)') { return [int]$Matches[1] }
    0
}

function Step-Jdk {
    $current = $env:JAVA_HOME
    foreach ($candidate in @($current, $Jdk25)) {
        $version = Get-JdkVersion $candidate
        if ($version -and (Get-Major $version) -ge 25) {
            Set-RunEnv 'JAVA_HOME' $candidate
            Set-RunEnv 'PATH' ((Join-Path $candidate 'bin') + ';' + $env:PATH)
            $note = "$candidate ($version)"
            if ($current -and $candidate -ne $current) { $note += "; JAVA_HOME был $current ($(Get-JdkVersion $current)) — взят JDK 25" }
            return Ok $note
        }
    }
    Fail 1 "нужен JDK 25: JAVA_HOME ($current) не 25+, в $Jdk25 его нет"
}

function Invoke-Captured([string]$Exe, [string[]]$Arguments) {
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $out = @(& $Exe @Arguments 2>&1 | ForEach-Object { "$_" }) }
    finally { $ErrorActionPreference = $savedPreference }
    @{ Code = $LASTEXITCODE; Output = $out }
}

function Step-Docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { return Fail 1 'docker не найден: установите Docker Desktop' }
    $info = Invoke-Captured 'docker' @('info', '--format', '{{.ServerVersion}}')
    if ($info.Code -ne 0) { return Fail $info.Code 'Docker не отвечает: запустите Docker Desktop' }
    $compose = Invoke-Captured 'docker' @('compose', 'version', '--short')
    if ($compose.Code -ne 0) { return Fail $compose.Code 'нет Docker Compose v2' }
    Ok "Docker $($info.Output[-1]), Compose $($compose.Output[-1])"
}

function Step-DevEnv {
    $shell = (Get-Process -Id $PID).Path
    $devArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $Deploy 'dev.ps1'))
    if ((Invoke-Logged $Root $shell ($devArgs + 'check') -Quiet) -eq 0) { return Ok 'dev.ps1 check — все проверки пройдены' }
    Write-Host '    dev.ps1 check не прошёл — поднимаю окружение: dev.ps1 up' -ForegroundColor Yellow
    $code = Invoke-Logged $Root $shell ($devArgs + 'up')
    if ($code -ne 0) { return Fail $code 'dev.ps1 up не поднял окружение — см. лог шага' }
    $code = Invoke-Logged $Root $shell ($devArgs + 'check')
    if ($code -ne 0) { return Fail $code 'окружение поднято, но dev.ps1 check не проходит — см. лог шага' }
    Ok 'было не поднято — подняли dev.ps1 up, check пройден (остановить: deploy/dev.ps1 down)'
}

function Step-Node {
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) { return Fail 1 "нет Node.js $NodeMajor+" }
    if (-not (Get-Command pnpm -ErrorAction SilentlyContinue)) { return Fail 1 "нет pnpm $PnpmMajor+" }
    $node = (Invoke-Captured 'node' @('--version')).Output[-1]
    $pnpm = (Invoke-Captured 'cmd.exe' @('/d', '/c', 'pnpm --version')).Output[-1]
    if ((Get-Major $node) -lt $NodeMajor) { return Fail 1 "Node $node, нужен $NodeMajor+" }
    if ((Get-Major $pnpm) -lt $PnpmMajor) { return Fail 1 "pnpm $pnpm, нужен $PnpmMajor+" }
    Ok "Node $node, pnpm $pnpm"
}

# ---------- шаги: бэкенд и спека ----------

function Get-SpecHash { if (Test-Path -LiteralPath $SpecFile) { (Get-FileHash -LiteralPath $SpecFile -Algorithm SHA256).Hash } else { $null } }

function Step-BackendVerify {
    $script:SpecBefore = Get-SpecHash
    $script:BackendStarted = Get-Date
    $code = Invoke-Logged $Backend $Mvnw @('-B', '-ntp', 'verify')
    if ($code -ne 0) {
        $errors = @(Read-Log | Where-Object { $_ -match '^\[ERROR\] \S' -and $_ -match 'Tests run:|FAILURE|COMPILATION ERROR|Failed to execute goal' } |
                Select-Object -First 2 | ForEach-Object { $line = ($_ -replace '^\[ERROR\] ', '').Trim(); if ($line.Length -gt 150) { $line.Substring(0, 150) + '…' } else { $line } })
        return Fail $code ('mvnw verify упал' + $(if ($errors) { ': ' + ($errors -join ' / ') } else { ' — см. лог шага' }))
    }
    $tests = Read-Log | Where-Object { $_ -match '^\[(INFO|WARNING)\] Tests run: \d+, Failures: \d+, Errors: \d+, Skipped: \d+$' } | Select-Object -Last 1
    Ok $(if ($tests) { ($tests -replace '^\[\w+\] ', '') } else { '' })
}

function Step-SpecCheck {
    $after = Get-SpecHash
    $dirty = ''
    $status = Invoke-Captured 'git' @('-C', $Root, 'status', '--porcelain', '--', 'api/openapi.json')
    if ($status.Code -eq 0 -and ($status.Output -join '').Trim()) { $dirty = '; api/openapi.json не закоммичен — коммитьте вместе с кодом' }
    if ($after -ne $script:SpecBefore) {
        return Fail 1 ('сборка переписала api/openapi.json: спека отставала от контроллеров. Проверьте git diff api/openapi.json, ' +
            'затем cd frontend; pnpm api:generate; pnpm typecheck — и закоммитьте всё вместе')
    }
    if (Test-StepPassed 'backend: ./mvnw -B verify') { return Ok ('спека = выгрузка OpenApiContractTests' + $dirty) }
    # Бэкенд красный: совпадению можно верить, только если тест контракта в этом прогоне прошёл.
    $report = Join-Path $Backend 'target\surefire-reports\TEST-kz.dentify.platform.OpenApiContractTests.xml'
    if ((Test-Path -LiteralPath $report) -and (Get-Item -LiteralPath $report).LastWriteTime -gt $script:BackendStarted) {
        $xml = [xml](Get-Content -LiteralPath $report -Raw -Encoding UTF8)
        if ([int]$xml.testsuite.failures -eq 0 -and [int]$xml.testsuite.errors -eq 0) {
            return Ok ('спека = выгрузка OpenApiContractTests (бэкенд красный по другим тестам)' + $dirty)
        }
    }
    Skipped 'бэкенд красный и OpenApiContractTests не прошёл — сверять не с чем'
}

# ---------- шаги: фронтенд ----------

function Get-VerifyScripts {
    $package = Get-Content -LiteralPath (Join-Path $Frontend 'package.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($part in ($package.scripts.verify -split '\s*&&\s*')) {
        if ($part -match '^pnpm\s+(?:run\s+)?(\S+)$') { $Matches[1] } else { throw "шаг verify не вида «pnpm <скрипт>»: $part" }
    }
}

function Step-FrontendScript([string]$ScriptName) {
    if ($ScriptName -eq 'size' -and -not (Test-StepPassed 'frontend: pnpm build')) { return Skipped 'сборка не прошла — бюджет мерить не по чему' }
    $code = Invoke-Logged $Frontend 'pnpm' @($ScriptName)
    if ($ScriptName -eq 'e2e') {
        $counts = (Read-Log | Where-Object { $_ -match '^\s+\d+ (passed|failed|flaky|skipped|did not run)' } | ForEach-Object { $_.Trim() }) -join ', '
        if ($code -ne 0) { return Fail $code "$counts; отчёт — frontend/playwright-report/run-<id> (путь — в конце лога шага)" }
        if ($counts) { return Ok $counts }
    }
    if ($code -ne 0) { return Fail $code 'см. лог шага' }
    if ($ScriptName -eq 'test') {
        $tests = Read-Log | Where-Object { $_ -match '^\s+Tests\s+\d+' } | Select-Object -Last 1
        if ($tests) { return Ok (($tests -replace '\s+', ' ').Trim()) }
    }
    Ok
}

# ---------- шаги: живой стек ----------

function Find-BackendJar {
    Get-ChildItem -LiteralPath (Join-Path $Backend 'target') -Filter 'dentify-backend-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '(sources|javadoc|plain)\.jar$' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

function Step-StackJar {
    $jar = Find-BackendJar
    if ((Test-StepPassed 'backend: ./mvnw -B verify') -and $jar) { $script:Jar = $jar.FullName; return Ok "из шага backend: $(Get-Relative $jar.FullName)" }
    $code = Invoke-Logged $Backend $Mvnw @('-B', '-ntp', '-DskipTests', 'package')
    if ($code -ne 0) { return Fail $code 'mvnw package упал — см. лог шага' }
    $jar = Find-BackendJar
    if (-not $jar) { return Fail 1 'jar не найден в backend/target' }
    $script:Jar = $jar.FullName
    Ok (Get-Relative $jar.FullName)
}

function Step-StackDist {
    if ((Test-StepPassed 'frontend: pnpm build') -and (Test-Path -LiteralPath (Join-Path $Frontend 'dist\index.html'))) { return Ok 'из шага frontend: frontend/dist' }
    $code = Invoke-Logged $Frontend 'pnpm' @('build')
    if ($code -ne 0) { return Fail $code 'pnpm build упал — см. лог шага' }
    Ok 'frontend/dist'
}

function Step-StackStart {
    if (-not $script:Jar) { return Skipped 'нет jar бэкенда' }
    if (-not (Test-StepPassed 'e2e: сборка фронта (dist)')) { return Skipped 'нет сборки фронта' }
    $ports = @(Get-FreePorts 3)
    $script:ApiPort = $ports[0]; $managementPort = $ports[1]; $script:WebPort = $ports[2]

    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
    $backend = Start-Background 'backend' $java @('-jar', $script:Jar, '--spring.profiles.active=dev',
        "--server.port=$($script:ApiPort)", "--management.server.port=$managementPort") $Backend
    $item = $script:Background | Where-Object { $_.Process -eq $backend }
    [void](Wait-Http "http://127.0.0.1:$managementPort/actuator/health/readiness" 180 $item { param($r) (Get-Body $r) -match '"status"\s*:\s*"UP"' })
    Write-Host "    бэкенд готов: API 127.0.0.1:$($script:ApiPort), actuator 127.0.0.1:$managementPort" -ForegroundColor DarkGray

    # vite preview: сборка без моков, /api — прокси на этот бэкенд (vite.config.ts, DENTIFY_API_TARGET).
    $web = Invoke-WithEnv @{ DENTIFY_API_TARGET = "http://127.0.0.1:$($script:ApiPort)" } {
        Start-Background 'frontend' 'node.exe' @('node_modules/vite/bin/vite.js', 'preview', '--port', "$($script:WebPort)", '--strictPort') $Frontend
    }
    $item = $script:Background | Where-Object { $_.Process -eq $web }
    [void](Wait-Http "http://127.0.0.1:$($script:WebPort)/login" 60 $item { param($r) $r.StatusCode -eq 200 -and (Get-Body $r) -match '<div id="root"' })
    # Сквозной запрос: страница → прокси Vite → бэкенд. 204 и cookie XSRF-TOKEN — стек собран правильно.
    $csrf = Invoke-WebRequest -Uri "http://127.0.0.1:$($script:WebPort)/api/v1/auth/csrf" -UseBasicParsing -TimeoutSec 10
    if ($csrf.StatusCode -ne 204 -or "$($csrf.Headers['Set-Cookie'])" -notmatch 'XSRF-TOKEN=') {
        return Fail 1 "GET /api/v1/auth/csrf через прокси: $($csrf.StatusCode), без cookie XSRF-TOKEN"
    }
    Ok "фронт http://localhost:$($script:WebPort) → API 127.0.0.1:$($script:ApiPort) (actuator $managementPort); /api/v1/auth/csrf через прокси — 204"
}

# Проекты живого стека — по договору stack-<вьюпорт> (T-PLT-16: stack-desktop 1440×900, stack-mobile 390×844): берём все
# по маске, чтобы оба вьюпорта шли одним прогоном и новое имя не выпало из него молча.
$StackProjects = '--project=stack*'

function Step-StackPlaywright {
    if (-not $script:WebPort -or -not (Test-StepPassed 'e2e: запуск стека')) { return Skipped 'стек не поднят' }
    $code = Invoke-Logged $Frontend 'pnpm' @('exec', 'playwright', 'test', '--list', $StackProjects) -Quiet
    if ($code -ne 0) {
        # Маска без совпадений — «No projects matched»; точное имя без проекта — «Project(s) "…" not found».
        if (Read-Log | Where-Object { $_ -match 'No projects matched|Project\(s\) "stack[^"]*" not found' }) {
            return Skipped 'в playwright.config.ts нет проектов stack-* (stack-desktop и stack-mobile добавит T-PLT-16) — живой стек поднят и проверен запросом, UI-сценарии пропущены'
        }
        return Fail $code 'playwright test --list упал — см. лог шага'
    }
    $stackDir = Join-Path $RunDir 'stack'
    # Договор с проектами stack-* (T-PLT-16): адрес фронта на живом бэкенде и прямой адрес API — из окружения.
    $vars = @{
        DENTIFY_STACK_URL          = "http://localhost:$($script:WebPort)"
        DENTIFY_STACK_API_URL      = "http://127.0.0.1:$($script:ApiPort)"
        PLAYWRIGHT_HTML_OUTPUT_DIR = (Join-Path $stackDir 'report')
        PLAYWRIGHT_HTML_OPEN       = 'never'
    }
    $code = Invoke-WithEnv $vars {
        Invoke-Logged $Frontend 'pnpm' @('exec', 'playwright', 'test', $StackProjects, '--output', (Join-Path $stackDir 'artifacts'))
    }
    $summary = (Read-Log | Where-Object { $_ -match '^\s+\d+ (passed|failed|flaky)' } | ForEach-Object { $_.Trim() }) -join ', '
    $where = "трейсы и скриншоты — $(Get-Relative $stackDir)"
    if ($code -ne 0) { return Fail $code "$summary; $where" }
    Ok "$summary; $where"
}

function Step-StackStop {
    $stopped = @(Stop-Background)
    $busy = @(@($script:ApiPort, $script:WebPort) | Where-Object { $_ -and -not (Test-PortFree $_) })
    if ($busy) { return Fail 1 "порты всё ещё заняты: $($busy -join ', ')" }
    if ($stopped) { Ok "остановлены: $($stopped -join ', ')" } else { Ok 'нечего останавливать' }
}

# ---------- шаги: сканеры ----------

function Get-NvdDataDir {
    if ($env:DENTIFY_NVD_DATA) { $env:DENTIFY_NVD_DATA } else { Join-Path $HOME '.m2\repository\org\owasp\dependency-check-data' }
}

function Get-NvdArgs {
    $arguments = @()
    if ($env:DENTIFY_NVD_DATA) { $arguments += "-DdataDirectory=$($env:DENTIFY_NVD_DATA)" }
    $arguments
}

function Step-NvdUpdate {
    $arguments = @('-B', '-ntp', 'dependency-check:update-only') + (Get-NvdArgs)
    $source = 'NVD API с ключом NVD_API_KEY'
    if (-not $env:NVD_API_KEY) {
        $feed = if ($env:DENTIFY_NVD_DATAFEED) { $env:DENTIFY_NVD_DATAFEED } else { $NvdFeed }
        $arguments += "-DnvdDatafeedUrl=$feed"
        $source = "JSON-фиды NVD ($feed)"
    }
    $code = Invoke-Logged $Backend $Mvnw $arguments
    if ($code -eq 0) {
        # Плагин ходит в NVD не чаще раза в 4 часа (nvdValidForHours): свежая база — без сети, это не «обновлена».
        if (Read-Log | Where-Object { $_ -match 'Skipping NVD API Cache check since last check was within' }) {
            return Ok "база свежая (NVD проверялся меньше 4 ч назад) — обновление не нужно; кэш — $(Get-NvdDataDir)"
        }
        return Ok "база обновлена: $source; кэш — $(Get-NvdDataDir)"
    }
    $reason = Read-Log | Where-Object { $_ -match '^\[ERROR\] Failed to execute goal' } | Select-Object -First 1
    # Суть — в конце строки Maven: «… for more details. Unable to download …; nvd.invalid: Unknown host …».
    $reason = if ($reason) {
        $reason -replace ' -> \[Help 1\]$', '' -replace '^.*for more details\. ', '' -replace '^\[ERROR\] Failed to execute goal .*? on project [^:]+: ', ''
    } else { 'см. лог шага' }
    if ($reason.Length -gt 200) { $reason = $reason.Substring(0, 200) + '…' }
    Unavailable "NVD недоступен ($source): $reason; проверка пойдёт по кэшу, если он есть"
}

function Step-DependencyCheck {
    $db = Get-ChildItem -LiteralPath (Get-NvdDataDir) -Recurse -Filter 'odc.mv.db' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $db) { return Unavailable "нет локальной базы NVD в $(Get-NvdDataDir) — проверять не по чему" }
    $code = Invoke-Logged $Backend $Mvnw (@('-B', '-ntp', 'dependency-check:check', '-DautoUpdate=false') + (Get-NvdArgs))
    $report = 'backend/target/dependency-check-report.html'
    # Возраст базы по времени файла не показываем: odc.mv.db (H2) переписывается при каждой проверке, а не только при
    # обновлении. Свежесть — в шаге «NVD — обновление базы».
    if ($code -eq 0) { return Ok "уязвимостей с CVSS 7+ нет (по локальной базе NVD); отчёт — $report" }
    $log = @(Read-Log)
    if ($log | Where-Object { $_ -match 'CVSS score greater than or equal to' }) {
        $found = foreach ($line in $log) {
            if ($line -match '^\[ERROR\] (\S+\.jar) \(.*\): (CVE.+)$') {
                $scores = [regex]::Matches($Matches[2], '\((\d+(?:[.,]\d+)?)\)') | ForEach-Object { [double]($_.Groups[1].Value -replace ',', '.') }
                $max = ($scores | Measure-Object -Maximum).Maximum
                '{0} — {1} CVE, макс. {2}' -f $Matches[1], @($scores).Count, $max
            }
        }
        return Fail $code ("уязвимости с CVSS 7+: " + ($found -join '; ') + "; отчёт — $report, исключения — backend/dependency-check-suppressions.xml")
    }
    # Первое обновление не дошло до NVD (новая машина, очищенный кэш, нет сети): плагин всё равно создал пустой
    # odc.mv.db, и проверка по нему падает с NoDataException. Это недоступность сканера, а не красная проверка.
    if ($log | Where-Object { $_ -match 'NoDataException|No documents exist' }) {
        return Unavailable "база NVD в $(Get-NvdDataDir) пуста — ни разу не скачана, проверять не по чему"
    }
    Fail $code 'dependency-check упал не из-за уязвимостей — см. лог шага'
}

function Step-PnpmAudit {
    $code = Invoke-Logged $Frontend 'pnpm' @('audit', '--prod', '--audit-level', 'high')
    $log = @(Read-Log)
    if ($code -eq 0) {
        $total = $log | Where-Object { $_ -match 'vulnerabilities found|No known vulnerabilities' } | Select-Object -Last 1
        return Ok $(if ($total) { $total.Trim() + ' (порог — high)' } else { 'high и critical нет' })
    }
    if ($log | Where-Object { $_ -match 'ERR_PNPM_AUDIT_BAD_RESPONSE|ERR_PNPM_META_FETCH_FAIL|ENOTFOUND|ETIMEDOUT|ECONNRESET|ECONNREFUSED|EAI_AGAIN|getaddrinfo' }) {
        return Unavailable 'реестр npm недоступен для pnpm audit'
    }
    $severity = $log | Where-Object { $_ -match '^Severity:' } | Select-Object -Last 1
    Fail $code ("уязвимости high/critical в prod-зависимостях: $severity; исключения — pnpm.auditConfig.ignoreGhsas в frontend/package.json")
}

function Get-GitleaksPlatform {
    $arch = if ($env:PROCESSOR_ARCHITEW6432) { $env:PROCESSOR_ARCHITEW6432 } else { $env:PROCESSOR_ARCHITECTURE }
    if ($arch -eq 'ARM64') { 'windows_arm64' } else { 'windows_x64' }
}

function Step-GitleaksBinary {
    $local = Join-Path $ToolsDir "gitleaks-$GitleaksVersion\gitleaks.exe"
    $onPath = Get-Command gitleaks -ErrorAction SilentlyContinue
    if ($onPath) {
        $version = "$((Invoke-Captured $onPath.Source @('version')).Output | Select-Object -Last 1)".Trim()
        $parsed = $null
        if ([version]::TryParse(($version -replace '^v', '' -replace '[^\d.].*$', ''), [ref]$parsed) -and $parsed -ge [version]'8.19') {
            $script:Gitleaks = $onPath.Source
            return Ok "$($onPath.Source) ($version)"
        }
        Write-Host "    gitleaks в PATH старый ($version), нужен 8.19+ (команда dir) — беру закреплённый $GitleaksVersion" -ForegroundColor Yellow
    }
    if (Test-Path -LiteralPath $local) { $script:Gitleaks = $local; return Ok "$local ($GitleaksVersion)" }

    $platform = Get-GitleaksPlatform
    $name = "gitleaks_${GitleaksVersion}_$platform.zip"
    $url = "https://github.com/gitleaks/gitleaks/releases/download/v$GitleaksVersion/$name"
    $zip = Join-Path ([IO.Path]::GetTempPath()) $name
    Write-Host "    скачиваю $url" -ForegroundColor DarkGray
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing -TimeoutSec 120
    }
    catch { return Unavailable "gitleaks не скачался ($url): $($_.Exception.Message). Поставьте сами: winget install Gitleaks.Gitleaks" }
    try {
        $hash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($hash -ne $GitleaksSha256[$platform]) { return Fail 1 "SHA-256 архива $name не совпал с закреплённым: $hash" }
        $target = Split-Path $local
        New-Item -ItemType Directory -Force -Path $target | Out-Null
        Expand-Archive -LiteralPath $zip -DestinationPath $target -Force
    }
    finally { Remove-Item -LiteralPath $zip -Force -ErrorAction SilentlyContinue }
    if (-not (Test-Path -LiteralPath $local)) { return Fail 1 "в архиве $name нет gitleaks.exe" }
    $script:Gitleaks = $local
    Ok "скачан $local ($GitleaksVersion, SHA-256 сверен)"
}

function Get-GitleaksResult([int]$Code, [string]$Report) {
    $log = @(Read-Log)
    if ($Code -eq 0) { return Ok 'утечек нет' }
    $leaks = $log | Where-Object { $_ -match 'leaks found: (\d+)' } | Select-Object -Last 1
    if ($leaks) {
        return Fail $Code ("найдено секретов: $($Matches[1]); отчёт — $(Get-Relative $Report) (значения скрыты). " +
            'Ложное — .gitleaks.toml (allowlist с причиной) или отпечаток в .gitleaksignore')
    }
    Fail $Code 'gitleaks упал — см. лог шага'
}

# Рабочая копия — .gitleaks-dir.toml (наследует .gitleaks.toml и исключает то, чего нет в git: deploy/.env, сборки,
# зависимости); запуск из $Root — путь [extend] gitleaks считает от текущей папки. История — строгим .gitleaks.toml.
function Step-GitleaksDir {
    if (-not $script:Gitleaks) { return Skipped 'нет gitleaks' }
    $report = Join-Path $RunDir 'gitleaks-dir.json'
    $code = Invoke-Logged $Root $script:Gitleaks @('dir', '.', '--config', '.gitleaks-dir.toml', '--redact', '--no-banner',
        '--report-format', 'json', '--report-path', $report)
    Get-GitleaksResult $code $report
}

function Step-GitleaksHistory {
    if (-not $script:Gitleaks) { return Skipped 'нет gitleaks' }
    $top = Invoke-Captured 'git' @('-C', $Root, 'rev-parse', '--show-toplevel')
    $prefix = Invoke-Captured 'git' @('-C', $Root, 'rev-parse', '--show-prefix')
    if ($top.Code -ne 0) { return Skipped 'не git-репозиторий — историю не проверить' }
    $path = ($prefix.Output -join '').Trim().TrimEnd('/')
    if (-not $path) { $path = '.' }
    $report = Join-Path $RunDir 'gitleaks-history.json'
    $code = Invoke-Logged $Root $script:Gitleaks @('git', ($top.Output -join '').Trim(), "--log-opts=-- $path",
        '--config', (Join-Path $Root '.gitleaks.toml'), '--redact', '--no-banner', '--report-format', 'json', '--report-path', $report)
    $result = Get-GitleaksResult $code $report
    $commits = Read-Log | Where-Object { $_ -match '(\d+) commits scanned' } | Select-Object -Last 1
    if ($result.Status -eq 'OK' -and $commits) { $result.Note = "утечек нет (коммитов с $path`: $($Matches[1]))" }
    $result
}

# ---------- сводка ----------

function Show-Summary([TimeSpan]$Total, [string]$Mode) {
    Write-Host ''
    Write-Host ("Сводка: verify.ps1 {0} — {1}, всего {2}" -f $Mode, (Get-Date -Format 'yyyy-MM-dd HH:mm'), (Format-Duration $Total)) -ForegroundColor Cyan
    Write-Host '   #  Итог  Время   Код  Шаг'
    foreach ($step in $script:Steps) {
        $line = '  {0,2}  {1,-4}  {2,5}  {3,4}  {4}' -f $step.Number, $step.Status, (Format-Duration $step.Duration), $step.Code, $step.Name
        if ($step.Note) { $line += ' — ' + $step.Note }
        Write-Host $line -ForegroundColor (Get-StatusColor $step.Status)
    }
    $failed = @($script:Steps | Where-Object { $_.Status -eq 'FAIL' })
    $warned = @($script:Steps | Where-Object { $_.Status -eq 'WARN' })
    Write-Host ''
    if ($failed) {
        Write-Host ("Итог: КРАСНО — не прошли {0} из {1}: {2}" -f $failed.Count, $script:Steps.Count, (($failed | ForEach-Object { $_.Name }) -join '; ')) -ForegroundColor Red
    }
    else {
        $extra = if ($warned) { ", предупреждений: $($warned.Count)" } else { '' }
        Write-Host ("Итог: ЗЕЛЕНО — шагов: {0}{1}" -f $script:Steps.Count, $extra) -ForegroundColor Green
    }
    Write-Host "Логи шагов: $(Get-Relative $RunDir)"
}

# ---------- main ----------

New-Item -ItemType Directory -Force -Path $RunDir | Out-Null
Get-ChildItem -LiteralPath $Results -Directory -Filter 'verify-*' -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-7) } | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

$mode = @()
if ($RunBase) { $mode += 'verify' }
if ($E2E) { $mode += '-E2E' }
if ($Scan) { $mode += '-Scan' }
if ($Strict) { $mode += '-Strict' }
if ($Skip) { $mode += "-Skip $($Skip -join ',')" }
$mode = $mode -join ' '

Set-RunEnv 'MAVEN_OPTS' (("$env:MAVEN_OPTS" + ' -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8').Trim())

$watch = [Diagnostics.Stopwatch]::StartNew()
Write-Host "Dentify verify ($mode), логи — $(Get-Relative $RunDir)" -ForegroundColor Cyan
try {
    if ($NeedJdk) { [void](Invoke-Step 'env: JDK 25' { Step-Jdk }) }
    if ($NeedDocker) { [void](Invoke-Step 'env: Docker' { Step-Docker }) }
    if ($DoEnv) {
        if (Test-StepPassed 'env: Docker') { [void](Invoke-Step 'env: dev-окружение (deploy/dev.ps1 check)' { Step-DevEnv }) }
        else { [void](Invoke-Step 'env: dev-окружение (deploy/dev.ps1 check)' { Skipped 'Docker не отвечает' }) }
    }
    if ($NeedNode) { [void](Invoke-Step 'env: Node и pnpm' { Step-Node }) }

    if ($DoBackend) {
        if (Test-StepPassed 'env: JDK 25') { [void](Invoke-Step 'backend: ./mvnw -B verify' { Step-BackendVerify }) }
        else { [void](Invoke-Step 'backend: ./mvnw -B verify' { Skipped 'нет JDK 25' }) }
        [void](Invoke-Step 'api: спека = выгрузка сборки бэкенда' {
                if ($null -eq (Get-Variable -Name SpecBefore -Scope Script -ErrorAction SilentlyContinue)) { return Skipped 'бэкенд не запускался' }
                Step-SpecCheck
            })
    }

    if ($DoFrontend) {
        if (Test-StepPassed 'env: Node и pnpm') {
            [void](Invoke-Step 'frontend: pnpm install --frozen-lockfile' {
                    $code = Invoke-Logged $Frontend 'pnpm' @('install', '--frozen-lockfile')
                    if ($code -ne 0) { Fail $code 'lockfile не совпадает с package.json или реестр недоступен — см. лог шага' } else { Ok }
                })
            foreach ($name in @(Get-VerifyScripts)) {
                $scriptName = $name
                [void](Invoke-Step "frontend: pnpm $scriptName" { Step-FrontendScript $scriptName })
            }
        }
        else { [void](Invoke-Step 'frontend: pnpm verify' { Skipped 'нет Node или pnpm' }) }
    }

    if ($E2E) {
        try {
            [void](Invoke-Step 'e2e: jar бэкенда' { if (Test-StepPassed 'env: JDK 25') { Step-StackJar } else { Skipped 'нет JDK 25' } })
            [void](Invoke-Step 'e2e: сборка фронта (dist)' { if (Test-StepPassed 'env: Node и pnpm') { Step-StackDist } else { Skipped 'нет Node или pnpm' } })
            [void](Invoke-Step 'e2e: запуск стека' { Step-StackStart })
            [void](Invoke-Step 'e2e: Playwright stack' { Step-StackPlaywright })
        }
        finally {
            [void](Invoke-Step 'e2e: остановка стека' { Step-StackStop })
        }
    }

    if ($Scan) {
        if (Test-StepPassed 'env: JDK 25') {
            [void](Invoke-Step 'scan: NVD — обновление базы' { Step-NvdUpdate })
            [void](Invoke-Step 'scan: OWASP dependency-check (CVSS 7+)' { Step-DependencyCheck })
        }
        else { [void](Invoke-Step 'scan: OWASP dependency-check (CVSS 7+)' { Skipped 'нет JDK 25' }) }
        if (Test-StepPassed 'env: Node и pnpm') { [void](Invoke-Step 'scan: pnpm audit --prod (high+)' { Step-PnpmAudit }) }
        else { [void](Invoke-Step 'scan: pnpm audit --prod (high+)' { Skipped 'нет Node или pnpm' }) }
        [void](Invoke-Step 'scan: gitleaks — бинарь' { Step-GitleaksBinary })
        [void](Invoke-Step 'scan: gitleaks — рабочая копия apps/dentify' { Step-GitleaksDir })
        [void](Invoke-Step 'scan: gitleaks — история git' { Step-GitleaksHistory })
    }
}
finally {
    # Ctrl+C и исключения: фоновые процессы стека не остаются висеть, окружение процесса — как было.
    if ($script:Background.Count -gt 0) { [void](Stop-Background) }
    Restore-RunEnv
    # Поток вывода уже создан в UTF-8 (см. начало), сводка ниже выйдет в нём же; консоли возвращаем её кодировку здесь,
    # чтобы вернуть и при Ctrl+C.
    Restore-ConsoleEncoding
}
$watch.Stop()

Show-Summary $watch.Elapsed $mode
if (@($script:Steps | Where-Object { $_.Status -eq 'FAIL' }).Count -gt 0) { exit 1 }
exit 0
