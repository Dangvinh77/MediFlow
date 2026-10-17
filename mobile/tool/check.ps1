$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & flutter pub get
    if ($LASTEXITCODE -ne 0) { throw 'Dependencies failed' }
    & dart format --output=none --set-exit-if-changed lib test tool
    if ($LASTEXITCODE -ne 0) { throw 'Run dart format lib test tool' }
    & flutter analyze
    if ($LASTEXITCODE -ne 0) { throw 'Analysis failed' }
    & flutter test test packages/mediflow_loader/test
    if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
    & flutter build web --dart-define=GATEWAY_URL=http://localhost:4300/api
    if ($LASTEXITCODE -ne 0) { throw 'Web build failed' }
} finally {
    Pop-Location
}
