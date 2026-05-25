# GitHub Actions

## CI (`ci.yml`)

Runs on every push and pull request to `master` / `main`.

- Matrix build for the four production services
- `mvn clean verify` with OWASP dependency-check skipped (`-Ddependency.check.skip=true`)
- Uploads Surefire/Failsafe reports when a job fails

## CD (`cd.yml`)

Runs on push to `master` / `main` and version tags `v*.*.*`.

1. Runs `infrastructure/scripts/ci-test.sh` (all four services sequentially)
2. Builds and pushes Docker images to GitHub Container Registry:
   - `ghcr.io/<owner>/<repo>/elvo-identity-service`
   - `ghcr.io/<owner>/<repo>/elvo-wallet-service`
   - `ghcr.io/<owner>/<repo>/elvo-billing-service`
   - `ghcr.io/<owner>/<repo>/elvo-account-management-service`

Images are tagged with branch name, git SHA, and `latest` on the default branch.

## Local parity

```bash
bash infrastructure/scripts/ci-test.sh
```

PowerShell equivalent:

```powershell
$services = @(
  'services/elvo-identity-service',
  'services/elvo-wallet-service',
  'services/elvo-billing-service',
  'services/elvo-account-management-service'
)
foreach ($s in $services) {
  Push-Location $s
  mvn clean verify "-Ddependency.check.skip=true"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  Pop-Location
}
```
