param(
    [string]$RootPath = (Resolve-Path "$PSScriptRoot/../..").Path
)

$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $RootPath 'infrastructure/docker/docker-compose.yml'

if (-not (Test-Path $composeFile)) {
    throw "Compose file not found: $composeFile"
}

Write-Host "[reset-local] Root path: $RootPath"
Write-Host '[reset-local] Removing containers, networks, and volumes for this stack...'
& docker compose -f $composeFile down --volumes --remove-orphans
if ($LASTEXITCODE -ne 0) {
    throw 'Docker compose reset failed.'
}

Write-Host '[reset-local] Reset completed.'
