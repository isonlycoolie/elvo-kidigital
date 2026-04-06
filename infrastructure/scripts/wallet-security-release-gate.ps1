param(
    [string]$WorkspaceRoot = "$(Resolve-Path "$PSScriptRoot\..\..")",
    [string]$ServiceBaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Invoke-GateStep {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    Write-Host "[GATE] $Name"
    & $Action
}

Push-Location "$WorkspaceRoot\services\elvo-wallet-service"
try {
    Invoke-GateStep -Name "Compile wallet service" -Action { mvn -DskipTests compile }

    Invoke-GateStep -Name "Run security-focused test suite" -Action {
        mvn "-Dtest=WalletControllerTest,ChaosResilienceSuiteTest,DisasterRecoveryValidationServiceTest,WalletMetricsRecorderSecurityTest" test
    }

    Invoke-GateStep -Name "Run dependency vulnerability scan" -Action {
        mvn -Psecurity-scanning verify
    }
}
finally {
    Pop-Location
}

Invoke-GateStep -Name "Validate disaster recovery endpoint" -Action {
    $response = Invoke-WebRequest -Uri "$ServiceBaseUrl/api/v1/internal/resilience/disaster-recovery/validate" -Method Get -UseBasicParsing
    if ($response.StatusCode -ne 200) {
        throw "DR readiness endpoint returned status $($response.StatusCode)"
    }
}

Write-Host "Wallet security release gate passed."
