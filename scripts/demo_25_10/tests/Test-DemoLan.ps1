$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\Demo-Lan.ps1')
function Assert-True($Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
$networks = @(
    [pscustomobject]@{ InterfaceAlias='vEthernet'; IPv4Address=@(@{IPAddress='172.28.0.1'}); IPv4DefaultGateway=$null; NetAdapter=@{HardwareInterface=$false;Status='Up'} },
    [pscustomobject]@{ InterfaceAlias='Wi-Fi'; IPv4Address=@(@{IPAddress='192.168.1.3'}); IPv4DefaultGateway=@{NextHop='192.168.1.1'}; NetAdapter=@{HardwareInterface=$true;Status='Up'} }
)
$selected = Resolve-DemoNetwork -Configurations $networks
Assert-True ($selected.Address -eq '192.168.1.3' -and $selected.InterfaceAlias -eq 'Wi-Fi') 'Physical Wi-Fi was not selected.'
Assert-True ((Resolve-DemoNetwork -Configurations $networks -LocalOnly).Address -eq '127.0.0.1') 'LocalOnly must stay on loopback.'
Assert-True ((Resolve-DemoNetwork -Configurations $networks -LanAddress '192.168.1.3').Address -eq '192.168.1.3') 'Explicit assigned address rejected.'
foreach ($invalid in @('8.8.8.8','192.168.1.99','127.0.0.1','host;echo bad')) {
    $rejected = $false
    try { Resolve-DemoNetwork -Configurations $networks -LanAddress $invalid | Out-Null } catch { $rejected = $true }
    Assert-True $rejected "Invalid/unassigned LAN address accepted: $invalid"
}
$rejected = $false
try { Resolve-DemoNetwork -Configurations @() | Out-Null } catch { $rejected = $true }
Assert-True $rejected 'Missing physical LAN must produce guidance.'
$global:rules = @()
function global:Get-NetFirewallRule { param($Name,$ErrorAction) return $null }
function global:New-NetFirewallRule {
    param($Name,$DisplayName,$Direction,$Action,$Protocol,$LocalPort,$RemoteAddress,$InterfaceAlias,$Profile,$Enabled)
    $global:rules += [pscustomobject]$PSBoundParameters
}
function global:Test-DemoAdministrator { return $true }
Enable-DemoLanFirewall -InterfaceAlias 'Wi-Fi'
Assert-True ($global:rules.Count -eq 2) 'Expected only two demo port rules.'
Assert-True (@($global:rules | Where-Object { $_.LocalPort -notin @(28080,3000) -or $_.RemoteAddress -ne 'LocalSubnet' -or $_.InterfaceAlias -ne 'Wi-Fi' -or $_.Direction -ne 'Inbound' }).Count -eq 0) 'Firewall scope expanded beyond LAN demo.'
function global:Test-DemoAdministrator { return $false }
Enable-DemoLanFirewall -InterfaceAlias 'Wi-Fi'
Assert-True ($global:rules.Count -eq 2) 'Non-admin path attempted a firewall mutation.'
Write-Host 'PASS: LAN selection, address rejection, loopback and scoped firewall rules.'
