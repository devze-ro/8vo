param(
  [ValidateSet("Win32Pdf", "AndroidEpub")]
  [string]$Target = "Win32Pdf"
)

$ErrorActionPreference = "Stop"
$script = Join-Path $PSScriptRoot "check_dependencies.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -File $script -Target $Target
if ($LASTEXITCODE -ne 0) {
  Write-Host "guardrail: octavo dependency is stale, dirty, missing, or API-incompatible"
  exit $LASTEXITCODE
}
Write-Host "guardrail: octavo dependencies are current target=$Target"
