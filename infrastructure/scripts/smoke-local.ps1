param(
    [switch]$SkipIdentitySmtpShim,
    [switch]$FullE2e
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$rootPath = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $rootPath

$composeFile = "infrastructure/docker/docker-compose.yml"

function Get-ContainerEnvValue {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerName,
        [Parameter(Mandatory = $true)][string]$Key
    )

    $raw = docker inspect $ContainerName --format "{{range .Config.Env}}{{println .}}{{end}}"
    $line = $raw | Select-String -Pattern ("^" + [Regex]::Escape($Key) + "=") | Select-Object -First 1
    if (-not $line) {
        return $null
    }
    $value = ($line.ToString() -split "=", 2)[1]
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $null
    }
    return $value.Trim()
}

function Wait-Healthy {
    param(
        [Parameter(Mandatory = $true)][string[]]$Containers,
        [int]$Attempts = 30,
        [int]$DelaySeconds = 2
    )

    for ($i = 0; $i -lt $Attempts; $i++) {
        $allHealthy = $true
        foreach ($container in $Containers) {
            $status = docker inspect -f '{{.State.Health.Status}}' $container 2>$null
            if ($status -ne 'healthy') {
                $allHealthy = $false
                break
            }
        }

        if ($allHealthy) {
            return
        }

        Start-Sleep -Seconds $DelaySeconds
    }

    throw "Timed out waiting for healthy containers: $($Containers -join ', ')"
}

function B64Url {
    param([byte[]]$Bytes)

    return [Convert]::ToBase64String($Bytes).TrimEnd('=') -replace '\+', '-' -replace '/', '_'
}

function New-Hs256Jwt {
    param(
        [Parameter(Mandatory = $true)][string]$Secret,
        [Parameter(Mandatory = $true)][hashtable]$Payload
    )

    $headerJson = '{"alg":"HS256","typ":"JWT"}'
    $payloadJson = $Payload | ConvertTo-Json -Compress

    $header = B64Url([Text.Encoding]::UTF8.GetBytes($headerJson))
    $body = B64Url([Text.Encoding]::UTF8.GetBytes($payloadJson))
    $unsigned = "$header.$body"

    $hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($Secret))
    $signature = B64Url($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsigned)))
    return "$unsigned.$signature"
}

function Remove-ContainerIfExists {
    param([Parameter(Mandatory = $true)][string]$Name)

    $id = docker ps -aq --filter "name=^$Name$"
    if ($id) {
        docker rm -f $id | Out-Null
    }
}

function New-InternalServiceJwt {
    param(
        [Parameter(Mandatory = $true)][string]$Secret,
        [Parameter(Mandatory = $true)][string]$SourceService,
        [Parameter(Mandatory = $true)][string]$Issuer,
        [Parameter(Mandatory = $true)][string]$Audience
    )

    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    return New-Hs256Jwt -Secret $Secret -Payload @{
        iss = $Issuer
        aud = $Audience
        iat = $now
        exp = ($now + 300)
        sourceService = $SourceService
        serviceIdentity = $SourceService
        roles = @('INTERNAL_SERVICE')
    }
}

function Get-GreenMailOtp {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerName,
        [Parameter(Mandatory = $true)][string]$RecipientEmail
    )

    $raw = docker exec $ContainerName wget -qO- "http://localhost:8080/api/user/local-email-user/email" 2>$null
    if (-not $raw) {
        throw 'Unable to fetch messages from GreenMail.'
    }

    $messages = $raw | ConvertFrom-Json
    foreach ($message in $messages) {
        if ($message.to -match [Regex]::Escape($RecipientEmail)) {
            $body = $message.body
            if ($body -match '(\d{6})') {
                return $Matches[1]
            }
        }
    }

    throw "No OTP found in GreenMail for $RecipientEmail"
}

function Invoke-IdentityRegisterWithRetry {
    param(
        [Parameter(Mandatory = $true)][string]$Body,
        [int]$Attempts = 5,
        [int]$DelaySeconds = 2
    )

    $lastError = $null
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            return Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/auth/register/email' -ContentType 'application/json' -Body $Body
        }
        catch {
            $lastError = $_
            if ($attempt -lt $Attempts) {
                Start-Sleep -Seconds $DelaySeconds
                continue
            }
        }
    }

    throw $lastError
}

function Wait-GreenMailReady {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerName,
        [int]$Attempts = 30,
        [int]$DelaySeconds = 1
    )

    for ($i = 0; $i -lt $Attempts; $i++) {
        $logs = docker logs $ContainerName 2>&1
        if ($logs -match 'Starting GreenMail API server') {
            return
        }
        Start-Sleep -Seconds $DelaySeconds
    }

    throw "Timed out waiting for GreenMail readiness ($ContainerName)."
}

function Get-BillingAuthHeaders {
    $billingPassword = $env:ELVO_BILLING_BASIC_PASSWORD
    if (-not $billingPassword) {
        $billingLogs = docker logs elvo-billing-service 2>&1
        $match = ($billingLogs | Select-String -Pattern 'Using generated security password:\s*([A-Za-z0-9\-]+)' | Select-Object -Last 1)
        if ($match) {
            $billingPassword = $match.Matches[0].Groups[1].Value
        }
    }

    if (-not $billingPassword) {
        throw 'Billing smoke failed: unable to resolve basic auth password. Set ELVO_BILLING_BASIC_PASSWORD and retry.'
    }

    $billingPair = "user:$billingPassword"
    $billingAuth = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($billingPair))
    return @{ Authorization = $billingAuth }
}

Write-Host "[smoke-local] Root path: $rootPath"
Write-Host "[smoke-local] Verifying core stack health..."
Wait-Healthy -Containers @('elvo-identity-service', 'elvo-wallet-service', 'elvo-billing-service', 'elvo-account-management-service')

docker compose -f $composeFile ps

$greenmailName = 'task24-greenmail'
$smtpProxyName = 'task24-smtp-proxy'
$smtpShimStarted = $false

try {
    if (-not $SkipIdentitySmtpShim) {
        Write-Host "[smoke-local] Preparing temporary SMTP shim (ports 587 and 1025) for identity registration flow..."
        Remove-ContainerIfExists -Name $smtpProxyName
        Remove-ContainerIfExists -Name $greenmailName

        docker run -d --name $greenmailName --network docker_backend-network `
            -e GREENMAIL_OPTS='-Dgreenmail.setup.test.smtp -Dgreenmail.hostname=0.0.0.0 -Dgreenmail.users=local-email-user:local-email-password' `
            greenmail/standalone:2.1.1 | Out-Null

        Wait-GreenMailReady -ContainerName $greenmailName

        docker run -d --name $smtpProxyName --network docker_backend-network --network-alias smtp.example.com `
            alpine sh -c "socat TCP-LISTEN:587,fork,reuseaddr TCP:$greenmailName:3025 & socat TCP-LISTEN:1025,fork,reuseaddr TCP:$greenmailName:3025 & wait" | Out-Null

        $smtpShimStarted = $true
        Write-Host '[smoke-local] Recreating identity service to ensure SMTP shim is reachable from runtime process...'
        docker compose -f $composeFile up -d --force-recreate --no-deps elvo-identity-service | Out-Null
        Wait-Healthy -Containers @('elvo-identity-service')
    }

    $identitySummary = ''
    if ($SkipIdentitySmtpShim) {
        Write-Host "[smoke-local] Running identity login smoke (no SMTP dependency mode)..."
        $identityLoginBody = @{
            identifier = 'smoke-no-user@example.com'
            password = 'Password123!'
            deviceId = 'device-smoke-01'
            deviceType = 'android'
            sourceIp = '10.24.0.10'
            sourceUserAgent = 'task24-smoke'
        } | ConvertTo-Json

        $identityStatus = 0
        $identityResponseText = ''
        try {
            $ok = Invoke-WebRequest -Method Post -Uri 'http://localhost:8081/auth/login' -ContentType 'application/json' -Body $identityLoginBody -ErrorAction Stop
            $identityStatus = [int]$ok.StatusCode
            $identityResponseText = $ok.Content
        } catch {
            if (-not $_.Exception.Response) {
                throw
            }
            $resp = $_.Exception.Response
            $identityStatus = [int]$resp.StatusCode
            $reader = New-Object IO.StreamReader($resp.GetResponseStream())
            $identityResponseText = $reader.ReadToEnd()
        }

        if ($identityStatus -ne 500 -or $identityResponseText -notmatch 'INTERNAL_ERROR') {
            throw "Identity smoke failed: expected 500 INTERNAL_ERROR for invalid-credential login path, got status=$identityStatus body=$identityResponseText"
        }

        $identitySummary = 'identity login invalid-credential path: PASS'
    }
    else {
        Write-Host "[smoke-local] Running identity registration smoke..."
        $ts = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
        $email = "smoke.task24.$ts@example.com"
        $identityBody = @{
            email = $email
            password = 'Password123!'
            displayName = 'Task24 Smoke User'
            sourceIp = '10.24.0.10'
            sourceUserAgent = 'task24-smoke'
        } | ConvertTo-Json

        $identityResponse = Invoke-IdentityRegisterWithRetry -Body $identityBody
        if (-not $identityResponse.success -or $identityResponse.data.status -ne 'VERIFICATION_REQUIRED') {
            throw "Identity smoke failed: unexpected register response."
        }

        $identitySummary = "identity register/email: PASS ($email)"

        if ($FullE2e) {
            Write-Host "[smoke-local] Running full E2E verify + wallet + account sync..."
            $verificationToken = $identityResponse.data.verificationToken
            Start-Sleep -Seconds 2
            $otp = Get-GreenMailOtp -ContainerName $greenmailName -RecipientEmail $email

            $verifyBody = @{
                identifier = $email
                verificationToken = $verificationToken
                otpCode = $otp
                sourceIp = '10.24.0.10'
                sourceUserAgent = 'task24-smoke'
            } | ConvertTo-Json

            $verifyResponse = Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/auth/verify-email-otp' -ContentType 'application/json' -Body $verifyBody
            if (-not $verifyResponse.data.verified) {
                throw "E2E verify failed: $($verifyResponse | ConvertTo-Json -Compress)"
            }

            $e2eWalletSecret = Get-ContainerEnvValue -ContainerName 'elvo-wallet-service' -Key 'ELVO_INTERNAL_JWT_SECRET'
            if (-not $e2eWalletSecret) {
                $e2eWalletSecret = 'wallet-internal-jwt-secret-local-at-least-32-chars'
            }

            $jwtIssuer = Get-ContainerEnvValue -ContainerName 'elvo-wallet-service' -Key 'ELVO_INTERNAL_JWT_ISSUER'
            if (-not $jwtIssuer) { $jwtIssuer = 'elvo-wallet-service-internal-dev' }
            $jwtAudience = Get-ContainerEnvValue -ContainerName 'elvo-wallet-service' -Key 'ELVO_INTERNAL_JWT_AUDIENCE'
            if (-not $jwtAudience) { $jwtAudience = $jwtIssuer }

            $serviceJwt = New-InternalServiceJwt -Secret $e2eWalletSecret -SourceService 'identity-service' -Issuer $jwtIssuer -Audience $jwtAudience
            $serviceHeaders = @{
                Authorization = "Bearer $serviceJwt"
                'X-Source-Service' = 'identity-service'
            }

            $registeredUserId = docker exec -e PGPASSWORD=(Get-ContainerEnvValue -ContainerName 'identity-db' -Key 'POSTGRES_PASSWORD') identity-db psql -U (Get-ContainerEnvValue -ContainerName 'identity-db' -Key 'POSTGRES_USER') -d identity_db -t -A -c "select id::text from users where email = '$email' limit 1;"
            $registeredUserId = $registeredUserId.Trim()
            if ([string]::IsNullOrWhiteSpace($registeredUserId)) {
                throw 'E2E failed: registered user not found in identity_db.'
            }

            $walletCheck = Invoke-RestMethod -Method Get -Uri "http://localhost:8082/api/v1/internal/wallets/$registeredUserId/balance" -Headers $serviceHeaders
            if ($null -eq $walletCheck.balance) {
                throw 'E2E failed: wallet not provisioned after verification.'
            }

            $creditBody = @{
                amount = 1000.00
                idempotencyKey = "smoke-credit-$ts"
                reason = 'smoke-local-e2e'
            } | ConvertTo-Json
            $creditResponse = Invoke-RestMethod -Method Post -Uri "http://localhost:8082/api/v1/internal/wallets/$registeredUserId/credit" -Headers $serviceHeaders -ContentType 'application/json' -Body $creditBody
            if (-not $creditResponse.success) {
                throw "E2E wallet credit failed: $($creditResponse | ConvertTo-Json -Compress)"
            }

            $e2eBillingHeaders = Get-BillingAuthHeaders
            $lookupBody = @{
                referenceNumber = "SMOKE-LUKU-$ts"
                lookupRequired = $true
                metadata = '{"meterType":"PREPAID","providerCode":"LUKU"}'
            } | ConvertTo-Json
            $lookupResponse = Invoke-RestMethod -Method Post -Uri 'http://localhost:8083/api/v1/bill-payments/lookup' -Headers $e2eBillingHeaders -ContentType 'application/json' -Body $lookupBody
            if (-not $lookupResponse.referenceNumber) {
                throw "E2E bill pay lookup failed: $($lookupResponse | ConvertTo-Json -Compress)"
            }

            $accountJwt = New-InternalServiceJwt -Secret $e2eWalletSecret -SourceService 'identity-service' -Issuer $jwtIssuer -Audience $jwtAudience
            $accountHeaders = @{
                Authorization = "Bearer $accountJwt"
                'X-Source-Service' = 'identity-service'
            }
            $accountResponse = Invoke-RestMethod -Method Get -Uri "http://localhost:8084/api/v1/internal/accounts/user/$registeredUserId" -Headers $accountHeaders
            if ($accountResponse.data.accountStatus -ne 'ACTIVE') {
                throw "E2E account sync failed: expected ACTIVE, got $($accountResponse | ConvertTo-Json -Compress)"
            }

            $identitySummary = "$identitySummary; full E2E verify/wallet/credit/bill-lookup/account-active: PASS"
        }
    }

    Write-Host "[smoke-local] Running wallet balance smoke..."
    $walletSecret = Get-ContainerEnvValue -ContainerName 'elvo-wallet-service' -Key 'ELVO_INTERNAL_JWT_SECRET'
    if (-not $walletSecret) {
        $walletSecret = 'wallet-internal-jwt-secret-local-at-least-32-chars'
    }

    $jwtIssuer = Get-ContainerEnvValue -ContainerName 'elvo-wallet-service' -Key 'ELVO_INTERNAL_JWT_ISSUER'
    if (-not $jwtIssuer) { $jwtIssuer = 'elvo-wallet-service-internal-dev' }
    $jwtAudience = Get-ContainerEnvValue -ContainerName 'elvo-wallet-service' -Key 'ELVO_INTERNAL_JWT_AUDIENCE'
    if (-not $jwtAudience) { $jwtAudience = $jwtIssuer }

    $walletJwt = New-InternalServiceJwt -Secret $walletSecret -SourceService 'identity-service' -Issuer $jwtIssuer -Audience $jwtAudience

    $walletHeaders = @{
        Authorization = "Bearer $walletJwt"
        'X-Source-Service' = 'identity-service'
    }

    $walletDbUser = Get-ContainerEnvValue -ContainerName 'wallet-db' -Key 'POSTGRES_USER'
    if (-not $walletDbUser) {
        $walletDbUser = 'wallet_user'
    }

    $walletDbName = Get-ContainerEnvValue -ContainerName 'wallet-db' -Key 'POSTGRES_DB'
    if (-not $walletDbName) {
        $walletDbName = 'wallet_db'
    }

    $walletDbPassword = Get-ContainerEnvValue -ContainerName 'wallet-db' -Key 'POSTGRES_PASSWORD'
    if (-not $walletDbPassword) {
        $walletDbPassword = 'wallet_password'
    }

    $walletProbeUserId = docker exec -e PGPASSWORD=$walletDbPassword wallet-db psql -U $walletDbUser -d $walletDbName -t -A -c "select user_id::text from wallets order by created_at asc limit 1;"
    if ([string]::IsNullOrWhiteSpace($walletProbeUserId)) {
        $seedWalletId = [guid]::NewGuid().ToString()
        $seedUserId = [guid]::NewGuid().ToString()
        $seedNow = [DateTimeOffset]::UtcNow.ToString('o')
        $seedSql = "insert into wallets (id, user_id, balance, reserved_balance, status, created_at, updated_at, version) values ('$seedWalletId', '$seedUserId', 0.0000, 0.0000, 'ACTIVE', '$seedNow', '$seedNow', 0);"
        docker exec -e PGPASSWORD=$walletDbPassword wallet-db psql -U $walletDbUser -d $walletDbName -c $seedSql | Out-Null
        $walletProbeUserId = $seedUserId
    }
    $walletProbeUserId = $walletProbeUserId.Trim()

    $walletResponse = Invoke-RestMethod -Method Get -Uri "http://localhost:8082/api/v1/internal/wallets/$walletProbeUserId/balance" -Headers $walletHeaders
    if (-not $walletResponse -or $null -eq $walletResponse.balance) {
        throw "Wallet smoke failed: balance payload missing."
    }

    Write-Host "[smoke-local] Running billing lookup smoke..."
    $billingHeaders = Get-BillingAuthHeaders

    $paymentId = [guid]::NewGuid().ToString()
    $billingStatus = 0
    $billingBody = ''

    try {
        $ok = Invoke-WebRequest -Method Get -Uri "http://localhost:8083/api/v1/bill-payments/$paymentId" -Headers $billingHeaders -ErrorAction Stop
        $billingStatus = [int]$ok.StatusCode
        $billingBody = $ok.Content
    } catch {
        if (-not $_.Exception.Response) {
            throw
        }
        $resp = $_.Exception.Response
        $billingStatus = [int]$resp.StatusCode
        $reader = New-Object IO.StreamReader($resp.GetResponseStream())
        $billingBody = $reader.ReadToEnd()
    }

    if ($billingStatus -ne 400 -or $billingBody -notmatch 'PAYMENT_VALIDATION_ERROR') {
        throw "Billing smoke failed: expected 400 PAYMENT_VALIDATION_ERROR, got status=$billingStatus body=$billingBody"
    }

    Write-Host "[smoke-local] Running account management lookup smoke..."
    $accountProbeUserId = '88888888-8888-4888-8888-888888888888'
    $accountStatus = 0
    $accountBody = ''

    try {
        $ok = Invoke-WebRequest -Method Get -Uri "http://localhost:8084/api/v1/internal/accounts/user/$accountProbeUserId" -ErrorAction Stop
        $accountStatus = [int]$ok.StatusCode
        $accountBody = $ok.Content
    } catch {
        if (-not $_.Exception.Response) {
            throw
        }
        $resp = $_.Exception.Response
        $accountStatus = [int]$resp.StatusCode
        $reader = New-Object IO.StreamReader($resp.GetResponseStream())
        $accountBody = $reader.ReadToEnd()
    }

    if ($accountStatus -ne 404 -and $accountStatus -ne 401 -and $accountStatus -ne 403) {
        throw "Account smoke failed: expected 404 (unknown user) or 401/403 (secured internal endpoint), got status=$accountStatus body=$accountBody"
    }

    Write-Host "[smoke-local] Smoke summary:"
    Write-Host "  - $identitySummary"
    Write-Host "  - wallet internal balance: PASS"
    Write-Host "  - billing get payment validation path: PASS"
    Write-Host "  - account internal lookup unknown-user path: PASS"
    Write-Host "[smoke-local] All local runtime smoke checks passed."
}
finally {
    if ($smtpShimStarted) {
        Write-Host '[smoke-local] Cleaning temporary SMTP shim containers...'
        Remove-ContainerIfExists -Name $smtpProxyName
        Remove-ContainerIfExists -Name $greenmailName
    }
}
