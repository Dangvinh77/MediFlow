param([ValidateSet('all','preview','clean','remove-failure','engine-off','disappearing','inspect-failure','runner-shutdown','orphans','orphan-preview','active-parent','reused-process','foreign-process','wrong-port','reused-parent')][string]$Case = 'all')
if ($Case -eq 'all') {
    foreach ($scenario in @('preview','clean','remove-failure','engine-off','disappearing','inspect-failure','runner-shutdown','orphans','orphan-preview','active-parent','reused-process','foreign-process','wrong-port','reused-parent')) {
        & powershell.exe -NoProfile -File $PSCommandPath -Case $scenario
        if ($LASTEXITCODE -ne 0) { throw "Launcher check failed: $scenario" }
    }
    exit 0
}
$ErrorActionPreference='Stop'
$global:demoFixtureOperations=[System.Collections.Generic.List[string]]::new()
$global:demoFixtureContainers=@(
 [pscustomobject]@{Id='owned-app';Name='/mediflow-app';Image='sha256:app';Config=@{Labels=@{}}},
 [pscustomobject]@{Id='owned-db';Name='/mediflow-db';Image='sha256:base';Config=@{Labels=@{}}},
 [pscustomobject]@{Id='foreign';Name='/other-project';Image='sha256:base';Config=@{Labels=@{}}})
$global:demoFixtureImages=@(
 @{Id='sha256:app';RepoTags=@('source-core-service:latest','unrelated:keep');Config=@{Labels=@{}}},
 @{Id='sha256:base';RepoTags=@('postgres:16-alpine');Config=@{Labels=@{}}},
 @{Id='sha256:old';RepoTags=@('mediflow-old:latest');Config=@{Labels=@{}}},
 @{Id='sha256:foreign';RepoTags=@('other-project:latest');Config=@{Labels=@{}}})
if($Case -in @('disappearing','runner-shutdown')){
 $global:demoFixtureContainers[2].Image='sha256:foreign'
}
$global:demoFixtureStopped=[System.Collections.Generic.List[int]]::new()
$global:demoFixtureProcesses=@()
$global:demoFixtureListeners=@()
$fixtureRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
if($Case -in @('orphans','orphan-preview','active-parent','reused-process','foreign-process','wrong-port','reused-parent')){
 $jar=Join-Path $fixtureRoot 'backend\core-service\target\core-service-0.0.1-SNAPSHOT.jar'
 if($Case -eq 'foreign-process'){$jar=Join-Path ($fixtureRoot+'-other') 'backend\core-service\target\core-service-0.0.1-SNAPSHOT.jar'}
 $global:demoFixtureProcesses=@([pscustomobject]@{ProcessId=41001;ParentProcessId=41000;Name='java.exe';CommandLine=('java -jar "'+$jar+'"');CreationDate=[datetime]'2026-11-04T08:00:00Z'})
 $port=28089
 if($Case -eq 'wrong-port'){$port=28082}
 $global:demoFixtureListeners=@([pscustomobject]@{OwningProcess=41001;LocalPort=$port})
 if($Case -in @('active-parent','reused-parent')){
  $created=[datetime]'2026-11-04T07:59:00Z'
  if($Case -eq 'reused-parent'){$created=[datetime]'2026-11-04T08:01:00Z'}
  $global:demoFixtureProcesses+= [pscustomobject]@{ProcessId=41000;Name='python.exe';CreationDate=$created}
 }
}
function global:Get-NetTCPConnection {param($State,$ErrorAction) return $global:demoFixtureListeners}
function global:Get-CimInstance {
 param($ClassName,$Filter,$ErrorAction)
 if($Filter -match 'ProcessId\s*=\s*(\d+)'){
  $requestedId=[int]$Matches[1]
  return $global:demoFixtureProcesses | Where-Object {$_.ProcessId -eq $requestedId}
 }
 return $global:demoFixtureProcesses | Where-Object {$_.Name -eq 'java.exe'}
}
function global:Get-Process {
 param($Id,$ErrorAction)
 $item=$global:demoFixtureProcesses | Where-Object {$_.ProcessId -eq $Id}
 if(-not $item){return}
 $started=$item.CreationDate
 if($Case -eq 'reused-process'){$started=$started.AddMinutes(1)}
 return [pscustomobject]@{Id=$Id;StartTime=$started;Handle=123}
}
function global:Stop-Process {
 param($InputObject,$ErrorAction)
 $global:demoFixtureStopped.Add($InputObject.Id)
 $global:demoFixtureProcesses=@($global:demoFixtureProcesses | Where-Object {$_.ProcessId -ne $InputObject.Id})
 $global:demoFixtureListeners=@($global:demoFixtureListeners | Where-Object {$_.OwningProcess -ne $InputObject.Id})
}
$global:demoFixtureListingCount=0
function global:docker {
 param([Parameter(ValueFromRemainingArguments=$true)][string[]]$DockerArgs)
 $global:LASTEXITCODE=0
 $key=$DockerArgs -join ' '
 if($key -eq 'info'){if($Case -eq 'engine-off'){$global:LASTEXITCODE=1}; return}
 if($key -eq 'compose version'){return 'Docker Compose version test'}
 if($key -eq 'container ls --all --quiet'){return $global:demoFixtureContainers.Id}
 if($key -eq 'container ls --all --no-trunc --format {{json .}}'){
  $global:demoFixtureListingCount++
  if($Case -eq 'runner-shutdown' -and $global:demoFixtureListingCount -eq 2){
   $global:demoFixtureContainers=@($global:demoFixtureContainers | Where-Object {$_.Id -eq 'foreign'})
  }
  return @($global:demoFixtureContainers | ForEach-Object {
   @{ID=$_.Id;Names=$_.Name.TrimStart('/');Image=$_.Image;Labels=''} | ConvertTo-Json -Compress
  })
 }
 if($key.StartsWith('container inspect ')){
  if($Case -eq 'inspect-failure'){$global:LASTEXITCODE=1;return 'Cannot connect to the Docker daemon'}
  if($Case -eq 'disappearing' -and 'owned-db' -in $DockerArgs){
   $global:demoFixtureContainers=@($global:demoFixtureContainers | Where-Object {$_.Id -ne 'owned-db'})
   $global:LASTEXITCODE=1;return @('[]', 'Error response from daemon: No such container: owned-db')
  }
  return ConvertTo-Json -Depth 8 -InputObject @($global:demoFixtureContainers | Where-Object {$_.Id -in $DockerArgs})}
 if($key.StartsWith('compose -f ')){return '{"name":"source","services":{"core-service":{"build":{"context":"."}}}}'}
 if($key -eq 'image ls --quiet --no-trunc'){return $global:demoFixtureImages.Id}
 if($key.StartsWith('image inspect ')){return ConvertTo-Json -Depth 8 -InputObject $global:demoFixtureImages}
 if($key.StartsWith('container rm --force ')){
  $global:demoFixtureOperations.Add($key)
  if($Case -eq 'remove-failure'){$global:LASTEXITCODE=1; return 'fixture removal failure'}
  $global:demoFixtureContainers=@($global:demoFixtureContainers | Where-Object {$_.Id -ne $DockerArgs[-1]}); return
 }
 if($key.StartsWith('image rm ')){
  $global:demoFixtureOperations.Add($key)
  foreach($image in $global:demoFixtureImages){$image.RepoTags=@($image.RepoTags | Where-Object {$_ -ne $DockerArgs[-1]})}
  return
 }
 throw "Unexpected Docker command: $key"
}
$launcher=Join-Path $PSScriptRoot '../Demo-Launcher.ps1'
& $launcher -Action Clean -Preview:($Case -in @('preview','orphan-preview'))
$code=$LASTEXITCODE
if($Case -in @('preview','orphan-preview','engine-off')){
 if($global:demoFixtureOperations.Count -ne 0){throw 'Unexpected destructive operation'}
 if($Case -in @('preview','orphan-preview') -and $code -ne 0){throw 'Preview failed'}
 if($Case -eq 'engine-off' -and $code -ne 1){throw 'Engine-off failure not propagated'}
}elseif($Case -eq 'inspect-failure'){
 if($code -ne 1 -or $global:demoFixtureOperations.Count -ne 0){throw 'Inspect daemon failure was masked'}
}elseif($Case -eq 'remove-failure'){
 if($code -ne 1 -or $global:demoFixtureOperations.Count -ne 1 -or $global:demoFixtureOperations[0] -notlike 'container rm*'){throw 'Did not stop after failed deletion'}
}else{
 $expected=@('container rm --force owned-app','container rm --force owned-db','image rm source-core-service:latest','image rm mediflow-old:latest')
 if($Case -eq 'disappearing'){$expected=@($expected | Where-Object {$_ -ne 'container rm --force owned-db'}) + @('image rm postgres:16-alpine')}
 if($Case -eq 'runner-shutdown'){$expected=@($expected | Where-Object {$_ -notlike 'container rm*'}) + @('image rm postgres:16-alpine')}
 if($code -ne 0 -or @(Compare-Object $expected @($global:demoFixtureOperations)).Count -ne 0){throw "Unexpected removal set: $($global:demoFixtureOperations -join '; ')"}
 if(@($global:demoFixtureContainers).Count -ne 1 -or $global:demoFixtureContainers[0].Id -ne 'foreign'){throw 'Foreign container was changed'}
}
$expectedStops=0
if($Case -in @('orphans','reused-parent')){$expectedStops=1}
if($global:demoFixtureStopped.Count -ne $expectedStops){throw "Unexpected stopped processes: $($global:demoFixtureStopped -join ',')"}
if($Case -in @('clean','disappearing','runner-shutdown','orphans','reused-parent')){
 $operationCount=$global:demoFixtureOperations.Count
 & $launcher -Action Clean
 if($LASTEXITCODE -ne 0 -or $global:demoFixtureOperations.Count -ne $operationCount){throw 'Repeated cleanup changed resources'}
 if($global:demoFixtureStopped.Count -ne $expectedStops){throw 'Repeated cleanup stopped additional processes'}
}
Write-Host "PASS: $Case"
exit 0
