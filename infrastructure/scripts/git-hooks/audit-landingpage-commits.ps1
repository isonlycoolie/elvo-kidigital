# Audit landingpage commits for insertion limit violations.
$ErrorActionPreference = "Stop"
Set-Location (git rev-parse --show-toplevel)

$base = git merge-base landingpage-replay 2be42d3 2>$null
if (-not $base) { $base = "2be42d3" }
$commits = git log --reverse --format=%H "$base..landingpage-replay" -- landingpage/
if (-not $commits) {
    $commits = git log --reverse --format=%H "$base..HEAD" -- landingpage/
}
$violations = @()
$lockfileMsg = "chore: add npm lockfile"

foreach ($hash in $commits) {
    $msg = git log -1 --format=%s $hash
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
if ($violations.Count -eq 0) {
    Write-Host "OK: all commits <= 100 insertions (lockfile excepted)"
} else {
    Write-Host "VIOLATIONS:"
    $violations | Format-Table -AutoSize
    exit 1
}

$badMsgs = git log --format=%s "$base..landingpage-replay" -- landingpage/ 2>$null | Select-String -Pattern "add landingpage|landingpage "
if ($badMsgs) {
    Write-Host "Old-style messages:"
    $badMsgs
    exit 1
}

Write-Host "OK: commit messages look clean"
