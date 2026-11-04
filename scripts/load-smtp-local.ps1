# Dot-source before starting Core/Gateway in this PowerShell window:
# . .\scripts\load-smtp-local.ps1
# Loads environment variables only; never prints credentials or runs file contents.
[CmdletBinding()]
param([string]$ConfigPath = (Join-Path $PSScriptRoot '..\smtp.local.env'))
$smtpConfigPath = (Resolve-Path -LiteralPath $ConfigPath -ErrorAction Stop).Path
$smtpEntries = @{}
foreach ($smtpLine in [System.IO.File]::ReadAllLines($smtpConfigPath)) {
    if ([string]::IsNullOrWhiteSpace($smtpLine) -or $smtpLine.TrimStart().StartsWith('#')) { continue }
    if ($smtpLine -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw 'Invalid SMTP environment file entry.' }
    $smtpName = $Matches[1]
    $smtpValue = $Matches[2]
    if ($smtpName -notmatch '^(SPRING_MAIL_|MEDIFLOW_MAIL_|MEDIFLOW_PUBLIC_WEB_URL$|MEDIFLOW_SECURITY_MAIL_KEY$|MEDIFLOW_DEMO_MAILBOX_ENABLED$)') {
        throw 'Unexpected environment variable in SMTP configuration.'
    }
    $smtpEntries[$smtpName] = $smtpValue
}
foreach ($smtpRequired in @('SPRING_MAIL_HOST', 'SPRING_MAIL_USERNAME', 'SPRING_MAIL_PASSWORD', 'MEDIFLOW_SECURITY_MAIL_KEY', 'MEDIFLOW_PUBLIC_WEB_URL')) {
    if ([string]::IsNullOrWhiteSpace($smtpEntries[$smtpRequired]) -or $smtpEntries[$smtpRequired].StartsWith('replace-with-')) { throw "Missing SMTP configuration: $smtpRequired" }
}
try { $smtpKeyBytes = [Convert]::FromBase64String($smtpEntries['MEDIFLOW_SECURITY_MAIL_KEY']) }
catch { throw 'MEDIFLOW_SECURITY_MAIL_KEY must be a base64-encoded 32-byte key.' }
if ($smtpKeyBytes.Length -ne 32) { throw 'MEDIFLOW_SECURITY_MAIL_KEY must contain exactly 32 decoded bytes.' }
foreach ($smtpName in $smtpEntries.Keys) {
    [Environment]::SetEnvironmentVariable($smtpName, $smtpEntries[$smtpName], 'Process')
}
Write-Host 'SMTP environment loaded for this PowerShell process. Credentials are hidden.'
Remove-Variable smtpEntries, smtpLine, smtpKeyBytes, smtpValue -ErrorAction SilentlyContinue
