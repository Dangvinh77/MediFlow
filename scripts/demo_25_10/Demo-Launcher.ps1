[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Backend', 'Frontend', 'Clean')][string]$Action,
    [switch]$Preview
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))

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

function Stop-Rehearsal {
    $manifestPath = Join-Path $PSScriptRoot 'evidence\active-rehearsal.local.json'
    if (-not (Test-Path -LiteralPath $manifestPath)) { return }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $live = @(Read-Containers | Where-Object {
        $_.Name.TrimStart('/') -in $manifest.containers -and (Test-OwnedContainer $_)
    })
    if ($live.Count -eq 0) { return }
    $evidenceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'evidence'))
    $evidence = [IO.Path]::GetFullPath([string]$manifest.evidence)
    $stop = [IO.Path]::GetFullPath([string]$manifest.stopFile)
    if (-not $evidence.StartsWith($evidenceRoot + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase) -or
        -not [string]::Equals($stop, (Join-Path $evidence 'stop'), [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Active demo stop file is outside this repository. Stop its backend window first.'
    }
    if ($Preview) { Write-Host 'Preview: would request demo JVM shutdown through its stop file.'; return }
    Write-Host 'Stopping the active demo backend and its disposable containers...'
    New-Item -ItemType File -Path $stop -Force | Out-Null
    $deadline = (Get-Date).AddSeconds(150)
    do {
        $remaining = @(Read-Containers | Where-Object { $_.Id -in $live.Id })
        if ($remaining.Count -eq 0) { return }
        # A closed/crashed runner can leave orphan containers. Never kill a reused PID.
        $javaProcesses = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
            $_.ProcessId -in $manifest.pids -and $_.CommandLine -and
            $_.CommandLine.IndexOf($repoRoot, [StringComparison]::OrdinalIgnoreCase) -ge 0
        })
        if ($javaProcesses.Count -eq 0) { return }
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
    if ($Preview -and $Action -ne 'Clean') { throw '-Preview is supported only by clean-docker.bat.' }
    if ($Action -in @('Backend', 'Clean')) {
        Require-Command 'docker'
        & docker info *> $null
        if ($LASTEXITCODE -ne 0) { throw 'Open Docker Desktop and wait for its Engine, then run this file again.' }
        Invoke-Docker -Arguments @('compose', 'version') | Out-Null
    }
    switch ($Action) {
        'Backend' {
            Require-Command 'java'
            Require-Command 'mvn'
            Require-Command 'python'
            Write-Host 'Building the eight demo applications. First startup may take several minutes...'
            & mvn -q -pl backend/core-service,backend/clinical-service,backend/lab-service,backend/pharmacy-service,backend/billing-service,backend/support-service,backend/gateway,backend/eureka-server -am '-DskipTests' package
            if ($LASTEXITCODE -ne 0) { throw 'Backend build failed. See Maven output above.' }
            Write-Host 'Restoring sample data. Wait for READY; keep this window open.'
            Write-Host 'Gateway: http://127.0.0.1:28080. This disposable demo stops after two hours.'
            & python -u (Join-Path $PSScriptRoot 'rehearse_snapshot.py') --scheduling --prepare-ui-demo --keep
            if ($LASTEXITCODE -ne 0) { throw 'Demo startup failed. See scripts/demo_25_10/evidence/snapshot-*/ logs.' }
        }
        'Frontend' {
            Require-Command 'node'
            Require-Command 'pnpm'
            $env:GATEWAY_URL = 'http://127.0.0.1:28080'
            $env:MEDIFLOW_SCHEDULING_UI_ENABLED = 'true'
            $env:MEDIFLOW_OUTPATIENT_QUOTES_UI_ENABLED = 'true'
            $env:MEDIFLOW_ZERO_PAYABLE_UI_ENABLED = 'true'
            $env:MEDIFLOW_OUTPATIENT_UI_ENABLED = 'true'
            $env:MEDIFLOW_PHARMACY_PRESCRIPTION_MODE = 'outpatient'
            $env:MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED = 'true'
            Set-Location (Join-Path $repoRoot 'frontend')
            & pnpm install --frozen-lockfile
            if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
            Write-Host 'Frontend: http://127.0.0.1:3000. Start backend first and wait for READY.'
            & pnpm dev --hostname 127.0.0.1 --port 3000
            if ($LASTEXITCODE -ne 0) { throw 'Frontend stopped with an error. Check the output above.' }
        }
        'Clean' { Clean-ProjectDocker }
    }
    exit 0
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
