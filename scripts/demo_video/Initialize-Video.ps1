param([switch]$Preview, [switch]$PlanOnly)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$outputRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'target\mediflow-demo-video'))
if (-not $outputRoot.StartsWith($repoRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Video output must stay inside the repository target directory.'
}
if ($PlanOnly) { Write-Output "Template: $PSScriptRoot"; Write-Output "Working copy: $outputRoot"; return }
if (-not (Test-Path -LiteralPath $outputRoot)) {
    New-Item -ItemType Directory -Path $outputRoot | Out-Null
    foreach ($name in @('index.html','package.json','hyperframes.json','hyperframes.lock.json',
                        'recorder.mjs','assemble.py','SPEC.md','STORYLINES.md','coverage-template.json','README.md')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $outputRoot
    }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'compositions') -Destination $outputRoot -Recurse
    New-Item -ItemType Directory -Path (Join-Path $outputRoot 'assets') | Out-Null
}
Write-Output "Video working copy: $outputRoot"
if ($Preview) {
    Push-Location -LiteralPath $outputRoot
    try { & npx.cmd --yes hyperframes@0.9.2 preview --background --port 3017; if ($LASTEXITCODE -ne 0) { throw 'Preview did not start.' } }
    finally { Pop-Location }
}
