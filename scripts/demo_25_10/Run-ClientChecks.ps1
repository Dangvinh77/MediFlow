[CmdletBinding()]
param(
    [ValidateSet('Web', 'Mobile', 'Both')][string]$Client = 'Both',
    [string]$Gateway = 'http://127.0.0.1:28080'
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$evidence = Join-Path $PSScriptRoot ('evidence/clients-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
function Checked {
    param([string]$Program, [string[]]$Arguments)
    & $Program @Arguments 2>&1 | Tee-Object -FilePath (Join-Path $evidence 'checks.log') -Append
    if ($LASTEXITCODE -ne 0) { throw "$Program $($Arguments -join ' ') failed" }
}
$previousNodeOptions = $env:NODE_OPTIONS
try {
    if ($Client -in @('Web', 'Both')) {
        Push-Location (Join-Path $repo 'frontend')
        try {
            Checked node @('--version')
            # Node 25+ exposes a native localStorage getter which conflicts with jsdom.
            $major = [int]((& node -p 'process.versions.node.split(".")[0]') | Select-Object -Last 1)
            if ($major -ge 25) { $env:NODE_OPTIONS = "$previousNodeOptions --no-experimental-webstorage".Trim() }
            $env:GATEWAY_URL = $Gateway
            $env:MEDIFLOW_OUTPATIENT_UI_ENABLED = 'true'
            $env:MEDIFLOW_PHARMACY_PRESCRIPTION_MODE = 'outpatient'
            $env:MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED = 'true'
            Checked pnpm @('typecheck')
            Checked pnpm @('lint')
            # Bound workers while six JVMs/Flutter may run; allow normal test work under load.
            Checked pnpm @('exec', 'vitest', 'run', '--maxWorkers=2', '--testTimeout=15000')
            Checked pnpm @('build')
        } finally { Pop-Location }
    }
    if ($Client -in @('Mobile', 'Both')) {
        Push-Location (Join-Path $repo 'mobile')
        try {
            Checked flutter @('pub', 'get')
            Checked dart @('format', '--output=none', '--set-exit-if-changed', 'lib', 'test', 'tool')
            Checked flutter @('analyze')
            Checked flutter @('test', 'test', 'packages/mediflow_loader/test')
            Checked flutter @('build', 'web', '--dart-define=GATEWAY_URL=http://localhost:4300/api',
                '--dart-define=MEDIFLOW_OUTPATIENT_UI_ENABLED=true', '--dart-define=PHARMACY_MODE=outpatient')
        } finally { Pop-Location }
    }
    "PASS: $Client checks and web build; native devices and business runtime are separate checks." |
        Set-Content (Join-Path $evidence 'result.txt')
} catch {
    ('FAILED: ' + $_.Exception.Message) | Set-Content (Join-Path $evidence 'result.txt')
    throw
} finally { $env:NODE_OPTIONS = $previousNodeOptions }
