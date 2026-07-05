# Install ELVO git hooks from infrastructure/scripts/git-hooks/
$ErrorActionPreference = "Stop"

$repoRoot = (git rev-parse --show-toplevel)
$sourceDir = Join-Path $repoRoot "infrastructure/scripts/git-hooks"
$hooksDir = Join-Path $repoRoot ".git/hooks"

foreach ($hook in @("commit-msg", "prepare-commit-msg", "pre-push")) {
    $src = Join-Path $sourceDir $hook
    $dest = Join-Path $hooksDir $hook
    Copy-Item -Path $src -Destination $dest -Force
    Write-Host "Installed $hook"
}

Write-Host "Git hooks installed successfully."
