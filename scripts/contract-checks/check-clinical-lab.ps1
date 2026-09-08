# Run against actual billing receive records without adding cross-service Maven dependencies.
$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $repositoryRoot
try {
    & mvn -q -pl backend/clinical-service,backend/lab-service,backend/billing-service -am test dependency:build-classpath '-Dmdep.outputFile=target/wire-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Clinical/lab/billing build or tests failed.' }

    $classpathFile = Join-Path $repositoryRoot 'backend/clinical-service/target/wire-classpath.txt'
    $classDirectories = @(
        'backend/clinical-service/target/classes',
        'backend/lab-service/target/classes',
        'backend/billing-service/target/classes',
        'backend/common/target/classes'
    ) | ForEach-Object { Join-Path $repositoryRoot $_ }
    $wireClasspath = ($classDirectories -join [IO.Path]::PathSeparator) +
        [IO.Path]::PathSeparator + (Get-Content -LiteralPath $classpathFile -Raw).Trim()
    & java --class-path $wireClasspath (Join-Path $PSScriptRoot 'ClinicalLabWireCheck.java')
    if ($LASTEXITCODE -ne 0) { throw 'Producer-to-billing JSON contract check failed.' }
}
finally {
    Pop-Location
}
