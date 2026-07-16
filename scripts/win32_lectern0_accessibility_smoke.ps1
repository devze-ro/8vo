param(
  [string]$BookPath = "local\slice1_host_smoke\lectern0_slice1.epub"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Exe = Join-Path $Root "build\win32\lectern0.exe"
if (!(Test-Path -LiteralPath $Exe -PathType Leaf)) {
  throw "missing lectern0 executable: $Exe"
}

$ResolvedBook = if ([System.IO.Path]::IsPathRooted($BookPath)) {
  (Resolve-Path -LiteralPath $BookPath).Path
} else {
  $candidate = Join-Path $Root $BookPath
  if (!(Test-Path -LiteralPath $candidate -PathType Leaf)) {
    & (Join-Path $PSScriptRoot "win32_lectern0_host_smoke.ps1") | Write-Host
  }
  (Resolve-Path -LiteralPath $candidate).Path
}

$lines = @()
for ($run = 0; $run -lt 2; $run += 1) {
  $output = & $Exe --accessibility-smoke $ResolvedBook 2>&1
  if ($LASTEXITCODE -ne 0) {
    $output | Write-Host
    throw "lectern0 accessibility smoke failed with exit code $LASTEXITCODE"
  }
  $line = $output | Where-Object {
    $_ -match '^lectern0_accessibility_smoke result=pass '
  } | Select-Object -Last 1
  if (!$line) {
    $output | Write-Host
    throw "lectern0 accessibility smoke did not report native adapter evidence"
  }
  $lines += [string]$line
}
if ($lines[0] -ne $lines[1]) {
  throw "lectern0 accessibility smoke is not repeatable"
}

Write-Host $lines[1]
Write-Host "win32_lectern0_accessibility_smoke result=pass repeat=2"
