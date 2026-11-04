# Shared by the batch launchers. No environment secrets are printed here.
function Test-DemoPrivateIPv4([string]$Address) {
    $parsed = $null
    if (-not [Net.IPAddress]::TryParse($Address, [ref]$parsed) -or
        $parsed.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) { return $false }
    $bytes = $parsed.GetAddressBytes()
    return $bytes[0] -eq 10 -or ($bytes[0] -eq 172 -and $bytes[1] -ge 16 -and $bytes[1] -le 31) -or
        ($bytes[0] -eq 192 -and $bytes[1] -eq 168)
}

function Resolve-DemoNetwork([object[]]$Configurations, [string]$LanAddress, [switch]$LocalOnly) {
    if ($LocalOnly) {
        if ($LanAddress) { throw 'Use either -LocalOnly or -LanAddress, not both.' }
        return [pscustomobject]@{Address='127.0.0.1'; InterfaceAlias=$null}
    }
    $candidates = @(foreach ($network in $Configurations) {
        if (-not $network.NetAdapter.HardwareInterface -or $network.NetAdapter.Status -ne 'Up') { continue }
        foreach ($ip in $network.IPv4Address) {
            if ((Test-DemoPrivateIPv4 $ip.IPAddress) -and ($LanAddress -or $network.IPv4DefaultGateway)) {
                [pscustomobject]@{Address=[string]$ip.IPAddress; InterfaceAlias=[string]$network.InterfaceAlias}
            }
        }
    })
    if ($LanAddress) { $candidates = @($candidates | Where-Object { $_.Address -eq $LanAddress }) }
    if ($candidates.Count -eq 0) {
        throw 'No assigned private IPv4 on an active physical network. Connect Wi-Fi, use -LanAddress <PC-IP>, or use -LocalOnly.'
    }
    return $candidates[0]
}

function Test-DemoAdministrator {
    return ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Enable-DemoLanFirewall([string]$InterfaceAlias) {
    if (-not (Test-DemoAdministrator)) {
        Write-Warning 'If firewall rules are missing or the adapter changed, run start-backend.bat as administrator once. Only LAN ports 28080 and 3000 are allowed.'
        return
    }
    foreach ($port in @(28080, 3000)) {
        $name = "MediFlow-Lan-Demo-$port"
        $rule = Get-NetFirewallRule -Name $name -ErrorAction SilentlyContinue
        if ($rule) {
            # Refresh only this launcher's own rule when the selected adapter changes.
            Set-NetFirewallRule -Name $name -Enabled True -Direction Inbound -Action Allow -Profile Any -InterfaceAlias $InterfaceAlias | Out-Null
            $rule | Get-NetFirewallPortFilter | Set-NetFirewallPortFilter -Protocol TCP -LocalPort $port | Out-Null
            $rule | Get-NetFirewallAddressFilter | Set-NetFirewallAddressFilter -RemoteAddress LocalSubnet | Out-Null
        } else {
            New-NetFirewallRule -Name $name -DisplayName "MediFlow Wi-Fi demo $port" -Enabled True -Direction Inbound -Action Allow `
                -Protocol TCP -LocalPort $port -RemoteAddress LocalSubnet -InterfaceAlias $InterfaceAlias -Profile Any | Out-Null
        }
    }
    Write-Host 'Windows Firewall: demo ports allowed from the local subnet on the selected adapter.'
}
