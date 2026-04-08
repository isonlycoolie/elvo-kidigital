param(
    [string]$RootPath = (Resolve-Path "$PSScriptRoot/../..").Path,
    [switch]$SkipTests = $true
)

$ErrorActionPreference = 'Stop'

function Invoke-MavenBuild {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ProjectPath,
        [switch]$SkipTests
    )

    Push-Location $ProjectPath
    try {
        $args = @('clean', 'package', '-Ddependency.check.skip=true')
        if ($SkipTests) {
            $args += '-DskipTests'
        }

        Write-Host "Building project: $ProjectPath"
        & mvn @args

        if ($LASTEXITCODE -ne 0) {
            throw "Build failed for $ProjectPath"
        }
    }
    finally {
        Pop-Location
    }
}

Write-Host "Root path: $RootPath"

# 1) Build shared modules first when present.
$sharedPomFiles = Get-ChildItem -Path (Join-Path $RootPath 'shared') -Recurse -Filter 'pom.xml' -File -ErrorAction SilentlyContinue
if ($null -ne $sharedPomFiles -and $sharedPomFiles.Count -gt 0) {
    Write-Host "Found shared module pom files: $($sharedPomFiles.Count)"
    foreach ($pom in $sharedPomFiles) {
        Invoke-MavenBuild -ProjectPath $pom.Directory.FullName -SkipTests:$SkipTests
    }
}
else {
    Write-Host 'No shared Maven modules found under shared/. Skipping shared build stage.'
}

# 2) Build service modules in deterministic order.
$serviceOrder = @(
    'services/elvo-identity-service',
    'services/elvo-wallet-service',
    'services/elvo-billing-service'
)

foreach ($serviceRelPath in $serviceOrder) {
    $servicePath = Join-Path $RootPath $serviceRelPath
    if (-not (Test-Path $servicePath)) {
        throw "Required service path not found: $servicePath"
    }

    Invoke-MavenBuild -ProjectPath $servicePath -SkipTests:$SkipTests
}

Write-Host 'Shared-first build pipeline completed successfully.'
