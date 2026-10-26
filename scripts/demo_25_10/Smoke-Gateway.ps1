[CmdletBinding()]
param([string]$Gateway = 'http://127.0.0.1:28080')

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$seed = Get-Content (Join-Path $repoRoot 'backend/core-service/src/main/resources/demo-seed.json') -Raw | ConvertFrom-Json
$admin = @($seed.accounts | Where-Object username -EQ 'demo.admin')
if ($admin.Count -ne 1) { throw 'Expected synthetic demo.admin account in the seed manifest.' }
$login = Invoke-RestMethod "$Gateway/api/v1/auth/login" -Method Post -ContentType application/json `
    -Body (@{username=$admin[0].username;password=$admin[0].password} | ConvertTo-Json) -TimeoutSec 30
if ($login.role -ne 'ADMIN' -or -not $login.accessToken) { throw 'Real Gateway login did not return ADMIN authority.' }
$headers = @{Authorization='Bearer ' + $login.accessToken}
$patient = '54fb1ca5-65a7-4ba2-9a0a-48c65832b246' # synthetic snapshot patient
$routes = @(
    '/api/v1/patients', '/api/v1/appointments', '/api/v1/lab',
    '/api/v1/pharmacy/drugs', "/api/v1/billing/patient/$patient",
    "/api/v1/notifications/patient/$patient",
    # Closed historical scope with genuine coverage already in the snapshot.
    '/api/v1/reports/outpatient/daily?fromDate=2025-06-15&toDate=2025-06-15&departmentId=9a6dd24a-1c1f-4122-9e31-7cfea345ec45'
)
$results = @()
foreach ($route in $routes) {
    try {
        $reply = Invoke-WebRequest ($Gateway + $route) -Headers $headers -TimeoutSec 30
        $envelope = $reply.Content | ConvertFrom-Json
        if ($envelope.success -ne $true) { throw "Unsuccessful API envelope at $route" }
        $results += [pscustomobject]@{route=$route;status=[int]$reply.StatusCode;success=$envelope.success}
    } catch {
        $results += [pscustomobject]@{route=$route;status='FAILED';error=$_.Exception.Message;details=$_.ErrorDetails.Message}
    }
}
$directory = Join-Path $PSScriptRoot ('evidence/gateway-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$results | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $directory 'routes.json')
$results | Format-Table -AutoSize
if (@($results | Where-Object status -EQ 'FAILED').Count) { throw "Gateway read smoke failed; see $directory" }
"PASS: real ADMIN login and seven read routes through Gateway. Evidence: $directory"
