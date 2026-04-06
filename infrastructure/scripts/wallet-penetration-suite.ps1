param(
    [string]$ServiceBaseUrl = "http://localhost:8080",
    [string]$WorkspaceRoot = "$(Resolve-Path "$PSScriptRoot\..\..")"
)

$ErrorActionPreference = "Stop"

Write-Host "Running wallet security verification suite..."

Push-Location "$WorkspaceRoot\services\elvo-wallet-service"
try {
    mvn "-Dtest=WalletControllerTest,ChaosResilienceSuiteTest" test
    mvn -Psecurity-scanning verify
}
finally {
    Pop-Location
}

Write-Host "Running OWASP ZAP baseline scan against $ServiceBaseUrl ..."
$zapCmd = @(
    "docker", "run", "--rm",
    "-t",
    "owasp/zap2docker-stable",
    "zap-baseline.py",
    "-t", $ServiceBaseUrl,
    "-m", "5",
    "-I"
)
& $zapCmd[0] $zapCmd[1..($zapCmd.Length - 1)]

Write-Host "Penetration suite completed. Review Maven and ZAP reports before release."
