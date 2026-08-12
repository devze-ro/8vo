param(
  [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$guard = Join-Path $RepoRoot "scripts\require_win32_product_source_state.ps1"
$build = Join-Path $RepoRoot "build\win32_build.bat"
$sentinelName = "octavo-untracked-provenance-probe-$([Guid]::NewGuid().ToString('N')).tmp"
$sentinel = Join-Path $RepoRoot $sentinelName
if (Test-Path -LiteralPath $sentinel) {
  throw "product source-state sentinel already exists: $sentinel"
}

function Get-ProductStatusText {
  $lines = @(& git -C $RepoRoot status --porcelain=v1 --untracked-files=all)
  if ($LASTEXITCODE -ne 0) { throw "could not inspect product status" }
  return @($lines | ForEach-Object { [string]$_ }) -join "`n"
}

function Invoke-SourceStateGuard {
  param([switch]$AllowDirtyDevelopment)

  $arguments = @(
    "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $guard,
    "-RepoRoot", $RepoRoot
  )
  if ($AllowDirtyDevelopment) { $arguments += "-AllowDirtyDevelopment" }
  $previousPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    $output = & powershell @arguments 2>&1
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousPreference
  }
  return [pscustomobject]@{
    ExitCode = $exitCode
    Text = @($output | ForEach-Object { [string]$_ }) -join "`n"
  }
}

function Invoke-DefaultBuildGuard {
  $oldOverride = [Environment]::GetEnvironmentVariable(
    "OCTAVO_ALLOW_DIRTY_DEVELOPMENT_BUILD", "Process")
  $previousPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    [Environment]::SetEnvironmentVariable(
      "OCTAVO_ALLOW_DIRTY_DEVELOPMENT_BUILD", $null, "Process")
    $output = & cmd /c $build no_run 2>&1
    $exitCode = $LASTEXITCODE
  } finally {
    [Environment]::SetEnvironmentVariable(
      "OCTAVO_ALLOW_DIRTY_DEVELOPMENT_BUILD", $oldOverride, "Process")
    $ErrorActionPreference = $previousPreference
  }
  return [pscustomobject]@{
    ExitCode = $exitCode
    Text = @($output | ForEach-Object { [string]$_ }) -join "`n"
  }
}

$baseline = Get-ProductStatusText
try {
  [IO.File]::WriteAllText(
    $sentinel, "8vo provenance negative guard probe`n",
    [Text.UTF8Encoding]::new($false))
  if (!(Test-Path -LiteralPath $sentinel -PathType Leaf)) {
    throw "could not create product source-state sentinel"
  }

  $release = Invoke-SourceStateGuard
  if ($release.ExitCode -eq 0 -or
      $release.Text -notmatch [regex]::Escape($sentinelName) -or
      $release.Text -notmatch 'release-profile Win32 PDF build requires a clean 8vo tree') {
    Write-Host $release.Text
    throw "release source-state guard accepted an untracked product input"
  }

  $releaseBuild = Invoke-DefaultBuildGuard
  if ($releaseBuild.ExitCode -eq 0 -or
      $releaseBuild.Text -notmatch [regex]::Escape($sentinelName) -or
      $releaseBuild.Text -match '\[8vo\] Compiling native reader host') {
    Write-Host $releaseBuild.Text
    throw "default build did not fail dirty before product compilation"
  }

  $development = Invoke-SourceStateGuard -AllowDirtyDevelopment
  if ($development.ExitCode -ne 0 -or
      $development.Text -notmatch [regex]::Escape($sentinelName) -or
      $development.Text -notmatch
        'profile=development-nonrelease .*release_eligible=false development_override=true') {
    Write-Host $development.Text
    throw "development source-state override did not retain non-release evidence"
  }
} finally {
  if (Test-Path -LiteralPath $sentinel) {
    Remove-Item -LiteralPath $sentinel -Force
  }
}

if (Test-Path -LiteralPath $sentinel) {
  throw "could not remove product source-state sentinel"
}
$after = Get-ProductStatusText
if ($after -cne $baseline) {
  throw "product source-state smoke did not restore its exact status baseline"
}
Write-Host "win32_pdf_product_source_state_smoke result=pass release=rejects_dirty build=precompile_reject development=nonrelease_evidence cleanup=verified"
