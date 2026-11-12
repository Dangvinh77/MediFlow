param(
    [int]$Port = 4300,
    [string]$Gateway = 'http://127.0.0.1:8080',
    # -Demo bật UI lệnh ngoại trú và PHARMACY_MODE=outpatient, khớp backend
    # chạy bằng scripts/run/start-backend.bat / compose.demo.local.yml.
    [switch]$Demo,
    [switch]$OutpatientUi,
    [switch]$ReportOutpatientUi,
    [ValidateSet('unavailable', 'outpatient', 'legacy')]
    [string]$PharmacyMode = 'unavailable'
)
$ErrorActionPreference = 'Stop'
if ($Demo) {
    $OutpatientUi = $true
    $ReportOutpatientUi = $true
    if (-not $PSBoundParameters.ContainsKey('PharmacyMode')) { $PharmacyMode = 'outpatient' }
}
$defines = @(
    "--dart-define=GATEWAY_URL=http://localhost:$Port/api",
    "--dart-define=PHARMACY_MODE=$PharmacyMode"
)
if ($OutpatientUi) {
    $defines += '--dart-define=MEDIFLOW_OUTPATIENT_UI_ENABLED=true'
    $defines += '--dart-define=MEDIFLOW_SCHEDULING_UI_ENABLED=true'
    $defines += '--dart-define=MEDIFLOW_OUTPATIENT_QUOTES_UI_ENABLED=true'
    $defines += '--dart-define=MEDIFLOW_ZERO_PAYABLE_UI_ENABLED=true'
    $defines += '--dart-define=MEDIFLOW_CLINIC_INTAKE_UI_ENABLED=true'
}
if ($ReportOutpatientUi -or $OutpatientUi) {
    $defines += '--dart-define=MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED=true'
}
Write-Host "Build flags: $($defines -join ' ')"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & flutter pub get
    if ($LASTEXITCODE -ne 0) { throw 'flutter pub get failed' }
    & flutter build web @defines
    if ($LASTEXITCODE -ne 0) { throw 'flutter build web failed' }
    & dart run tool/dev_server.dart $Port $Gateway
    if ($LASTEXITCODE -ne 0) { throw 'Development server failed' }
} finally {
    Pop-Location
}
