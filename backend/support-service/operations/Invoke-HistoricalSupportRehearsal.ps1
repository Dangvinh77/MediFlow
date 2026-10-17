<#
Cold-copy the stopped local DEVELOPMENT stores/broker, inventory them read-only and rehearse
Support on copies. Original volumes are never started, migrated, reset or mounted writable.
Credentials match this repository's local Compose defaults. Not a production migration tool.
Retains clone volumes and evidence, stops only containers created by this invocation.
#>
[CmdletBinding()]
param(
    [string]$SourcePgVolume = 'mediflow_mediflow-pgdata',
    [string]$SourceRabbitVolume = 'mediflow_mediflow-rabbitdata',
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$dockerExecutable = (Get-Command docker -CommandType Application | Select-Object -First 1).Source
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$tag = 'mediflow-h6-' + (Get-Date -Format 'yyyyMMddHHmmss') + '-' + ([guid]::NewGuid().ToString('N').Substring(0, 6))
if (!$OutputDirectory) { $OutputDirectory = Join-Path $repoRoot "backend/support-service/target/historical-support-rehearsal/$tag" }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Choose a new evidence directory; existing evidence is never overwritten.' }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$createdContainers = [Collections.Generic.List[string]]::new()
$savedEnvironment = @{}

function Docker([Parameter(ValueFromRemainingArguments=$true)][string[]]$Arguments) {
    $result = & $dockerExecutable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker operation failed: $($Arguments[0]) (see diagnostic above)" }
    return $result
}
function Save-Json($Value, [string]$Name) {
    $Value | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $OutputDirectory $Name) -Encoding utf8
}
function Source-Fingerprint([string]$Volume) {
    $result = Docker @('run','--rm','--network','none','--entrypoint','sh','--mount',"type=volume,src=$Volume,dst=/source,readonly",'postgres:16-alpine','-c','tar -C /source -cf - . | sha256sum')
    return (($result -join '').Split(' ')[0])
}
function Assert-SourceStopped {
    $running = @(Docker @('ps','-q'))
    if ($running.Count) {
        $details = Docker (@('inspect') + $running) | ConvertFrom-Json
        foreach ($container in $details) {
            if (@($container.Mounts | Where-Object { $_.Name -in @($SourcePgVolume,$SourceRabbitVolume) }).Count) {
                throw "Source volume has a running writer: $($container.Name). No process was stopped."
            }
        }
    }
}
function Cold-Copy([string]$Source,[string]$Destination) {
    Docker @('volume','inspect',$Source) | Out-Null
    Docker @('volume','create',$Destination) | Out-Null
    Docker @('run','--rm','--network','none','--entrypoint','sh','--mount',"type=volume,src=$Source,dst=/source,readonly",'--mount',"type=volume,src=$Destination,dst=/clone",'postgres:16-alpine','-c','cp -a /source/. /clone/') | Out-Null
}
function Wait-Container([string]$Name,[string[]]$Command) {
    $deadline = (Get-Date).AddSeconds(90)
    do {
        & $dockerExecutable exec $Name @Command *> $null
        if ($LASTEXITCODE -eq 0) { return }
        $state = Docker @('inspect','--format','{{.State.Running}}',$Name)
        if ($state -ne 'true') { throw "Clone process exited: $Name; inspect its Docker logs." }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "Clone readiness timed out: $Name"
}
function Inventory([string]$PgContainer,[string]$Phase) {
    # Hash rows inside PostgreSQL. Neither the script nor evidence exports PII/event bodies.
    $sql = @'
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='10s';
SET LOCAL lock_timeout='1s';
SELECT jsonb_build_object('database',current_database(),'observedAt',transaction_timestamp(),'readOnly',current_setting('transaction_read_only'),'isolation',current_setting('transaction_isolation'),'version',current_setting('server_version'));
SELECT format('SELECT jsonb_build_object(''table'',%L,''rows'',count(*),''rowsMd5'',md5(coalesce(string_agg(md5(row_to_json(t)::text),'''' ORDER BY md5(row_to_json(t)::text)),''''))) FROM %I.%I t',table_name,table_schema,table_name) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name
\gexec
ROLLBACK;
'@
    $sql | Set-Content -LiteralPath (Join-Path $OutputDirectory 'inventory-readonly.sql') -Encoding utf8
    foreach ($database in @('mediflow_pharmacy','mediflow_report','mediflow_notification')) {
        $sql | & $dockerExecutable exec -i $PgContainer psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d $database |
            Set-Content -LiteralPath (Join-Path $OutputDirectory "$database-$Phase.jsonl") -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw "Read-only inventory failed: $database" }
    }
}

Push-Location $repoRoot
try {
    Assert-SourceStopped
    $pgHash = Source-Fingerprint $SourcePgVolume
    $rabbitHash = Source-Fingerprint $SourceRabbitVolume
    $pgClone = "$tag-pg"; $rabbitClone = "$tag-rabbit"
    Cold-Copy $SourcePgVolume $pgClone
    Cold-Copy $SourceRabbitVolume $rabbitClone
    Assert-SourceStopped
    if ((Source-Fingerprint $SourcePgVolume) -ne $pgHash -or (Source-Fingerprint $SourceRabbitVolume) -ne $rabbitHash) {
        throw 'Source changed during cold copy; clones must not be accepted.'
    }
    # Preserve all persisted node identities; never silently boot a new, empty Rabbit node.
    $nodeLines = Docker @('run','--rm','--network','none','--entrypoint','sh','--mount',"type=volume,src=$rabbitClone,dst=/source,readonly",'postgres:16-alpine','-c','find /source/mnesia -mindepth 2 -maxdepth 2 -name schema.DAT | sort')
    $nodes = @($nodeLines | ForEach-Object {
        if ($_ -notmatch '/mnesia/rabbit@([a-zA-Z0-9-]+)/schema\.DAT$') { throw 'Unrecognized persisted Rabbit node identity.' }
        $Matches[1]
    })
    if (!$nodes.Count) { throw 'No persisted broker identity found; refusing a fabricated empty broker.' }
    $pgContainer = "$tag-pg"
    $createdContainers.Add($pgContainer)
    Docker @('run','-d','--name',$pgContainer,'-p','127.0.0.1::5432','--mount',"type=volume,src=$pgClone,dst=/var/lib/postgresql/data",'postgres:16-alpine') | Out-Null
    Wait-Container $pgContainer -Command @('pg_isready','-U','postgres')
    Inventory $pgContainer 'before'
    $rabbitContainer = $null
    foreach ($node in $nodes) {
        if ($rabbitContainer) { Docker @('stop',$rabbitContainer) | Out-Null }
        $rabbitContainer = "$tag-rabbit-$node"
        $createdContainers.Add($rabbitContainer)
        Docker @('run','-d','--name',$rabbitContainer,'--hostname',$node,'-p','127.0.0.1::5672','--mount',"type=volume,src=$rabbitClone,dst=/var/lib/rabbitmq",'-e',"RABBITMQ_NODENAME=rabbit@$node",'rabbitmq:3.13-management-alpine') | Out-Null
        Wait-Container $rabbitContainer -Command @('rabbitmq-diagnostics','-q','check_running')
        Docker @('exec',$rabbitContainer,'rabbitmqctl','list_vhosts','--formatter','json') |
            Set-Content -LiteralPath (Join-Path $OutputDirectory "broker-$node-vhosts.json") -Encoding utf8
        $vhosts = Get-Content -LiteralPath (Join-Path $OutputDirectory "broker-$node-vhosts.json") -Raw | ConvertFrom-Json
        foreach ($vhost in $vhosts) {
            $safeName = [Convert]::ToHexString([Text.Encoding]::UTF8.GetBytes($vhost.name)).ToLowerInvariant()
            Docker @('exec',$rabbitContainer,'rabbitmqctl','list_queues','-p',$vhost.name,'name','messages_ready','messages_unacknowledged','consumers','durable','arguments','--formatter','json') |
                Set-Content -LiteralPath (Join-Path $OutputDirectory "broker-$node-$safeName-before.json") -Encoding utf8
        }
    }
    $manifest = [ordered]@{tag=$tag;startedAt=(Get-Date).ToString('o');sourceHead=(git rev-parse HEAD);
        dirtyWorkingTree=[bool](git status --porcelain);sourcePgVolume=$SourcePgVolume;sourceRabbitVolume=$SourceRabbitVolume;
        pgCloneVolume=$pgClone;rabbitCloneVolume=$rabbitClone;pgContainer=$pgContainer;rabbitContainer=$rabbitContainer;
        rabbitNodes=$nodes;sourcePgSha256=$pgHash;sourceRabbitSha256=$rabbitHash;
        scope='User-authorized local development volume rehearsal, not production activation';images=@{
            postgres=(Docker @('image','inspect','postgres:16-alpine','--format','{{.Id}}'));
            rabbit=(Docker @('image','inspect','rabbitmq:3.13-management-alpine','--format','{{.Id}}'))}}
    Save-Json $manifest 'source-manifest.json'
    $environment = @{
        H6_REHEARSAL_DIRECTORY=$OutputDirectory;H6_REHEARSAL_PG_CONTAINER=$pgContainer;H6_REHEARSAL_RABBIT_CONTAINER=$rabbitContainer;
        H6_REHEARSAL_PG_PORT=((Docker @('port',$pgContainer,'5432/tcp')) -split ':')[-1];
        H6_REHEARSAL_RABBIT_PORT=((Docker @('port',$rabbitContainer,'5672/tcp')) -split ':')[-1]
    }
    foreach ($entry in $environment.GetEnumerator()) {
        $savedEnvironment[$entry.Key]=[Environment]::GetEnvironmentVariable($entry.Key,'Process')
        [Environment]::SetEnvironmentVariable($entry.Key,$entry.Value,'Process')
    }
    # Use an exact source snapshot outside target/classes: an IDE compiler may otherwise
    # overwrite Maven classes with error stubs while it resolves the new lib classifiers.
    # No source file is rewritten and no existing target/evidence directory is cleaned.
    $buildRoot = Join-Path $OutputDirectory 'build'
    New-Item -ItemType Directory -Path $buildRoot -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $repoRoot 'pom.xml') -Destination $buildRoot
    $buildInputs = [Collections.Generic.List[object]]::new()
    foreach ($module in Get-ChildItem -LiteralPath (Join-Path $repoRoot 'backend') -Directory) {
        $modulePom = Join-Path $module.FullName 'pom.xml'
        if (!(Test-Path -LiteralPath $modulePom)) { continue }
        $destination = Join-Path $buildRoot "backend/$($module.Name)"
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
        Copy-Item -LiteralPath $modulePom -Destination $destination
        if ($module.Name -in @('common','report-service','notification-service','support-service')) {
            Copy-Item -LiteralPath (Join-Path $module.FullName 'src') -Destination $destination -Recurse
        }
    }
    foreach ($inputFile in Get-ChildItem -LiteralPath $buildRoot -File -Recurse) {
        $relative = [IO.Path]::GetRelativePath($buildRoot,$inputFile.FullName)
        $source = Join-Path $repoRoot $relative
        $hash = (Get-FileHash -LiteralPath $inputFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($hash -ne (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()) { throw 'Build source snapshot differs from the workspace.' }
        $buildInputs.Add([ordered]@{path=$relative;sha256=$hash})
    }
    Save-Json $buildInputs 'build-source-sha256.json'
    # Package current libraries/launchers; this profile executes only the operator IT.
    $mavenLog = Join-Path $OutputDirectory 'maven.log'
    Push-Location $buildRoot
    try {
        & mvn -q -pl backend/support-service -am -Phistorical-support-acceptance '-Dtest=__NoUnitTests__' '-Dsurefire.failIfNoSpecifiedTests=false' verify *> $mavenLog
        $mavenExit = $LASTEXITCODE
    } finally { Pop-Location }
    Inventory $pgContainer 'after'
    $oldPharmacy = @(Get-Content -LiteralPath (Join-Path $OutputDirectory 'mediflow_pharmacy-before.jsonl') | ConvertFrom-Json | Where-Object table)
    $newPharmacy = @(Get-Content -LiteralPath (Join-Path $OutputDirectory 'mediflow_pharmacy-after.jsonl') | ConvertFrom-Json | Where-Object table)
    if (($oldPharmacy | ConvertTo-Json -Compress) -ne ($newPharmacy | ConvertTo-Json -Compress)) { throw 'Pharmacy rows changed during a Support-only rehearsal.' }
    Docker @('exec',$rabbitContainer,'rabbitmqctl','list_queues','-p','/','name','messages_ready','messages_unacknowledged','consumers','durable','arguments','--formatter','json') |
        Set-Content -LiteralPath (Join-Path $OutputDirectory 'broker-after.json') -Encoding utf8
    Assert-SourceStopped
    $pgAfter = Source-Fingerprint $SourcePgVolume; $rabbitAfter = Source-Fingerprint $SourceRabbitVolume
    $unchanged = $pgAfter -eq $pgHash -and $rabbitAfter -eq $rabbitHash
    Save-Json ([ordered]@{mavenExit=$mavenExit;sourceVolumesUnchanged=$unchanged;sourcePgBefore=$pgHash;sourcePgAfter=$pgAfter;
        sourceRabbitBefore=$rabbitHash;sourceRabbitAfter=$rabbitAfter;pharmacyUnchanged=$true;completedAt=(Get-Date).ToString('o')}) 'reconciliation.json'
    $acceptanceXml = Join-Path $buildRoot 'backend/support-service/target/failsafe-reports/TEST-com.mediflow.support.HistoricalSupportCutoverIT.xml'
    Copy-Item -LiteralPath $acceptanceXml -Destination $OutputDirectory
    if ($mavenExit -ne 0 -or !$unchanged) { throw "Historical rehearsal failed; see $OutputDirectory" }
    Copy-Item -LiteralPath $acceptanceXml -Destination (Join-Path $repoRoot 'backend/support-service/target/failsafe-reports')
    Write-Output "Historical Support rehearsal PASS. Evidence: $OutputDirectory"
} finally {
    foreach ($container in $createdContainers) {
        & $dockerExecutable stop $container *> $null
    }
    foreach ($entry in $savedEnvironment.GetEnumerator()) { [Environment]::SetEnvironmentVariable($entry.Key,$entry.Value,'Process') }
    Pop-Location
}
