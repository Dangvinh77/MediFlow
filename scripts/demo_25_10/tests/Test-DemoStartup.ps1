param([ValidateSet('all','cached','stale','forced','build-failure','cache-failure','stop-retained','stop-initializing','stop-disposable','unsafe-stop')][string]$Case='all')
$ErrorActionPreference='Stop'
if($Case -eq 'all'){
 foreach($scenario in @('cached','stale','forced','build-failure','cache-failure','stop-retained','stop-initializing','stop-disposable','unsafe-stop')){
  & powershell.exe -NoProfile -File $PSCommandPath -Case $scenario
  if($LASTEXITCODE -ne 0){throw "Startup check failed: $scenario"}
 }
 exit 0
}
# Load only helper definitions; never execute the launcher's app/Docker actions.
$launcher=Join-Path $PSScriptRoot '../Demo-Launcher.ps1'
$tokens=$null; $parseErrors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($launcher,[ref]$tokens,[ref]$parseErrors)
if($parseErrors.Count){throw 'Launcher parsing failed'}
foreach($name in @('Invoke-DemoBuild','Stop-Rehearsal')){
 $definition=$ast.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name},$true)
 $definitionText=$definition.Extent.Text.Replace('$PSScriptRoot',("'"+$PSScriptRoot.Replace("'","''")+"'"))
 Invoke-Expression $definitionText
}
$script:operations=[Collections.Generic.List[string]]::new()
function Require-Command {param($Name)}
function python {
 param([Parameter(ValueFromRemainingArguments=$true)][string[]]$PythonArgs)
 $action=$PythonArgs[1]
 $script:operations.Add('python '+$action)
 $global:LASTEXITCODE=0
 if($action -eq 'check'){
  if($Case -eq 'cache-failure'){$global:LASTEXITCODE=1}
  elseif($Case -ne 'cached'){$global:LASTEXITCODE=10}
 }
 if($action -eq 'fingerprint'){return 'fixture-fingerprint'}
 if($action -eq 'record' -and ($PythonArgs[-2] -ne '--expected-inputs' -or $PythonArgs[-1] -ne 'fixture-fingerprint')){throw 'Build input identity was not checked'}
}
function mvn {
 param([Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArgs)
 $script:operations.Add('maven')
 $global:LASTEXITCODE=0
 if($Case -eq 'build-failure'){$global:LASTEXITCODE=1}
}
if($Case -like '*stop*' -or $Case -eq 'stop-disposable'){
 $repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
 $evidence=Join-Path $PSScriptRoot 'evidence/fixture'
 $script:manifestData=@{persistent=($Case -ne 'stop-disposable');containers=@('fixture');pids=@(41001);runnerPid=41000;evidence=$evidence;stopFile=(Join-Path $evidence 'stop')}
 if($Case -eq 'unsafe-stop'){$script:manifestData.stopFile=Join-Path $repoRoot 'outside-stop'}
 function Test-Path {param($LiteralPath) return $true}
 function Get-Content {param($LiteralPath,[switch]$Raw) return ($script:manifestData | ConvertTo-Json)}
 function Read-Containers {
  if($Case -eq 'stop-initializing'){return @()}
  return [pscustomobject]@{Name='/fixture';Id='id-fixture'}
 }
 function Test-OwnedContainer {param($Container) return $true}
 function New-Item {param($ItemType,$Path,[switch]$Force) $script:operations.Add('signal '+$Path)}
 $script:queries=0
 $script:runnerQueries=0
 function Get-CimInstance {
  param($ClassName,$Filter)
  if($Filter -like 'ProcessId*'){
   $script:runnerQueries++
   if($Case -eq 'stop-initializing' -and $script:runnerQueries -eq 1){
    return [pscustomobject]@{ProcessId=41000;CommandLine=('python "'+(Join-Path $repoRoot 'scripts\demo_25_10\rehearse_snapshot.py')+'" --persistent')}
   }
   return
  }
  $script:queries++
  if($Case -eq 'stop-initializing'){return}
  if($script:queries -eq 1){return [pscustomobject]@{ProcessId=41001;ParentProcessId=41000;CommandLine=('java -jar '+$repoRoot+'\backend\core-service\target\core-service-0.0.1-SNAPSHOT.jar')}}
 }
 function Start-Sleep {param($Seconds) $script:operations.Add('wait')}
 $failed=$false
 try {Stop-Rehearsal -PreserveInfrastructure} catch {$failed=$true; Write-Host $_.Exception.Message}
 if($Case -in @('stop-retained','stop-initializing')){
  if($failed -or $script:operations.Count -ne 2 -or $script:operations[0] -ne ('signal '+$script:manifestData.stopFile) -or $script:operations[1] -ne 'wait'){throw 'Retained stop did not signal and await application shutdown'}
 }elseif(-not $failed -or $script:operations.Count){throw 'Unsafe/disposable stop was not rejected before writes'}
}else{
 $failed=$false
 try {Invoke-DemoBuild -Force:($Case -eq 'forced')} catch {$failed=$true; Write-Host $_.Exception.Message}
 $expected=@('python check','python fingerprint','maven','python record')
 if($Case -eq 'cached'){$expected=@('python check')}
 if($Case -eq 'forced'){$expected=@('python fingerprint','maven','python record')}
 if($Case -eq 'build-failure'){$expected=@('python check','python fingerprint','maven')}
 if($Case -eq 'cache-failure'){$expected=@('python check')}
 if($failed -ne ($Case -in @('build-failure','cache-failure')) -or ($expected -join '|') -ne ($script:operations -join '|')){throw "Unexpected build operations: $($script:operations -join ',')"}
}
Write-Host "PASS: $Case"
exit 0

