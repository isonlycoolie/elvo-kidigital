param(
    [Parameter(Mandatory = $true)]
    [string[]]$Paths,
    [Parameter(Mandatory = $true)]
    [string]$Message,
    [int]$MaxInsertions = 100,
    [switch]$AllowOverLimit
)

$ErrorActionPreference = "Stop"
Set-Location (git rev-parse --show-toplevel)

function Get-StagedInsertions {
    $insertions = 0
    $stat = git diff --cached --numstat
    if (-not $stat) { return 0 }
    foreach ($line in $stat) {
        if ($line -match '^(\d+)') {
            $insertions += [int]$Matches[1]
        }
    }
    return $insertions
}

git add -- @Paths
$insertions = Get-StagedInsertions
$cached = git diff --cached --name-only
if (-not $cached) {
    Write-Host "Nothing staged for: $Message"
    exit 0
}

Write-Host "Staged insertions: $insertions"
git diff --cached --stat

if ($insertions -eq 0) {
    git commit -m $Message
    Write-Host "Committed (binary): $Message"
    exit 0
}

if ($insertions -gt $MaxInsertions -and -not $AllowOverLimit) {
    Write-Host "ERROR: $insertions insertions exceeds limit $MaxInsertions" -ForegroundColor Red
    git reset HEAD -- @Paths 2>$null
    exit 1
}

git commit -m $Message
Write-Host "Committed: $Message"
