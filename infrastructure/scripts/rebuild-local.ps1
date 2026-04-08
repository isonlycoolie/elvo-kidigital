param(
    [string]$RootPath = (Resolve-Path "$PSScriptRoot/../..").Path,
    [switch]$SkipTests = $true
)

$ErrorActionPreference = 'Stop'

$buildScript = Join-Path $RootPath 'infrastructure/scripts/build-shared-first.ps1'
$composeFile = Join-Path $RootPath 'infrastructure/docker/docker-compose.yml'

if (-not (Test-Path $buildScript)) {
    throw "Build script not found: $buildScript"
}

if (-not (Test-Path $composeFile)) {
    throw "Compose file not found: $composeFile"
}

Write-Host "[rebuild-local] Root path: $RootPath"
Write-Host '[rebuild-local] Rebuilding shared and service modules...'
& $buildScript -RootPath $RootPath -SkipTests:$SkipTests
if ($LASTEXITCODE -ne 0) {
    throw 'Build stage failed.'
}

Write-Host '[rebuild-local] Recreating runtime stack with rebuilt images...'
& docker compose -f $composeFile up -d --build --force-recreate
if ($LASTEXITCODE -ne 0) {
    throw 'Docker compose rebuild failed.'
}

Write-Host '[rebuild-local] Current status:'
& docker compose -f $composeFile ps
