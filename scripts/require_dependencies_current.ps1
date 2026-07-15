$ErrorActionPreference = "Stop"
$script = Join-Path $PSScriptRoot "check_dependencies.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -File $script
if ($LASTEXITCODE -ne 0) {
  Write-Host "guardrail: lectern0 dependency is stale, dirty, missing, or API-incompatible"
  exit $LASTEXITCODE
}
Write-Host "guardrail: lectern0 dependencies are current"
