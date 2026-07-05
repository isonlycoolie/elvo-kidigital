param(
    [Parameter(Mandatory = $true)]
    [string]$SnapshotRoot,
    [Parameter(Mandatory = $true)]
    [string]$RepoRoot
)

$ErrorActionPreference = "Stop"

function Commit-Paths($msg, [string[]]$paths) {
    & "$RepoRoot/infrastructure/scripts/git-hooks/commit-batch.ps1" -Paths $paths -Message $msg
    if ($LASTEXITCODE -ne 0) { throw "Commit failed: $msg" }
}

function Write-CumulativeSlice {
    param(
        [string]$RelativePath,
        [string[]]$AllLines,
        [int]$EndIndex,
        [string]$Message
    )
    $target = Join-Path $RepoRoot ("landingpage/" + $RelativePath.Replace("\", "/"))
    $dir = Split-Path $target -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $slice = $AllLines[0..$EndIndex]
    Set-Content -Path $target -Value $slice -Encoding UTF8
    Commit-Paths $Message @("landingpage/$($RelativePath.Replace('\','/'))")
}

function Commit-SiteCopySections {
    $rel = "src\content\site-copy.ts"
    $lines = Get-Content (Join-Path $SnapshotRoot $rel) -Encoding UTF8
    $slices = @(
        @{ End = 38; Message = "content: hero headline copy" },
        @{ End = 68; Message = "content: features grid copy" },
        @{ End = 94; Message = "content: cards waitlist copy" },
        @{ End = 106; Message = "content: transfer section copy" },
        @{ End = 118; Message = "content: bills payment copy" },
        @{ End = 148; Message = "content: everyday wallet copy" },
        @{ End = 193; Message = "content: trust advantage copy" },
        @{ End = 209; Message = "content: security founder copy" },
        @{ End = 255; Message = "content: faq questions copy" },
        @{ End = 263; Message = "content: footer tagline copy" }
    )
    foreach ($s in $slices) {
        Write-CumulativeSlice -RelativePath $rel -AllLines $lines -EndIndex $s.End -Message $s.Message
    }
}

function Commit-GlobalsCssSections {
    $rel = "src\app\globals.css"
    $lines = Get-Content (Join-Path $SnapshotRoot $rel) -Encoding UTF8
    $slices = @(
        @{ End = 56; Message = "style: theme color tokens" },
        @{ End = 119; Message = "style: base layout styles" },
        @{ End = 208; Message = "style: interaction utilities" }
    )
    foreach ($s in $slices) {
        Write-CumulativeSlice -RelativePath $rel -AllLines $lines -EndIndex $s.End -Message $s.Message
    }
}

function Commit-HeaderSections {
    $rel = "src\components\layout\Header.tsx"
    $lines = Get-Content (Join-Path $SnapshotRoot $rel) -Encoding UTF8
    $slices = @(
        @{ End = 89; Message = "feat: header scroll behavior" },
        @{ End = 178; Message = "feat: header logo nav bar" },
        @{ End = 221; Message = "feat: header mobile drawer" }
    )
    foreach ($s in $slices) {
        Write-CumulativeSlice -RelativePath $rel -AllLines $lines -EndIndex $s.End -Message $s.Message
    }
}

function Commit-ReadmeSections {
    $rel = "README.md"
    $lines = Get-Content (Join-Path $SnapshotRoot $rel) -Encoding UTF8
    $slices = @(
        @{ End = 15; Message = "docs: landing page intro" },
        @{ End = 40; Message = "docs: landing highlights table" },
        @{ End = 83; Message = "docs: landing setup guide" },
        @{ End = 128; Message = "docs: landing folder structure" },
        @{ End = 148; Message = "docs: landing maintainer guide" }
    )
    foreach ($s in $slices) {
        Write-CumulativeSlice -RelativePath $rel -AllLines $lines -EndIndex $s.End -Message $s.Message
    }
}

function Commit-HeroSections {
    $rel = "src\features\hero\Hero.tsx"
    $lines = Get-Content (Join-Path $SnapshotRoot $rel) -Encoding UTF8
    $slices = @(
        @{ End = 54; Message = "feat: hero trust badge row" },
        @{ End = 109; Message = "feat: hero social proof row" }
    )
    foreach ($s in $slices) {
        Write-CumulativeSlice -RelativePath $rel -AllLines $lines -EndIndex $s.End -Message $s.Message
    }
}

function Commit-ContributionSections {
    $rel = "src\features\contribution\Contribution.tsx"
    $lines = Get-Content (Join-Path $SnapshotRoot $rel) -Encoding UTF8
    $slices = @(
        @{ End = 51; Message = "feat: open source qr block" },
        @{ End = 104; Message = "feat: open source cta row" }
    )
    foreach ($s in $slices) {
        Write-CumulativeSlice -RelativePath $rel -AllLines $lines -EndIndex $s.End -Message $s.Message
    }
}

function Commit-TrustedAdvantageMain {
    $rel = "src\features\trusted-advantage\TrustedAdvantage.tsx"
    $lines = Get-Content (Join-Path $SnapshotRoot $rel) -Encoding UTF8
    $slices = @(
        @{ End = 95; Message = "feat: trust advantage layout" },
        @{ End = 191; Message = "feat: trust promise stats row" }
    )
    foreach ($s in $slices) {
        Write-CumulativeSlice -RelativePath $rel -AllLines $lines -EndIndex $s.End -Message $s.Message
    }
}

function Commit-EverydayWalletMain {
    $rel = "src\features\everyday-wallet\EverydayWallet.tsx"
    $lines = Get-Content (Join-Path $SnapshotRoot $rel) -Encoding UTF8
    $slices = @(
        @{ End = 55; Message = "feat: wallet send receive block" },
        @{ End = 111; Message = "feat: wallet bills promo block" }
    )
    foreach ($s in $slices) {
        Write-CumulativeSlice -RelativePath $rel -AllLines $lines -EndIndex $s.End -Message $s.Message
    }
}

function Commit-FooterSections {
    $rel = "src\components\layout\Footer.tsx"
    $lines = Get-Content (Join-Path $SnapshotRoot $rel) -Encoding UTF8
    if ($lines.Count -le 100) {
        $target = Join-Path $RepoRoot "landingpage/src/components/layout/Footer.tsx"
        Copy-Item (Join-Path $SnapshotRoot $rel) $target -Force
        Commit-Paths "feat: site footer cta" @("landingpage/src/components/layout/Footer.tsx")
    } else {
        $slices = @(
            @{ End = 62; Message = "feat: footer nav links row" },
            @{ End = 125; Message = "feat: footer cta copyright" }
        )
        foreach ($s in $slices) {
            Write-CumulativeSlice -RelativePath $rel -AllLines $lines -EndIndex $s.End -Message $s.Message
        }
    }
}
