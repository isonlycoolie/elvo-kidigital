param(
    [Parameter(Mandatory = $true)]
    [string]$ServiceName,
    [string]$RootPath = (Resolve-Path "$PSScriptRoot/../..").Path
)

$sharedFile = Join-Path $RootPath ".env.shared"
$serviceFile = Join-Path $RootPath "services/$ServiceName/.env"

function Import-EnvFile {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return
    }

    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
            return
        }

        $parts = $line -split "=", 2
        if ($parts.Count -eq 2) {
            [Environment]::SetEnvironmentVariable($parts[0], $parts[1], "Process")
        }
    }
}

Import-EnvFile -Path $sharedFile
Import-EnvFile -Path $serviceFile
Write-Host "Loaded env files in order: $sharedFile then $serviceFile"
