param(
  [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$failures = [System.Collections.Generic.List[string]]::new()
$buildPath = Join-Path $RepoRoot "code\build.c"
$appPath = Join-Path $RepoRoot "code\lectern0.c"
if (!(Test-Path -LiteralPath $buildPath)) { $failures.Add("missing code/build.c") }
if (!(Test-Path -LiteralPath $appPath)) { $failures.Add("missing code/lectern0.c") }

if ($failures.Count -eq 0) {
  $build = [System.IO.File]::ReadAllText($buildPath)
  $app = [System.IO.File]::ReadAllText($appPath)
  if ([regex]::Matches($build, '#include\s+"reader0\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile reader0.c exactly once")
  }
  if ([regex]::Matches($build, '#include\s+"ui0\.c"').Count -ne 1) {
    $failures.Add("code/build.c must compile ui0.c exactly once")
  }
  if ($app.IndexOf('#include "reader0.h"') -lt 0) {
    $failures.Add("lectern0 must consume the reader0 umbrella")
  }
  if ($app.IndexOf('#include "ui0.h"') -lt 0) {
    $failures.Add("lectern0 must consume the UI0 umbrella")
  }
  if ($app.IndexOf('epub_reader_move_page') -lt 0) {
    $failures.Add("lectern0 must consume the concrete reader0 API 2 page move")
  }
  if ($build -match '(?i)re10' -or $app -match '(?i)re10') {
    $failures.Add("lectern0 source closure must not depend on re10")
  }
}

if ($failures.Count -gt 0) {
  $failures | ForEach-Object { Write-Error $_ }
  exit 1
}

Write-Host "lectern0 architecture audit: pass"
Write-Host "boundary: zero_foundation + ui0 host chrome + reader0 concrete EPUB core"
