param(
    [string]$RootPath = (Resolve-Path "$PSScriptRoot/../..").Path
)

$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $RootPath 'infrastructure/docker/docker-compose.yml'

if (-not (Test-Path $composeFile)) {
    throw "Compose file not found: $composeFile"
}

Write-Host "[stop-local] Root path: $RootPath"
Write-Host '[stop-local] Stopping runtime services (volumes preserved)...'
& docker compose -f $composeFile stop
if ($LASTEXITCODE -ne 0) {
    throw 'Docker compose stop failed.'
}

Write-Host '[stop-local] Current status:'
& docker compose -f $composeFile ps
