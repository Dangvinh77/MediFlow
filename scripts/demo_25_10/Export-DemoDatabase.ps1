[CmdletBinding()]
param(
    # Postgres container to export. Default: this checkout's demo, then Compose.
    [string]$Container,
    [string]$OutputDir,
    # Export even though the running demo has not reported READY yet.
    [switch]$Force
)

# Exports every mediflow_* database of the seeded demo to plain SQL. Read-only for the
# databases: pg_dump runs inside the container, so no local PostgreSQL client is needed
# and files are copied with docker cp to keep their UTF-8 bytes intact.
# The dump holds password hashes and audit data, so the default folder is gitignored.

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$evidence = Join-Path $PSScriptRoot 'evidence'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

function Invoke-Docker {
    $output = & docker @args
    if ($LASTEXITCODE -ne 0) { throw "docker $($args -join ' ') failed with exit code $LASTEXITCODE." }
    $output
}

function Get-ContainerState([string]$Name) {
    $state = @(Invoke-Docker ps -a --filter "name=^/$Name`$" --format '{{.State}}')
    if ($state.Count -gt 0) { return "$($state[0])" }
    return $null
}

function Read-Json([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    return Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Get-ActiveDemo {
    $manifest = Read-Json (Join-Path $evidence 'active-rehearsal.local.json')
    if (-not $manifest -or -not (Get-Process -Id $manifest.runnerPid -ErrorAction SilentlyContinue)) { return $null }
    return $manifest
}

function Get-Candidates($Active) {
    if ($Container) { return @($Container) }
    $names = @()
    if ($Active) { $names += @($Active.containers | Where-Object { $_ -like '*-pg' }) }
    $workspace = Read-Json (Join-Path $evidence 'workspace\infrastructure.local.json')
    if ($workspace -and $workspace.containers.pg) { $names += $workspace.containers.pg.name }
    $names += 'mediflow-postgres'
    return @($names | Select-Object -Unique)
}

function Invoke-Sql([string]$Database, [string]$Sql) {
    Invoke-Docker exec $script:pg psql -X -At -v ON_ERROR_STOP=1 -U postgres -d $Database -c $Sql
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Missing command: docker.' }

$active = Get-ActiveDemo
if ($active -and -not $Force) {
    $result = Join-Path $active.evidence 'result.txt'
    $ready = (Test-Path -LiteralPath $result) -and
        ((Get-Content -LiteralPath $result -TotalCount 1 -Encoding UTF8) -like 'PASS*')
    if (-not $ready) {
        throw 'The demo backend is still seeding. Wait for READY, then export again (or pass -Force).'
    }
}

$found = @(Get-Candidates $active | ForEach-Object {
    $state = Get-ContainerState $_
    if ($state) { [pscustomobject]@{ Name = $_; State = $state } }
})
if ($found.Count -eq 0) {
    throw 'No demo Postgres container found. Run scripts\run\start-backend.bat and wait for READY, or pass -Container <name>.'
}
$selected = $found | Where-Object { $_.State -eq 'running' } | Select-Object -First 1
$startedHere = $false
if (-not $selected) {
    $selected = $found[0]
    Write-Host "Starting stopped container $($selected.Name) for the export..."
    Invoke-Docker start $selected.Name | Out-Null
    $startedHere = $true
}
$pg = $selected.Name
$remote = "/tmp/mediflow-export-$stamp"
$createdRemote = $false

try {
    $deadline = (Get-Date).AddSeconds(60)
    while ($true) {
        & docker exec $pg pg_isready -q -U postgres
        if ($LASTEXITCODE -eq 0) { break }
        if ((Get-Date) -gt $deadline) { throw "Postgres in $pg is not ready after 60 seconds." }
        Start-Sleep -Seconds 1
    }

    $databases = @(Invoke-Sql 'postgres' "select datname from pg_database where datname like 'mediflow\_%' order by datname")
    if ($databases.Count -eq 0) { throw "Container $pg has no mediflow_* database." }
    $accounts = 0
    if ($databases -contains 'mediflow_organization') {
        $hasAccount = "$(Invoke-Sql 'mediflow_organization' "select to_regclass('public.account') is not null")"
        if ($hasAccount -eq 't') { $accounts = [int]"$(Invoke-Sql 'mediflow_organization' 'select count(*) from account')" }
    }
    if ($accounts -eq 0) { throw "Container $pg has no seeded accounts; seed the demo before exporting." }

    if ($active) {
        Write-Warning 'The backend is running: each database is a consistent snapshot, but the set is not atomic across databases.'
    }

    if (-not $OutputDir) { $OutputDir = Join-Path $PSScriptRoot "exports\$stamp" }
    $OutputDir = [IO.Path]::GetFullPath($OutputDir)
    if ((Test-Path -LiteralPath $OutputDir) -and @(Get-ChildItem -LiteralPath $OutputDir -Force).Count -gt 0) {
        throw "Output folder is not empty: $OutputDir"
    }
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

    $createdRemote = $true
    Invoke-Docker exec $pg mkdir -p $remote | Out-Null
    $rows = @()
    foreach ($database in $databases) {
        Write-Host "Exporting $database..."
        Invoke-Docker exec $pg pg_dump -U postgres --create --no-owner --no-privileges --encoding=UTF8 -f "$remote/$database.sql" $database | Out-Null
        $counts = Invoke-Sql $database @'
select count(*) || '|' || coalesce(sum((xpath('/row/c/text()', query_to_xml(
  format('select count(*) c from %I.%I', schemaname, tablename), false, true, '')))[1]::text::bigint), 0)
from pg_tables where schemaname not in ('pg_catalog', 'information_schema')
'@
        $parts = "$counts" -split '\|'
        $rows += [pscustomobject]@{ Database = $database; Tables = [int]$parts[0]; Rows = [long]$parts[1] }
    }

    # mediflow_*.sql never matches ALL_DATABASES.sql, so the glob cannot read its own output.
    Invoke-Docker exec $pg sh -c "cat $remote/mediflow_*.sql > $remote/ALL_DATABASES.sql" | Out-Null
    Invoke-Docker cp "${pg}:$remote/." $OutputDir | Out-Null

    $allFile = Join-Path $OutputDir 'ALL_DATABASES.sql'
    $hash = (Get-FileHash -LiteralPath $allFile -Algorithm SHA256).Hash.ToLowerInvariant()
    $commit = (& git -C $repoRoot rev-parse --short HEAD)
    $lines = @(
        'MediFlow demo database export (synthetic data; contains password hashes - do not publish).',
        "Exported at : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')",
        "Container   : $pg",
        "Commit      : $commit",
        "Backend     : $(if ($active) { 'running while exporting' } else { 'not running' })",
        "SHA-256     : $hash  ALL_DATABASES.sql",
        '',
        'Database                        Tables       Rows       Size'
    )
    foreach ($row in $rows) {
        $size = (Get-Item -LiteralPath (Join-Path $OutputDir "$($row.Database).sql")).Length
        $lines += '{0,-30} {1,7} {2,10} {3,7:N1} MB' -f $row.Database, $row.Tables, $row.Rows, ($size / 1MB)
    }
    $lines += @(
        '',
        'Restore into an empty Postgres 16.10+ server with no mediflow_* database (run in cmd.exe):',
        '  docker exec -i <container> psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres < ALL_DATABASES.sql',
        'Each mediflow_<name>.sql restores one database the same way. The dump has no DROP statements,',
        'so a target that already has the database refuses it. RabbitMQ contents are not exported.'
    )
    $lines | Set-Content -LiteralPath (Join-Path $OutputDir 'MANIFEST.txt') -Encoding UTF8

    $lines[7..(7 + $rows.Count)] | Write-Host
    Write-Host ''
    Write-Host "EXPORT READY: $OutputDir"
}
finally {
    if ($createdRemote) { & docker exec $pg rm -rf $remote }
    if ($startedHere) { & docker stop $pg | Out-Null }
}
