# Isolated EXAM acceptance, not deployment configuration. Start requires the dedicated fresh
# mediflow-g1-postgres / mediflow-g1-rabbit containers described in the handoff.
# Smoke can be rerun with a fresh source each time; it intentionally exits nonzero on contract defects.
param([ValidateSet('Start','Smoke','Stop')][string]$Phase = 'Smoke')
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path "$PSScriptRoot/../../../../..").Path
$work = Join-Path $repo 'backend/gateway/target/g1-live'
New-Item -ItemType Directory -Force $work | Out-Null
$docker = 'C:/Program Files/Docker/Docker/resources/bin/docker.exe'
$java = 'C:/Program Files/Java/jdk-21.0.11/bin/java.exe'
$secret = 'g1-isolated-local-acceptance-secret-at-least-32-bytes'
$password = 'G1-local-test-2026!'
$base = 'http://127.0.0.1:18080'

function Check($condition, $label) {
    if (-not $condition) { throw "FAIL: $label" }
    Write-Output "PASS: $label"
}
function Healthy($port) {
    for ($i=0; $i -lt 90; $i++) {
        try {
            $r = Invoke-RestMethod "http://127.0.0.1:$port/actuator/health" -TimeoutSec 2
            if ($r.status -eq 'UP') { Write-Output "Healthy: $port"; return }
        } catch { }
        Start-Sleep -Seconds 2
    }
    throw "Service $port failed readiness; inspect $work"
}
function Launch($name, $port, $extra) {
    $jar = Join-Path $repo "backend/$name/target/$name-0.0.1-SNAPSHOT.jar"
    $arguments = @('-Xmx320m','-jar',$jar,"--server.port=$port",'--eureka.client.enabled=false') + $extra
    $process = Start-Process $java -ArgumentList $arguments -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput "$work/$name.log" -RedirectStandardError "$work/$name.err"
    Add-Content "$work/processes.txt" $process.Id
    Healthy $port
}
if ($Phase -eq 'Stop') {
    if (Test-Path "$work/processes.txt") {
        Get-Content "$work/processes.txt" | ForEach-Object { Stop-Process -Id ([int]$_) -ErrorAction SilentlyContinue }
    }
    exit
}
if ($Phase -eq 'Start') {
    foreach ($db in @('mediflow_organization','mediflow_patient','mediflow_clinical','mediflow_billing')) {
        & $docker exec mediflow-g1-postgres createdb -U postgres $db
        if ($LASTEXITCODE -ne 0) { throw "Cannot create isolated database $db" }
    }
    $env:MEDIFLOW_JWT_SECRET = $secret
    $env:MEDIFLOW_DB_PASSWORD = 'g1-local-test'
    $env:MEDIFLOW_ORGANIZATION_DB_URL = 'jdbc:postgresql://127.0.0.1:15432/mediflow_organization'
    $env:MEDIFLOW_PATIENT_DB_URL = 'jdbc:postgresql://127.0.0.1:15432/mediflow_patient'
    $env:MEDIFLOW_RABBIT_PORT = '15672'
    $env:SPRING_RABBITMQ_PORT = '15672'
    $common = @('--spring.cloud.discovery.client.simple.instances.core-service[0].uri=http://127.0.0.1:18089')
    Launch 'core-service' 18089 @()
    $env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:15432/mediflow_clinical'
    Launch 'clinical-service' 18082 ($common + @('--spring.profiles.active=outpatient-mvp',
        '--mediflow.features.care-finance-v2.enabled=true','--mediflow.clinical.outbox-poll-interval=250'))
    $env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:15432/mediflow_billing'
    # Match the exact price code/amount used by Billing's owner integration test, not V0 defaults.
    @'
billing:
  care-finance:
    prices:
      OUTPATIENT_EXAM:
        description: Kham ngoai tru
        unit-amount: 100000.00
'@ | Set-Content "$work/billing-catalog.yml"
    Launch 'billing-service' 18086 @("--spring.config.additional-location=file:$work/billing-catalog.yml",
        '--mediflow.billing.ledger.enabled=true','--mediflow.billing.payment-request-discovery.enabled=true',
        '--mediflow.billing.exam-charge-consumer.enabled=true','--mediflow.features.outpatient-mvp.enabled=true',
        '--mediflow.billing.outpatient-publication.enabled=true','--mediflow.billing.outpatient-publication.purposes=EXAM',
        '--mediflow.billing.outpatient-publication.acceptance-reference=G1-local-da99a906',
        '--spring.datasource.hikari.connection-timeout=2000')
    Remove-Item Env:SPRING_DATASOURCE_URL
    Launch 'gateway' 18080 ($common + @(
        '--spring.cloud.discovery.client.simple.instances.clinical-service[0].uri=http://127.0.0.1:18082',
        '--spring.cloud.discovery.client.simple.instances.billing-service[0].uri=http://127.0.0.1:18086'))
    $observerHeaders = @{Authorization='Basic '+[Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('guest:guest'))}
    Invoke-RestMethod 'http://127.0.0.1:15673/api/queues/%2F/g1-evidence' -Method Put -Headers $observerHeaders -ContentType application/json -Body '{"durable":true,"auto_delete":false,"arguments":{}}' | Out-Null
    foreach ($key in @('payment.completed','financial.clearance.granted')) {
        Invoke-RestMethod 'http://127.0.0.1:15673/api/bindings/%2F/e/mediflow.events/q/g1-evidence' -Method Post -Headers $observerHeaders -ContentType application/json -Body (@{routing_key=$key;arguments=@{}} | ConvertTo-Json -Compress) | Out-Null
    }
    exit
}

function Base64Url([byte[]]$bytes) { [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_') }
function Signed($role, $type='access', $subject=([guid]::NewGuid().ToString())) {
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $payload = @{sub=$subject;role=$role;type=$type;iat=$now;exp=($now+600)} | ConvertTo-Json -Compress
    $head = Base64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256"}'))
    $body = Base64Url ([Text.Encoding]::UTF8.GetBytes($payload))
    $hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($secret))
    try { "$head.$body.$(Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes("$head.$body"))))" }
    finally { $hmac.Dispose() }
}
$script:evidence = [System.Collections.Generic.List[object]]::new()
$script:contractFailures = [System.Collections.Generic.List[string]]::new()
function Call($method, $path, $token, $body=$null, $expected=200, $label=$path) {
    $cid = [guid]::NewGuid().ToString()
    $headers = @{'X-Correlation-Id'=$cid}
    if ($token) { $headers.Authorization = "Bearer $token" }
    $args = @{Uri="$base$path";Method=$method;Headers=$headers;SkipHttpErrorCheck=$true;TimeoutSec=45}
    if ($null -ne $body) { $args.ContentType='application/json'; $args.Body=($body | ConvertTo-Json -Depth 12 -Compress) }
    $r = Invoke-WebRequest @args
    $json = $r.Content | ConvertFrom-Json
    if ([int]$r.StatusCode -ne $expected) { throw "$label expected $expected got $($r.StatusCode): $($r.Content)" }
    Check ($r.Headers['X-Correlation-Id'] -contains $cid) "$label HTTP $expected / correlation header"
    # Login returns a token DTO rather than the business API envelope. Never capture credentials.
    if ($path -ne '/api/v1/auth/login') {
        $script:evidence.Add(@{label=$label;method=$method;path=$path;http=[int]$r.StatusCode;correlation=$cid;response=$json})
        if ($json.correlationId -ne $cid) {
            $script:contractFailures.Add("$label HTTP $expected body correlation mismatch")
            Write-Output "CONTRACT FAILURE: $label body correlation mismatch; continuing to capture independent checks"
        }
    }
    return ,$json
}
function Api($method,$path,$token,$body=$null) {
    # Suppress progress records in the function result, keeping JSON as the single return value.
    $expected = if ($method -eq 'POST' -and $path -notlike '*/payments') { 201 } else { 200 }
    $result = @(Call $method $path $token $body $expected)
    return $result[-1].data
}
function Login($username) {
    $r = @(Call 'POST' '/api/v1/auth/login' $null @{username=$username;password=$password})
    return $r[-1].accessToken
}

$bootstrap = Signed 'ADMIN'
$unique = [guid]::NewGuid().ToString('N').Substring(0,10)
$dept = Api 'POST' '/api/v1/org/departments' $bootstrap @{departmentName="G1 $unique";abbreviation="G$($unique.Substring(0,4).ToUpper())";departmentType='CLINICAL';location='Isolated test'}
$identities = @{}
foreach ($role in @('ADMIN','CASHIER','DOCTOR')) {
    $title = if ($role -eq 'ADMIN') { 'ADMINISTRATIVE' } else { $role }
    $staff = Api 'POST' '/api/v1/org/staff' $bootstrap @{fullName="G1 $role $unique";departmentId=$dept.departmentId;jobTitle=$title;licenseNumber="G1-$unique";phoneNumber='0900000000';email="$role-$unique@example.test"}
    $username = "g1-$($role.ToLower())-$unique"
    $account = Api 'POST' '/api/v1/org/accounts' $bootstrap @{username=$username;password=$password;staffId=$staff.staffId;patientId=$null;role=$role}
    $identities[$role] = @{token=(Login $username);staffId=$staff.staffId;accountId=$account.accountId}
}
$admin = $identities.ADMIN.token
$cashier = $identities.CASHIER.token
Check (-not [string]::IsNullOrWhiteSpace($admin)) 'ADMIN login via Gateway/Core'
Check (-not [string]::IsNullOrWhiteSpace($cashier)) 'CASHIER login via Gateway/Core'
$digits = ('1' + ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()).Substring(2,11))
$patient = Api 'POST' '/api/v1/patients' $admin @{hoTen="G1 Patient $unique";ngaySinh='1988-01-02';gioiTinh='M';soCmnd=$digits;diaChi='Isolated test';soDienThoai='0900000001';email="patient-$unique@example.test";bhytSo=$null}
$appointment = Api 'POST' '/api/v1/appointments' $admin @{patientId=$patient.maBenhNhan;doctorId=$identities.DOCTOR.staffId;departmentId=$dept.departmentId;appointmentDate=(Get-Date).AddDays(1).ToString('yyyy-MM-dd');appointmentTime='09:00';reason='G1 real Billing smoke'}
$source = $appointment.appointmentId
Check (-not [string]::IsNullOrWhiteSpace($source)) 'Source appointment created via Clinical API'
$checkedIn = Api 'PUT' "/api/v1/appointments/$source/check-in" $admin @{}
Check ($checkedIn.status -eq 'AWAITING_PAYMENT') 'Real Clinical check-in awaiting payment'
$path = "/api/v1/billing/payment-requests/by-source?purpose=EXAM&sourceId=$source&patientId=$($patient.maBenhNhan)&careEpisodeId=$source"
$found = $null
for ($i=0; $i -lt 20; $i++) {
    $r = Invoke-WebRequest "$base$path" -Headers @{Authorization="Bearer $admin"} -SkipHttpErrorCheck -TimeoutSec 10
    if ($r.StatusCode -eq 200) { $found=($r.Content|ConvertFrom-Json).data; break }
    if ($r.StatusCode -ne 404) { throw "Discovery poll: HTTP $($r.StatusCode) $($r.Content)" }
    Start-Sleep -Seconds 2
}
Check ($null -ne $found) 'Real Billing issued request from actual Clinical event'
$found = Api 'GET' $path $admin
$cashierFound = Api 'GET' $path $cashier
Check ($found.paymentRequestId -eq $cashierFound.paymentRequestId) 'ADMIN/CASHIER discover same request'
Check ($found.sourceId -eq $source -and $found.patientId -eq $patient.maBenhNhan -and $found.careEpisodeId -eq $source) 'Exact source/patient/episode'
Check ([decimal]$found.remainingAmount -eq 100000) 'Billing owner test catalog OUTPATIENT_EXAM = 100000.00'
$invalid = @(Call 'GET' ($path.Replace("sourceId=$source",'sourceId=malformed')) $admin $null 400 'Billing validation')[-1]
Check ($invalid.error.code -eq 'INVALID_REQUEST') 'Billing validation error preserved'
$missing = @(Call 'GET' ($path.Replace("patientId=$($patient.maBenhNhan)","patientId=$([guid]::NewGuid())")) $admin $null 404 'Wrong patient')[-1]
Check ($missing.error.code -eq 'BILLING_PAYMENT_REQUEST_NOT_FOUND') 'Wrong patient returns Billing not-found'
$last = $script:evidence[$script:evidence.Count-1]
$direct = Invoke-WebRequest ("http://127.0.0.1:18086" + $last.path) -Headers @{Authorization="Bearer $admin";'X-Correlation-Id'=$last.correlation} -SkipHttpErrorCheck
$directBody = $direct.Content | ConvertFrom-Json
Check ($direct.StatusCode -eq 404 -and $directBody.error.code -eq $missing.error.code -and $directBody.correlationId -eq $missing.correlationId) '404 correlation defect also exists directly at Billing'
@{http=[int]$direct.StatusCode;sentCorrelation=$last.correlation;headerCorrelation=$direct.Headers['X-Correlation-Id'];response=$directBody} | ConvertTo-Json -Depth 10 | Set-Content "$work/direct-billing-404.json"
Call 'GET' $path $null $null 401 'Missing token' | Out-Null
Call 'GET' $path $identities.DOCTOR.token $null 403 'Forbidden doctor' | Out-Null
Call 'GET' $path (Signed 'ADMIN' 'refresh') $null 401 'Refresh token' | Out-Null
Call 'GET' $path (Signed 'SYSTEM' 'service' 'clinical-service') $null 403 'Service token' | Out-Null
$paymentPath = "/api/v1/billing/payment-requests/$($found.paymentRequestId)/payments"
$paymentBody = @{idempotencyKey="g1-$unique";amount=$found.remainingAmount;currency=$found.currency;paymentMethod='CASH'}
$payment = Api 'POST' $paymentPath $cashier $paymentBody
$replay = Api 'POST' $paymentPath $cashier $paymentBody
Check ($payment.transactionId -eq $replay.transactionId -and $null -ne $payment.transactionId) 'Payment retry returns same transaction'
$paid = Api 'GET' $path $admin
Check ($paid.status -eq 'PAID' -and [decimal]$paid.remainingAmount -eq 0 -and [decimal]$paid.paidAmount -eq [decimal]$found.requestedAmount) 'Discovery readback PAID / remaining 0'
$observerHeaders = @{Authorization='Basic '+[Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('guest:guest'))}
$brokerEvidence = @()
for ($i=0; $i -lt 15; $i++) {
    $messages = Invoke-RestMethod 'http://127.0.0.1:15673/api/queues/%2F/g1-evidence/get' -Method Post -Headers $observerHeaders -ContentType application/json -Body '{"count":10,"ackmode":"ack_requeue_false","encoding":"base64","truncate":100000}'
    foreach ($m in $messages) {
        $bytes = [Convert]::FromBase64String($m.payload)
        $raw = [Text.Encoding]::UTF8.GetString($bytes)
        $event = $raw | ConvertFrom-Json
        # Clearance refers to the exact care target, not necessarily the payment request ID.
        if ($raw.Contains($found.paymentRequestId) -or $raw.Contains($source)) {
            $sha = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLower()
            [IO.File]::WriteAllBytes("$work/$($event.eventId).json", $bytes)
            $brokerEvidence += @{eventId=$event.eventId;routingKey=$m.routing_key;sha256=$sha;body=$event}
        }
    }
    if ($brokerEvidence.Count -ge 2) { break }
    Start-Sleep -Seconds 1
}
Check (@($brokerEvidence | Where-Object routingKey -eq 'payment.completed').Count -eq 1) 'Actual Rabbit payment.completed for discovered request'
Check (@($brokerEvidence | Where-Object routingKey -eq 'financial.clearance.granted').Count -eq 1) 'Actual Rabbit financial.clearance.granted for discovered request'
$brokerEvidence | ConvertTo-Json -Depth 20 | Set-Content "$work/broker-evidence.json"
try {
    & $docker stop -t 5 mediflow-g1-postgres | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot stop isolated PG for unavailable test' }
    $unavailable = @(Call 'GET' $path $admin $null 503 'Billing storage unavailable')[-1]
    Check ($unavailable.error.code -eq 'BILLING_LEDGER_UNAVAILABLE') 'Billing 503 code preserved through Gateway'
} finally {
    & $docker start mediflow-g1-postgres | Out-Null
    Healthy 18086
}
$recovered = Api 'GET' $path $admin
Check ($recovered.paymentRequestId -eq $found.paymentRequestId -and $recovered.status -eq 'PAID') 'Storage recovery preserves paid request'
$script:evidence | ConvertTo-Json -Depth 20 | Set-Content "$work/http-evidence.json"
@{baseline=(& git -C $repo rev-parse HEAD);sourceId=$source;patientId=$patient.maBenhNhan;departmentId=$dept.departmentId;before=$found;payment=$payment;after=$paid;contractFailures=@($script:contractFailures)} | ConvertTo-Json -Depth 15 | Set-Content "$work/result.json"
Write-Output "Evidence: $work/result.json and http-evidence.json (no tokens)"
if ($script:contractFailures.Count -gt 0) { throw ($script:contractFailures -join '; ') }
