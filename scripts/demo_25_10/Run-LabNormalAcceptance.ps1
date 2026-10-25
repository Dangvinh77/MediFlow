[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$runDirectory = Join-Path $PSScriptRoot ('evidence/' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null

function Invoke-CheckedMaven {
    param([string]$Name, [string[]]$MavenArguments)
    & mvn @MavenArguments 2>&1 | Tee-Object -FilePath (Join-Path $runDirectory ($Name + '.log'))
    if ($LASTEXITCODE -ne 0) { throw "$Name failed; inspect $runDirectory. This is not a successful acceptance run." }
}

Push-Location $repoRoot
try {
    & git rev-parse HEAD | Set-Content (Join-Path $runDirectory 'base-sha.txt')
    & git diff -- backend/lab-service | Set-Content (Join-Path $runDirectory 'lab-working.patch')
    & docker info --format '{{.ServerVersion}}' 2>&1 | Tee-Object -FilePath (Join-Path $runDirectory 'docker.log')
    if ($LASTEXITCODE -ne 0) { throw 'Docker engine must be running. No database or delivery state was changed.' }

    Invoke-CheckedMaven 'adapter' @('-q', '-pl', 'backend/lab-service', '-am',
        '-Dtest=LabBillingExecutionAuthorityAdapterTest', '-Dsurefire.failIfNoSpecifiedTests=false', 'test')
    Invoke-CheckedMaven 'lab-suite' @('-q', '-pl', 'backend/lab-service', '-am', 'test')
    Invoke-CheckedMaven 'package' @('-q', '-pl',
        'backend/core-service,backend/gateway,backend/eureka-server,backend/clinical-service,backend/lab-service,backend/billing-service',
        '-am', '-DskipTests', 'package')

    $billingJar = Join-Path $repoRoot 'backend/billing-service/target/billing-service-0.0.1-SNAPSHOT.jar'
    $labTarget = Join-Path $repoRoot 'backend/lab-service/target'
    # The profile supplies the other five JAR paths; Billing must be supplied explicitly.
    $packagedStartedAt = [DateTime]::UtcNow
    Invoke-CheckedMaven 'packaged-normal' @('-q', '-pl', 'backend/lab-service', '-am', '-Ppackaged-slice',
        '-Dtest=__NoUnitTests__', '-Dsurefire.failIfNoSpecifiedTests=false',
        "-Dlab.acceptance.billing.jar=$billingJar", 'verify')

    $reportPath = Join-Path $labTarget 'failsafe-reports/TEST-com.mediflow.lab.acceptance.PackagedOutpatientLabAcceptanceIT.xml'
    foreach ($freshFile in @($reportPath, (Join-Path $labTarget 'paid-lab-evidence.json'),
            (Join-Path $labTarget 'paid-lab-clearance.json'), (Join-Path $labTarget 'paid-lab-authority.json'))) {
        if ((Get-Item -LiteralPath $freshFile).LastWriteTimeUtc -lt $packagedStartedAt) {
            throw "Stale acceptance output: $freshFile. This is not evidence from this run."
        }
    }
    [xml]$report = Get-Content $reportPath -Raw
    if ([int]$report.testsuite.tests -ne 1 -or [int]$report.testsuite.failures -ne 0 -or
        [int]$report.testsuite.errors -ne 0 -or [int]$report.testsuite.skipped -ne 0) {
        throw 'Packaged acceptance did not execute one successful, unskipped normal flow.'
    }
    Copy-Item (Join-Path $labTarget 'paid-lab-evidence.json') $runDirectory
    Copy-Item (Join-Path $labTarget 'paid-lab-clearance.json') $runDirectory
    Copy-Item (Join-Path $labTarget 'paid-lab-authority.json') $runDirectory
    Copy-Item (Join-Path $labTarget 'billing-lifecycle-queues.txt') $runDirectory
    Copy-Item (Join-Path $labTarget 'integrated-recovery') $runDirectory -Recurse
    Copy-Item (Join-Path $labTarget 'failsafe-reports') $runDirectory -Recurse
    Copy-Item (Join-Path $labTarget 'surefire-reports') $runDirectory -Recurse
    'PASS: real paid EXAM -> Clinical order -> Lab request -> payment -> READY -> IN_PROGRESS -> COMPLETED -> Clinical attachment.' |
        Tee-Object -FilePath (Join-Path $runDirectory 'result.txt')
    Write-Host "Evidence: $runDirectory"
} catch {
    'FAILED: ' + $_.Exception.Message | Set-Content (Join-Path $runDirectory 'result.txt')
    if ($null -ne $packagedStartedAt) {
        foreach ($diagnostic in @('paid-lab-authority.json', 'paid-lab-clearance.json',
                'failsafe-reports/com.mediflow.lab.acceptance.PackagedOutpatientLabAcceptanceIT.txt')) {
            $diagnosticPath = Join-Path $labTarget $diagnostic
            if ((Test-Path -LiteralPath $diagnosticPath) -and
                (Get-Item -LiteralPath $diagnosticPath).LastWriteTimeUtc -ge $packagedStartedAt) {
                Copy-Item -LiteralPath $diagnosticPath -Destination $runDirectory
            }
        }
    }
    throw
} finally {
    Pop-Location
}
