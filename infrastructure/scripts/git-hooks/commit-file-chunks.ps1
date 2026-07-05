param(
    [Parameter(Mandatory = $true)]
    [string]$SnapshotRoot,
    [Parameter(Mandatory = $true)]
    [string]$RelativePath,
    [Parameter(Mandatory = $true)]
    [string[]]$Messages,
    [int]$ChunkLines = 90,
    [int]$MaxInsertions = 100
)

$ErrorActionPreference = "Stop"
$RepoRoot = git rev-parse --show-toplevel
Set-Location $RepoRoot

$SourceFile = Join-Path $SnapshotRoot $RelativePath
$TargetFile = Join-Path $RepoRoot ("landingpage/" + $RelativePath.Replace("\", "/"))
$TargetDir = Split-Path $TargetFile -Parent
if (-not (Test-Path $TargetDir)) {
    New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
}

$lines = Get-Content -Path $SourceFile -Encoding UTF8
$total = $lines.Count
$chunkCount = [Math]::Ceiling($total / [double]$ChunkLines)

if ($chunkCount -gt $Messages.Count) {
    Write-Error "Need $($chunkCount) messages for $RelativePath but got $($Messages.Count)"
    exit 1
}

$part = 0
for ($start = 0; $start -lt $total; $start += $ChunkLines) {
    $end = [Math]::Min($start + $ChunkLines, $total) - 1
    if ($end -ge ($total - 1)) {
        Copy-Item -Path $SourceFile -Destination $TargetFile -Force
    } else {
        $chunk = $lines[0..$end]
        Set-Content -Path $TargetFile -Value $chunk -Encoding UTF8
    }

    $msg = $Messages[$part]
    if ($msg -match '\bpart\s+\d+\b') {
        Write-Error "Message must not contain 'part N': $msg"
        exit 1
    }

    & "$RepoRoot/infrastructure/scripts/git-hooks/commit-batch.ps1" -Paths @("landingpage/$($RelativePath.Replace('\','/'))") -Message $msg -MaxInsertions $MaxInsertions
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $part++
}
