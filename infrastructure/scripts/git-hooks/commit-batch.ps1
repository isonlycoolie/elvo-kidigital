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

git add -- @Paths
$stat = git diff --cached --numstat
if (-not $stat) {
    Write-Host "Nothing staged for: $Message"
    exit 0
}

$insertions = 0
foreach ($line in $stat) {
    if ($line -match '^(\d+)') {
        $insertions += [int]$Matches[1]
    }
}

Write-Host "Staged insertions: $insertions"
git diff --cached --stat

if ($insertions -gt $MaxInsertions -and -not $AllowOverLimit) {
    Write-Host "ERROR: $insertions insertions exceeds limit $MaxInsertions" -ForegroundColor Red
    git reset HEAD -- @Paths
    exit 1
}

git commit -m $Message
Write-Host "Committed: $Message"
