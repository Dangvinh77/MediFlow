param(
    [int]$Port = 4300,
    [string]$Gateway = 'http://127.0.0.1:8080'
)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & flutter pub get
    if ($LASTEXITCODE -ne 0) { throw 'flutter pub get failed' }
    & flutter build web "--dart-define=GATEWAY_URL=http://localhost:$Port/api"
    if ($LASTEXITCODE -ne 0) { throw 'flutter build web failed' }
    & dart run tool/dev_server.dart $Port $Gateway
    if ($LASTEXITCODE -ne 0) { throw 'Development server failed' }
} finally {
    Pop-Location
}
