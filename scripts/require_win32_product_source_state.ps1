param(
  [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
  [switch]$AllowDirtyDevelopment
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 3.0
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path

$commit = (& git -C $RepoRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or !$commit) {
  throw "could not resolve the 8vo product commit"
}
$tree = (& git -C $RepoRoot rev-parse 'HEAD^{tree}').Trim()
if ($LASTEXITCODE -ne 0 -or !$tree) {
  throw "could not resolve the 8vo product tree"
}
$status = @(& git -C $RepoRoot status --porcelain=v1 --untracked-files=all)
if ($LASTEXITCODE -ne 0) {
  throw "could not inspect the complete 8vo product source state"
}
$status = @($status | ForEach-Object { [string]$_ })
$verifiedClean = $status.Count -eq 0
foreach ($line in $status) {
  Write-Host "8vo product source evidence: $line"
}

if (!$verifiedClean -and !$AllowDirtyDevelopment) {
  Write-Error "release-profile Win32 PDF build requires a clean 8vo tree; use only the explicit development override for non-release qualification"
  exit 1
}

$releaseEligible = $verifiedClean -and !$AllowDirtyDevelopment
$profile = if ($releaseEligible) { "release-clean" } else {
  "development-nonrelease"
}
Write-Host "8vo product source state: pass profile=$profile verified_clean=$($verifiedClean.ToString().ToLowerInvariant()) release_eligible=$($releaseEligible.ToString().ToLowerInvariant()) development_override=$($AllowDirtyDevelopment.ToString().ToLowerInvariant()) status_count=$($status.Count) commit=$commit tree=$tree"
