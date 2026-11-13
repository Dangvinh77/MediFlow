[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Backend', 'Frontend', 'Mobile', 'Seed', 'Clean', 'Stop')][string]$Action,
    [switch]$Preview,
    [switch]$LocalOnly,
    [string]$LanAddress,
    # Frontend only: Gateway on another PC, e.g. http://172.16.2.55:28080 (default: this PC).
    [string]$GatewayUrl,
    [switch]$Fast,
    [switch]$Rebuild,
    [switch]$ResetData,
    # Backend only: also open the RabbitMQ UI (port 28672) to other PCs on the local subnet.
    [switch]$BrokerUiLan,
    [ValidateRange(1, 3)][int]$StartupParallelism = 2,
    # Mobile only: chrome (default), android (emulator) or device (phone on the same Wi-Fi).
    [ValidateSet('chrome', 'android', 'device')][string]$Target = 'chrome',
    [string]$Device,
    # Seed only: create Loc's unpaid exam-fee registration now (it holds the slot 15 minutes).
    [switch]$ExamChargeNow
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
. (Join-Path $PSScriptRoot 'Demo-Lan.ps1')

function Require-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing command: $Name. Install it and reopen this window."
    }
}

function Invoke-Docker([string[]]$Arguments) {
    $output = & docker @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Docker failed: $($Arguments -join ' ')`n$($output -join "`n")" }
    return $output
}

# The same UI switches as the backend demo profile; build-time defines for every mobile target.
function Get-MobileDemoDefines {
    return @(
        '--dart-define=PHARMACY_MODE=outpatient',
        '--dart-define=MEDIFLOW_OUTPATIENT_UI_ENABLED=true',
        '--dart-define=MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED=true',
        '--dart-define=MEDIFLOW_SCHEDULING_UI_ENABLED=true',
        '--dart-define=MEDIFLOW_OUTPATIENT_QUOTES_UI_ENABLED=true',
        '--dart-define=MEDIFLOW_ZERO_PAYABLE_UI_ENABLED=true',
        '--dart-define=MEDIFLOW_CLINIC_INTAKE_UI_ENABLED=true')
}

function Assert-GatewayReady {
    try {
        $health = Invoke-RestMethod -Uri 'http://127.0.0.1:28080/actuator/health' -TimeoutSec 5
        if ($health.status -eq 'UP') { return }
    } catch { }
    throw 'Gateway 28080 is not ready. Run start-backend.bat first and wait for READY.'
}

function Invoke-DemoBuild([switch]$Force) {
    $helper = Join-Path $PSScriptRoot 'demo_build.py'
    $timer = [Diagnostics.Stopwatch]::StartNew()
    if (-not $Force) {
        & python $helper check
        if ($LASTEXITCODE -eq 0) {
            Write-Host 'Using verified current demo JARs; Maven build skipped.'
            Write-Host "TIMING build check: $([Math]::Round($timer.Elapsed.TotalSeconds, 1))s"
            return
        }
        if ($LASTEXITCODE -ne 10) { throw 'Could not validate the demo build cache.' }
    }
    Require-Command 'mvn'
    $inputs = & python $helper fingerprint
    if ($LASTEXITCODE -ne 0) { throw 'Could not fingerprint the Maven build inputs.' }
    Write-Host 'Building the eight demo applications...'
    # A clock correction can leave future-dated classes that Maven considers current.
    # Only rebuilds clear outputs; the verified content-hash fast path above still skips Maven.
    & mvn -q -pl backend/core-service,backend/clinical-service,backend/lab-service,backend/pharmacy-service,backend/billing-service,backend/support-service,backend/gateway,backend/eureka-server -am '-DskipTests' clean package
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed. See Maven output above.' }
    & python $helper record --expected-inputs $inputs
    if ($LASTEXITCODE -ne 0) { throw 'Could not record the successful demo build.' }
    Write-Host "TIMING build: $([Math]::Round($timer.Elapsed.TotalSeconds, 1))s"
}

function Read-Containers {
    # Save image references from the listing itself: the runner may remove a
    # container before inspect, leaving no container metadata to recover later.
    $rows = @(Invoke-Docker -Arguments @('container', 'ls', '--all', '--no-trunc', '--format', '{{json .}}'))
    foreach ($row in $rows) {
        $listed = $row | ConvertFrom-Json
        $workingDir = @($listed.Labels -split ',' | Where-Object {
            $_ -like 'com.docker.compose.project.working_dir=*'
        }) | Select-Object -First 1
        if ($workingDir) { $workingDir = $workingDir.Substring('com.docker.compose.project.working_dir='.Length) }
        if ($listed.Names -match '^mediflow-' -or (Test-ProjectPath $workingDir)) {
            $script:capturedImageReferences += $listed.Image
        }
        # Only a missing object is benign; daemon/permission/transport errors
        # must abort cleanup before any destructive action.
        $savedPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            $output = & docker container inspect $listed.ID 2>&1
            $code = $LASTEXITCODE
        } finally { $ErrorActionPreference = $savedPreference }
        if ($code -ne 0) {
            $message = (@($output | ForEach-Object { $_.ToString().Trim() } |
                    Where-Object { $_ -and $_ -ne '[]' }) -join "`n").Trim()
            if ($message -match ('^Error response from daemon: No such (container|object): ' +
                    [regex]::Escape($listed.ID) + '$')) { continue }
            throw "Docker failed: container inspect $($listed.ID)`n$message"
        }
        $output | Out-String | ConvertFrom-Json
    }
}

function Test-ProjectPath([string]$Path) {
    if (-not $Path) { return $false }
    return [string]::Equals($Path.TrimEnd('\', '/'), $repoRoot.TrimEnd('\', '/'),
        [StringComparison]::OrdinalIgnoreCase)
}

function Test-OwnedContainer($Container) {
    $name = $Container.Name.TrimStart('/')
    $workingDir = $Container.Config.Labels.'com.docker.compose.project.working_dir'
    return $name -match '^mediflow-' -or (Test-ProjectPath $workingDir)
}

function Test-DemoJar($Process, [string]$Module) {
    if (-not $Process -or $Process.Name -ne 'java.exe' -or -not $Process.CommandLine) { return $false }
    $match = [regex]::Match($Process.CommandLine, '(?:^|\s)-jar\s+(?:"(?<jar>[^"]+)"|(?<jar>\S+))(?=\s|$)')
    if (-not $match.Success) { return $false }
    $jar = $match.Groups['jar'].Value
    if (-not [IO.Path]::IsPathRooted($jar)) { return $false }
    $expected = Join-Path $repoRoot "backend\$Module\target\$Module-0.0.1-SNAPSHOT.jar"
    return [string]::Equals([IO.Path]::GetFullPath($jar), $expected, [StringComparison]::OrdinalIgnoreCase)
}

function Test-OrphanProcess($Process) {
    if (-not $Process.CreationDate -or -not $Process.ParentProcessId) { return $false }
    $parent = Get-CimInstance Win32_Process -Filter "ProcessId = $($Process.ParentProcessId)" -ErrorAction Stop
    # A newer parent means Windows reused the original runner's PID.
    return -not $parent -or ($parent.CreationDate -and $parent.CreationDate -gt $Process.CreationDate)
}

function Stop-OrphanDemoProcesses {
    $modulesByPort = @{
        28761 = 'eureka-server'; 28089 = 'core-service'; 28082 = 'clinical-service'
        28084 = 'lab-service'; 28085 = 'pharmacy-service'; 28086 = 'billing-service'
        28087 = 'support-service'; 28080 = 'gateway'
    }
    $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object {
        $modulesByPort.ContainsKey([int]$_.LocalPort)
    })
    if ($listeners.Count -eq 0) { return }
    $processes = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction Stop)
    foreach ($process in $processes) {
        $listener = $listeners | Where-Object {
            $_.OwningProcess -eq $process.ProcessId -and
            (Test-DemoJar $process $modulesByPort[[int]$_.LocalPort])
        } | Select-Object -First 1
        if (-not $listener -or -not (Test-OrphanProcess $process)) { continue }
        $module = $modulesByPort[[int]$listener.LocalPort]
        $description = "$module (PID $($process.ProcessId), port $($listener.LocalPort))"
        if ($Preview) { Write-Host "Preview: would stop orphan demo process: $description"; continue }

        # Re-read identity immediately before stopping; never trust a saved PID alone.
        $current = Get-CimInstance Win32_Process -Filter "ProcessId = $($process.ProcessId)" -ErrorAction Stop
        if (-not (Test-DemoJar $current $module) -or
            $current.CreationDate -ne $process.CreationDate -or -not (Test-OrphanProcess $current)) { continue }
        $liveProcess = Get-Process -Id $current.ProcessId -ErrorAction SilentlyContinue
        if (-not $liveProcess) { continue }
        try {
            # Open the process handle before checking its start time, pinning its identity.
            $null = $liveProcess.Handle
            $difference = ($liveProcess.StartTime.ToUniversalTime() - $current.CreationDate.ToUniversalTime()).TotalMilliseconds
            if ([Math]::Abs($difference) -ge 1) { continue }
            Write-Host "Stopping orphan demo process: $description"
            Stop-Process -InputObject $liveProcess -ErrorAction Stop
        } finally {
            if ($liveProcess.PSObject.Methods['Dispose']) { $liveProcess.Dispose() }
        }
    }
}

function Stop-Rehearsal([switch]$PreserveInfrastructure) {
    $manifestPath = Join-Path $PSScriptRoot 'evidence\active-rehearsal.local.json'
    if (-not (Test-Path -LiteralPath $manifestPath)) { return }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($PreserveInfrastructure -and -not $manifest.persistent) {
        throw 'This session is disposable. Close its backend window normally; use start-backend.bat -Fast for sessions that retain data.'
    }
    $live = @(Read-Containers | Where-Object {
        $_.Name.TrimStart('/') -in $manifest.containers -and (Test-OwnedContainer $_)
    })
    # A persistent runner publishes its stop identity before it creates containers.
    if ($live.Count -eq 0 -and -not $manifest.persistent) { return }
    $evidenceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))
    $evidence = [IO.Path]::GetFullPath([string]$manifest.evidence)
    $stop = [IO.Path]::GetFullPath([string]$manifest.stopFile)
    if (-not $evidence.StartsWith($evidenceRoot + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase) -or
        -not [string]::Equals($stop, (Join-Path $evidence 'stop'), [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Active demo stop file is outside this repository. Stop its backend window first.'
    }
    if ($Preview) { Write-Host 'Preview: would request demo JVM shutdown through its stop file.'; return }
    if ($manifest.persistent) { Write-Host 'Stopping demo applications; database, broker and images will be kept.' }
    else { Write-Host 'Stopping the active demo backend and its disposable containers...' }
    New-Item -ItemType File -Path $stop -Force | Out-Null
    $deadline = (Get-Date).AddSeconds(150)
    do {
        $remaining = @(Read-Containers | Where-Object { $_.Id -in $live.Id })
        if ($remaining.Count -eq 0 -and -not $manifest.persistent) { return }
        # A closed/crashed runner can leave orphan containers. Never kill a reused PID.
        $javaProcesses = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
            $_.ProcessId -in $manifest.pids -and $_.CommandLine -and
            (-not $manifest.runnerPid -or $_.ParentProcessId -eq $manifest.runnerPid) -and
            $_.CommandLine.IndexOf($repoRoot, [StringComparison]::OrdinalIgnoreCase) -ge 0
        })
        $runnerAlive = $false
        if ($manifest.persistent -and $manifest.runnerPid) {
            $runner = Get-CimInstance Win32_Process -Filter "ProcessId = $($manifest.runnerPid)"
            $runnerPath = Join-Path $repoRoot 'scripts\demo_25_10\rehearse_snapshot.py'
            $runnerAlive = $runner -and $runner.CommandLine -and
                $runner.CommandLine.IndexOf($runnerPath, [StringComparison]::OrdinalIgnoreCase) -ge 0
        }
        if ($javaProcesses.Count -eq 0 -and -not $runnerAlive) { return }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw 'Demo is still stopping. Close its backend window, then run cleanup again.'
}

function Clean-ProjectDocker {
    $script:capturedImageReferences = @()
    # Capture image identities before the runner removes its own containers.
    $before = @(Read-Containers)
    $ownedBefore = @($before | Where-Object { Test-OwnedContainer $_ })
    $candidateIds = @($ownedBefore | ForEach-Object { $_.Image })
    Stop-OrphanDemoProcesses
    Stop-Rehearsal

    $compose = Invoke-Docker -Arguments @('compose', '-f', (Join-Path $repoRoot 'docker-compose.yml'),
        'config', '--format', 'json') | Out-String | ConvertFrom-Json
    $buildTags = @($compose.services.PSObject.Properties | Where-Object { $_.Value.build } |
        ForEach-Object {
            if ($_.Value.image) { $_.Value.image }
            else { "$($compose.name)-$($_.Name):latest" }
        })
    $imageIds = @(Invoke-Docker -Arguments @('image', 'ls', '--quiet', '--no-trunc') | Sort-Object -Unique)
    $images = @()
    if ($imageIds.Count -gt 0) {
        $parsed = Invoke-Docker -Arguments (@('image', 'inspect') + $imageIds) | Out-String | ConvertFrom-Json
        $images = @($parsed)
    }
    foreach ($image in $images) {
        if ($image.Id -in $script:capturedImageReferences -or
                @($image.RepoTags | Where-Object { $_ -in $script:capturedImageReferences }).Count -gt 0) {
            $candidateIds += $image.Id
        }
        $ownedTags = @($image.RepoTags | Where-Object {
            $_ -in $buildTags -or $_ -match '(^|/)mediflow[-_/:]'
        })
        if ($ownedTags.Count -gt 0 -or (Test-ProjectPath $image.Config.Labels.'com.docker.compose.project.working_dir')) {
            $candidateIds += $image.Id
        }
    }
    $owned = @(Read-Containers | Where-Object { Test-OwnedContainer $_ })
    foreach ($container in $owned) {
        Write-Host "Remove container: $($container.Name.TrimStart('/'))"
        if (-not $Preview) { Invoke-Docker -Arguments @('container', 'rm', '--force', $container.Id) | Out-Null }
    }
    # Images used by another project survive, including stopped containers' images.
    $protected = @(Read-Containers | Where-Object {
        -not $Preview -or -not (Test-OwnedContainer $_)
    } | ForEach-Object { $_.Image })
    foreach ($id in @($candidateIds | Sort-Object -Unique)) {
        if ($id -in $protected) { Write-Host "Keep shared image: $id"; continue }
        $image = $images | Where-Object { $_.Id -eq $id } | Select-Object -First 1
        if (-not $image) { continue }
        # Remove explicit tags without --force; unrelated aliases are preserved.
        $tags = @($image.RepoTags | Where-Object { $_ -and $_ -ne '<none>:<none>' })
        if ($tags.Count -eq 0) {
            Write-Host "Remove untagged project image: $id"
            if (-not $Preview) { Invoke-Docker -Arguments @('image', 'rm', $id) | Out-Null }
        } else {
            foreach ($tag in $tags) {
                if ($tag -in $buildTags -or $tag -match '(^|/)mediflow[-_/:]' -or
                    (($id -in $ownedBefore.Image -or $id -in $script:capturedImageReferences -or
                        $tag -in $script:capturedImageReferences) -and $tag -match '^(postgres|rabbitmq):')) {
                    Write-Host "Remove image tag: $tag"
                    if (-not $Preview) { Invoke-Docker -Arguments @('image', 'rm', $tag) | Out-Null }
                }
            }
        }
    }
    Write-Host 'Finished. Database volumes and other projects are preserved. No global prune was run.'
}

Push-Location $repoRoot
try {
    if ($ExamChargeNow -and $Action -ne 'Seed') { throw '-ExamChargeNow is supported only by seed-demo.bat.' }
    if ($Preview -and $Action -ne 'Clean') { throw '-Preview is supported only by clean-docker.bat.' }
    if ($Fast -and $Rebuild) { throw 'Use either -Fast or -Rebuild.' }
    if (($Fast -or $Rebuild -or $ResetData -or $BrokerUiLan) -and $Action -ne 'Backend') { throw 'Build/data/broker options are supported only by start-backend.bat.' }
    if ($BrokerUiLan -and $LocalOnly) { throw 'Use either -LocalOnly or -BrokerUiLan, not both.' }
    if ($Action -in @('Backend', 'Clean', 'Stop')) {
        Require-Command 'docker'
        & docker info *> $null
        if ($LASTEXITCODE -ne 0) { throw 'Open Docker Desktop and wait for its Engine, then run this file again.' }
        Invoke-Docker -Arguments @('compose', 'version') | Out-Null
    }
    switch ($Action) {
        'Backend' {
            if (-not (Test-Path -LiteralPath (Join-Path $repoRoot 'smtp.local.env'))) {
                throw 'Copy smtp.env.example to smtp.local.env and fill the SMTP configuration before starting the backend.'
            }
            . (Join-Path $repoRoot 'scripts\load-smtp-local.ps1') -ConfigPath (Join-Path $repoRoot 'smtp.local.env')
            $network = Resolve-DemoNetwork -Configurations @(Get-NetIPConfiguration) -LanAddress $LanAddress -LocalOnly:$LocalOnly
            if (-not $LocalOnly) {
                $extraPorts = @()
                if ($BrokerUiLan) { $extraPorts = @(28672) }
                Enable-DemoLanFirewall -InterfaceAlias $network.InterfaceAlias -ExtraPorts $extraPorts
            }
            if ($env:MEDIFLOW_PUBLIC_WEB_URL -match '^http://(localhost|127\.0\.0\.1)(:3000)?/?$') {
                $env:MEDIFLOW_PUBLIC_WEB_URL = "http://$($network.Address):3000"
            }
            Require-Command 'java'
            Require-Command 'python'
            $persistent = $Fast -or $Rebuild -or $ResetData
            Invoke-DemoBuild -Force:($Rebuild -or -not $persistent)
            if ($persistent) { Write-Host 'Starting retained demo workspace. Existing data will be reused unless -ResetData was supplied.' }
            else { Write-Host 'Restoring a fresh disposable demo. Use -Fast for retained data and verified build reuse.' }
            Write-Host 'Wait for READY; keep this window open. Applications stop after two hours.'
            Write-Host 'Gateway: http://127.0.0.1:28080.'
            Write-Host "Mobile Gateway: http://$($network.Address):28080/api"
            Write-Host "Email/web origin: $env:MEDIFLOW_PUBLIC_WEB_URL"
            Write-Host 'Mobile app: run start-mobile.bat after READY (-Target chrome | android | device).'
            if ($BrokerUiLan) { Write-Host "RabbitMQ UI: http://$($network.Address):28672 (rehearsal / rehearsal-local)" }
            else { Write-Host 'RabbitMQ UI: http://127.0.0.1:28672 (rehearsal / rehearsal-local); add -BrokerUiLan for other PCs.' }
            $networkArguments = @()
            if (-not $LocalOnly) { $networkArguments += '--lan' }
            if ($BrokerUiLan) { $networkArguments += '--broker-ui-lan' }
            if ($persistent) { $networkArguments += '--persistent' }
            if ($ResetData) { $networkArguments += '--reset-data' }
            & python -u (Join-Path $PSScriptRoot 'rehearse_snapshot.py') --scheduling --prepare-ui-demo --prepare-report-demo --keep --startup-parallelism $StartupParallelism @networkArguments
            if ($LASTEXITCODE -ne 0) { throw 'Demo startup failed. See scripts/demo_25_10/evidence/workspace or snapshot-*/ logs.' }
        }
        'Frontend' {
            $network = Resolve-DemoNetwork -Configurations @(Get-NetIPConfiguration) -LanAddress $LanAddress -LocalOnly:$LocalOnly
            if (-not $LocalOnly) { Enable-DemoLanFirewall -InterfaceAlias $network.InterfaceAlias }
            Require-Command 'node'
            Require-Command 'pnpm'
            $env:GATEWAY_URL = Resolve-DemoGatewayUrl $GatewayUrl
            Write-Host "Gateway: $env:GATEWAY_URL"
            $env:MEDIFLOW_DEV_ALLOWED_ORIGINS = $network.Address
            $env:MEDIFLOW_SCHEDULING_UI_ENABLED = 'true'
            $env:MEDIFLOW_OUTPATIENT_QUOTES_UI_ENABLED = 'true'
            $env:MEDIFLOW_ZERO_PAYABLE_UI_ENABLED = 'true'
            $env:MEDIFLOW_OUTPATIENT_UI_ENABLED = 'true'
            $env:MEDIFLOW_PHARMACY_PRESCRIPTION_MODE = 'outpatient'
            $env:MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED = 'true'
            # Backend demo runs clinic intake v2: the nurse issues the room ticket before the exam.
            $env:MEDIFLOW_CLINIC_INTAKE_UI_ENABLED = 'true'
            Set-Location (Join-Path $repoRoot 'frontend')
            & pnpm install --frozen-lockfile
            if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
            Write-Host "Frontend: http://$($network.Address):3000. Start backend first and wait for READY."
            $bindAddress = '0.0.0.0'
            if ($LocalOnly) { $bindAddress = '127.0.0.1' }
            & pnpm dev --hostname $bindAddress --port 3000
            if ($LASTEXITCODE -ne 0) { throw 'Frontend stopped with an error. Check the output above.' }
        }
        'Mobile' {
            Require-Command 'flutter'
            Assert-GatewayReady
            $defines = Get-MobileDemoDefines
            if ($Target -eq 'device') {
                # A phone reaches the Gateway over Wi-Fi; HTTP on the LAN needs the insecure switch.
                $network = Resolve-DemoNetwork -Configurations @(Get-NetIPConfiguration) -LanAddress $LanAddress -LocalOnly:$false
                Enable-DemoLanFirewall -InterfaceAlias $network.InterfaceAlias
                $gateway = "http://$($network.Address):28080/api"
                Write-Host "Phone Gateway: $gateway (check http://$($network.Address):28080/actuator/health on the phone)"
                Set-Location (Join-Path $repoRoot 'mobile')
                & flutter pub get
                if ($LASTEXITCODE -ne 0) { throw 'flutter pub get failed.' }
                $deviceArguments = @()
                if ($Device) { $deviceArguments = @('-d', $Device) }
                & flutter run @deviceArguments "--dart-define=GATEWAY_URL=$gateway" '--dart-define=ALLOW_INSECURE_HTTP=true' @defines
            } else {
                # run_dev.ps1 proxies Chrome to the Gateway and maps 127.0.0.1 to 10.0.2.2 for the emulator.
                & (Join-Path $repoRoot 'mobile\tool\run_dev.ps1') -Target $Target -Demo -Gateway 'http://127.0.0.1:28080'
            }
            if ($LASTEXITCODE -ne 0) { throw 'Mobile app stopped with an error. Check the output above.' }
        }
        'Seed' {
            Require-Command 'python'
            Assert-GatewayReady
            $seedArguments = @()
            if ($ExamChargeNow) { $seedArguments += '--exam-charge-now' }
            & python -u (Join-Path $PSScriptRoot 'seed_demo.py') @seedArguments
            if ($LASTEXITCODE -ne 0) { throw 'Demo seeding failed. See the output above.' }
        }
        'Clean' { Clean-ProjectDocker }
        'Stop' {
            Stop-OrphanDemoProcesses
            Stop-Rehearsal -PreserveInfrastructure
            Write-Host 'Backend stopped. Retained demo data and images were not removed.'
        }
    }
    exit 0
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
