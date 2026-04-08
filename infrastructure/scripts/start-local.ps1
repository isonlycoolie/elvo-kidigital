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

Write-Host "[start-local] Root path: $RootPath"
Write-Host '[start-local] Building shared and service modules...'
& $buildScript -RootPath $RootPath -SkipTests:$SkipTests
if ($LASTEXITCODE -ne 0) {
    throw 'Build stage failed.'
}

Write-Host '[start-local] Starting Docker Compose stack...'
& docker compose -f $composeFile up -d --build
if ($LASTEXITCODE -ne 0) {
    throw 'Docker compose startup failed.'
}

Write-Host '[start-local] Stack started. Current status:'
& docker compose -f $composeFile ps
