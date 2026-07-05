# Replay landingpage/ with semantic commit messages (no "part N").
param(
    [Parameter(Mandatory = $true)]
    [string]$SnapshotRoot
)

$ErrorActionPreference = "Stop"
$RepoRoot = git rev-parse --show-toplevel
Set-Location $RepoRoot

. "$RepoRoot/infrastructure/scripts/git-hooks/semantic-split.ps1" -SnapshotRoot $SnapshotRoot -RepoRoot $RepoRoot

function Commit-File($msg, [string]$relPath) {
    $src = Join-Path $SnapshotRoot $relPath
    if (-not (Test-Path $src)) { throw "Missing: $relPath" }
    $dest = Join-Path $RepoRoot ("landingpage/" + $relPath.Replace("\", "/"))
    $destDir = Split-Path $dest -Parent
    if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir -Force | Out-Null }
    Copy-Item -Path $src -Destination $dest -Force
    Commit-Paths $msg @("landingpage/$($relPath.Replace('\','/'))")
}

Write-Host "=== Tooling ==="
Commit-File "chore: init next.js package" "package.json"
Commit-File "chore: configure typescript aliases" "tsconfig.json"
Commit-File "chore: configure next.js build" "next.config.ts"
Commit-File "chore: configure eslint rules" "eslint.config.mjs"
Commit-File "chore: configure postcss pipeline" "postcss.config.mjs"
Commit-File "chore: ignore local env files" ".gitignore"
Commit-File "chore: treat svg as binary" ".gitattributes"
$lockSrc = Join-Path $SnapshotRoot "package-lock.json"
Copy-Item $lockSrc "landingpage/package-lock.json" -Force
& "$RepoRoot/infrastructure/scripts/git-hooks/commit-batch.ps1" -Paths @("landingpage/package-lock.json") -Message "chore: add npm lockfile" -AllowOverLimit

Write-Host "=== App shell ==="
Commit-File "feat: root layout shell" "src\app\layout.tsx"
Commit-File "feat: home page sections" "src\app\page.tsx"
Commit-GlobalsCssSections
Commit-File "assets: favicon icon" "src\app\favicon.ico"
Commit-File "assets: app icon svg" "src\app\icon.svg"
Commit-File "assets: apple touch icon" "src\app\apple-icon.svg"

Write-Host "=== Foundation ==="
Commit-File "content: copy normalization helper" "src\content\normalize-copy.ts"
Commit-SiteCopySections
Commit-File "feat: scroll helper utilities" "src\lib\scroll.ts"
Commit-File "feat: external link constants" "src\lib\links.ts"
Commit-File "feat: motion preset utilities" "src\lib\motion.ts"
Commit-File "feat: classname merge utility" "src\lib\utils.ts"
Commit-File "feat: theme provider setup" "src\providers\theme-provider.tsx"
Commit-File "feat: providers barrel export" "src\providers\index.tsx"
Commit-File "feat: app context scaffold" "src\contexts\index.tsx"
Commit-File "feat: client mount hook" "src\hooks\use-mounted.ts"
Commit-File "feat: shared constants scaffold" "src\constants\index.ts"
Commit-File "feat: shared types scaffold" "src\types\index.ts"

Write-Host "=== Shared UI ==="
Commit-File "feat: primary button component" "src\components\ui\Button.tsx"
Commit-File "feat: bento chip component" "src\components\ui\BentoChip.tsx"
Commit-File "feat: ui components barrel" "src\components\ui\index.ts"
Commit-File "feat: scroll reveal animation" "src\components\motion\Reveal.tsx"
Commit-File "feat: hero entrance animation" "src\components\motion\HeroEntrance.tsx"
Commit-File "feat: staggered reveal group" "src\components\motion\RevealGroup.tsx"
Commit-File "feat: motion components barrel" "src\components\motion\index.ts"
Commit-HeaderSections
Commit-FooterSections
Commit-File "feat: layout components barrel" "src\components\layout\index.ts"
Commit-File "feat: feature banner styles" "src\shared\feature-banner\styles.ts"
Commit-File "feat: feature banner barrel" "src\shared\feature-banner\index.ts"
Commit-File "feat: feature banner content" "src\shared\feature-banner\FeatureBannerContent.tsx"
Commit-File "feat: feature banner phone" "src\shared\feature-banner\FeatureBannerPhone.tsx"

Write-Host "=== Feature sections ==="
Commit-HeroSections
Commit-File "feat: hero section export" "src\features\hero\index.ts"
Commit-EverydayWalletMain
Commit-File "feat: wallet section styles" "src\features\everyday-wallet\styles.ts"
Commit-File "feat: wallet section export" "src\features\everyday-wallet\index.ts"
Commit-File "feat: product features grid" "src\features\product-features\Features.tsx"
Commit-File "feat: features section export" "src\features\product-features\index.ts"
Commit-File "feat: cards waitlist section" "src\features\cards\Cards.tsx"
Commit-File "feat: cards section export" "src\features\cards\index.ts"
Commit-File "feat: transfer showcase section" "src\features\transfer\Transfer.tsx"
Commit-File "feat: transfer section export" "src\features\transfer\index.ts"
Commit-File "feat: bills payment section" "src\features\bills\BillsPayments.tsx"
Commit-File "feat: bills section export" "src\features\bills\index.ts"
Commit-File "feat: coming soon section" "src\features\coming-soon\ComingSoon.tsx"
Commit-File "feat: coming soon export" "src\features\coming-soon\index.ts"
Commit-File "feat: trust balance mockup" "src\features\trusted-advantage\BalanceMockup.tsx"
Commit-File "feat: trust shield card picker" "src\features\trusted-advantage\ShieldCardPicker.tsx"
Commit-File "feat: trust shield card visual" "src\features\trusted-advantage\ShieldCardVisual.tsx"
Commit-TrustedAdvantageMain
Commit-File "feat: trust section styles" "src\features\trusted-advantage\styles.ts"
Commit-File "feat: trust section export" "src\features\trusted-advantage\index.ts"
Commit-File "feat: founder quote section" "src\features\founder-quote\FounderQuote.tsx"
Commit-File "feat: founder quote export" "src\features\founder-quote\index.ts"
Commit-File "feat: security layer section" "src\features\secured-vc\SecuredVc.tsx"
Commit-File "feat: security section export" "src\features\secured-vc\index.ts"
Commit-File "feat: faq section layout" "src\features\faq\Faq.tsx"
Commit-File "feat: faq accordion component" "src\features\faq\FaqAccordion.tsx"
Commit-File "feat: faq section export" "src\features\faq\index.ts"
Commit-ContributionSections
Commit-File "feat: contribution section export" "src\features\contribution\index.ts"
Commit-File "feat: export section barrel" "src\features\index.ts"

Write-Host "=== Public assets ==="
$publicFiles = @(
    @("assets: hero phone mockup png", "public\images\hero\Hero-mobile-mock.png"),
    @("assets: hero home phone mockup svg", "public\images\hero\Home-mobile-mock.svg"),
    @("assets: pay bills icon png", "public\images\features\pay-bills.png"),
    @("assets: pay bills feature icon svg", "public\images\features\payb-bills.svg"),
    @("assets: secure payment icon svg", "public\images\features\secure-payment.svg"),
    @("assets: secure payments icon png", "public\images\features\secure-payments.png"),
    @("assets: track bills icon png", "public\images\features\track-bills.png"),
    @("assets: track bills feature icon svg", "public\images\features\track-bills.svg"),
    @("assets: stacking cards png", "public\images\cards\stacking-cards.png"),
    @("assets: stacking cards svg", "public\images\cards\Stacking-cards.svg"),
    @("assets: transfer screen mockup svg", "public\images\transfer\phone-mockup-transfer.svg"),
    @("assets: bills screen mockup svg", "public\images\bills\phone-mockup-bills.svg"),
    @("assets: coming soon mockup png", "public\images\coming-soon\phone-mockup-coming-soon.png"),
    @("assets: coming soon mockup svg", "public\images\coming-soon\phone-mockup-coming-soon.svg"),
    @("assets: wallet bills icon svg", "public\images\everyday-wallet\bills.svg"),
    @("assets: wallet multi icons svg", "public\images\everyday-wallet\multi-icons.svg"),
    @("assets: wallet phone mockup svg", "public\images\everyday-wallet\phone-mockup.svg"),
    @("assets: wallet send receive svg", "public\images\everyday-wallet\send-receive.svg"),
    @("assets: gold trust card svg", "public\images\trusted-advantage\gold-card.svg"),
    @("assets: stacked trust cards svg", "public\images\trusted-advantage\stacked-cards.svg"),
    @("assets: founder avatar png", "public\images\founder-quote\avatar.png"),
    @("assets: security red card svg", "public\images\secured-vc\red-card.svg"),
    @("assets: faq section icons svg", "public\images\faq\faq-icons.svg"),
    @("assets: open source device mockup svg", "public\images\contribution\device-mockup.svg"),
    @("assets: open source qr code svg", "public\images\contribution\qr-code.svg"),
    @("assets: next default icon svg", "public\file.svg"),
    @("assets: globe default icon svg", "public\globe.svg"),
    @("assets: next logo icon svg", "public\next.svg"),
    @("assets: vercel logo icon svg", "public\vercel.svg"),
    @("assets: window default icon svg", "public\window.svg")
)
foreach ($item in $publicFiles) {
    Commit-File $item[0] $item[1]
}

Write-Host "=== Source assets ==="
Commit-File "assets: hero avatar one jpg" "src\assets\avatar1.jpg"
Commit-File "assets: hero avatar two jpg" "src\assets\avatar2.jpg"
Commit-File "assets: contribution cta device svg" "src\assets\Contribution-section device mock.svg"

Write-Host "=== Docs ==="
Commit-ReadmeSections

Write-Host "=== Replay complete ==="
git log --oneline -5
