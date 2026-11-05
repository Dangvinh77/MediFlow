# Fast UI loop with hot reload: Chrome (use DevTools device mode for iPhone
# layouts) or the Android emulator. Press r = hot reload, R = hot restart, q = quit.
#
#   ./tool/run_dev.ps1                          # Chrome, Gateway on port 8080
#   ./tool/run_dev.ps1 -Target android          # starts the emulator if needed
#   ./tool/run_dev.ps1 -Demo -Gateway http://127.0.0.1:28080
param(
    [ValidateSet('chrome', 'android')]
    [string]$Target = 'chrome',
    [int]$Port = 4300,
    [string]$Gateway = 'http://127.0.0.1:8080',
    [string]$Emulator = 'pixel_api36',
    [int]$BootTimeoutSeconds = 180,
    # -Demo enables the outpatient UI and PHARMACY_MODE=outpatient, as run_web.ps1 does.
    [switch]$Demo,
    [ValidateSet('unavailable', 'outpatient', 'legacy')]
    [string]$PharmacyMode = 'unavailable'
)
$ErrorActionPreference = 'Stop'

$gatewayUri = [Uri]$Gateway
if ($gatewayUri.Scheme -notin @('http', 'https') -or
    $gatewayUri.UserInfo -or $gatewayUri.Query -or $gatewayUri.Fragment -or
    $gatewayUri.AbsolutePath -ne '/' -or
    ($gatewayUri.Port -ge 8081 -and $gatewayUri.Port -le 8091) -or
    $gatewayUri.Port -eq 8761) {
    throw 'Gateway must be a Gateway origin, for example http://127.0.0.1:8080'
}

if ($Demo -and -not $PSBoundParameters.ContainsKey('PharmacyMode')) {
    $PharmacyMode = 'outpatient'
}
$defines = @("--dart-define=PHARMACY_MODE=$PharmacyMode")
if ($Demo) {
    $defines += '--dart-define=MEDIFLOW_OUTPATIENT_UI_ENABLED=true'
    $defines += '--dart-define=MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED=true'
    $defines += '--dart-define=MEDIFLOW_SCHEDULING_UI_ENABLED=true'
    $defines += '--dart-define=MEDIFLOW_OUTPATIENT_QUOTES_UI_ENABLED=true'
    $defines += '--dart-define=MEDIFLOW_ZERO_PAYABLE_UI_ENABLED=true'
}

function Get-RunningEmulator {
    $line = & adb devices | Where-Object { $_ -match '^emulator-\d+\s+device$' } |
        Select-Object -First 1
    if ($line) { return ($line -split '\s+')[0] }
    return $null
}

function Start-AndroidEmulator {
    $device = Get-RunningEmulator
    if (-not $device) {
        Write-Host "Starting emulator $Emulator..."
        # Detached with file logs: started through `flutter emulators --launch`,
        # qemu was closed by Windows as hung and adb lost the device mid-install.
        $logDir = Join-Path $env:TEMP 'mediflow-emulator'
        New-Item -ItemType Directory -Force -Path $logDir | Out-Null
        Start-Process -FilePath 'emulator' -WindowStyle Hidden `
            -ArgumentList @('-avd', $Emulator, '-no-audio', '-no-boot-anim') `
            -RedirectStandardOutput (Join-Path $logDir 'emulator.out.log') `
            -RedirectStandardError (Join-Path $logDir 'emulator.err.log')
    }
    $deadline = (Get-Date).AddSeconds($BootTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $device = Get-RunningEmulator
        if ($device -and (& adb -s $device shell getprop sys.boot_completed) -eq '1') {
            return $device
        }
        Start-Sleep -Seconds 2
    }
    throw "Emulator did not finish booting within $BootTimeoutSeconds seconds"
}

Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    if ($Target -eq 'chrome') {
        # Generated per run: flutter_tools only reads the proxy from this file.
        $origin = $gatewayUri.GetLeftPart([UriPartial]::Authority)
        @(
            'server:',
            '  host: localhost',
            '  proxy:',
            "    - target: `"$origin/`"",
            '      prefix: "/api/"'
        ) | Set-Content -Path 'web_dev_config.yaml' -Encoding ascii

        $defines += "--dart-define=GATEWAY_URL=http://localhost:$Port/api"
        Write-Host "Build flags: $($defines -join ' ')"
        & flutter run -d chrome --web-port $Port @defines
    } else {
        $device = Start-AndroidEmulator
        $builder = [UriBuilder]$gatewayUri
        if ($gatewayUri.IsLoopback) { $builder.Host = '10.0.2.2' }
        $origin = $builder.Uri.GetLeftPart([UriPartial]::Authority)
        $defines += "--dart-define=GATEWAY_URL=$origin/api"
        $defines += '--dart-define=ALLOW_INSECURE_HTTP=true'
        Write-Host "Build flags: $($defines -join ' ')"
        & flutter run -d $device @defines
    }
    if ($LASTEXITCODE -ne 0) { throw 'flutter run failed' }
} finally {
    Pop-Location
}
