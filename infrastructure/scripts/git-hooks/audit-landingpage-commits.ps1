# Audit landingpage commits for insertion limits and message quality.
param(
    [string]$Branch = "landingpage-v2",
    [string]$Base = "2be42d3"
)

$ErrorActionPreference = "Stop"
Set-Location (git rev-parse --show-toplevel)

$mergeBase = git merge-base $Branch $Base 2>$null
if (-not $mergeBase) { $mergeBase = $Base }

$commits = git log --reverse --format=%H "$mergeBase..$Branch" -- landingpage/
if (-not $commits) {
    $commits = git log --reverse --format=%H "$mergeBase..HEAD" -- landingpage/
}

$violations = @()
$partMsgs = @()
$lockfileMsg = "chore: add npm lockfile"

foreach ($hash in $commits) {
    $msg = git log -1 --format=%s $hash
    if ($msg -match '\bpart\s+\d+\b') {
        $partMsgs += [PSCustomObject]@{ Hash = $hash.Substring(0,7); Message = $msg }
    }

    $numstat = git show --numstat --format="" $hash -- landingpage/
    $ins = 0
    foreach ($line in $numstat) {
        if ($line -match '^(\d+)') { $ins += [int]$Matches[1] }
    }
    if ($ins -gt 100 -and $msg -ne $lockfileMsg) {
        $violations += [PSCustomObject]@{ Hash = $hash.Substring(0,7); Insertions = $ins; Message = $msg }
    }
}

Write-Host "Total landingpage commits: $($commits.Count)"

if ($partMsgs.Count -gt 0) {
    Write-Host "FAIL: messages contain 'part N':"
    $partMsgs | Format-Table -AutoSize
    exit 1
}

if ($violations.Count -gt 0) {
    Write-Host "FAIL: insertion limit violations:"
    $violations | Format-Table -AutoSize
    exit 1
}

Write-Host "OK: all commits <= 100 insertions (lockfile excepted)"
Write-Host "OK: no opaque part N messages"

$badMsgs = git log --format=%s "$mergeBase..$Branch" -- landingpage/ 2>$null |
    Select-String -Pattern "add landingpage|landingpage "
if ($badMsgs) {
    Write-Host "FAIL: old-style landingpage prefix messages:"
    $badMsgs
    exit 1
}

Write-Host "OK: commit messages look clean"
