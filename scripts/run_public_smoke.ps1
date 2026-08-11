param(
  [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path

if (!$SkipBuild) {
  & cmd /c (Join-Path $Root "build\win32_build.bat") no_run
  if ($LASTEXITCODE -ne 0) {
    throw "8vo build failed with exit code $LASTEXITCODE"
  }
}

$tests = @(
  "win32_pdf_product_source_state_smoke.ps1",
  "win32_octavo_pdf_stage1_smoke.ps1",
  "win32_octavo_pdf_content_smoke.ps1",
  "win32_octavo_pdf_selection_smoke.ps1",
  "win32_octavo_host_smoke.ps1",
  "win32_octavo_data_migration_smoke.ps1",
  "win32_octavo_reader_view_smoke.ps1",
  "win32_octavo_reader_view_startup_interaction_smoke.ps1",
  "win32_octavo_accessibility_smoke.ps1",
  "win32_octavo_visual_smoke.ps1",
  "win32_octavo_image_smoke.ps1"
)

foreach ($name in $tests) {
  $script = Join-Path $PSScriptRoot $name
  Write-Host "public smoke: $name"
  & powershell -NoProfile -ExecutionPolicy Bypass -File $script
  if ($LASTEXITCODE -ne 0) {
    throw "$name failed with exit code $LASTEXITCODE"
  }
}

Write-Host "octavo_public_smoke result=pass build=$(!$SkipBuild) tests=$($tests.Count)"
