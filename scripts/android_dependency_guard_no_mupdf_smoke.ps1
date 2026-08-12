param(
  [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$guard = Join-Path $RepoRoot "scripts\check_dependencies.ps1"
$missingMupdf = Join-Path $RepoRoot "local\__missing_android_mupdf_guard__"
if (Test-Path -LiteralPath $missingMupdf) {
  throw "no-MuPDF guard sentinel unexpectedly exists: $missingMupdf"
}

function Invoke-DependencyGuard {
  param([Parameter(Mandatory = $true)][string]$Target)

  $previousPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $guard `
      -RepoRoot $RepoRoot -Target $Target 2>&1
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousPreference
  }
  return [pscustomobject]@{
    ExitCode = $exitCode
    Text = @($output | ForEach-Object { [string]$_ }) -join "`n"
  }
}

$oldOctavoMupdf = [Environment]::GetEnvironmentVariable(
  "OCTAVO_MUPDF_DIR", "Process")
$oldMupdf = [Environment]::GetEnvironmentVariable("MUPDF_DIR", "Process")
try {
  $env:OCTAVO_MUPDF_DIR = $missingMupdf
  $env:MUPDF_DIR = $missingMupdf

  $android = Invoke-DependencyGuard -Target "AndroidEpub"
  if ($android.ExitCode -ne 0 -or
      $android.Text -notmatch
        [regex]::Escape("MuPDF dependency: skipped for AndroidEpub target")) {
    Write-Host $android.Text
    throw "Android EPUB guard resolved or required MuPDF"
  }

  $win32 = Invoke-DependencyGuard -Target "Win32Pdf"
  if ($win32.ExitCode -eq 0 -or
      $win32.Text -notmatch
        [regex]::Escape("OCTAVO_MUPDF_DIR does not name an existing directory")) {
    Write-Host $win32.Text
    throw "Win32 PDF guard accepted a missing MuPDF checkout"
  }
} finally {
  [Environment]::SetEnvironmentVariable(
    "OCTAVO_MUPDF_DIR", $oldOctavoMupdf, "Process")
  [Environment]::SetEnvironmentVariable("MUPDF_DIR", $oldMupdf, "Process")
}

if (Test-Path -LiteralPath $missingMupdf) {
  throw "dependency guard created the missing MuPDF sentinel"
}
Write-Host "android_dependency_guard_no_mupdf_smoke result=pass android=skipped win32=required"
